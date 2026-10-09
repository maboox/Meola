package ir.nama.launcher.ui.widgets

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessAlarm
import androidx.compose.material.icons.outlined.Brightness3
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dehaze
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.nama.core.Cities
import ir.nama.core.Hijri
import ir.nama.core.IranCalendar
import ir.nama.core.Jalali
import ir.nama.core.JalaliDate
import ir.nama.core.PrayTimes
import ir.nama.launcher.Nama
import ir.nama.launcher.data.Countdown
import ir.nama.launcher.data.NetState
import ir.nama.launcher.num
import ir.nama.launcher.system.Actions
import ir.nama.launcher.system.Checks
import ir.nama.launcher.tr
import ir.nama.launcher.ui.common.JalaliDateDialog
import ir.nama.launcher.ui.common.TextInputDialog
import ir.nama.launcher.ui.home.HomeEnv
import ir.nama.launcher.ui.theme.LocalNamaStyle
import ir.nama.launcher.ui.theme.Occasions
import ir.nama.launcher.ui.theme.wallpaperText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

// ------------------------------------------------------------------------------------------------
// At a glance: no card, text right on the wallpaper
// ------------------------------------------------------------------------------------------------

@Composable
fun GlanceWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val now = rememberNow()
    val today = now.toLocalDate()
    val weather by Nama.weather.weather.collectAsState()
    LaunchedEffect(sc.env.settings.cityId) { Nama.weather.refreshIfStale(sc.env.settings.cityId) }
    val events = remember(today) { IranCalendar.events(today, sc.env.settings.hijriOffset) }
    val occasion = remember(today) { IranCalendar.occasion(today, sc.env.settings.hijriOffset) }
    val prayer = nextPrayer(sc.env, now)
    val dateLine = weekdayName(today) + (if (Nama.isFa) "، " else ", ") + jalaliShort(today)
    val info = buildList {
        weather?.takeIf { it.cityId == sc.env.settings.cityId }?.let { add(num(Math.round(it.tempC)) + "°") }
        if (prayer != null) add(prayer.first + " " + hhmm(prayer.second, prayer.third))
        events.firstOrNull()?.let { add(it.title) }
        if (sc.env.settings.seasonalThemes) Occasions.greeting(occasion, Nama.isFa)?.let { add(it) }
        sc.env.space?.let { add(it.name) }
        if (sc.env.settings.nationalNetMode && sc.env.net == NetState.NATIONAL_ONLY) add(tr("فقط اینترنت داخلی", "Domestic internet only"))
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        if (sc.tall) {
            Text(
                hhmm(now.hour, now.minute), style = wallpaperText(if (sc.heightDp > 150) 76 else 60, FontWeight.ExtraLight),
                modifier = Modifier.clickable { Actions.start(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
            )
        }
        Text(
            dateLine, style = wallpaperText(if (sc.tall) 20 else 22, FontWeight.Medium),
            modifier = Modifier.clickable { Actions.start(ctx, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)) }
        )
        if (info.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                weather?.let { Icon(weatherIcon(it.code), null, tint = s.onWallpaper, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
                Text(info.joinToString("  ·  "), style = wallpaperText(13), maxLines = 1)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Clock: analog dial (2×2), big digital (4×2), compact (2×1)
// ------------------------------------------------------------------------------------------------

@Composable
fun ClockWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val now = rememberNow()
    val open = { Actions.start(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS)); Unit }
    when {
        sc.size.w == 2 && sc.size.h == 2 -> Box(Modifier.fillMaxSize().clickable(onClick = open), contentAlignment = Alignment.Center) {
            AnalogClock(now, Modifier.fillMaxSize(0.94f).aspectRatio(1f))
        }
        sc.size.h == 1 -> WidgetCard(onClick = open) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                WText(hhmm(now.hour, now.minute), 28.sp, FontWeight.Light)
                WText(weekdayName(now.toLocalDate()), 12.sp, secondary = true)
            }
        }
        else -> WidgetCard(onClick = open) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(hhmm(now.hour, now.minute), color = s.onSurface, fontSize = 64.sp, fontWeight = FontWeight.ExtraLight, lineHeight = 70.sp)
                WText(weekdayName(now.toLocalDate()) + " · " + jalaliLong(now.toLocalDate()), 14.sp, secondary = true)
            }
        }
    }
}

@Composable
fun AnalogClock(now: LocalDateTime, modifier: Modifier) {
    val s = LocalNamaStyle.current
    val face = if (s.minimal) Color(0xFF111111) else s.surface
    val tick = s.onSurfaceVariant.copy(alpha = 0.6f)
    val hourHand = s.onSurface
    val minuteHand = s.accent
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(face, r, c)
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0 - 90)
            val outer = r * 0.86f
            val inner = if (i % 3 == 0) r * 0.74f else r * 0.80f
            drawLine(
                tick, Offset(c.x + inner * cos(a).toFloat(), c.y + inner * sin(a).toFloat()),
                Offset(c.x + outer * cos(a).toFloat(), c.y + outer * sin(a).toFloat()),
                strokeWidth = if (i % 3 == 0) r * 0.045f else r * 0.025f, cap = StrokeCap.Round
            )
        }
        val hourA = Math.toRadians(((now.hour % 12) + now.minute / 60.0) * 30 - 90)
        val minA = Math.toRadians(now.minute * 6.0 - 90)
        drawLine(hourHand, c, Offset(c.x + r * 0.48f * cos(hourA).toFloat(), c.y + r * 0.48f * sin(hourA).toFloat()), strokeWidth = r * 0.09f, cap = StrokeCap.Round)
        drawLine(minuteHand, c, Offset(c.x + r * 0.70f * cos(minA).toFloat(), c.y + r * 0.70f * sin(minA).toFloat()), strokeWidth = r * 0.06f, cap = StrokeCap.Round)
        drawCircle(minuteHand, r * 0.06f, c)
    }
}

// ------------------------------------------------------------------------------------------------
// Calendar: today (2×2) or month (4×3 / 4×4)
// ------------------------------------------------------------------------------------------------

@Composable
fun CalendarWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val today = rememberNow().toLocalDate()
    val off = sc.env.settings.hijriOffset
    if (!sc.wide) {
        val j = JalaliDate.from(today)
        val events = remember(today) { IranCalendar.events(today, off) }
        val holiday = IranCalendar.isHoliday(today, off)
        WidgetCard {
            Column(Modifier.fillMaxSize()) {
                WText(weekdayName(today), 14.sp, FontWeight.Medium, color = if (holiday) s.danger else s.accent)
                Text(num(j.day), color = s.onSurface, fontSize = 52.sp, fontWeight = FontWeight.Light, lineHeight = 58.sp)
                WText(monthName(j) + " " + num(j.year), 13.sp, secondary = true)
                WSpacer()
                WText(events.firstOrNull()?.title ?: (Hijri.from(today, off)?.formatLong(Nama.faDigits) ?: ""), 11.sp, secondary = true, maxLines = 2)
            }
        }
        return
    }
    val tj = JalaliDate.from(today)
    var shown by remember { mutableStateOf(JalaliDate(tj.year, tj.month, 1)) }
    var selected by remember { mutableStateOf(today) }
    val first = shown.toLocalDate()
    val len = Jalali.monthLength(shown.year, shown.month)
    val lead = Jalali.weekIndex(first)
    WidgetCard(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WText(monthName(shown) + " " + num(shown.year), 16.sp, FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, null, tint = s.onSurfaceVariant, modifier = Modifier.clip(CircleShape).clickable { shown = Jalali.addMonths(shown, -1) }.padding(4.dp))
                Icon(Icons.Outlined.ChevronLeft, null, tint = s.onSurfaceVariant, modifier = Modifier.clip(CircleShape).clickable { shown = Jalali.addMonths(shown, 1) }.padding(4.dp))
            }
            val names = if (Nama.isFa) Jalali.WEEKDAYS_SHORT else Jalali.WEEKDAYS_EN.map { it.take(2) }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                names.forEachIndexed { i, n -> Text(n, Modifier.weight(1f), color = if (i == 6) s.danger else s.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.Center) }
            }
            val rows = (lead + len + 6) / 7
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val dayNum = r * 7 + c - lead + 1
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (dayNum in 1..len) {
                                    val d = first.plusDays((dayNum - 1).toLong())
                                    val isToday = d == today
                                    val isSel = d == selected && !isToday
                                    val holiday = IranCalendar.isHoliday(d, off)
                                    Box(
                                        Modifier.size(28.dp).clip(CircleShape)
                                            .background(if (isToday) s.accent else if (isSel) s.accentContainer else Color.Transparent)
                                            .clickable { selected = d },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            num(dayNum), fontSize = 13.sp,
                                            color = when { isToday -> if (s.minimal) Color.Black else Color.White; holiday -> s.danger; else -> s.onSurface },
                                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val ev = remember(selected) { IranCalendar.events(selected, off) }
            WText(
                (ev.firstOrNull()?.title ?: weekdayName(selected) + " " + jalaliLong(selected)) +
                    (Hijri.from(selected, off)?.let { " · " + it.formatLong(Nama.faDigits) } ?: ""),
                11.sp, secondary = true, color = if (ev.any { it.holiday }) s.danger else null
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Today
// ------------------------------------------------------------------------------------------------

@Composable
fun TodayWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val now = rememberNow()
    val today = now.toLocalDate()
    val personal by Nama.store.personal.flow.collectAsState()
    val alarm = remember(now) {
        try { ctx.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime } catch (e: Exception) { null }
    }
    val prayer = nextPrayer(sc.env, now)
    val bill = personal.bills.filter { it.paidEpochDay == null || it.paidEpochDay < it.dueEpochDay }.minByOrNull { it.dueEpochDay }
    val todos = personal.todos.filter { !it.done }
    WidgetCard {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            WText(weekdayName(today) + " · " + jalaliShort(today), 15.sp, FontWeight.Bold)
            alarm?.let {
                val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
                WRow(Icons.Outlined.AccessAlarm, tr("آلارم بعدی", "Next alarm"), trailing = hhmm(t.hour, t.minute))
            }
            if (sc.size.h >= 2 || alarm == null) prayer?.let { (n, h, m) -> WRow(Icons.Outlined.Brightness3, n, trailing = hhmm(h, m)) }
            bill?.let { b ->
                val days = b.dueEpochDay - today.toEpochDay()
                WRow(Icons.Outlined.ReceiptLong, b.title, trailing = when { days < 0 -> tr("گذشته", "overdue"); days == 0L -> tr("امروز", "today"); else -> num(days) + tr(" روز", "d") }, trailingColor = if (days <= 1) s.danger else null)
            }
            if (todos.isNotEmpty()) WRow(Icons.Outlined.TaskAlt, todos.first().text, sub = if (todos.size > 1) num(todos.size - 1) + tr(" کار دیگر", " more") else null)
            if (alarm == null && bill == null && todos.isEmpty()) WText(tr("روز آرامی است", "A calm day"), 13.sp, secondary = true)
        }
    }
}

/** (name, hour, minute) of the next prayer time, or tomorrow's Fajr. */
fun nextPrayer(env: HomeEnv, now: LocalDateTime): Triple<String, Int, Int>? {
    val c = Cities.byId(env.settings.cityId)
    val times = PrayTimes.compute(now.toLocalDate(), c.lat, c.lng)
    val cur = now.hour * 60 + now.minute
    for (k in listOf(PrayTimes.Time.FAJR, PrayTimes.Time.SUNRISE, PrayTimes.Time.DHUHR, PrayTimes.Time.MAGHRIB, PrayTimes.Time.MIDNIGHT)) {
        val m = PrayTimes.toMinuteOfDay(times.getValue(k))
        if (m > cur) return Triple(prayerName(k), m / 60, m % 60)
    }
    val m = PrayTimes.toMinuteOfDay(PrayTimes.compute(now.toLocalDate().plusDays(1), c.lat, c.lng).getValue(PrayTimes.Time.FAJR))
    return Triple(prayerName(PrayTimes.Time.FAJR), m / 60, m % 60)
}

fun prayerName(t: PrayTimes.Time) = when (t) {
    PrayTimes.Time.IMSAK -> tr("امساک", "Imsak")
    PrayTimes.Time.FAJR -> tr("اذان صبح", "Fajr")
    PrayTimes.Time.SUNRISE -> tr("طلوع", "Sunrise")
    PrayTimes.Time.DHUHR -> tr("اذان ظهر", "Dhuhr")
    PrayTimes.Time.ASR -> tr("عصر", "Asr")
    PrayTimes.Time.SUNSET -> tr("غروب", "Sunset")
    PrayTimes.Time.MAGHRIB -> tr("اذان مغرب", "Maghrib")
    PrayTimes.Time.ISHA -> tr("عشا", "Isha")
    PrayTimes.Time.MIDNIGHT -> tr("نیمه‌شب", "Midnight")
}

private fun shortPrayer(t: PrayTimes.Time) = when (t) {
    PrayTimes.Time.IMSAK -> tr("امساک", "Imsak")
    PrayTimes.Time.FAJR -> tr("صبح", "Fajr")
    PrayTimes.Time.SUNRISE -> tr("طلوع", "Rise")
    PrayTimes.Time.DHUHR -> tr("ظهر", "Dhuhr")
    PrayTimes.Time.MAGHRIB -> tr("مغرب", "Maghrib")
    PrayTimes.Time.MIDNIGHT -> tr("نیمه‌شب", "Midn.")
    else -> prayerName(t)
}

@Composable
fun PrayerWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val now = rememberNow()
    val city = Cities.byId(sc.env.settings.cityId)
    val times = remember(now.toLocalDate(), city) { PrayTimes.compute(now.toLocalDate(), city.lat, city.lng) }
    val ramadan = remember(now.toLocalDate()) { IranCalendar.isRamadan(now.toLocalDate(), sc.env.settings.hijriOffset) }
    val cur = now.hour * 60 + now.minute
    val keys = if (ramadan) listOf(PrayTimes.Time.IMSAK, PrayTimes.Time.DHUHR, PrayTimes.Time.MAGHRIB, PrayTimes.Time.MIDNIGHT)
    else listOf(PrayTimes.Time.FAJR, PrayTimes.Time.SUNRISE, PrayTimes.Time.DHUHR, PrayTimes.Time.MAGHRIB, PrayTimes.Time.MIDNIGHT)
    val next = keys.firstOrNull { PrayTimes.toMinuteOfDay(times.getValue(it)) > cur }
    val left = next?.let { PrayTimes.toMinuteOfDay(times.getValue(it)) - cur }
    val leftText = left?.let { if (it >= 60) num(it / 60) + tr(" ساعت و ", "h ") + num(it % 60) + tr(" دقیقه", "m") else num(it) + tr(" دقیقه", " min") }
    WidgetCard {
        if (sc.wide && sc.size.h == 1) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Brightness3, null, tint = s.accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    WText(next?.let { prayerName(it) } ?: prayerName(PrayTimes.Time.FAJR), 15.sp, FontWeight.Medium)
                    WText((if (Nama.isFa) city.fa else city.en) + (leftText?.let { " · " + it + tr(" دیگر", " left") } ?: ""), 12.sp, secondary = true)
                }
                WText(PrayTimes.format(times.getValue(next ?: PrayTimes.Time.FAJR), Nama.faDigits), 24.sp, FontWeight.Light)
            }
        } else if (sc.wide) {
            Column(Modifier.fillMaxSize()) {
                WHeader(Icons.Outlined.Brightness3, tr("اوقات شرعی ", "Prayer times ") + (if (Nama.isFa) city.fa else city.en), leftText?.let { it + tr(" تا ", " to ") + (next?.let { n -> shortPrayer(n) } ?: "") })
                WSpacer()
                Row(Modifier.fillMaxWidth()) {
                    keys.forEach { k ->
                        val isNext = k == next
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(PrayTimes.format(times.getValue(k), Nama.faDigits), color = if (isNext) s.accent else s.onSurface, fontSize = 16.sp, fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal)
                            Text(shortPrayer(k), color = s.onSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                }
                WSpacer()
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Icon(Icons.Outlined.Brightness3, null, tint = s.accent, modifier = Modifier.size(22.dp))
                WSpacer()
                WText(next?.let { prayerName(it) } ?: prayerName(PrayTimes.Time.FAJR), 14.sp, secondary = true)
                WText(PrayTimes.format(times.getValue(next ?: PrayTimes.Time.FAJR), Nama.faDigits), 34.sp, FontWeight.Light)
                leftText?.let { WText(it + tr(" دیگر", " left"), 11.sp, secondary = true) }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Countdown and birthdays
// ------------------------------------------------------------------------------------------------

@Composable
fun CountdownWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val personal by Nama.store.personal.flow.collectAsState()
    val today = rememberNow().toLocalDate()
    var adding by remember { mutableStateOf(false) }
    var pickFor by remember { mutableStateOf<String?>(null) }
    val nowruz = IranCalendar.nextSolar(today.plusDays(1), 1, 1)
    val items = (personal.countdowns.map { c ->
        val target = if (c.yearly) Checks.nextYearly(c.epochDay, today) else c.epochDay
        c.title to (target - today.toEpochDay())
    } + (tr("نوروز", "Nowruz") to (nowruz.toEpochDay() - today.toEpochDay()))).filter { it.second >= 0 }.sortedBy { it.second }
    val (title, days) = items.first()
    WidgetCard(onClick = { adding = true }) {
        if (sc.size.h == 1) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Text(num(days), color = s.accent, fontSize = 30.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.width(8.dp))
                WText(tr("روز تا ", "days to ") + title, 13.sp, secondary = true, maxLines = 2)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                WText(tr("تا ", "To ") + title, 13.sp, secondary = true)
                WSpacer()
                Text(num(days), color = s.accent, fontSize = 52.sp, fontWeight = FontWeight.Light, lineHeight = 56.sp)
                WText(tr("روز", "days"), 14.sp)
                items.getOrNull(1)?.let { WText(num(it.second) + tr(" روز تا ", " days to ") + it.first, 11.sp, secondary = true) }
            }
        }
    }
    if (adding) TextInputDialog(tr("شمارش معکوس تازه (مثلاً کنکور، تولد سارا)", "New countdown (e.g. exam, birthday)"), onDismiss = { adding = false }) { t ->
        if (t.isNotBlank()) pickFor = t
    }
    pickFor?.let { t ->
        JalaliDateDialog(today.plusDays(30), tr("تاریخ «$t»", "Date for \"$t\""), onDismiss = { pickFor = null }) { d ->
            val yearly = t.contains("تولد") || t.contains("سالگرد") || t.contains("birthday", true)
            Nama.store.personal.update { it.copy(countdowns = it.countdowns + Countdown(UUID.randomUUID().toString(), t, d.toEpochDay(), yearly)) }
        }
    }
}

@Composable
fun BirthdaysWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val granted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val list by produceState(emptyList<Pair<String, Long>>(), granted) {
        value = if (granted) withContext(Dispatchers.IO) { Checks.birthdays(ctx).take(4) } else emptyList()
    }
    WidgetCard {
        when {
            !granted -> WEmpty(Icons.Outlined.Cake, tr("برای دیدن تولدها، اجازه مخاطبین لازم است", "Contacts access is needed"), tr("اجازه بده", "Allow")) {
                sc.ctrl.requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS))
            }
            list.isEmpty() -> WEmpty(Icons.Outlined.Cake, tr("تولدی در مخاطبین ثبت نشده", "No birthdays in contacts"))
            else -> Column(Modifier.fillMaxSize()) {
                WHeader(Icons.Outlined.Cake, tr("تولدها", "Birthdays"))
                list.take(if (sc.wide) 3 else 2).forEach { (name, days) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(s.accentContainer), contentAlignment = Alignment.Center) {
                            Text(name.take(1), color = s.onAccentContainer, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                        WText(name, 13.sp, modifier = Modifier.weight(1f))
                        WText(if (days == 0L) tr("امروز", "today") else jalaliShort(LocalDate.now().plusDays(days)), 12.sp, color = if (days == 0L) s.accent else s.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

fun weatherIcon(code: Int) = when (code) {
    0 -> Icons.Outlined.WbSunny
    1, 2 -> Icons.Outlined.WbCloudy
    3 -> Icons.Outlined.Cloud
    45, 48 -> Icons.Outlined.Dehaze
    51, 53, 55, 56, 57 -> Icons.Outlined.Grain
    61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Outlined.Umbrella
    71, 73, 75, 77, 85, 86 -> Icons.Outlined.AcUnit
    95, 96, 99 -> Icons.Outlined.FlashOn
    else -> Icons.Outlined.WbSunny
}
