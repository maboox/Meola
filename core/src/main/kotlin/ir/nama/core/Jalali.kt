package ir.nama.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/** Jalali (Solar Hijri) date. Conversion follows the jalaali-js algorithm (Borkowski breaks). */
data class JalaliDate(val year: Int, val month: Int, val day: Int) : Comparable<JalaliDate> {

    fun toLocalDate(): LocalDate {
        val jdn = Jalali.j2d(year, month, day)
        val (gy, gm, gd) = Jalali.d2g(jdn)
        return LocalDate.of(gy, gm, gd)
    }

    val monthName: String get() = Jalali.MONTHS[month - 1]

    override fun compareTo(other: JalaliDate): Int =
        compareValuesBy(this, other, { it.year }, { it.month }, { it.day })

    fun format(persian: Boolean = true): String =
        PersianText.digits("$year/${month.toString().padStart(2, '0')}/${day.toString().padStart(2, '0')}", persian)

    fun formatLong(persian: Boolean = true): String =
        PersianText.digits("$day $monthName $year", persian)

    companion object {
        fun from(date: LocalDate): JalaliDate {
            val jdn = Jalali.g2d(date.year, date.monthValue, date.dayOfMonth)
            return Jalali.d2j(jdn)
        }

        fun today(): JalaliDate = from(LocalDate.now())
    }
}

object Jalali {
    val MONTHS = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )
    val MONTHS_EN = listOf(
        "Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
        "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand"
    )

    /** Week starts on Saturday (index 0) and ends on Friday (index 6). */
    val WEEKDAYS = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه")
    val WEEKDAYS_SHORT = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
    val WEEKDAYS_EN = listOf("Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri")

    fun weekIndex(dow: DayOfWeek): Int = (dow.value + 1) % 7
    fun weekIndex(date: LocalDate): Int = weekIndex(date.dayOfWeek)

    private val BREAKS = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
        1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    )

    private fun div(a: Long, b: Long): Long = a / b
    private fun mod(a: Long, b: Long): Long = a - (a / b) * b

    private data class Cal(val leap: Int, val gy: Int, val march: Int)

    private fun jalCal(jy: Int): Cal {
        val bl = BREAKS.size
        val gy = jy + 621
        var leapJ = -14L
        var jp = BREAKS[0].toLong()
        require(jy >= jp && jy < BREAKS[bl - 1]) { "Invalid Jalali year $jy" }
        var jump = 0L
        for (i in 1 until bl) {
            val jm = BREAKS[i].toLong()
            jump = jm - jp
            if (jy < jm) break
            leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
            jp = jm
        }
        var n = jy - jp
        leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
        if (mod(jump, 33) == 4L && jump - n == 4L) leapJ += 1
        val leapG = div(gy.toLong(), 4) - div((div(gy.toLong(), 100) + 1) * 3, 4) - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
        var leap = mod(mod(n + 1, 33) - 1, 4)
        if (leap == -1L) leap = 4
        return Cal(leap.toInt(), gy, march.toInt())
    }

    fun isLeap(jy: Int): Boolean = jalCal(jy).leap == 0

    fun monthLength(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        isLeap(jy) -> 30
        else -> 29
    }

    internal fun g2d(gy: Int, gm: Int, gd: Int): Long {
        val y = gy.toLong(); val m = gm.toLong(); val d = gd.toLong()
        var r = div((y + div(m - 8, 6) + 100100) * 1461, 4) + div(153 * mod(m + 9, 12) + 2, 5) + d - 34840408
        r = r - div(div(y + 100100 + div(m - 8, 6), 100) * 3, 4) + 752
        return r
    }

    internal fun d2g(jdn: Long): Triple<Int, Int, Int> {
        var j = 4 * jdn + 139361631
        j += div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908
        val i = div(mod(j, 1461), 4) * 5 + 308
        val gd = div(mod(i, 153), 5) + 1
        val gm = mod(div(i, 153), 12) + 1
        val gy = div(j, 1461) - 100100 + div(8 - gm, 6)
        return Triple(gy.toInt(), gm.toInt(), gd.toInt())
    }

    internal fun j2d(jy: Int, jm: Int, jd: Int): Long {
        val r = jalCal(jy)
        return g2d(r.gy, 3, r.march) + (jm - 1) * 31L - div(jm.toLong(), 7) * (jm - 7) + jd - 1
    }

    internal fun d2j(jdn: Long): JalaliDate {
        val gy = d2g(jdn).first
        var jy = gy - 621
        val r = jalCal(jy)
        val jdn1f = g2d(gy, 3, r.march)
        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185) {
                return JalaliDate(jy, (1 + div(k, 31)).toInt(), (mod(k, 31) + 1).toInt())
            } else {
                k -= 186
            }
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        return JalaliDate(jy, (7 + div(k, 30)).toInt(), (mod(k, 30) + 1).toInt())
    }

    /** First day (as LocalDate) of a Jalali month. */
    fun firstOfMonth(jy: Int, jm: Int): LocalDate = JalaliDate(jy, jm, 1).toLocalDate()

    fun addMonths(d: JalaliDate, months: Int): JalaliDate {
        val total = d.year * 12 + (d.month - 1) + months
        val y = Math.floorDiv(total, 12)
        val m = Math.floorMod(total, 12) + 1
        val day = minOf(d.day, monthLength(y, m))
        return JalaliDate(y, m, day)
    }
}

/** Islamic (lunar) date helper using the Umm al-Qura chronology with a configurable day offset for Iran. */
data class HijriDate(val year: Int, val month: Int, val day: Int, val monthLength: Int) {
    val monthName: String get() = Hijri.MONTHS[month - 1]
    fun formatLong(persian: Boolean = true): String = PersianText.digits("$day $monthName $year", persian)
}

object Hijri {
    val MONTHS = listOf(
        "محرم", "صفر", "ربیع‌الاول", "ربیع‌الثانی", "جمادی‌الاول", "جمادی‌الثانی",
        "رجب", "شعبان", "رمضان", "شوال", "ذی‌القعده", "ذی‌الحجه"
    )

    /** offset: days to shift. Iran usually starts lunar months one day after Umm al-Qura, so the default is -1. */
    fun from(date: LocalDate, offset: Int = -1): HijriDate? = try {
        val h = HijrahDate.from(date.plusDays(offset.toLong()))
        HijriDate(
            h.get(ChronoField.YEAR),
            h.get(ChronoField.MONTH_OF_YEAR),
            h.get(ChronoField.DAY_OF_MONTH),
            h.lengthOfMonth()
        )
    } catch (e: Exception) {
        null
    }
}

data class CalendarEvent(val title: String, val holiday: Boolean, val kind: Kind) {
    enum class Kind { SOLAR, LUNAR, GREGORIAN }
}

/** Official Iranian holidays and notable occasions. */
object IranCalendar {
    private data class E(val month: Int, val day: Int, val title: String, val holiday: Boolean)

    private val SOLAR = listOf(
        E(1, 1, "عید نوروز", true), E(1, 2, "عید نوروز", true), E(1, 3, "عید نوروز", true), E(1, 4, "عید نوروز", true),
        E(1, 12, "روز جمهوری اسلامی", true), E(1, 13, "روز طبیعت", true),
        E(2, 1, "روز بزرگداشت سعدی", false), E(2, 25, "روز بزرگداشت فردوسی", false),
        E(3, 14, "رحلت امام خمینی", true), E(3, 15, "قیام ۱۵ خرداد", true),
        E(4, 1, "آغاز تابستان", false), E(5, 8, "روز بزرگداشت سهروردی", false),
        E(6, 1, "روز بزرگداشت ابوعلی سینا", false), E(6, 27, "روز شعر و ادب فارسی", false),
        E(7, 1, "آغاز پاییز و بازگشایی مدارس", false), E(7, 20, "روز بزرگداشت حافظ", false),
        E(8, 8, "روز بزرگداشت مولوی", false), E(9, 30, "شب یلدا", false),
        E(10, 1, "آغاز زمستان", false), E(11, 22, "پیروزی انقلاب اسلامی", true),
        E(12, 29, "ملی شدن صنعت نفت", true)
    )

    private val LUNAR = listOf(
        E(1, 9, "تاسوعای حسینی", true), E(1, 10, "عاشورای حسینی", true),
        E(2, 20, "اربعین حسینی", true), E(2, 28, "رحلت پیامبر و شهادت امام حسن مجتبی", true),
        E(3, 8, "شهادت امام حسن عسکری", true), E(3, 17, "میلاد پیامبر اکرم و امام جعفر صادق", true),
        E(6, 3, "شهادت حضرت فاطمه زهرا", true), E(7, 13, "ولادت امام علی", true),
        E(7, 27, "مبعث پیامبر اکرم", true), E(8, 15, "ولادت امام مهدی", true),
        E(9, 1, "آغاز ماه رمضان", false), E(9, 19, "شب قدر", false),
        E(9, 21, "شهادت امام علی", true), E(10, 1, "عید فطر", true), E(10, 2, "تعطیل به مناسبت عید فطر", true),
        E(10, 25, "شهادت امام جعفر صادق", true), E(12, 10, "عید قربان", true), E(12, 18, "عید غدیر خم", true)
    )

    fun events(date: LocalDate, hijriOffset: Int = -1): List<CalendarEvent> {
        val out = mutableListOf<CalendarEvent>()
        val j = JalaliDate.from(date)
        SOLAR.filter { it.month == j.month && it.day == j.day }
            .forEach { out += CalendarEvent(it.title, it.holiday, CalendarEvent.Kind.SOLAR) }
        // Chaharshanbe Suri: the evening of the last Tuesday of the year.
        if (j.month == 12 && date.dayOfWeek == DayOfWeek.TUESDAY && JalaliDate.from(date.plusDays(7)).month == 1) {
            out += CalendarEvent("چهارشنبه‌سوری", false, CalendarEvent.Kind.SOLAR)
        }
        val h = Hijri.from(date, hijriOffset)
        if (h != null) {
            LUNAR.filter { it.month == h.month && it.day == h.day }
                .forEach { out += CalendarEvent(it.title, it.holiday, CalendarEvent.Kind.LUNAR) }
            if (h.month == 2 && h.day == h.monthLength) {
                out += CalendarEvent("شهادت امام رضا", true, CalendarEvent.Kind.LUNAR)
            }
        }
        if (date.monthValue == 1 && date.dayOfMonth == 1) out += CalendarEvent("آغاز سال میلادی", false, CalendarEvent.Kind.GREGORIAN)
        return out
    }

    fun isHoliday(date: LocalDate, hijriOffset: Int = -1): Boolean =
        date.dayOfWeek == DayOfWeek.FRIDAY || events(date, hijriOffset).any { it.holiday }

    fun isRamadan(date: LocalDate, hijriOffset: Int = -1): Boolean = Hijri.from(date, hijriOffset)?.month == 9

    /** Next date (from [from], inclusive) on which the solar event with this month/day falls. */
    fun nextSolar(from: LocalDate, month: Int, day: Int): LocalDate {
        val j = JalaliDate.from(from)
        var y = j.year
        if (j.month > month || (j.month == month && j.day > day)) y += 1
        val d = minOf(day, Jalali.monthLength(y, month))
        return JalaliDate(y, month, d).toLocalDate()
    }

    /** Seasonal theme occasion for decorative accents. */
    enum class Occasion { NONE, NOWRUZ, YALDA, RAMADAN, MUHARRAM, CHAHARSHANBE_SURI }

    fun occasion(date: LocalDate, hijriOffset: Int = -1): Occasion {
        val j = JalaliDate.from(date)
        val h = Hijri.from(date, hijriOffset)
        return when {
            h != null && h.month == 1 && h.day <= 12 -> Occasion.MUHARRAM
            j.month == 12 && date.dayOfWeek == DayOfWeek.TUESDAY && JalaliDate.from(date.plusDays(7)).month == 1 -> Occasion.CHAHARSHANBE_SURI
            (j.month == 12 && j.day >= 25) || (j.month == 1 && j.day <= 13) -> Occasion.NOWRUZ
            (j.month == 9 && j.day >= 28) || (j.month == 10 && j.day == 1) -> Occasion.YALDA
            h != null && h.month == 9 -> Occasion.RAMADAN
            else -> Occasion.NONE
        }
    }
}
