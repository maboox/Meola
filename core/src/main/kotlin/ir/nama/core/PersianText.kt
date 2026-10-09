package ir.nama.core

/** Persian text helpers: digits, normalization, number formatting and number-to-words. */
object PersianText {
    private const val FA_DIGITS = "۰۱۲۳۴۵۶۷۸۹"
    private const val AR_DIGITS = "٠١٢٣٤٥٦٧٨٩"

    fun toFaDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(if (c in '0'..'9') FA_DIGITS[c - '0'] else c)
        return sb.toString()
    }

    fun toEnDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            val fa = FA_DIGITS.indexOf(c)
            val ar = AR_DIGITS.indexOf(c)
            sb.append(
                when {
                    fa >= 0 -> '0' + fa
                    ar >= 0 -> '0' + ar
                    c == '٫' -> '.'
                    c == '٬' -> ','
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    fun digits(s: String, persian: Boolean): String = if (persian) toFaDigits(s) else toEnDigits(s)

    /** Normalizes text for searching: unifies Arabic/Persian letters, removes diacritics and ZWNJ, lowercases. */
    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in toEnDigits(s)) {
            when (c) {
                'ي', 'ى', 'ئ' -> sb.append('ی')
                'ك' -> sb.append('ک')
                'ة' -> sb.append('ه')
                'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
                'ؤ' -> sb.append('و')
                '‌', '‍', 'ـ' -> {}
                in 'ً'..'ٟ', 'ٰ' -> {}
                else -> sb.append(c.lowercaseChar())
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    fun containsPersian(s: String): Boolean = s.any { it in '؀'..'ۿ' }

    /** Groups thousands. Persian uses '٬' as separator. */
    fun group(n: Long, persian: Boolean): String {
        val neg = n < 0
        val digits = kotlin.math.abs(n).toString()
        val sb = StringBuilder()
        for ((i, c) in digits.withIndex()) {
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append(if (persian) '٬' else ',')
            sb.append(c)
        }
        val out = (if (neg) "-" else "") + sb.toString()
        return digits(out, persian)
    }

    fun groupDecimal(v: Double, persian: Boolean, maxFraction: Int = 2): String {
        val whole = v.toLong()
        val frac = kotlin.math.abs(v - whole)
        if (frac < 1e-9 || maxFraction == 0) return group(whole, persian)
        val fracStr = String.format(java.util.Locale.US, "%.${maxFraction}f", frac).substringAfter('.').trimEnd('0')
        if (fracStr.isEmpty()) return group(whole, persian)
        return group(whole, persian) + (if (persian) "٫" else ".") + digits(fracStr, persian)
    }

    /** Parses a number written with Persian/Arabic/English digits and optional separators. */
    fun parseNumber(s: String): Double? {
        val cleaned = toEnDigits(s).replace("٬", "").replace(",", "").replace("_", "").trim()
        if (cleaned.isEmpty()) return null
        return cleaned.toDoubleOrNull()
    }

    private val ONES = arrayOf("", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه")
    private val TEENS = arrayOf("ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده")
    private val TENS = arrayOf("", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود")
    private val HUNDREDS = arrayOf("", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد")
    private val SCALES = arrayOf("", "هزار", "میلیون", "میلیارد", "تریلیون", "کوادریلیون")

    private fun threeDigits(n: Int): String {
        val parts = mutableListOf<String>()
        val h = n / 100
        val rest = n % 100
        if (h > 0) parts += HUNDREDS[h]
        if (rest in 10..19) parts += TEENS[rest - 10]
        else {
            val t = rest / 10
            val o = rest % 10
            if (t > 0) parts += TENS[t]
            if (o > 0) parts += ONES[o]
        }
        return parts.joinToString(" و ")
    }

    /** Converts an integer to Persian words, e.g. 250000 -> "دویست و پنجاه هزار". */
    fun numberToWords(n: Long): String {
        if (n == 0L) return "صفر"
        if (n < 0) return "منفی " + numberToWords(-n)
        val groups = mutableListOf<Int>()
        var x = n
        while (x > 0) {
            groups += (x % 1000).toInt()
            x /= 1000
        }
        val parts = mutableListOf<String>()
        for (i in groups.indices.reversed()) {
            val g = groups[i]
            if (g == 0) continue
            val words = threeDigits(g)
            parts += if (i == 0) words else "$words ${SCALES.getOrElse(i) { "" }}".trim()
        }
        return parts.joinToString(" و ")
    }

    /** Human friendly toman amount, e.g. 1250000 -> "۱ میلیون و ۲۵۰ هزار". */
    fun shortToman(toman: Long, persian: Boolean = true): String {
        if (toman < 1000) return group(toman, persian)
        val units = listOf(1_000_000_000L to "میلیارد", 1_000_000L to "میلیون", 1_000L to "هزار")
        var rest = toman
        val parts = mutableListOf<String>()
        for ((size, name) in units) {
            val q = rest / size
            if (q > 0) {
                parts += "${group(q, persian)} $name"
                rest %= size
            }
        }
        if (rest > 0) parts += group(rest, persian)
        return parts.joinToString(" و ")
    }
}
