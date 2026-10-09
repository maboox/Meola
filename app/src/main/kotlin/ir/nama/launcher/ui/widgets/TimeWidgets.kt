package ir.nama.launcher.ui.widgets

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import ir.nama.core.PersianText
import ir.nama.core.PrayTimes
import ir.nama.launcher.Nama
import ir.nama.launcher.data.Countdown
import ir.nama.launcher.data.WidgetInstance
import ir.nama.launcher.num
import ir.nama.launcher.system.Actions
import ir.nama.launcher.system.Checks
import ir.nama.launcher.tr
import ir.nama.launcher.ui.common.JalaliDateDialog
import ir.nama.launcher.ui.common.TextInputDialog
import ir.nama.launcher.ui.home.HomeController
import ir.nama.launcher.ui.home.HomeEnv
import ir.nama.launcher.ui.theme.LocalNamaStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

fun weekdayName(d: LocalDate): String = if (Nama.isFa) Jalali.WEEKDAYS[Jalali.weekIndex(d)] else Jalali.WEEKDAYS_EN[Jalali.weekIndex(d)]

fun jalaliLong(d: LocalDate): String {
    val j = JalaliDate.from(d)
    return if (Nama.isFa) num("${j.day} ${j.monthName} ${j.year}") else "${j.day} ${Jalali.MONTHS_EN[j.month - 1]} ${j.year}"
}

fun hhmm(h: Int, m: Int) = num("${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}")

@Composable
fun ClockWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val now = rememberNow()
    val today = now.toLocalDate()
    val hijri = remember(today) { Hijri.from(today, env.settings.hijriOffset) }
    val events = remember(today) { IranCalendar.events(today, env.settings.hijriOffset) }
    WidgetFrame(ctrl, pageIndex, w, title = null, onClick = { Actions.start(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS)) }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (s.textOnly) Alignment.Start else Alignment.CenterHorizontally) {
            Text(
                hhmm(now.hour, now.minute), color = s.accent2.takeIf { s.pattern } ?: s.text,
                fontSize = if (s.pattern) 64.sp else 58.sp, fontFamily = s.clockFont,
                fontWeight = if (s.pattern) FontWeight.Normal else FontWeight.ExtraLight, lineHeight = 70.sp
            )
            Text(
                weekdayName(today) + " · " + jalaliLong(today), color = s.text, fontSize = 15.sp, fontWeight = FontWeight.Medium
            )
            Text(
                listOfNotNull(hijri?.let { if (Nama.isFa) it.formatLong(Nama.faDigits) else null }, today.toString().let { num(it) }).joinToString(" · "),
                color = s.subText, fontSize = 12.sp
            )
            events.firstOrNull()?.let { e ->
                Text((if (e.holiday) "● " else "○ ") + e.title, color = if (e.holiday) s.danger else s.accent, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

fun timeInWords(h: Int, m: Int): String {
    if (!Nama.isFa) return hhmm(h, m)
    val part = when (h) {
        in 0..3 -> "بامداد"
        in 4..11 -> "صبح"
        in 12..13 -> "ظهر"
        in 14..17 -> "بعدازظهر"
        in 18..19 -> "عصر"
        else -> "شب"
    }
    fun hw(x: Int): String { val v = x % 12; return PersianText.numberToWords((if (v == 0) 12 else v).toLong()) }
    val text = when {
        m == 0 -> "ساعت ${hw(h)}"
        m == 15 -> "ساعت ${hw(h)} و ربع"
        m == 30 -> "ساعت ${hw(h)} و نیم"
        m == 45 -> "یک ربع به ${hw(h + 1)}"
        m < 30 -> "ساعت ${hw(h)} و ${PersianText.numberToWords(m.toLong())} دقیقه"
        else -> "${PersianText.numberToWords((60 - m).toLong())} دقیقه به ${hw(h + 1)}"
    }
    return "$text $part"
}

@Composable
fun ClockWordsWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val now = rememberNow()
    WidgetFrame(ctrl, pageIndex, w, title = null) {
        Text(timeInWords(now.hour, now.minute), color = s.text, fontSize = 30.sp, fontWeight = FontWeight.Light, lineHeight = 44.sp)
        Text(weekdayName(now.toLocalDate()) + "، " + jalaliLong(now.toLocalDate()), color = s.subText, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun CalendarWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val today = rememberNow().toLocalDate()
    val tj = JalaliDate.from(today)
    var shown by remember { mutableStateOf(JalaliDate(tj.year, tj.month, 1)) }
    var selected by remember { mutableStateOf(today) }
    val first = shown.toLocalDate()
    val len = Jalali.monthLength(shown.year, shown.month)
    val lead = Jalali.weekIndex(first)
    val title = (if (Nama.isFa) shown.monthName else Jalali.MONTHS_EN[shown.month - 1]) + " " + num(shown.year)
    WidgetFrame(ctrl, pageIndex, w, title = title) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = s.subText, fontSize = 20.sp, modifier = Modifier.clickable { shown = Jalali.addMonths(shown, -1) }.padding(horizontal = 10.dp))
            Spacer(Modifier.weight(1f))
            if (shown.year != tj.year || shown.month != tj.month) Text(tr("امروز", "Today"), color = s.accent, fontSize = 12.sp, modifier = Modifier.clickable { shown = JalaliDate(tj.year, tj.month, 1); selected = today })
            Spacer(Modifier.weight(1f))
            Text("›", color = s.subText, fontSize = 20.sp, modifier = Modifier.clickable { shown = Jalali.addMonths(shown, 1) }.padding(horizontal = 10.dp))
        }
        val names = if (Nama.isFa) Jalali.WEEKDAYS_SHORT else Jalali.WEEKDAYS_EN.map { it.take(2) }
        Row(Modifier.fillMaxWidth()) {
            names.forEachIndexed { i, n -> Text(n, Modifier.weight(1f), color = if (i == 6) s.danger else s.subText, fontSize = 11.sp, textAlign = TextAlign.Center) }
        }
        val cells = lead + len
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1.25f), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..len) {
                            val d = first.plusDays((dayNum - 1).toLong())
                            val holiday = IranCalendar.isHoliday(d, env.settings.hijriOffset)
                            val isToday = d == today
                            val isSel = d == selected
                            Box(
                                Modifier.clip(CircleShape)
                                    .background(if (isToday) s.accent else if (isSel) s.accent.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable { selected = d }.padding(horizontal = 6.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    num(dayNum), fontSize = 13.sp,
                                    color = when { isToday -> if (s.dark) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White; holiday -> s.danger; else -> s.text },
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
        val events = remember(selected) { IranCalendar.events(selected, env.settings.hijriOffset) }
        val h = remember(selected) { Hijri.from(selected, env.settings.hijriOffset) }
        Text(
            weekdayName(selected) + " " + jalaliLong(selected) + (h?.let { " · " + it.formatLong(Nama.faDigits) } ?: "") + " · " + num(selected.toString()),
            color = s.subText, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)
        )
        events.forEach { e -> Text((if (e.holiday) "● " else "○ ") + e.title, color = if (e.holiday) s.danger else s.text, fontSize = 12.sp) }
    }
}

@Composable
fun TodayWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val now = rememberNow()
    val personal by Nama.store.personal.flow.collectAsState()
    val weather by Nama.weather.weather.collectAsState()
    val next = remember(now) {
        try { ctx.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime } catch (e: Exception) { null }
    }
    val prayer = nextPrayer(env, now)
    val todos = personal.todos.filter { !it.done }.take(3)
    val today = now.toLocalDate()
    val bill = personal.bills.filter { it.paidEpochDay == null || it.paidEpochDay < it.dueEpochDay }.minByOrNull { it.dueEpochDay }
    WidgetFrame(ctrl, pageIndex, w, title = tr("امروزم", "My day"), trailing = weekdayName(today) + " " + jalaliLong(today)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            weather?.let { wt ->
                val (icon, desc) = ir.nama.launcher.data.WeatherRepo.describe(wt.code, Nama.isFa)
                Text("$icon ${num(Math.round(wt.tempC))}° $desc" + (wt.aqi?.let { tr(" · آلودگی هوا ", " · AQI ") + num(it) } ?: ""), color = s.text, fontSize = 13.sp)
            }
            if (next != null) {
                val t = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(next), java.time.ZoneId.systemDefault())
                Text("⏰ " + tr("آلارم بعدی ", "Next alarm ") + hhmm(t.hour, t.minute) + if (t.toLocalDate() != today) tr(" (فردا)", " (tomorrow)") else "", color = s.text, fontSize = 13.sp)
            }
            prayer?.let { (name, h, m) -> Text("🕌 $name " + hhmm(h, m), color = s.text, fontSize = 13.sp) }
            bill?.let { b ->
                val days = b.dueEpochDay - today.toEpochDay()
                Text("💳 ${b.title}: " + when { days < 0 -> tr("گذشته!", "overdue!"); days == 0L -> tr("امروز", "today"); else -> num(days) + tr(" روز دیگر", " days") }, color = if (days <= 1) s.danger else s.text, fontSize = 13.sp)
            }
            todos.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("○ ", color = s.accent, fontSize = 13.sp, modifier = Modifier.clickable {
                        Nama.store.personal.update { p -> p.copy(todos = p.todos.map { if (it.id == t.id) it.copy(done = true) else it }) }
                    })
                    Text(t.text, color = s.text, fontSize = 13.sp, maxLines = 1)
                }
            }
            if (todos.isEmpty() && next == null && bill == null) Text(tr("روز آرامی است. ✨", "A calm day. ✨"), color = s.subText, fontSize = 13.sp)
        }
    }
}

/** (name, hour, minute) of the next prayer time today, or Fajr of tomorrow. */
fun nextPrayer(env: HomeEnv, now: LocalDateTime): Triple<String, Int, Int>? {
    val c = Cities.byId(env.settings.cityId)
    val times = PrayTimes.compute(now.toLocalDate(), c.lat, c.lng)
    val cur = now.hour * 60 + now.minute
    val keys = listOf(PrayTimes.Time.FAJR, PrayTimes.Time.SUNRISE, PrayTimes.Time.DHUHR, PrayTimes.Time.MAGHRIB, PrayTimes.Time.MIDNIGHT)
    for (k in keys) {
        val m = PrayTimes.toMinuteOfDay(times.getValue(k))
        if (m > cur) return Triple(if (Nama.isFa) k.fa else k.en, m / 60, m % 60)
    }
    val t = PrayTimes.compute(now.toLocalDate().plusDays(1), c.lat, c.lng)
    val m = PrayTimes.toMinuteOfDay(t.getValue(PrayTimes.Time.FAJR))
    return Triple(if (Nama.isFa) PrayTimes.Time.FAJR.fa else "Fajr", m / 60, m % 60)
}

@Composable
fun PrayerWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val now = rememberNow()
    val city = Cities.byId(env.settings.cityId)
    val times = remember(now.toLocalDate(), city) { PrayTimes.compute(now.toLocalDate(), city.lat, city.lng) }
    val ramadan = remember(now.toLocalDate()) { IranCalendar.isRamadan(now.toLocalDate(), env.settings.hijriOffset) }
    val cur = now.hour * 60 + now.minute
    val shown = if (ramadan) listOf(PrayTimes.Time.IMSAK, PrayTimes.Time.SUNRISE, PrayTimes.Time.DHUHR, PrayTimes.Time.MAGHRIB, PrayTimes.Time.MIDNIGHT)
    else listOf(PrayTimes.Time.FAJR, PrayTimes.Time.SUNRISE, PrayTimes.Time.DHUHR, PrayTimes.Time.SUNSET, PrayTimes.Time.MAGHRIB, PrayTimes.Time.MIDNIGHT)
    val nextKey = shown.firstOrNull { PrayTimes.toMinuteOfDay(times.getValue(it)) > cur }
    val left = nextKey?.let { PrayTimes.toMinuteOfDay(times.getValue(it)) - cur }
    WidgetFrame(ctrl, pageIndex, w, title = tr("اوقات شرعی ", "Prayer times ") + (if (Nama.isFa) city.fa else city.en),
        trailing = if (nextKey != null && left != null) num("${left / 60}:${(left % 60).toString().padStart(2, '0')}") + tr(" تا ", " to ") + (if (Nama.isFa) nextKey.fa else nextKey.en) else null) {
        if (ramadan) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Text(tr("سحر تا ", "Suhoor until ") + PrayTimes.format(times.getValue(PrayTimes.Time.IMSAK), Nama.faDigits), color = s.accent2, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(tr("افطار ", "Iftar ") + PrayTimes.format(times.getValue(PrayTimes.Time.MAGHRIB), Nama.faDigits), color = s.accent2, fontSize = 14.sp)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            shown.forEach { k ->
                val isNext = k == nextKey
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(PrayTimes.format(times.getValue(k), Nama.faDigits), color = if (isNext) s.accent else s.text, fontSize = 14.sp, fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal)
                    Text(shortPrayerName(k), color = s.subText, fontSize = 10.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private fun shortPrayerName(t: PrayTimes.Time) = when (t) {
    PrayTimes.Time.IMSAK -> tr("امساک", "Imsak")
    PrayTimes.Time.FAJR -> tr("صبح", "Fajr")
    PrayTimes.Time.SUNRISE -> tr("طلوع", "Sunrise")
    PrayTimes.Time.DHUHR -> tr("ظهر", "Dhuhr")
    PrayTimes.Time.ASR -> tr("عصر", "Asr")
    PrayTimes.Time.SUNSET -> tr("غروب", "Sunset")
    PrayTimes.Time.MAGHRIB -> tr("مغرب", "Maghrib")
    PrayTimes.Time.ISHA -> tr("عشا", "Isha")
    PrayTimes.Time.MIDNIGHT -> tr("نیمه‌شب", "Midnight")
}

@Composable
fun CountdownWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val personal by Nama.store.personal.flow.collectAsState()
    val today = rememberNow().toLocalDate()
    var adding by remember { mutableStateOf(false) }
    var pickDateFor by remember { mutableStateOf<String?>(null) }
    val nowruz = IranCalendar.nextSolar(today.plusDays(1), 1, 1)
    val items = (personal.countdowns.map { c ->
        val target = if (c.yearly) Checks.nextYearly(c.epochDay, today) else c.epochDay
        Triple(c.id, c.title, target - today.toEpochDay())
    } + Triple("nowruz", tr("نوروز", "Nowruz"), nowruz.toEpochDay() - today.toEpochDay()))
        .filter { it.third >= 0 }.sortedBy { it.third }
    val top = items.first()
    WidgetFrame(ctrl, pageIndex, w, title = tr("شمارش معکوس", "Countdown")) {
        Text(num(top.third), color = s.accent, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Text(tr("روز تا ", "days to ") + top.second, color = s.text, fontSize = 13.sp)
        items.drop(1).take(2).forEach { Text(num(it.third) + tr(" روز تا ", " days to ") + it.second, color = s.subText, fontSize = 11.sp) }
        Text(tr("＋ افزودن", "＋ Add"), color = s.accent, fontSize = 12.sp, modifier = Modifier.clickable { adding = true }.padding(top = 4.dp))
    }
    if (adding) TextInputDialog(tr("عنوان (مثلاً کنکور، تولد سارا)", "Title (e.g. exam, birthday)"), onDismiss = { adding = false }) { t ->
        if (t.isNotBlank()) pickDateFor = t
    }
    pickDateFor?.let { title ->
        JalaliDateDialog(today.plusDays(30), tr("تاریخ «$title»", "Date for \"$title\""), onDismiss = { pickDateFor = null }) { d ->
            val yearly = title.contains("تولد") || title.contains("سالگرد") || title.contains("birthday", true)
            Nama.store.personal.update { it.copy(countdowns = it.countdowns + Countdown(UUID.randomUUID().toString(), title, d.toEpochDay(), yearly)) }
        }
    }
}

@Composable
fun BirthdaysWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val granted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val list by produceState(emptyList<Pair<String, Long>>(), granted) {
        value = if (granted) withContext(Dispatchers.IO) { Checks.birthdays(ctx).take(4) } else emptyList()
    }
    WidgetFrame(ctrl, pageIndex, w, title = tr("تولدهای نزدیک 🎂", "Upcoming birthdays 🎂")) {
        if (!granted) {
            Text(tr("برای دیدن تولدها، دسترسی مخاطبین لازم است.", "Contacts access is needed."), color = s.subText, fontSize = 12.sp)
            TextButton({ ctrl.requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS)) }) { Text(tr("اجازه بده", "Allow")) }
        } else if (list.isEmpty()) {
            Text(tr("تولدی در مخاطبین ثبت نشده.", "No birthdays saved in contacts."), color = s.subText, fontSize = 12.sp)
        } else list.forEach { (name, days) ->
            val d = LocalDate.now().plusDays(days)
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(name, color = s.text, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Text(if (days == 0L) tr("امروز! 🎉", "today! 🎉") else jalaliLong(d).substringBeforeLast(' ') + " · " + num(days) + tr(" روز", "d"), color = if (days == 0L) s.accent else s.subText, fontSize = 12.sp)
            }
        }
    }
}
