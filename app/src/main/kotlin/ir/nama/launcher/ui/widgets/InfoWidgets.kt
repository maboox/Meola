package ir.nama.launcher.ui.widgets

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ir.nama.core.Cities
import ir.nama.core.Jalali
import ir.nama.core.NewsCategory
import ir.nama.core.OddEvenRule
import ir.nama.core.PersianText
import ir.nama.core.UssdCode
import ir.nama.launcher.Nama
import ir.nama.launcher.data.BlackoutSlot
import ir.nama.launcher.data.DataPackage
import ir.nama.launcher.data.FavContact
import ir.nama.launcher.data.WeatherRepo
import ir.nama.launcher.data.WidgetInstance
import ir.nama.launcher.data.formatToman
import ir.nama.launcher.num
import ir.nama.launcher.system.Actions
import ir.nama.launcher.tr
import ir.nama.launcher.ui.common.ChipsSelector
import ir.nama.launcher.ui.common.JalaliDateDialog
import ir.nama.launcher.ui.common.TextInputDialog
import ir.nama.launcher.ui.common.TimeDialog
import ir.nama.launcher.ui.common.minuteText
import ir.nama.launcher.ui.home.HomeController
import ir.nama.launcher.ui.home.HomeEnv
import ir.nama.launcher.ui.home.ago
import ir.nama.launcher.ui.theme.LocalNamaStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

@Composable
fun PricesWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val quotes by Nama.prices.quotes.collectAsState()
    val loading by Nama.prices.loading.collectAsState()
    val failed by Nama.prices.failed.collectAsState()
    LaunchedEffect(Unit) { Nama.prices.refreshIfStale() }
    var manual by remember { mutableStateOf(false) }
    val updated = quotes.maxOfOrNull { it.at }
    WidgetFrame(
        ctrl, pageIndex, w, title = tr("قیمت‌ها", "Prices"),
        trailing = if (loading) "…" else updated?.let { ago(it) },
        onClick = { Nama.prices.refreshIfStale(0) }
    ) {
        if (quotes.isEmpty()) {
            Text(
                if (failed) tr("دریافت قیمت ممکن نشد. اینترنت را بررسی کنید یا نرخ دستی بگذارید.", "Couldn't load prices. Check the internet or set a manual rate.")
                else tr("در حال دریافت…", "Loading…"),
                color = s.subText, fontSize = 12.sp
            )
        }
        quotes.forEach { q ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(q.title, color = s.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(formatToman(q.value, Nama.faDigits) + " " + q.unit, color = s.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                q.changePercent?.let { ch ->
                    Text(
                        "  " + (if (ch >= 0) "▲" else "▼") + num(PersianText.groupDecimal(kotlin.math.abs(ch), false, 1)) + "٪",
                        color = if (ch >= 0) s.success else s.danger, fontSize = 11.sp
                    )
                }
            }
        }
        val manualUsd = env.settings.manualUsdRate
        val manualGold = env.settings.manualGoldRate
        if (manualUsd > 0) Text(tr("دلار (دستی): ", "USD (manual): ") + num(PersianText.group(manualUsd, false)), color = s.subText, fontSize = 12.sp)
        if (manualGold > 0) Text(tr("طلای ۱۸ عیار (دستی): ", "18k gold (manual): ") + num(PersianText.group(manualGold, false)), color = s.subText, fontSize = 12.sp)
        Text(tr("نرخ دستی…", "Manual rate…"), color = s.accent, fontSize = 11.sp, modifier = Modifier.clickable { manual = true }.padding(top = 4.dp))
    }
    if (manual) {
        var usd by remember { mutableStateOf(if (env.settings.manualUsdRate > 0) env.settings.manualUsdRate.toString() else "") }
        var gold by remember { mutableStateOf(if (env.settings.manualGoldRate > 0) env.settings.manualGoldRate.toString() else "") }
        AlertDialog(
            onDismissRequest = { manual = false },
            title = { Text(tr("نرخ دستی (تومان)", "Manual rates (toman)")) },
            text = {
                Column {
                    OutlinedTextField(usd, { usd = it }, label = { Text(tr("دلار", "USD")) }, singleLine = true)
                    OutlinedTextField(gold, { gold = it }, label = { Text(tr("هر گرم طلای ۱۸", "18k gold per gram")) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton({
                    Nama.store.settings.update {
                        it.copy(
                            manualUsdRate = PersianText.parseNumber(usd)?.toLong() ?: 0L,
                            manualGoldRate = PersianText.parseNumber(gold)?.toLong() ?: 0L
                        )
                    }
                    manual = false
                }) { Text(tr("ذخیره", "Save")) }
            },
            dismissButton = { TextButton({ manual = false }) { Text(tr("انصراف", "Cancel")) } }
        )
    }
}

@Composable
fun WeatherWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val weather by Nama.weather.weather.collectAsState()
    LaunchedEffect(env.settings.cityId) { Nama.weather.refreshIfStale(env.settings.cityId) }
    val city = Cities.byId(env.settings.cityId)
    WidgetFrame(ctrl, pageIndex, w, title = if (Nama.isFa) city.fa else city.en) {
        val wt = weather
        if (wt == null || wt.cityId != env.settings.cityId) {
            Text(tr("در حال دریافت…", "Loading…"), color = s.subText, fontSize = 12.sp)
        } else {
            val (icon, desc) = WeatherRepo.describe(wt.code, Nama.isFa)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 28.sp)
                Text(" " + num(Math.round(wt.tempC)) + "°", color = s.text, fontSize = 30.sp, fontWeight = FontWeight.Light)
            }
            Text(desc + (if (wt.maxC != null && wt.minC != null) " · " + num(Math.round(wt.maxC)) + "°/" + num(Math.round(wt.minC)) + "°" else ""), color = s.subText, fontSize = 12.sp)
            wt.aqi?.let { a ->
                val color = when { a <= 50 -> s.success; a <= 100 -> s.accent2; a <= 150 -> Color(0xFFFF9F43); else -> s.danger }
                Text(tr("آلودگی هوا: ", "Air: ") + num(a) + " · " + WeatherRepo.aqiLabel(a, Nama.isFa), color = color, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun BlackoutWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val personal by Nama.store.personal.flow.collectAsState()
    val now = rememberNow()
    val today = now.toLocalDate()
    val cur = now.hour * 60 + now.minute
    var adding by remember { mutableStateOf(false) }
    fun slotsOn(d: LocalDate) = personal.blackouts.filter { (it.epochDay == d.toEpochDay()) || (it.epochDay == null && Jalali.weekIndex(d) in it.days) }.sortedBy { it.startMinute }
    val todays = slotsOn(today)
    val ongoing = todays.firstOrNull { cur in it.startMinute until it.endMinute }
    val nextToday = todays.firstOrNull { it.startMinute > cur }
    val tomorrow = slotsOn(today.plusDays(1)).firstOrNull()
    WidgetFrame(ctrl, pageIndex, w, title = tr("⚡ خاموشی برق", "⚡ Power cuts")) {
        when {
            ongoing != null -> {
                val total = (ongoing.endMinute - ongoing.startMinute).coerceAtLeast(1)
                val done = (cur - ongoing.startMinute).toFloat() / total
                Text(tr("الان خاموشی است · تا ", "Power is off · until ") + minuteText(ongoing.endMinute), color = s.danger, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(s.cardBorder)) {
                    Box(Modifier.fillMaxWidth(done.coerceIn(0f, 1f)).height(6.dp).background(s.accent2))
                }
            }
            nextToday != null -> {
                val left = nextToday.startMinute - cur
                Text(tr("امروز ", "Today ") + minuteText(nextToday.startMinute) + tr(" تا ", " to ") + minuteText(nextToday.endMinute), color = s.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(num(left / 60) + tr(" ساعت و ", " h ") + num(left % 60) + tr(" دقیقه دیگر · گوشی را شارژ کن", " min left · charge your phone"), color = if (left < 60) s.danger else s.subText, fontSize = 12.sp)
            }
            else -> Text(tr("امروز خاموشی ثبت نشده ✓", "No power cut today ✓"), color = s.success, fontSize = 13.sp)
        }
        tomorrow?.let { Text(tr("فردا ", "Tomorrow ") + minuteText(it.startMinute) + "–" + minuteText(it.endMinute), color = s.subText, fontSize = 12.sp) }
        Row {
            Text(tr("＋ ثبت جدول", "＋ Add schedule"), color = s.accent, fontSize = 12.sp, modifier = Modifier.clickable { adding = true }.padding(top = 6.dp))
            if (personal.blackouts.isNotEmpty()) Text(
                tr("   پاک کردن همه", "   Clear all"), color = s.subText, fontSize = 12.sp,
                modifier = Modifier.clickable { Nama.store.personal.update { it.copy(blackouts = emptyList()) } }.padding(top = 6.dp)
            )
        }
    }
    if (adding) BlackoutDialog { adding = false }
}

@Composable
private fun BlackoutDialog(onDismiss: () -> Unit) {
    var days by remember { mutableStateOf(setOf<Int>()) }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var start by remember { mutableStateOf(14 * 60) }
    var end by remember { mutableStateOf(16 * 60) }
    var pick by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("زمان خاموشی", "Power cut time")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("جدول خاموشی منطقه را از اپ یا سایت شرکت برق ببینید و اینجا ثبت کنید.", "Copy the schedule from your power company's app or site."), fontSize = 12.sp)
                Text(tr("روزهای هفته (تکرار هفتگی):", "Weekdays (weekly):"), fontSize = 13.sp)
                ChipsSelector((0..6).toList(), days, { Jalali.WEEKDAYS[it] }) { d -> days = if (d in days) days - d else days + d; date = null }
                TextButton({ pick = 3 }) { Text(date?.let { tr("یا فقط تاریخ: ", "Or only on: ") + jalaliLong(it) } ?: tr("یا فقط یک تاریخ مشخص…", "Or a single date…")) }
                Row {
                    TextButton({ pick = 1 }) { Text(tr("از ", "From ") + minuteText(start)) }
                    TextButton({ pick = 2 }) { Text(tr("تا ", "To ") + minuteText(end)) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = days.isNotEmpty() || date != null, onClick = {
                val slot = BlackoutSlot(UUID.randomUUID().toString(), start, end, if (date == null) days else emptySet(), date?.toEpochDay())
                Nama.store.personal.update { it.copy(blackouts = it.blackouts + slot) }
                onDismiss()
            }) { Text(tr("ثبت", "Save")) }
        },
        dismissButton = { TextButton(onDismiss) { Text(tr("انصراف", "Cancel")) } }
    )
    when (pick) {
        1 -> TimeDialog(start, tr("شروع", "Start"), { pick = 0 }) { start = it }
        2 -> TimeDialog(end, tr("پایان", "End"), { pick = 0 }) { end = it }
        3 -> JalaliDateDialog(LocalDate.now(), tr("تاریخ", "Date"), { pick = 0 }) { date = it; days = emptySet() }
    }
}

@Composable
fun OddEvenWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val rule = Nama.remote.config.value.oddEven
    var setting by remember { mutableStateOf(false) }
    val today = rememberNow().toLocalDate()
    WidgetFrame(ctrl, pageIndex, w, title = tr("🚗 طرح زوج و فرد", "🚗 Odd/even")) {
        val digit = env.settings.plateLastDigit
        if (digit == null) {
            Text(tr("رقم آخر پلاک را وارد کنید", "Enter your plate's last digit"), color = s.subText, fontSize = 12.sp)
            TextButton({ setting = true }) { Text(tr("تنظیم پلاک", "Set plate")) }
        } else {
            fun label(d: LocalDate): Pair<String, Color> {
                val st = rule.status(d, digit, ir.nama.core.IranCalendar.isHoliday(d, env.settings.hijriOffset))
                return when (st) {
                    OddEvenRule.Status.ALLOWED -> tr("مجاز ✓", "Allowed ✓") to s.success
                    OddEvenRule.Status.NOT_ALLOWED -> tr("غیرمجاز ✗", "Not allowed ✗") to s.danger
                    OddEvenRule.Status.FREE -> tr("آزاد", "Free") to s.accent
                }
            }
            val (t1, c1) = label(today)
            val (t2, _) = label(today.plusDays(1))
            Text(t1, color = c1, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(tr("امروز · فردا: ", "Today · Tomorrow: ") + t2, color = s.subText, fontSize = 12.sp)
            Text(tr("پلاک با رقم ", "Plate ending ") + num(digit), color = s.subText, fontSize = 11.sp, modifier = Modifier.clickable { setting = true })
        }
    }
    if (setting) TextInputDialog(tr("رقم آخر پلاک (۰ تا ۹)", "Last digit (0-9)"), numeric = true, onDismiss = { setting = false }) { v ->
        PersianText.toEnDigits(v).trim().toIntOrNull()?.takeIf { it in 0..9 }?.let { d -> Nama.store.settings.update { it.copy(plateLastDigit = d) } }
    }
}

@Composable
fun FootballWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val items by Nama.news.items.collectAsState()
    var setting by remember { mutableStateOf(false) }
    val team = env.settings.footballTeam
    LaunchedEffect(Unit) { Nama.news.refreshIfStale() }
    val sportsOn = Nama.news.enabledSources().any { it.category == NewsCategory.SPORTS }
    val news = remember(items, team) {
        if (team.isBlank()) emptyList() else {
            val t = PersianText.normalize(team)
            items.filter { PersianText.normalize(it.title + " " + it.summary).contains(t) }.take(3)
        }
    }
    WidgetFrame(ctrl, pageIndex, w, title = if (team.isBlank()) tr("⚽ تیم محبوب", "⚽ My team") else "⚽ $team") {
        when {
            team.isBlank() -> TextButton({ setting = true }) { Text(tr("انتخاب تیم (مثلاً پرسپولیس، استقلال، تیم ملی)", "Pick a team")) }
            !sportsOn -> {
                Text(tr("برای خبرهای تیم، یک منبع ورزشی لازم است.", "A sports source is needed."), color = s.subText, fontSize = 12.sp)
                TextButton({
                    val ids = Nama.news.enabledSources().map { it.id }.toSet() + Nama.news.allSources().filter { it.category == NewsCategory.SPORTS }.map { it.id }
                    Nama.store.settings.update { it.copy(newsSourceIds = ids) }
                    Nama.news.refreshIfStale(0)
                }) { Text(tr("فعال کردن منبع ورزشی", "Enable sports news")) }
            }
            news.isEmpty() -> Text(tr("خبر تازه‌ای از $team نیست.", "No news about $team yet."), color = s.subText, fontSize = 12.sp)
            else -> news.forEach { n ->
                Text("• " + n.title, color = s.text, fontSize = 13.sp, maxLines = 2, modifier = Modifier.clickable { Actions.openUrl(ctx, n.link) }.padding(vertical = 2.dp))
            }
        }
        if (team.isNotBlank()) Text(tr("تغییر تیم", "Change team"), color = s.subText, fontSize = 11.sp, modifier = Modifier.clickable { setting = true })
    }
    if (setting) TextInputDialog(tr("نام تیم", "Team name"), team, onDismiss = { setting = false }) { v -> Nama.store.settings.update { it.copy(footballTeam = v) } }
}

@Composable
fun UssdWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val personal by Nama.store.personal.flow.collectAsState()
    var adding by remember { mutableStateOf(false) }
    val codes = Nama.remote.config.value.ussd + personal.customUssd
    WidgetFrame(ctrl, pageIndex, w, title = tr("📶 کدهای سیم‌کارت", "📶 SIM codes")) {
        codes.groupBy { it.operator }.forEach { (op, list) ->
            Text(op, color = s.subText, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                list.forEach { u ->
                    Text(
                        u.title, color = s.text, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(s.accent.copy(alpha = 0.14f))
                            .combinedClickable(
                                onClick = { Actions.dialUssd(ctx, u.code) },
                                onLongClick = {
                                    if (u in personal.customUssd) Nama.store.personal.update { p -> p.copy(customUssd = p.customUssd - u) }
                                }
                            ).padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
        Text(tr("＋ کد دلخواه", "＋ Custom code"), color = s.accent, fontSize = 12.sp, modifier = Modifier.clickable { adding = true }.padding(top = 6.dp))
    }
    if (adding) {
        var title by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }
        var op by remember { mutableStateOf(tr("دلخواه", "Custom")) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(tr("کد دلخواه", "Custom code")) },
            text = {
                Column {
                    OutlinedTextField(title, { title = it }, label = { Text(tr("عنوان", "Title")) }, singleLine = true)
                    OutlinedTextField(code, { code = it }, label = { Text(tr("کد (مثلاً *۱۴۰#)", "Code (e.g. *140#)")) }, singleLine = true)
                    OutlinedTextField(op, { op = it }, label = { Text(tr("اپراتور", "Operator")) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton({
                    if (title.isNotBlank() && code.isNotBlank()) Nama.store.personal.update { it.copy(customUssd = it.customUssd + UssdCode(op, title, PersianText.toEnDigits(code))) }
                    adding = false
                }) { Text(tr("ذخیره", "Save")) }
            },
            dismissButton = { TextButton({ adding = false }) { Text(tr("انصراف", "Cancel")) } }
        )
    }
}

fun formatBytes(b: Long): String {
    val mb = b / (1024.0 * 1024.0)
    return if (mb >= 1024) num(PersianText.groupDecimal(mb / 1024.0, false, 2)) + tr(" گیگ", " GB")
    else num(PersianText.groupDecimal(mb, false, 0)) + tr(" مگ", " MB")
}

@Composable
fun DataUsageWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val personal by Nama.store.personal.flow.collectAsState()
    var adding by remember { mutableStateOf(false) }
    val usage = Nama.usage
    val pkg = personal.dataPackage
    val data by produceState<Triple<Long?, Long?, Long?>>(Triple(null, null, null), pkg) {
        value = withContext(Dispatchers.IO) {
            val today = usage.mobileBytes(usage.startOfDayMillis())
            val month = usage.mobileBytes(usage.startOfMonthMillis())
            val pkgUsed = pkg?.let {
                usage.mobileBytes(LocalDate.ofEpochDay(it.startEpochDay).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
            }
            Triple(today, month, pkgUsed)
        }
    }
    WidgetFrame(ctrl, pageIndex, w, title = tr("📱 اینترنت همراه", "📱 Mobile data")) {
        if (!usage.hasPermission()) {
            Text(tr("از روشن شدن گوشی: ", "Since boot: ") + formatBytes(usage.mobileBytesSinceBoot()), color = s.text, fontSize = 13.sp)
            Text(tr("برای آمار روزانه و ماهانه، دسترسی آمار استفاده لازم است.", "Usage access gives daily/monthly numbers."), color = s.subText, fontSize = 11.sp)
            TextButton({ Actions.start(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text(tr("دادن دسترسی", "Grant")) }
        } else {
            data.first?.let { Text(tr("امروز: ", "Today: ") + formatBytes(it), color = s.text, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            data.second?.let { Text(tr("این ماه: ", "This month: ") + formatBytes(it), color = s.subText, fontSize = 12.sp) }
        }
        if (pkg != null) {
            val left = pkg.endEpochDay - LocalDate.now().toEpochDay()
            Text(
                (pkg.title.ifBlank { tr("بسته", "Package") }) + ": " + (if (left >= 0) num(left) + tr(" روز مانده", " days left") else tr("تمام شده", "expired")) +
                    (data.third?.let { used -> if (pkg.sizeMb > 0) " · " + formatBytes(used) + " / " + formatBytes(pkg.sizeMb * 1024 * 1024) else "" } ?: ""),
                color = if (left <= 2) s.danger else s.subText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }
        Text(if (pkg == null) tr("＋ ثبت بسته اینترنت", "＋ Add data package") else tr("تغییر بسته", "Change package"), color = s.accent, fontSize = 12.sp, modifier = Modifier.clickable { adding = true }.padding(top = 4.dp))
    }
    if (adding) {
        var title by remember { mutableStateOf(pkg?.title ?: "") }
        var size by remember { mutableStateOf(pkg?.sizeMb?.let { (it / 1024).toString() } ?: "") }
        var days by remember { mutableStateOf("30") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(tr("بسته اینترنت", "Data package")) },
            text = {
                Column {
                    OutlinedTextField(title, { title = it }, label = { Text(tr("نام (اختیاری)", "Name (optional)")) }, singleLine = true)
                    OutlinedTextField(size, { size = it }, label = { Text(tr("حجم (گیگ)", "Size (GB)")) }, singleLine = true)
                    OutlinedTextField(days, { days = it }, label = { Text(tr("مدت (روز، از امروز)", "Duration (days from today)")) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton({
                    val gb = PersianText.parseNumber(size) ?: 0.0
                    val d = PersianText.parseNumber(days)?.toLong() ?: 30
                    val start = LocalDate.now().toEpochDay()
                    Nama.store.personal.update { it.copy(dataPackage = DataPackage(title, (gb * 1024).toLong(), start, start + d)) }
                    adding = false
                }) { Text(tr("ذخیره", "Save")) }
            },
            dismissButton = {
                Row {
                    if (pkg != null) TextButton({ Nama.store.personal.update { it.copy(dataPackage = null) }; adding = false }) { Text(tr("حذف", "Remove")) }
                    TextButton({ adding = false }) { Text(tr("انصراف", "Cancel")) }
                }
            }
        )
    }
}

@Composable
fun BatteryWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val pct by Nama.context.batteryPercent.collectAsState()
    val charging by Nama.context.charging.collectAsState()
    WidgetFrame(ctrl, pageIndex, w, title = tr("🔋 باتری", "🔋 Battery"), onClick = { Actions.start(ctx, Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }) {
        Text(num(pct) + "٪", color = if (pct <= 15 && !charging) s.danger else s.text, fontSize = 30.sp, fontWeight = FontWeight.Light)
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(s.cardBorder)) {
            Box(Modifier.fillMaxWidth(pct / 100f).height(6.dp).background(if (pct <= 15) s.danger else s.success))
        }
        Text(if (charging) tr("در حال شارژ ⚡", "Charging ⚡") else tr("باتری", "On battery"), color = s.subText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun TogglesWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val items = listOf(
        "🔦" to (tr("چراغ‌قوه", "Torch") to { Actions.toggleTorch(ctx); Unit }),
        "📶" to (tr("وای‌فای", "Wi-Fi") to { Actions.start(ctx, Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS)); Unit }),
        "🌐" to (tr("اینترنت", "Internet") to { Actions.start(ctx, Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS)); Unit }),
        "🔊" to (tr("صدا", "Volume") to { Actions.start(ctx, Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_VOLUME else Settings.ACTION_SOUND_SETTINGS)); Unit }),
        "ᛒ" to (tr("بلوتوث", "Bluetooth") to { Actions.start(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); Unit }),
        "📷" to (tr("دوربین", "Camera") to { Actions.start(ctx, Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); Unit })
    )
    WidgetFrame(ctrl, pageIndex, w, title = tr("میانبرها", "Shortcuts")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            items.forEach { (icon, pair) ->
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable { pair.second() }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(icon, fontSize = 22.sp, color = s.text)
                    Text(pair.first, color = s.subText, fontSize = 10.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun MusicWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    WidgetFrame(ctrl, pageIndex, w, title = tr("🎵 موسیقی", "🎵 Music")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            listOf(
                "⏮" to KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                "⏯" to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                "⏭" to KeyEvent.KEYCODE_MEDIA_NEXT
            ).forEach { (icon, key) ->
                Text(icon, color = s.text, fontSize = 30.sp, modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { Actions.mediaKey(ctx, key) }.padding(10.dp))
            }
        }
        Text(tr("آخرین پخش‌کننده را کنترل می‌کند", "Controls the last media app"), color = s.subText, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
fun ContactsWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val personal by Nama.store.personal.flow.collectAsState()
    var picking by remember { mutableStateOf(false) }
    val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    WidgetFrame(ctrl, pageIndex, w, title = tr("مخاطب‌های محبوب", "Favorite contacts")) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            personal.favoriteContacts.forEach { c ->
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(s.accent.copy(alpha = 0.12f))
                        .combinedClickable(
                            onClick = { Actions.dial(ctx, c.phone) },
                            onLongClick = { Actions.sms(ctx, c.phone) }
                        ).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(c.name.take(1), color = s.accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(c.name, color = s.text, fontSize = 12.sp, maxLines = 1)
                }
            }
            Text(
                "＋", color = s.accent, fontSize = 22.sp,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable {
                    if (granted) picking = true else ctrl.requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS))
                }.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        if (personal.favoriteContacts.isNotEmpty()) Text(tr("لمس: تماس · لمس طولانی: پیامک", "Tap: call · Long press: SMS"), color = s.subText, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
    if (picking) ContactPickDialog(onDismiss = { picking = false }) { c ->
        Nama.store.personal.update { it.copy(favoriteContacts = (it.favoriteContacts + c).distinctBy { x -> x.phone }.take(8)) }
    }
}

@Composable
fun ContactPickDialog(onDismiss: () -> Unit, onPick: (FavContact) -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val results by produceState(emptyList<FavContact>(), q) {
        value = withContext(Dispatchers.IO) { queryContacts(ctx, q) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("انتخاب مخاطب", "Pick a contact")) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text(tr("نام", "Name")) }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(results) { c ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(c); onDismiss() }.padding(vertical = 8.dp)) {
                            Text(c.name)
                            Text(num(c.phone), fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text(tr("بستن", "Close")) } }
    )
}

private fun queryContacts(ctx: android.content.Context, q: String): List<FavContact> = try {
    val uri = if (q.isBlank()) ContactsContract.CommonDataKinds.Phone.CONTENT_URI
    else Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(q))
    val out = mutableListOf<FavContact>()
    ctx.contentResolver.query(
        uri,
        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY),
        null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
    )?.use { c ->
        while (c.moveToNext() && out.size < 60) {
            val n = c.getString(0) ?: continue
            val p = c.getString(1) ?: continue
            if (out.none { it.phone == p }) out += FavContact(n, p, c.getString(2) ?: "")
        }
    }
    out
} catch (e: Exception) {
    emptyList()
}
