package ir.nama.launcher.ui.widgets

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.nama.core.Jalali
import ir.nama.core.NewsLogic
import ir.nama.core.OddEvenRule
import ir.nama.core.Poem
import ir.nama.core.Poems
import ir.nama.launcher.Nama
import ir.nama.launcher.data.NotificationRepo
import ir.nama.launcher.data.WidgetInstance
import ir.nama.launcher.num
import ir.nama.launcher.system.Actions
import ir.nama.launcher.tr
import ir.nama.launcher.ui.common.AppPickerDialog
import ir.nama.launcher.ui.common.minuteText
import ir.nama.launcher.ui.home.HomeController
import ir.nama.launcher.ui.home.HomeEnv
import ir.nama.launcher.ui.home.visibleNews
import ir.nama.launcher.ui.theme.LocalNamaStyle
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
private fun PoemBody(p: Poem, verse: Boolean = false) {
    val s = LocalNamaStyle.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(p.line1, color = s.text, fontSize = if (verse) 17.sp else 15.sp, textAlign = TextAlign.Center, lineHeight = 28.sp)
        Text(p.line2, color = if (verse) s.subText else s.text, fontSize = if (verse) 13.sp else 15.sp, textAlign = TextAlign.Center, lineHeight = 26.sp)
        Text(p.poet, color = s.accent, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun HafezWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val all = Poems.HAFEZ + Nama.remote.config.value.extraHafez
    val day = rememberNow().toLocalDate().toEpochDay()
    var pick by remember(day) { mutableIntStateOf(-1) }
    val poem = if (pick >= 0) all[pick % all.size] else Poems.ofDay(all, day, 7)
    WidgetFrame(ctrl, pageIndex, w, title = tr("📜 فال حافظ", "📜 Hafez")) {
        AnimatedContent(poem, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "fal") { PoemBody(it) }
        Text(
            tr("نیت کن و دوباره فال بگیر", "Make a wish, draw again"), color = s.subText, fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { pick = Random.nextInt(all.size) }.padding(top = 6.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PoemWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val all = Poems.OTHERS + Poems.HAFEZ + Nama.remote.config.value.extraPoems
    val day = rememberNow().toLocalDate().toEpochDay()
    WidgetFrame(ctrl, pageIndex, w, title = tr("شعر روز", "Poem of the day")) { PoemBody(Poems.ofDay(all, day, 3)) }
}

@Composable
fun QuoteWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val day = rememberNow().toLocalDate().toEpochDay()
    WidgetFrame(ctrl, pageIndex, w, title = tr("جمله روز", "Quote")) {
        Text(Poems.ofDay(Poems.QUOTES, day, 11), color = s.text, fontSize = 15.sp, lineHeight = 26.sp)
    }
}

@Composable
fun VerseWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val day = rememberNow().toLocalDate().toEpochDay()
    WidgetFrame(ctrl, pageIndex, w, title = tr("آیه روز", "Verse of the day")) { PoemBody(Poems.ofDay(Poems.VERSES, day, 5), verse = true) }
}

private fun minutes(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) num(m / 60) + tr(" ساعت و ", "h ") + num(m % 60) + tr(" دقیقه", "m") else num(m) + tr(" دقیقه", " min")
}

@Composable
fun UsageWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val today by Nama.usage.today.collectAsState()
    LaunchedEffect(Unit) { Nama.usage.refreshToday(true) }
    WidgetFrame(ctrl, pageIndex, w, title = tr("⏳ امروز با گوشی", "⏳ Screen time")) {
        if (!Nama.usage.hasPermission()) {
            Text(tr("برای آمار، دسترسی آمار استفاده لازم است.", "Usage access is needed."), color = s.subText, fontSize = 12.sp)
            TextButton({ Actions.start(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text(tr("دادن دسترسی", "Grant")) }
        } else {
            val total = today.values.sum()
            Text(minutes(total), color = s.text, fontSize = 22.sp, fontWeight = FontWeight.Light)
            today.entries.sortedByDescending { it.value }.take(3).forEach { (pkg, ms) ->
                val name = Nama.apps.byPackage(pkg)?.label ?: return@forEach
                Text("$name · ${minutes(ms)}", color = s.subText, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun FocusWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    // Reading the minute ticker recomposes this widget every minute, keeping "minutes left" fresh.
    rememberNow()
    var picking by remember { mutableStateOf(false) }
    val until = env.settings.focusUntil
    val active = until > System.currentTimeMillis()
    WidgetFrame(ctrl, pageIndex, w, title = tr("◎ حالت تمرکز", "◎ Focus")) {
        if (active) {
            Text(num((until - System.currentTimeMillis()) / 60_000) + tr(" دقیقه مانده", " min left"), color = s.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(tr("پایان تمرکز", "End focus"), color = s.subText, fontSize = 12.sp, modifier = Modifier.clickable { Nama.store.settings.update { it.copy(focusUntil = 0L) } }.padding(top = 4.dp))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(25, 50, 90).forEach { m ->
                    Text(num(m) + "'", color = s.text, fontSize = 15.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                        Nama.store.settings.update { it.copy(focusUntil = System.currentTimeMillis() + m * 60_000L) }
                    }.padding(6.dp))
                }
            }
            Text(
                tr("برنامه‌های مجاز: ", "Allowed apps: ") + num(env.settings.focusApps.size), color = s.subText, fontSize = 11.sp,
                modifier = Modifier.clickable { picking = true }
            )
        }
    }
    if (picking) AppPickerDialog(tr("برنامه‌های مجاز در تمرکز", "Apps allowed in focus"), env.settings.focusApps, onDismiss = { picking = false }) { set ->
        Nama.store.settings.update { it.copy(focusApps = set) }
    }
}

@Composable
fun SpaceWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    WidgetFrame(ctrl, pageIndex, w, title = tr("فضای فعلی", "Current space"), onClick = { ctrl.spaceSwitcher = true }) {
        Text(env.space?.icon ?: "○", fontSize = 26.sp)
        Text(env.space?.name ?: tr("بدون فضا", "No space"), color = s.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(if (env.settings.manualSpaceId == null) tr("خودکار", "Automatic") else tr("دستی", "Manual"), color = s.subText, fontSize = 11.sp)
    }
}

@Composable
fun NotifDigestWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val recent by NotificationRepo.recent.collectAsState()
    val access = remember { NotificationRepo.hasAccess(ctx) }
    WidgetFrame(ctrl, pageIndex, w, title = tr("🔔 خلاصه اعلان‌ها", "🔔 Notification digest")) {
        if (!access) {
            Text(tr("برای خلاصه اعلان‌ها و نقطه روی آیکون‌ها، دسترسی اعلان‌ها لازم است.", "Notification access enables the digest and icon dots."), color = s.subText, fontSize = 12.sp)
            TextButton({ Actions.start(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text(tr("دادن دسترسی", "Grant")) }
        } else {
            val important = recent.filter { it.important }
            val others = recent.size - important.size
            Text(tr("${num(recent.size)} اعلان · ${num(important.size)} مهم · ${num(others)} بقیه", "${recent.size} notifications · ${important.size} important"), color = s.text, fontSize = 13.sp)
            important.take(4).forEach { n ->
                val app = Nama.apps.byPackage(n.pkg)?.label ?: n.pkg
                Text("• $app: ${n.title} ${n.text}".trim(), color = s.subText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (recent.isNotEmpty()) Text(tr("پاک کردن خلاصه", "Clear"), color = s.accent, fontSize = 11.sp, modifier = Modifier.clickable { NotificationRepo.clearHistory() }.padding(top = 4.dp))
        }
    }
}

@Composable
fun NewsTickerWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val items by Nama.news.items.collectAsState()
    LaunchedEffect(Unit) { Nama.news.refreshIfStale() }
    val list = remember(items, env.settings) { NewsLogic.digest(visibleNews(items, env), 12) }
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(list.size, env.reduceMotion) {
        while (list.isNotEmpty() && !env.reduceMotion) { delay(6000); i = (i + 1) % list.size }
    }
    val n = list.getOrNull(i % list.size.coerceAtLeast(1))
    WidgetFrame(ctrl, pageIndex, w, title = tr("📰 خبر", "📰 News"), trailing = n?.sourceName, onClick = { n?.let { Actions.openUrl(ctx, it.link) } }) {
        AnimatedContent(n, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "ticker") { item ->
            Text(item?.title ?: tr("خبری نیست.", "No news."), color = s.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun NewsDigestWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val items by Nama.news.items.collectAsState()
    LaunchedEffect(Unit) { Nama.news.refreshIfStale() }
    val list = remember(items, env.settings) { NewsLogic.digest(visibleNews(items, env), 5, 2) }
    WidgetFrame(ctrl, pageIndex, w, title = tr("📰 خلاصه خبرها", "📰 News digest")) {
        if (list.isEmpty()) Text(tr("خبری نیست.", "No news."), color = s.subText, fontSize = 12.sp)
        list.forEach { n ->
            Text("• " + n.title, color = s.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { Actions.openUrl(ctx, n.link) }.padding(vertical = 2.dp))
        }
    }
}

@Composable
fun MorningWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val now = rememberNow()
    val today = now.toLocalDate()
    val personal by Nama.store.personal.flow.collectAsState()
    val weather by Nama.weather.weather.collectAsState()
    val quotes by Nama.prices.quotes.collectAsState()
    LaunchedEffect(Unit) { Nama.weather.refreshIfStale(env.settings.cityId); Nama.prices.refreshIfStale() }
    val hour = now.hour
    val greet = when (hour) {
        in 4..11 -> tr("صبح بخیر ☀", "Good morning ☀")
        in 12..15 -> tr("ظهر بخیر", "Good afternoon")
        in 16..19 -> tr("عصر بخیر", "Good evening")
        else -> tr("شب بخیر 🌙", "Good night 🌙")
    }
    WidgetFrame(ctrl, pageIndex, w, title = null) {
        Text(greet, color = s.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(weekdayName(today) + " " + jalaliLong(today), color = s.subText, fontSize = 12.sp)
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            weather?.let { wt ->
                val (icon, _) = ir.nama.launcher.data.WeatherRepo.describe(wt.code, Nama.isFa)
                Fact("$icon " + num(Math.round(wt.tempC)) + "°" + (wt.aqi?.let { tr(" · آلودگی ", " · AQI ") + num(it) } ?: ""))
            }
            val cutsToday = personal.blackouts.filter { it.epochDay == today.toEpochDay() || (it.epochDay == null && Jalali.weekIndex(today) in it.days) }
            cutsToday.firstOrNull()?.let { Fact("⚡ " + minuteText(it.startMinute) + "–" + minuteText(it.endMinute)) }
            env.settings.plateLastDigit?.let { d ->
                val st = Nama.remote.config.value.oddEven.status(today, d, ir.nama.core.IranCalendar.isHoliday(today, env.settings.hijriOffset))
                Fact("🚗 " + when (st) { OddEvenRule.Status.ALLOWED -> tr("مجاز", "allowed"); OddEvenRule.Status.NOT_ALLOWED -> tr("غیرمجاز", "not allowed"); else -> tr("آزاد", "free") })
            }
            quotes.firstOrNull { it.symbol == "USDT" }?.let { Fact("💵 " + ir.nama.launcher.data.formatToman(it.value, Nama.faDigits)) }
            val open = personal.todos.count { !it.done }
            if (open > 0) Fact("✓ " + num(open) + tr(" کار", " tasks"))
            nextPrayer(env, now)?.let { (n, h, m) -> Fact("🕌 $n " + hhmm(h, m)) }
        }
    }
}

@Composable
private fun Fact(text: String) {
    val s = LocalNamaStyle.current
    Text(text, color = s.text, fontSize = 13.sp)
}
