package ir.nama.launcher.context

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import ir.nama.core.ContextSnapshot
import ir.nama.core.IranCalendar
import ir.nama.core.Jalali
import ir.nama.core.Trigger
import ir.nama.launcher.data.NetState
import ir.nama.launcher.data.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalTime

/**
 * Watches the phone's situation and publishes a [ContextSnapshot]. Everything here is passive:
 * it listens to system broadcasts and never polls GPS.
 */
class ContextMonitor(private val context: Context, private val store: Store, private val netState: StateFlow<NetState>) {
    private val _snapshot = MutableStateFlow(initial())
    val snapshot: StateFlow<ContextSnapshot> = _snapshot.asStateFlow()

    /** Bluetooth device names/addresses seen connected (for the space editor). */
    val recentBluetooth = MutableStateFlow<Set<String>>(emptySet())
    private val main = Handler(Looper.getMainLooper())
    private var started = false

    private fun initial(): ContextSnapshot {
        val now = LocalTime.now()
        val today = LocalDate.now()
        val off = store.settings.value.hijriOffset
        return ContextSnapshot(
            minuteOfDay = now.hour * 60 + now.minute,
            weekIndex = Jalali.weekIndex(today),
            holiday = IranCalendar.isHoliday(today, off),
            ramadan = IranCalendar.isRamadan(today, off)
        )
    }

    fun hasPermission(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (started) return
        started = true
        registerTime()
        registerBattery()
        registerAudio()
        registerWifi()
        registerBluetooth()
        refreshLocation()
    }

    /** Called when the home screen becomes visible, to catch up after permission changes. */
    fun refresh() {
        tickTime()
        if (!wifiRegistered) registerWifi()
        if (!btRegistered) registerBluetooth()
        refreshLocation()
        _snapshot.update { it.copy(internationalDown = netState.value == NetState.NATIONAL_ONLY) }
    }

    fun onNetState(s: NetState) {
        _snapshot.update { it.copy(internationalDown = s == NetState.NATIONAL_ONLY) }
    }

    // --- time -----------------------------------------------------------------------------------
    private fun registerTime() {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        register(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = tickTime()
        }, f)
    }

    private var lastDay: LocalDate? = null

    private fun tickTime() {
        val now = LocalTime.now()
        val today = LocalDate.now()
        val off = store.settings.value.hijriOffset
        if (today != lastDay) {
            lastDay = today
            _snapshot.update {
                it.copy(
                    weekIndex = Jalali.weekIndex(today),
                    holiday = IranCalendar.isHoliday(today, off),
                    ramadan = IranCalendar.isRamadan(today, off)
                )
            }
        }
        _snapshot.update { it.copy(minuteOfDay = now.hour * 60 + now.minute) }
    }

    // --- battery --------------------------------------------------------------------------------
    private fun registerBattery() {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        val sticky = register(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = onBattery(i)
        }, f)
        sticky?.let { onBattery(it) }
    }

    val batteryPercent = MutableStateFlow(100)
    val charging = MutableStateFlow(false)

    private fun onBattery(i: Intent) {
        when (i.action) {
            Intent.ACTION_POWER_CONNECTED -> setCharging(true)
            Intent.ACTION_POWER_DISCONNECTED -> setCharging(false)
            Intent.ACTION_BATTERY_CHANGED -> {
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val pct = if (level >= 0 && scale > 0) level * 100 / scale else 100
                batteryPercent.value = pct
                val ch = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                charging.value = ch
                _snapshot.update { it.copy(batteryPercent = pct, charging = ch) }
            }
        }
    }

    private fun setCharging(v: Boolean) {
        charging.value = v
        _snapshot.update { it.copy(charging = v) }
    }

    // --- headphones -----------------------------------------------------------------------------
    private fun registerAudio() {
        try {
            val am = context.getSystemService(AudioManager::class.java)
            am.registerAudioDeviceCallback(object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = updateHeadphones(am)
                override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = updateHeadphones(am)
            }, main)
            updateHeadphones(am)
        } catch (e: Exception) {
            Log.w("Nama", "audio callback failed", e)
        }
    }

    private fun updateHeadphones(am: AudioManager) {
        val types = mutableSetOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_USB_HEADSET
        )
        if (Build.VERSION.SDK_INT >= 31) types += AudioDeviceInfo.TYPE_BLE_HEADSET
        val on = try { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in types } } catch (e: Exception) { false }
        _snapshot.update { it.copy(headphones = on) }
    }

    // --- Wi-Fi ----------------------------------------------------------------------------------
    private var wifiRegistered = false
    val currentSsid = MutableStateFlow<String?>(null)

    private fun locationGranted() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

    @SuppressLint("MissingPermission")
    private fun registerWifi() {
        if (!locationGranted()) return
        try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val req = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
            val cb = if (Build.VERSION.SDK_INT >= 31) {
                object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                        val ssid = (caps.transportInfo as? WifiInfo)?.ssid
                        setSsid(ssid)
                    }
                    override fun onLost(network: Network) = setSsid(null)
                }
            } else {
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) = setSsid(legacySsid())
                    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = setSsid(legacySsid())
                    override fun onLost(network: Network) = setSsid(null)
                }
            }
            cm.registerNetworkCallback(req, cb)
            wifiRegistered = true
        } catch (e: Exception) {
            Log.w("Nama", "wifi callback failed", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun legacySsid(): String? = try {
        context.applicationContext.getSystemService(WifiManager::class.java).connectionInfo?.ssid
    } catch (e: Exception) {
        null
    }

    private fun setSsid(raw: String?) {
        val ssid = raw?.trim('"')?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID.trim('<', '>') && !it.contains("unknown ssid") }
        currentSsid.value = ssid
        _snapshot.update { it.copy(wifiSsid = ssid) }
    }

    // --- Bluetooth ------------------------------------------------------------------------------
    private var btRegistered = false
    private val connectedBt = mutableSetOf<String>()

    private fun btGranted() = Build.VERSION.SDK_INT < 31 || hasPermission(Manifest.permission.BLUETOOTH_CONNECT)

    @SuppressLint("MissingPermission")
    private fun registerBluetooth() {
        if (!btGranted()) return
        try {
            val f = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            register(object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    val d: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
                        i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    d ?: return
                    val ids = deviceIds(d)
                    synchronized(connectedBt) {
                        if (i.action == BluetoothDevice.ACTION_ACL_CONNECTED) connectedBt += ids else connectedBt -= ids.toSet()
                        publishBt()
                    }
                }
            }, f)
            btRegistered = true
            // Seed with devices already connected through audio profiles.
            val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
            for (profile in listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
                adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                        try {
                            synchronized(connectedBt) {
                                proxy.connectedDevices.forEach { connectedBt += deviceIds(it) }
                                publishBt()
                            }
                        } catch (_: SecurityException) {
                        }
                        try { adapter.closeProfileProxy(p, proxy) } catch (_: Exception) {}
                    }
                    override fun onServiceDisconnected(p: Int) {}
                }, profile)
            }
        } catch (e: Exception) {
            Log.w("Nama", "bluetooth setup failed", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun deviceIds(d: BluetoothDevice): List<String> = try {
        listOfNotNull(d.name, d.address)
    } catch (e: SecurityException) {
        listOfNotNull(d.address)
    }

    private fun publishBt() {
        val set = connectedBt.toSet()
        recentBluetooth.update { it + set }
        _snapshot.update { it.copy(bluetoothDevices = set) }
    }

    @SuppressLint("MissingPermission")
    fun pairedBluetoothNames(): List<String> = try {
        if (!btGranted()) emptyList() else
            context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices?.mapNotNull { it.name }?.sorted() ?: emptyList()
    } catch (e: Exception) { emptyList() }

    // --- Location -------------------------------------------------------------------------------
    private var locationListening = false

    private fun needsLocation(): Boolean = store.spaces.value.any { s -> s.enabled && s.triggers.any { it is Trigger.Location } }

    @SuppressLint("MissingPermission")
    fun refreshLocation() {
        if (!locationGranted() || !needsLocation()) return
        try {
            val lm = context.getSystemService(LocationManager::class.java)
            val best = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
                .mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (e: Exception) { null } }
                .maxByOrNull { it.time }
            best?.let { onLocation(it) }
            if (!locationListening) {
                lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 5 * 60 * 1000L, 100f, object : LocationListener {
                    override fun onLocationChanged(location: Location) = onLocation(location)
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }, Looper.getMainLooper())
                locationListening = true
            }
        } catch (e: Exception) {
            Log.w("Nama", "location failed", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun currentLocation(): Location? = try {
        if (!locationGranted()) null else {
            val lm = context.getSystemService(LocationManager::class.java)
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (e: Exception) { null } }
                .maxByOrNull { it.time }
        }
    } catch (e: Exception) { null }

    private fun onLocation(l: Location) {
        _snapshot.update { it.copy(lat = l.latitude, lng = l.longitude) }
    }

    // --- helpers --------------------------------------------------------------------------------
    private fun register(r: BroadcastReceiver, f: IntentFilter): Intent? = try {
        ContextCompat.registerReceiver(context, r, f, ContextCompat.RECEIVER_EXPORTED)
    } catch (e: Exception) {
        Log.w("Nama", "receiver failed", e)
        null
    }
}
