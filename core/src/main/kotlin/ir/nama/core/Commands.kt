package ir.nama.core

import java.time.LocalDate

/** Small recursive-descent calculator that accepts Persian digits and ×, ÷ symbols. */
object Calculator {
    fun looksLikeMath(input: String): Boolean {
        val s = PersianText.toEnDigits(input).replace(" ", "")
        if (s.length < 3) return false
        if (!s.any { it.isDigit() }) return false
        if (!s.any { it in "+-*/×÷^%()" }) return false
        return s.all { it.isDigit() || it in "+-*/×÷^%().,٬" }
    }

    fun evaluate(input: String): Double? = try {
        val s = PersianText.toEnDigits(input)
            .replace("×", "*").replace("÷", "/").replace("٬", "").replace(",", "").replace(" ", "")
        val p = Parser(s)
        val v = p.expr()
        if (p.pos != s.length || v.isNaN() || v.isInfinite()) null else v
    } catch (e: Exception) {
        null
    }

    private class Parser(val s: String) {
        var pos = 0
        fun peek(): Char? = s.getOrNull(pos)
        fun expr(): Double {
            var v = term()
            while (true) {
                when (peek()) {
                    '+' -> { pos++; v += term() }
                    '-' -> { pos++; v -= term() }
                    else -> return v
                }
            }
        }
        fun term(): Double {
            var v = factor()
            while (true) {
                when (peek()) {
                    '*' -> { pos++; v *= factor() }
                    '/' -> { pos++; v /= factor() }
                    '%' -> { pos++; v = if (peek() == null || peek() in listOf('+', '-', ')')) v / 100.0 else v % factor() }
                    else -> return v
                }
            }
        }
        fun factor(): Double {
            val base = unary()
            if (peek() == '^') { pos++; return Math.pow(base, factor()) }
            return base
        }
        fun unary(): Double {
            if (peek() == '-') { pos++; return -unary() }
            if (peek() == '+') { pos++; return unary() }
            return primary()
        }
        fun primary(): Double {
            if (peek() == '(') {
                pos++
                val v = expr()
                if (peek() == ')') pos++
                return v
            }
            val start = pos
            while (peek()?.let { it.isDigit() || it == '.' } == true) pos++
            require(pos > start) { "number expected" }
            return s.substring(start, pos).toDouble()
        }
    }
}

sealed class Command {
    data class Alarm(val hour: Int, val minute: Int, val label: String?) : Command()
    data class Timer(val seconds: Int) : Command()
    data class Reminder(val text: String, val dayOffset: Int, val hour: Int?, val minute: Int?) : Command()
    data class Calc(val expression: String, val result: Double) : Command()
    /** A ready answer the UI can show and copy. */
    data class Info(val title: String, val subtitle: String, val copyText: String) : Command()
    /** Needs live prices from the app: amount of [symbol] to toman. */
    data class CurrencyConvert(val amount: Double, val symbol: String) : Command()
    data class Call(val name: String) : Command()
    data class Ussd(val topic: String) : Command()
    data class WebSearch(val query: String) : Command()
}

object CommandParser {
    private const val NUM = "[0-9۰-۹٠-٩]"

    private fun n(s: String): Int? = PersianText.toEnDigits(s).toIntOrNull()

    /** Parses "7", "7:30", "7 و نیم", "۷ شب" into hour/minute (24h). */
    fun parseTime(text: String): Pair<Int, Int>? {
        val t = PersianText.toEnDigits(PersianText.normalize(text))
        val m = Regex("(\\d{1,2})(?:[:.](\\d{1,2}))?(\\s*و\\s*نیم|\\s*و\\s*ربع)?\\s*(صبح|ظهر|عصر|شب|بعدازظهر|بعد از ظهر|am|pm)?").find(t) ?: return null
        var h = m.groupValues[1].toIntOrNull() ?: return null
        var min = m.groupValues[2].toIntOrNull() ?: 0
        when {
            m.groupValues[3].contains("نیم") -> min = 30
            m.groupValues[3].contains("ربع") -> min = 15
        }
        val suffix = m.groupValues[4]
        if (suffix in listOf("عصر", "شب", "بعدازظهر", "بعد از ظهر", "pm") && h < 12) h += 12
        if (suffix == "شب" && h == 24) h = 0
        if (suffix in listOf("صبح", "am") && h == 12) h = 0
        if (suffix == "ظهر" && h < 6) h += 12
        if (h !in 0..23 || min !in 0..59) return null
        return h to min
    }

    fun parse(raw: String, today: LocalDate = LocalDate.now()): List<Command> {
        val input = raw.trim()
        if (input.isEmpty()) return emptyList()
        val norm = PersianText.normalize(input)
        val en = PersianText.toEnDigits(norm)
        val out = mutableListOf<Command>()

        // Alarm: "زنگ ۷", "آلارم ۶:۳۰ صبح", "alarm 7"
        Regex("^(زنگ|الارم|آلارم|ساعت زنگ|بیدارم کن|alarm|wake me)\\s+(?!بزن)(.+)$").find(norm)?.let { m ->
            parseTime(m.groupValues[2])?.let { (h, mi) -> out += Command.Alarm(h, mi, null) }
        }

        // Timer: "تایمر ۱۰", "تایمر ۳۰ ثانیه", "timer 5 min"
        Regex("^(تایمر|timer)\\s*($NUM+)\\s*(ثانیه|دقیقه|ساعت|s|sec|m|min|h|hour)?").find(norm)?.let { m ->
            val v = n(m.groupValues[2]) ?: return@let
            val unit = m.groupValues[3]
            val sec = when (unit) {
                "ثانیه", "s", "sec" -> v
                "ساعت", "h", "hour" -> v * 3600
                else -> v * 60
            }
            if (sec in 1..86_400) out += Command.Timer(sec)
        }

        // Reminder: "یادم بنداز فردا ساعت ۹ قسط بدم"
        Regex("^(یادم بنداز|یادم بیار|یادآوری|یاداوری|remind me( to)?)\\s+(.+)$").find(norm)?.let { m ->
            var body = m.groupValues[3]
            var dayOffset = 0
            when {
                body.contains("پس فردا") || body.contains("پسفردا") -> { dayOffset = 2; body = body.replace("پس فردا", "").replace("پسفردا", "") }
                body.contains("فردا") || body.contains("tomorrow") -> { dayOffset = 1; body = body.replace("فردا", "").replace("tomorrow", "") }
                body.contains("امروز") || body.contains("today") -> { body = body.replace("امروز", "").replace("today", "") }
            }
            var hour: Int? = null
            var minute: Int? = null
            Regex("(ساعت|at)\\s*($NUM{1,2}(?:[:.]$NUM{1,2})?(?:\\s*(?:صبح|ظهر|عصر|شب|am|pm))?)").find(body)?.let { tm ->
                parseTime(tm.groupValues[2])?.let { (h, mi) -> hour = h; minute = mi }
                body = body.replace(tm.value, "")
            }
            val text = body.replace(Regex("\\s+"), " ").trim()
            if (text.isNotEmpty()) out += Command.Reminder(text, dayOffset, hour, minute)
        }

        // Call: "زنگ بزن به علی", "تماس با مامان", "call ali"
        Regex("^(زنگ بزن به|زنگ بزن|تماس با|تماس|call)\\s+(.+)$").find(norm)?.let { m ->
            out += Command.Call(m.groupValues[2].trim())
        }

        // USSD topics
        if (Regex("^(شارژ|موجودی|بسته|بسته اینترنت|کد دستوری|ussd|اعتبار)").containsMatchIn(norm)) {
            out += Command.Ussd(norm)
        }

        // Math
        if (Calculator.looksLikeMath(en)) {
            Calculator.evaluate(en)?.let { out += Command.Calc(input, it) }
        }

        // Rial / Toman conversions
        Regex("^($NUM[0-9۰-۹٠-٩,٬]*)\\s*(ریال|rial|irr)$").find(norm)?.let { m ->
            val rial = PersianText.parseNumber(m.groupValues[1])?.toLong() ?: return@let
            val toman = rial / 10
            out += Command.Info(
                "${PersianText.group(toman, true)} تومان",
                PersianText.numberToWords(toman) + " تومان",
                toman.toString()
            )
        }
        Regex("^($NUM[0-9۰-۹٠-٩,٬]*)\\s*(تومان|تومن|toman)$").find(norm)?.let { m ->
            val toman = PersianText.parseNumber(m.groupValues[1])?.toLong() ?: return@let
            val rial = toman * 10
            out += Command.Info(
                "${PersianText.group(rial, true)} ریال",
                PersianText.numberToWords(rial) + " ریال",
                rial.toString()
            )
        }

        // Foreign currency to toman (needs prices from the app)
        Regex("^($NUM[0-9۰-۹٠-٩.,٬٫]*)\\s*(دلار|usd|\\$|یورو|eur|تتر|usdt|بیت ?کوین|btc|bitcoin|اتریوم|eth)$").find(norm)?.let { m ->
            val amount = PersianText.parseNumber(m.groupValues[1]) ?: return@let
            val sym = when (m.groupValues[2].replace(" ", "")) {
                "دلار", "usd", "$" -> "USD"
                "یورو", "eur" -> "EUR"
                "تتر", "usdt" -> "USDT"
                "بیتکوین", "btc", "bitcoin" -> "BTC"
                else -> "ETH"
            }
            out += Command.CurrencyConvert(amount, sym)
        }

        // Plain number: show it in words (useful for writing cheques)
        if (Regex("^$NUM[0-9۰-۹٠-٩,٬]*$").matches(norm)) {
            val v = PersianText.parseNumber(norm)?.toLong()
            if (v != null && v >= 100 && norm.replace(Regex("[,٬]"), "").length <= 16) {
                out += Command.Info(PersianText.numberToWords(v), PersianText.group(v, true), PersianText.numberToWords(v))
            }
        }

        // Date conversion: 1404/7/17 or 2026-10-09
        Regex("^($NUM{4})[/\\-.]($NUM{1,2})[/\\-.]($NUM{1,2})$").find(norm)?.let { m ->
            val y = n(m.groupValues[1]) ?: return@let
            val mo = n(m.groupValues[2]) ?: return@let
            val d = n(m.groupValues[3]) ?: return@let
            try {
                if (y > 1700) {
                    val g = LocalDate.of(y, mo, d)
                    val j = JalaliDate.from(g)
                    val wd = Jalali.WEEKDAYS[Jalali.weekIndex(g)]
                    out += Command.Info("$wd ${j.formatLong()}", "${j.format()} · ${Hijri.from(g)?.formatLong() ?: ""}", j.format(false))
                } else if (mo in 1..12 && d in 1..Jalali.monthLength(y, mo)) {
                    val g = JalaliDate(y, mo, d).toLocalDate()
                    val wd = Jalali.WEEKDAYS[Jalali.weekIndex(g)]
                    out += Command.Info("$g", "$wd · ${Hijri.from(g)?.formatLong() ?: ""}", g.toString())
                }
            } catch (_: Exception) {
            }
        }

        // Days until / since a date phrase is left to the UI. Always offer web search last.
        if (out.none { it is Command.Info || it is Command.Calc }) out += Command.WebSearch(input)
        return out
    }
}
