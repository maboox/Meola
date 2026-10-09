package ir.nama.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Prayer times calculator based on the PrayTimes.org algorithm, configured with the
 * Institute of Geophysics, University of Tehran method (Fajr 17.7°, Isha 14°, Maghrib 4.5°, Jafari midnight).
 */
object PrayTimes {
    enum class Time(val fa: String, val en: String) {
        IMSAK("اذان صبح (امساک)", "Imsak"),
        FAJR("اذان صبح", "Fajr"),
        SUNRISE("طلوع آفتاب", "Sunrise"),
        DHUHR("اذان ظهر", "Dhuhr"),
        ASR("عصر", "Asr"),
        SUNSET("غروب آفتاب", "Sunset"),
        MAGHRIB("اذان مغرب", "Maghrib"),
        ISHA("عشا", "Isha"),
        MIDNIGHT("نیمه‌شب شرعی", "Midnight")
    }

    private const val FAJR_ANGLE = 17.7
    private const val ISHA_ANGLE = 14.0
    private const val MAGHRIB_ANGLE = 4.5
    private const val RISE_SET_ANGLE = 0.833

    private fun dtr(d: Double) = d * Math.PI / 180.0
    private fun rtd(r: Double) = r * 180.0 / Math.PI
    private fun dsin(d: Double) = sin(dtr(d))
    private fun dcos(d: Double) = cos(dtr(d))
    private fun dtan(d: Double) = tan(dtr(d))
    private fun darcsin(x: Double) = rtd(asin(x))
    private fun darccos(x: Double) = rtd(acos(x.coerceIn(-1.0, 1.0)))
    private fun darctan2(y: Double, x: Double) = rtd(atan2(y, x))
    private fun darccot(x: Double) = rtd(atan(1 / x))
    private fun fix(a: Double, b: Double): Double {
        val r = a - b * floor(a / b)
        return if (r < 0) r + b else r
    }
    private fun fixAngle(a: Double) = fix(a, 360.0)
    private fun fixHour(a: Double) = fix(a, 24.0)

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private data class SunPos(val declination: Double, val equation: Double)

    private fun sunPosition(jd: Double): SunPos {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0
        val eqt = q / 15.0 - fixHour(ra)
        val decl = darcsin(dsin(e) * dsin(l))
        return SunPos(decl, eqt)
    }

    /**
     * Returns prayer times as hours of the local day (e.g. 11.9 = 11:54) for the given location.
     * Times can be NaN for extreme latitudes (not an issue in Iran).
     */
    fun compute(date: LocalDate, lat: Double, lng: Double, zone: ZoneId = ZoneId.of("Asia/Tehran")): Map<Time, Double> {
        val tzHours = zone.rules.getOffset(date.atStartOfDay()).totalSeconds / 3600.0
        val jDate = julian(date.year, date.monthValue, date.dayOfMonth) - lng / (15.0 * 24.0)

        fun midDay(time: Double): Double = fixHour(12 - sunPosition(jDate + time).equation)
        fun sunAngleTime(angle: Double, time: Double, ccw: Boolean): Double {
            val decl = sunPosition(jDate + time).declination
            val noon = midDay(time)
            val t = 1.0 / 15.0 * darccos((-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat)))
            return noon + if (ccw) -t else t
        }
        fun asrTime(factor: Double, time: Double): Double {
            val decl = sunPosition(jDate + time).declination
            val angle = -darccot(factor + dtan(abs(lat - decl)))
            return sunAngleTime(angle, time, false)
        }

        // Two refinement passes from default guesses.
        var fajr = 5.0; var sunrise = 6.0; var dhuhr = 12.0; var asr = 13.0
        var sunset = 18.0; var maghrib = 18.0; var isha = 18.0
        repeat(2) {
            fajr = sunAngleTime(FAJR_ANGLE, fajr / 24, true)
            sunrise = sunAngleTime(RISE_SET_ANGLE, sunrise / 24, true)
            dhuhr = midDay(dhuhr / 24)
            asr = asrTime(1.0, asr / 24)
            sunset = sunAngleTime(RISE_SET_ANGLE, sunset / 24, false)
            maghrib = sunAngleTime(MAGHRIB_ANGLE, maghrib / 24, false)
            isha = sunAngleTime(ISHA_ANGLE, isha / 24, false)
        }
        val adjust = tzHours - lng / 15.0
        fajr += adjust; sunrise += adjust; dhuhr += adjust; asr += adjust
        sunset += adjust; maghrib += adjust; isha += adjust
        val imsak = fajr - 10.0 / 60.0
        // Jafari midnight: halfway between sunset and next fajr.
        val midnight = sunset + fixHour(fajr - sunset) / 2.0
        return linkedMapOf(
            Time.IMSAK to imsak, Time.FAJR to fajr, Time.SUNRISE to sunrise, Time.DHUHR to dhuhr,
            Time.ASR to asr, Time.SUNSET to sunset, Time.MAGHRIB to maghrib, Time.ISHA to isha,
            Time.MIDNIGHT to fixHour(midnight)
        )
    }

    /** Formats fractional hours as HH:mm (rounded to the nearest minute). */
    fun format(hours: Double, persian: Boolean = true): String {
        if (hours.isNaN()) return "--:--"
        val totalMin = Math.round(fixHour(hours) * 60).toInt() % (24 * 60)
        val s = "${(totalMin / 60).toString().padStart(2, '0')}:${(totalMin % 60).toString().padStart(2, '0')}"
        return PersianText.digits(s, persian)
    }

    fun toMinuteOfDay(hours: Double): Int = (Math.round(fixHour(hours) * 60).toInt()) % (24 * 60)
}
