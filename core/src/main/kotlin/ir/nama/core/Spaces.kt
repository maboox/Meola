package ir.nama.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
enum class StyleId(val fa: String, val en: String) {
    DEFAULT("پیش‌فرض", "Default"),
    MINIMAL("مینیمال", "Minimal")
}

@Serializable
sealed class Trigger {
    /** Connected to one of these Wi-Fi networks (SSID, case-insensitive). */
    @Serializable @SerialName("wifi") data class Wifi(val ssids: List<String>) : Trigger()

    /** Time window in minutes from midnight; days use the Persian week (0 = Saturday ... 6 = Friday). */
    @Serializable @SerialName("time") data class Time(
        val startMinute: Int,
        val endMinute: Int,
        val days: Set<Int> = (0..6).toSet()
    ) : Trigger()

    /** A Bluetooth device whose name or address is in the list is connected. */
    @Serializable @SerialName("bluetooth") data class Bluetooth(val devices: List<String>) : Trigger()
    @Serializable @SerialName("charging") data object Charging : Trigger()
    @Serializable @SerialName("battery_below") data class BatteryBelow(val percent: Int) : Trigger()
    @Serializable @SerialName("headphones") data object Headphones : Trigger()
    @Serializable @SerialName("location") data class Location(
        val lat: Double,
        val lng: Double,
        val radiusMeters: Int = 200,
        val label: String = ""
    ) : Trigger()
    @Serializable @SerialName("holiday") data object Holiday : Trigger()
    @Serializable @SerialName("workday") data object Workday : Trigger()
    @Serializable @SerialName("ramadan") data object Ramadan : Trigger()
    @Serializable @SerialName("no_intl") data object NoInternationalInternet : Trigger()
}

@Serializable
data class SpaceOverrides(
    val style: StyleId? = null,
    val hiddenApps: Set<String> = emptySet(),
    val hiddenCategories: Set<AppCategory> = emptySet(),
    /** Apps shown first on the first home page while this space is active. */
    val pinnedApps: List<String> = emptyList(),
    val dockApps: List<String>? = null,
    /** Only these apps are visible (kids / guest / focus). Empty means no restriction. */
    val allowOnlyApps: Set<String> = emptySet(),
    val allowOnlyCategories: Set<AppCategory> = emptySet(),
    val reduceMotion: Boolean = false,
    val calmNews: Boolean = false,
    val hideNews: Boolean = false,
    /** Exiting requires the device lock (kids and guest spaces). */
    val locked: Boolean = false,
    val extraFrictionApps: Set<String> = emptySet(),
    val darkTint: Boolean = false,
    /** Hide apps that need international internet (useful with the national-internet trigger). */
    val hideForeignApps: Boolean = false
)

@Serializable
data class Space(
    val id: String,
    val name: String,
    val icon: String = "●",
    val enabled: Boolean = true,
    /** Lower number wins when several spaces match. */
    val priority: Int = 50,
    val matchAll: Boolean = true,
    val triggers: List<Trigger> = emptyList(),
    val overrides: SpaceOverrides = SpaceOverrides(),
    /** Built-in preset this space came from (for display), or null. */
    val preset: String? = null
)

/** Everything the space engine knows about the current moment. */
data class ContextSnapshot(
    val minuteOfDay: Int,
    /** Persian week index: 0 = Saturday ... 6 = Friday. */
    val weekIndex: Int,
    val wifiSsid: String? = null,
    val bluetoothDevices: Set<String> = emptySet(),
    val charging: Boolean = false,
    val batteryPercent: Int = 100,
    val headphones: Boolean = false,
    val lat: Double? = null,
    val lng: Double? = null,
    val holiday: Boolean = false,
    val ramadan: Boolean = false,
    val internationalDown: Boolean = false
)

object SpaceEngine {
    fun matches(space: Space, ctx: ContextSnapshot): Boolean {
        if (!space.enabled || space.triggers.isEmpty()) return false
        val r = space.triggers.map { matches(it, ctx) }
        return if (space.matchAll) r.all { it } else r.any { it }
    }

    fun matches(t: Trigger, ctx: ContextSnapshot): Boolean = when (t) {
        is Trigger.Wifi -> {
            val ssid = ctx.wifiSsid?.trim('"')?.trim()
            ssid != null && t.ssids.any { it.trim().equals(ssid, ignoreCase = true) }
        }
        is Trigger.Time -> inTimeWindow(t, ctx.minuteOfDay, ctx.weekIndex)
        is Trigger.Bluetooth -> t.devices.any { d -> ctx.bluetoothDevices.any { it.equals(d, ignoreCase = true) } }
        is Trigger.Charging -> ctx.charging
        is Trigger.BatteryBelow -> !ctx.charging && ctx.batteryPercent <= t.percent
        is Trigger.Headphones -> ctx.headphones
        is Trigger.Location -> ctx.lat != null && ctx.lng != null && distanceMeters(t.lat, t.lng, ctx.lat, ctx.lng) <= t.radiusMeters
        is Trigger.Holiday -> ctx.holiday
        is Trigger.Workday -> !ctx.holiday
        is Trigger.Ramadan -> ctx.ramadan
        is Trigger.NoInternationalInternet -> ctx.internationalDown
    }

    fun inTimeWindow(t: Trigger.Time, minute: Int, weekIndex: Int): Boolean {
        val s = t.startMinute
        val e = t.endMinute
        return if (s == e) {
            weekIndex in t.days
        } else if (s < e) {
            weekIndex in t.days && minute >= s && minute < e
        } else {
            // Crosses midnight: the evening part belongs to today, the morning part to the previous day.
            val prevDay = (weekIndex + 6) % 7
            (minute >= s && weekIndex in t.days) || (minute < e && prevDay in t.days)
        }
    }

    /**
     * Picks the active space. A manual choice wins unless it no longer exists or is disabled.
     * Otherwise the matching space with the lowest priority number wins; null means the base layout.
     */
    fun resolve(spaces: List<Space>, ctx: ContextSnapshot, manualId: String?): Space? {
        if (manualId != null) {
            if (manualId == BASE_ID) return null
            spaces.firstOrNull { it.id == manualId && it.enabled }?.let { return it }
        }
        return spaces.filter { matches(it, ctx) }.minByOrNull { it.priority }
    }

    const val BASE_ID = "base"

    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
