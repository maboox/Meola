package ir.nama.launcher.data

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import android.provider.Settings as AndroidSettings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.service.notification.StatusBarNotification
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.security.KeyStore
import java.time.LocalDate
import java.time.ZoneId
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Screen time from UsageStatsManager (only after the user grants usage access). */
class UsageRepo(private val context: Context) {
    private val _today = MutableStateFlow<Map<String, Long>>(emptyMap())
    val today: StateFlow<Map<String, Long>> = _today.asStateFlow()
    private var lastRefresh = 0L

    fun hasPermission(): Boolean = try {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        false
    }

    private fun startOfDay(daysAgo: Long = 0): Long =
        LocalDate.now().minusDays(daysAgo).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Foreground time per package between [from] and [to], in milliseconds. */
    suspend fun usage(from: Long, to: Long = System.currentTimeMillis()): Map<String, Long> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyMap()
        try {
            val usm = context.getSystemService(UsageStatsManager::class.java)
            usm.queryAndAggregateUsageStats(from, to)
                .mapValues { it.value.totalTimeInForeground }
                .filter { it.value > 30_000 && it.key != context.packageName }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun refreshToday(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - lastRefresh < 60_000) return
        lastRefresh = System.currentTimeMillis()
        _today.value = usage(startOfDay())
    }

    suspend fun lastWeek(): Map<String, Long> = usage(startOfDay(7), startOfDay())
    suspend fun thisWeek(): Map<String, Long> = usage(startOfDay(6))

    /** Last time each package was used (for "unused for N days" rules). */
    suspend fun lastUsed(): Map<String, Long> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyMap()
        try {
            val usm = context.getSystemService(UsageStatsManager::class.java)
            usm.queryAndAggregateUsageStats(System.currentTimeMillis() - 120L * 24 * 3600 * 1000, System.currentTimeMillis())
                .mapValues { it.value.lastTimeUsed }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** Mobile data used between two times in bytes. Uses NetworkStatsManager when allowed. */
    suspend fun mobileBytes(from: Long, to: Long = System.currentTimeMillis()): Long? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        try {
            val nsm = context.getSystemService(NetworkStatsManager::class.java)
            val b: NetworkStats.Bucket = nsm.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, from, to)
            b.rxBytes + b.txBytes
        } catch (e: Exception) {
            null
        }
    }

    fun mobileBytesSinceBoot(): Long = try {
        val v = TrafficStats.getMobileRxBytes() + TrafficStats.getMobileTxBytes()
        if (v < 0) 0 else v
    } catch (e: Exception) { 0 }

    fun startOfDayMillis(): Long = startOfDay()
    fun startOfMonthMillis(): Long = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

data class NotifEntry(val key: String, val pkg: String, val title: String, val text: String, val at: Long, val important: Boolean)

/** Notification counts and recent history, filled by [ir.nama.launcher.system.NamaNotificationListener]. */
object NotificationRepo {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()
    private val _recent = MutableStateFlow<List<NotifEntry>>(emptyList())
    val recent: StateFlow<List<NotifEntry>> = _recent.asStateFlow()
    val connected = MutableStateFlow(false)

    fun setActive(list: List<StatusBarNotification>) {
        _counts.value = list.filter { !it.isOngoing && it.isClearable }.groupingBy { it.packageName }.eachCount()
    }

    fun posted(sbn: StatusBarNotification, important: Boolean) {
        if (sbn.isOngoing) return
        val ex = sbn.notification.extras
        val title = ex.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = ex.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val e = NotifEntry(sbn.key, sbn.packageName, title, text, sbn.postTime, important)
        _recent.value = (listOf(e) + _recent.value.filter { it.key != sbn.key }).take(150)
    }

    fun hasAccess(context: Context): Boolean = try {
        val flat = AndroidSettings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""
        val me = ComponentName(context, "ir.nama.launcher.system.NamaNotificationListener").flattenToString()
        flat.split(':').any { it == me }
    } catch (e: Exception) {
        false
    }

    fun clearHistory() { _recent.value = emptyList() }
}

/** Bank cards are encrypted with an Android Keystore key and never leave the device. */
class SecureCards(private val context: Context) {
    private val file = File(File(context.filesDir, "nama").apply { mkdirs() }, "cards.enc")
    private val _cards = MutableStateFlow(load())
    val cards: StateFlow<List<BankCard>> = _cards.asStateFlow()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun load(): List<BankCard> = try {
        if (!file.exists()) emptyList() else {
            val raw = Base64.decode(file.readText(), Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, 12)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val plain = c.doFinal(raw.copyOfRange(12, raw.size)).toString(Charsets.UTF_8)
            NamaJson.decodeFromString(ListSerializer(BankCard.serializer()), plain)
        }
    } catch (e: Exception) {
        Log.e("Nama", "cards unreadable", e)
        emptyList()
    }

    private fun save(list: List<BankCard>) {
        try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key())
            val enc = c.doFinal(NamaJson.encodeToString(ListSerializer(BankCard.serializer()), list).toByteArray(Charsets.UTF_8))
            val tmp = File(file.parentFile, "cards.tmp")
            tmp.writeText(Base64.encodeToString(c.iv + enc, Base64.NO_WRAP))
            tmp.renameTo(file)
        } catch (e: Exception) {
            Log.e("Nama", "cards save failed", e)
        }
    }

    fun set(list: List<BankCard>) {
        _cards.value = list
        save(list)
    }

    companion object { private const val ALIAS = "nama_cards_key" }
}

/**
 * Optional cloud backup with Supabase (email + password auth, one row per user).
 * See docs/SUPABASE.md for the table definition.
 */
class SupabaseSync(private val store: Store) {
    @Serializable
    private data class Session(val accessToken: String, val userId: String)

    private var session: Session? = null

    private fun base(): String = store.settings.value.supabaseUrl.trim().trimEnd('/')
    private fun key(): String = store.settings.value.supabaseKey.trim()

    fun configured() = base().startsWith("https://") && key().isNotEmpty()
    fun signedIn() = session != null

    suspend fun signIn(email: String, password: String, createAccount: Boolean): String? {
        if (!configured()) return "آدرس و کلید Supabase را وارد کنید."
        val body = buildJsonObject { put("email", email); put("password", password) }.toString()
        val headers = mapOf("apikey" to key(), "Authorization" to "Bearer ${key()}")
        if (createAccount) {
            val (code, text) = Net.send("${base()}/auth/v1/signup", "POST", body, headers)
            if (code !in 200..299) return "ثبت‌نام ناموفق بود ($code): ${text.take(160)}"
        }
        val (code, text) = Net.send("${base()}/auth/v1/token?grant_type=password", "POST", body, headers)
        if (code !in 200..299) return "ورود ناموفق بود ($code): ${text.take(160)}"
        return try {
            val o = Json.parseToJsonElement(text) as JsonObject
            val token = o["access_token"]!!.jsonPrimitive.content
            val user = (o["user"] as JsonObject)["id"]!!.jsonPrimitive.content
            session = Session(token, user)
            store.settings.update { it.copy(supabaseEmail = email) }
            null
        } catch (e: Exception) {
            "پاسخ سرور قابل خواندن نبود."
        }
    }

    suspend fun upload(): String? {
        val s = session ?: return "اول وارد حساب شوید."
        val data = NamaJson.encodeToJsonElement(Backup.serializer(), store.backup())
        val row = buildJsonObject {
            put("user_id", s.userId)
            put("data", data)
            put("updated_at", java.time.Instant.now().toString())
        }
        val (code, text) = Net.send(
            "${base()}/rest/v1/nama_backups", "POST", JsonArray(listOf(row)).toString(),
            mapOf(
                "apikey" to key(),
                "Authorization" to "Bearer ${s.accessToken}",
                "Prefer" to "resolution=merge-duplicates,return=minimal"
            )
        )
        return if (code in 200..299) null else "ارسال ناموفق بود ($code): ${text.take(160)}"
    }

    suspend fun download(): Pair<Backup?, String?> {
        val s = session ?: return null to "اول وارد حساب شوید."
        val (code, text) = Net.send(
            "${base()}/rest/v1/nama_backups?select=data&user_id=eq.${s.userId}", "GET", null,
            mapOf("apikey" to key(), "Authorization" to "Bearer ${s.accessToken}")
        )
        if (code !in 200..299) return null to "دریافت ناموفق بود ($code)"
        return try {
            val arr = Json.parseToJsonElement(text) as JsonArray
            if (arr.isEmpty()) return null to "هنوز پشتیبانی در حساب شما نیست."
            val data = (arr[0] as JsonObject)["data"] ?: JsonPrimitive("")
            NamaJson.decodeFromJsonElement(Backup.serializer(), data) to null
        } catch (e: Exception) {
            null to "پشتیبان قابل خواندن نبود."
        }
    }
}
