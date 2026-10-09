package ir.nama.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JalaliTest {
    @Test fun knownDates() {
        assertEquals(JalaliDate(1405, 7, 17), JalaliDate.from(LocalDate.of(2026, 10, 9)))
        assertEquals(JalaliDate(1404, 1, 1), JalaliDate.from(LocalDate.of(2025, 3, 21)))
        assertEquals(JalaliDate(1403, 1, 1), JalaliDate.from(LocalDate.of(2024, 3, 20)))
        assertEquals(JalaliDate(1403, 12, 30), JalaliDate.from(LocalDate.of(2025, 3, 20)))
        assertEquals(JalaliDate(1357, 11, 22), JalaliDate.from(LocalDate.of(1979, 2, 11)))
        assertEquals(LocalDate.of(2026, 10, 9), JalaliDate(1405, 7, 17).toLocalDate())
    }

    @Test fun leapYears() {
        assertTrue(Jalali.isLeap(1403))
        assertFalse(Jalali.isLeap(1404))
        assertEquals(30, Jalali.monthLength(1403, 12))
        assertEquals(29, Jalali.monthLength(1404, 12))
    }

    @Test fun roundTripTwentyYears() {
        var d = LocalDate.of(2010, 1, 1)
        val end = LocalDate.of(2035, 1, 1)
        var prev: JalaliDate? = null
        while (d.isBefore(end)) {
            val j = JalaliDate.from(d)
            assertEquals(d, j.toLocalDate())
            if (prev != null) assertTrue(j > prev)
            prev = j
            d = d.plusDays(1)
        }
    }

    @Test fun weekIndexStartsSaturday() {
        assertEquals(6, Jalali.weekIndex(LocalDate.of(2026, 10, 9))) // Friday
        assertEquals(0, Jalali.weekIndex(LocalDate.of(2026, 10, 10))) // Saturday
    }

    @Test fun holidays() {
        assertTrue(IranCalendar.isHoliday(JalaliDate(1405, 1, 1).toLocalDate()))
        assertTrue(IranCalendar.isHoliday(JalaliDate(1405, 11, 22).toLocalDate()))
        assertTrue(IranCalendar.isHoliday(LocalDate.of(2026, 10, 9))) // Friday
        assertNotNull(Hijri.from(LocalDate.of(2026, 10, 9)))
    }

    @Test fun addMonths() {
        assertEquals(JalaliDate(1405, 1, 29), Jalali.addMonths(JalaliDate(1404, 12, 29), 1))
        assertEquals(JalaliDate(1404, 12, 29), Jalali.addMonths(JalaliDate(1404, 11, 30), 1))
        assertEquals(JalaliDate(1403, 12, 1), Jalali.addMonths(JalaliDate(1404, 1, 1), -1))
    }
}

class PersianTextTest {
    @Test fun words() {
        assertEquals("دویست و پنجاه هزار", PersianText.numberToWords(250_000))
        assertEquals("یک میلیون و دویست هزار", PersianText.numberToWords(1_200_000))
        assertEquals("صفر", PersianText.numberToWords(0))
        assertEquals("نوزده", PersianText.numberToWords(19))
        assertEquals("صد و یک", PersianText.numberToWords(101))
    }

    @Test fun digitsAndGrouping() {
        assertEquals("۱۲۳", PersianText.toFaDigits("123"))
        assertEquals("123", PersianText.toEnDigits("۱۲۳"))
        assertEquals("۱٬۲۵۰٬۰۰۰", PersianText.group(1_250_000, true))
        assertEquals("1,250,000", PersianText.group(1_250_000, false))
        assertEquals("۱ میلیون و ۲۵۰ هزار", PersianText.shortToman(1_250_000))
    }

    @Test fun normalize() {
        assertEquals("کیف پول", PersianText.normalize("كيف  پول"))
        assertEquals("دیجیکالا", PersianText.normalize("دیجی‌کالا"))
    }
}

class SearchTest {
    private fun s(q: String, label: String) = SearchScorer.score(q, SearchKey(label))

    @Test fun finglishAndPersian() {
        assertTrue(s("تلگرام", "Telegram") > 0)
        assertTrue(s("telegram", "تلگرام") > 0)
        assertTrue(s("telgrm", "Telegram") > 0)
        assertTrue(s("insta", "Instagram") > 0)
        assertTrue(s("اینستا", "Instagram") > 0)
        assertTrue(s("divar", "دیوار") > 0)
        assertTrue(s("tanzimat", "تنظیمات") > 0)
        assertTrue(s("یوتیوب", "YouTube") > 0)
        assertTrue(s("whatsapp", "واتساپ") > 0)
    }

    @Test fun wrongKeyboard() {
        // "telegram" typed with the Persian layout
        val typed = Finglish.swapLayout("telegram")
        assertTrue(s(typed, "Telegram") > 0)
    }

    @Test fun ranking() {
        assertTrue(s("tel", "Telegram") > s("tel", "Hotel booking"))
        assertEquals(0, s("zzzz", "Telegram"))
    }
}

class CommandTest {
    @Test fun alarm() {
        val c = CommandParser.parse("زنگ ۷:۳۰").filterIsInstance<Command.Alarm>().single()
        assertEquals(7, c.hour); assertEquals(30, c.minute)
        val c2 = CommandParser.parse("آلارم ۸ شب").filterIsInstance<Command.Alarm>().single()
        assertEquals(20, c2.hour)
        assertTrue(CommandParser.parse("زنگ بزن به علی").none { it is Command.Alarm })
        assertEquals("علی", CommandParser.parse("زنگ بزن به علی").filterIsInstance<Command.Call>().single().name)
    }

    @Test fun timerAndReminder() {
        assertEquals(600, CommandParser.parse("تایمر ۱۰").filterIsInstance<Command.Timer>().single().seconds)
        assertEquals(30, CommandParser.parse("تایمر ۳۰ ثانیه").filterIsInstance<Command.Timer>().single().seconds)
        val r = CommandParser.parse("یادم بنداز فردا ساعت ۹ قسط بدم").filterIsInstance<Command.Reminder>().single()
        assertEquals(1, r.dayOffset); assertEquals(9, r.hour); assertEquals("قسط بدم", r.text)
    }

    @Test fun conversions() {
        val toman = CommandParser.parse("۱۲۰۰۰۰۰ ریال").filterIsInstance<Command.Info>().first()
        assertTrue(toman.title.startsWith("۱۲۰٬۰۰۰"))
        val words = CommandParser.parse("250000").filterIsInstance<Command.Info>().first()
        assertEquals("دویست و پنجاه هزار", words.title)
        assertTrue(CommandParser.parse("1405/7/17").none { it is Command.Calc })
        val date = CommandParser.parse("1405/7/17").filterIsInstance<Command.Info>().first()
        assertEquals("2026-10-09", date.copyText)
        val back = CommandParser.parse("2026-10-09").filterIsInstance<Command.Info>().first()
        assertEquals("1405/07/17", back.copyText)
        assertEquals("USD", CommandParser.parse("۵۰ دلار").filterIsInstance<Command.CurrencyConvert>().single().symbol)
    }

    @Test fun calculator() {
        assertEquals(10000.0, Calculator.evaluate("۲۵*۴۰۰")!!, 1e-9)
        assertEquals(7.0, Calculator.evaluate("1+2×3")!!, 1e-9)
        assertEquals(9.0, Calculator.evaluate("(1+2)^2")!!, 1e-9)
        assertNull(Calculator.evaluate("2/0"))
        assertNotNull(CommandParser.parse("12*3").filterIsInstance<Command.Calc>().firstOrNull())
    }
}

class PrayTimesTest {
    @Test fun tehranIsReasonable() {
        val t = PrayTimes.compute(LocalDate.of(2026, 10, 9), 35.6892, 51.3890)
        val dhuhr = PrayTimes.toMinuteOfDay(t.getValue(PrayTimes.Time.DHUHR))
        val fajr = PrayTimes.toMinuteOfDay(t.getValue(PrayTimes.Time.FAJR))
        val maghrib = PrayTimes.toMinuteOfDay(t.getValue(PrayTimes.Time.MAGHRIB))
        assertTrue("dhuhr=$dhuhr", dhuhr in (11 * 60 + 40)..(12 * 60 + 5))
        assertTrue("fajr=$fajr", fajr in (4 * 60 + 40)..(5 * 60 + 30))
        assertTrue("maghrib=$maghrib", maghrib in (17 * 60 + 40)..(18 * 60 + 30))
    }
}

class IranDataTest {
    @Test fun cards() {
        assertTrue(BankCards.isValidLuhn("6037990000000006"))
        assertEquals("ملی", BankCards.bankName("6037-9912-3456-7893"))
        assertFalse(BankCards.isValidLuhn("1234"))
        assertFalse(BankCards.isValidSheba("IR00"))
    }

    @Test fun oddEven() {
        val rule = OddEvenRule()
        val saturday = LocalDate.of(2026, 10, 10)
        assertEquals(OddEvenRule.Status.ALLOWED, rule.status(saturday, 4, false))
        assertEquals(OddEvenRule.Status.NOT_ALLOWED, rule.status(saturday, 3, false))
        assertEquals(OddEvenRule.Status.FREE, rule.status(LocalDate.of(2026, 10, 9), 3, false))
    }
}

class SpaceTest {
    private val ctx = ContextSnapshot(minuteOfDay = 10 * 60, weekIndex = 1, wifiSsid = "\"Office-5G\"", batteryPercent = 15)

    @Test fun triggers() {
        assertTrue(SpaceEngine.matches(Trigger.Wifi(listOf("office-5g")), ctx))
        assertTrue(SpaceEngine.matches(Trigger.Time(9 * 60, 17 * 60, setOf(0, 1, 2, 3, 4)), ctx))
        assertFalse(SpaceEngine.matches(Trigger.Time(9 * 60, 17 * 60, setOf(5, 6)), ctx))
        assertTrue(SpaceEngine.matches(Trigger.BatteryBelow(20), ctx))
        // Overnight window 23:00 - 07:00
        val night = Trigger.Time(23 * 60, 7 * 60, setOf(0))
        assertTrue(SpaceEngine.inTimeWindow(night, 23 * 60 + 30, 0))
        assertTrue(SpaceEngine.inTimeWindow(night, 3 * 60, 1))
        assertFalse(SpaceEngine.inTimeWindow(night, 3 * 60, 0))
    }

    @Test fun priorityAndManual() {
        val work = Space("work", "کار", priority = 20, triggers = listOf(Trigger.Wifi(listOf("Office-5G"))))
        val battery = Space("battery", "باتری", priority = 5, triggers = listOf(Trigger.BatteryBelow(20)))
        assertEquals("battery", SpaceEngine.resolve(listOf(work, battery), ctx, null)?.id)
        assertEquals("work", SpaceEngine.resolve(listOf(work, battery), ctx, "work")?.id)
        assertNull(SpaceEngine.resolve(listOf(work, battery), ctx, SpaceEngine.BASE_ID))
    }

    @Test fun rules() {
        val facts = AppFacts("com.example.bank", "بانک نمونه", installedAtMillis = 1)
        val cat = AppClassifier.classify(facts)
        assertEquals(AppCategory.FINANCE, cat)
        val rule = SortRule("r", "old", conditions = listOf(RuleCondition.UnusedForDays(30)), action = RuleAction.Archive)
        assertTrue(RuleEngine.matches(rule, facts, cat, 40L * 24 * 3600 * 1000))
        assertEquals(AppCategory.MESSAGING, AppClassifier.classify(AppFacts("org.telegram.messenger", "Telegram")))
        assertEquals(AppCategory.TOOLS, AppClassifier.classify(AppFacts("com.simplemobiletools.calculator", "Calculator")))
        assertFalse(AppClassifier.classify(AppFacts("com.android.bluetooth", "Bluetooth", isSystem = true)) == AppCategory.FINANCE)
    }
}

class GridTest {
    @Test fun placement() {
        val cells = listOf(Cell("clock", 0, 0, 4, 2), Cell("a", 0, 5))
        assertFalse(GridMath.canPlace(cells, Cell("x", 1, 1, 2, 2), 4, 6))
        assertTrue(GridMath.canPlace(cells, Cell("x", 0, 2, 2, 2), 4, 6))
        assertFalse(GridMath.canPlace(cells, Cell("x", 3, 0, 2, 1), 4, 6))
        assertEquals(0 to 2, GridMath.findFree(cells, 4, 2, 4, 6))
        assertEquals(1 to 5, GridMath.findFree(cells, 1, 1, 4, 6, fromBottom = true))
        assertNull(GridMath.findFree(cells, 4, 5, 4, 6))
        assertEquals("a", GridMath.at(cells, 0, 5)?.id)
    }

    @Test fun normalizeShrinksGrid() {
        val cells = listOf(Cell("w", 0, 0, 5, 2), Cell("a", 4, 3), Cell("b", 0, 3))
        val (placed, homeless) = GridMath.normalize(cells, 4, 6)
        assertTrue(homeless.isEmpty())
        assertEquals(4, placed.first { it.id == "w" }.w)
        assertTrue(placed.all { GridMath.fits(it, 4, 6) })
        for (i in placed.indices) for (j in placed.indices) if (i != j) assertFalse(placed[i].overlaps(placed[j]))
    }
}
