package ir.nama.launcher.ui.widgets

import android.Manifest
import android.content.Intent
import android.os.Build
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Wifi
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ir.nama.core.Cities
import ir.nama.core.IranCalendar
import ir.nama.core.Jalali
import ir.nama.core.NewsCategory
import ir.nama.core.OddEvenRule
import ir.nama.core.PersianText
import ir.nama.launcher.Nama
import ir.nama.launcher.data.WeatherRepo
import ir.nama.launcher.data.formatToman
import ir.nama.launcher.num
import ir.nama.launcher.system.Actions
import ir.nama.launcher.tr
import ir.nama.launcher.ui.common.TextInputDialog
import ir.nama.launcher.ui.common.minuteText
import ir.nama.launcher.ui.home.ago
import ir.nama.launcher.ui.theme.LocalNamaStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

// ------------------------------------------------------------------------------------------------
// Weather
// ------------------------------------------------------------------------------------------------

@Composable
fun WeatherWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val weather by Nama.weather.weather.collectAsState()
    LaunchedEffect(sc.env.settings.cityId) { Nama.weather.refreshIfStale(sc.env.settings.cityId) }
    val city = Cities.byId(sc.env.settings.cityId)
    val cityName = if (Nama.isFa) city.fa else city.en
    val wt = weather?.takeIf { it.cityId == sc.env.settings.cityId }
    WidgetCard(onClick = { Nama.weather.refreshIfStale(sc.env.settings.cityId) }) {
        if (wt == null) { WEmpty(Icons.Outlined.Language, tr("در حال دریافت هوا…", "Loading weather…")); return@WidgetCard }
        val (_, desc) = WeatherRepo.describe(wt.code, Nama.isFa)
        val temp = num(Math.round(wt.tempC)) + "°"
        val aqiColor = wt.aqi?.let { aqiColor(it) }
        when {
            sc.size.h == 1 -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Icon(weatherIcon(wt.code), null, tint = s.accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text(temp, color = s.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    WText(cityName, 13.sp, FontWeight.Medium)
                    WText(desc + (wt.aqi?.let { tr(" · آلودگی ", " · AQI ") + num(it) } ?: ""), 11.sp, secondary = true)
                }
            }
            else -> Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WText(cityName, 14.sp, FontWeight.Medium, modifier = Modifier.weight(1f))
                    Icon(weatherIcon(wt.code), null, tint = s.accent, modifier = Modifier.size(24.dp))
                }
                WSpacer()
                Text(temp, color = s.onSurface, fontSize = if (sc.wide) 44.sp else 40.sp, fontWeight = FontWeight.Light, lineHeight = 46.sp)
                WText(desc + (if (wt.maxC != null && wt.minC != null) " · " + num(Math.round(wt.maxC)) + "°/" + num(Math.round(wt.minC)) + "°" else ""), 12.sp, secondary = true)
                wt.aqi?.let { a ->
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(aqiColor ?: s.accent))
                        Spacer(Modifier.width(6.dp))
                        WText(tr("آلودگی ", "AQI ") + num(a) + " · " + WeatherRepo.aqiLabel(a, Nama.isFa), 11.sp, secondary = true)
                    }
                }
            }
        }
    }
}

fun aqiColor(a: Int): Color = when {
    a <= 50 -> Color(0xFF34A853)
    a <= 100 -> Color(0xFFF9AB00)
    a <= 150 -> Color(0xFFFA7B17)
    a <= 200 -> Color(0xFFEA4335)
    else -> Color(0xFF9334E6)
}

// ------------------------------------------------------------------------------------------------
// Power cuts
// ------------------------------------------------------------------------------------------------

@Composable
fun BlackoutWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val personal by Nama.store.personal.flow.collectAsState()
    val now = rememberNow()
    val today = now.toLocalDate()
    val cur = now.hour * 60 + now.minute
    var adding by remember { mutableStateOf(false) }
    fun slotsOn(d: LocalDate) = personal.blackouts.filter { it.epochDay == d.toEpochDay() || (it.epochDay == null && Jalali.weekIndex(d) in it.days) }.sortedBy { it.startMinute }
    val todays = slotsOn(today)
    val ongoing = todays.firstOrNull { cur in it.startMinute until it.endMinute }
    val next = todays.firstOrNull { it.startMinute > cur }
    val tomorrow = slotsOn(today.plusDays(1)).firstOrNull()
    WidgetCard(onClick = { adding = true }) {
        if (personal.blackouts.isEmpty()) {
            WEmpty(Icons.Outlined.Bolt, tr("جدول خاموشی منطقه‌ات را وارد کن", "Add your area's power cut schedule"), tr("افزودن", "Add")) { adding = true }
            return@WidgetCard
        }
        val (title, sub, color) = when {
            ongoing != null -> Triple(tr("برق قطع است", "Power is off"), tr("تا ", "Until ") + minuteText(ongoing.endMinute), s.danger)
            next != null -> {
                val left = next.startMinute - cur
                Triple(minuteText(next.startMinute) + " – " + minuteText(next.endMinute), tr("امروز · ", "Today · ") + num(left / 60) + tr(" ساعت و ", "h ") + num(left % 60) + tr(" دقیقه دیگر", "m left"), if (left < 60) s.warning else s.onSurface)
            }
            else -> Triple(tr("امروز خاموشی نیست", "No cut today"), tomorrow?.let { tr("فردا ", "Tomorrow ") + minuteText(it.startMinute) + "–" + minuteText(it.endMinute) } ?: "", s.success)
        }
        if (sc.size.h == 1) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Bolt, null, tint = color, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Column { WText(title, 15.sp, FontWeight.Medium, color = color); WText(sub, 12.sp, secondary = true) }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                WHeader(Icons.Outlined.Bolt, tr("خاموشی برق", "Power cuts"))
                WSpacer()
                WText(title, 18.sp, FontWeight.Medium, color = color, maxLines = 2)
                WText(sub, 12.sp, secondary = true, maxLines = 2)
                if (ongoing != null) {
                    val total = (ongoing.endMinute - ongoing.startMinute).coerceAtLeast(1)
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(s.outline)) {
                        Box(Modifier.fillMaxWidth(((cur - ongoing.startMinute).toFloat() / total).coerceIn(0f, 1f)).fillMaxHeight().background(s.warning))
                    }
                }
            }
        }
    }
    if (adding) BlackoutDialog { adding = false }
}

// ------------------------------------------------------------------------------------------------
// Odd/even plates
// ------------------------------------------------------------------------------------------------

@Composable
fun OddEvenWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val rule = Nama.remote.config.value.oddEven
    val today = rememberNow().toLocalDate()
    var setting by remember { mutableStateOf(false) }
    val digit = sc.env.settings.plateLastDigit
    WidgetCard(onClick = { setting = true }) {
        if (digit == null) {
            WEmpty(Icons.Outlined.DirectionsCar, tr("رقم آخر پلاک را بزن", "Set your plate digit"))
        } else {
            val st = rule.status(today, digit, IranCalendar.isHoliday(today, sc.env.settings.hijriOffset))
            val (label, color) = when (st) {
                OddEvenRule.Status.ALLOWED -> tr("امروز مجازی", "Allowed today") to s.success
                OddEvenRule.Status.NOT_ALLOWED -> tr("امروز مجاز نیستی", "Not allowed today") to s.danger
                OddEvenRule.Status.FREE -> tr("امروز آزاد است", "Free today") to s.accent
            }
            if (sc.size.h == 1) Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.DirectionsCar, null, tint = color, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                WText(label, 14.sp, FontWeight.Medium, color = color, maxLines = 2)
            } else Column(Modifier.fillMaxSize()) {
                WHeader(Icons.Outlined.DirectionsCar, tr("زوج و فرد", "Odd/even"))
                WSpacer()
                WText(label, 18.sp, FontWeight.Medium, color = color, maxLines = 2)
                WText(tr("پلاک ", "Plate ") + num(digit), 12.sp, secondary = true)
            }
        }
    }
    if (setting) TextInputDialog(tr("رقم آخر پلاک (۰ تا ۹)", "Last digit (0-9)"), numeric = true, onDismiss = { setting = false }) { v ->
        PersianText.toEnDigits(v).trim().toIntOrNull()?.takeIf { it in 0..9 }?.let { d -> Nama.store.settings.update { it.copy(plateLastDigit = d) } }
    }
}

// ------------------------------------------------------------------------------------------------
// Football
// ------------------------------------------------------------------------------------------------

@Composable
fun FootballWidget(sc: WScope) {
    val ctx = LocalContext.current
    val items by Nama.news.items.collectAsState()
    var setting by remember { mutableStateOf(false) }
    val team = sc.env.settings.footballTeam
    LaunchedEffect(Unit) { Nama.news.refreshIfStale() }
    val sportsOn = Nama.news.enabledSources().any { it.category == NewsCategory.SPORTS }
    val news = remember(items, team) {
        if (team.isBlank()) emptyList() else {
            val t = PersianText.normalize(team)
            items.filter { PersianText.normalize(it.title + " " + it.summary).contains(t) }.take(3)
        }
    }
    WidgetCard {
        when {
            team.isBlank() -> WEmpty(Icons.Outlined.SportsSoccer, tr("تیم محبوبت را انتخاب کن", "Pick your team"), tr("انتخاب تیم", "Pick")) { setting = true }
            !sportsOn -> WEmpty(Icons.Outlined.SportsSoccer, tr("یک منبع خبر ورزشی لازم است", "A sports source is needed"), tr("فعال کن", "Enable")) {
                val ids = Nama.news.enabledSources().map { it.id }.toSet() + Nama.news.allSources().filter { it.category == NewsCategory.SPORTS }.map { it.id }
                Nama.store.settings.update { it.copy(newsSourceIds = ids) }
                Nama.news.refreshIfStale(0)
            }
            else -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.clickable { setting = true }) { WHeader(Icons.Outlined.SportsSoccer, team) }
                if (news.isEmpty()) WText(tr("خبر تازه‌ای نیست", "No news yet"), 13.sp, secondary = true)
                news.take(if (sc.size.h >= 3) 3 else 2).forEach { n ->
                    WText(n.title, 13.sp, maxLines = 2, modifier = Modifier.clickable { Actions.openUrl(ctx, n.link) }.padding(vertical = 3.dp))
                }
            }
        }
    }
    if (setting) TextInputDialog(tr("نام تیم (مثلاً پرسپولیس)", "Team name"), team, onDismiss = { setting = false }) { v -> Nama.store.settings.update { it.copy(footballTeam = v) } }
}

// ------------------------------------------------------------------------------------------------
// Prices
// ------------------------------------------------------------------------------------------------

@Composable
fun PricesWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val quotes by Nama.prices.quotes.collectAsState()
    val failed by Nama.prices.failed.collectAsState()
    LaunchedEffect(Unit) { Nama.prices.refreshIfStale() }
    var manual by remember { mutableStateOf(false) }
    val list = quotes.filter { it.symbol != "BTC_IRT" }
    val usdManual = sc.env.settings.manualUsdRate
    WidgetCard(onClick = { manual = true }) {
        if (list.isEmpty() && usdManual <= 0) {
            WEmpty(Icons.Outlined.TrendingUp, if (failed) tr("قیمت در دسترس نیست. نرخ دستی بگذار.", "Prices unavailable. Set a manual rate.") else tr("در حال دریافت…", "Loading…"))
            return@WidgetCard
        }
        val main = list.firstOrNull()
        when {
            !sc.wide && main != null -> Column(Modifier.fillMaxSize()) {
                WHeader(Icons.Outlined.TrendingUp, main.title)
                WSpacer()
                WText(formatToman(main.value, Nama.faDigits), 24.sp, FontWeight.Medium)
                WText(main.unit, 12.sp, secondary = true)
                main.changePercent?.let { Change(it) }
            }
            sc.size.h == 1 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                list.take(3).forEach { q ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        WText(q.title, 11.sp, secondary = true)
                        WText(formatToman(q.value, Nama.faDigits), 14.sp, FontWeight.Medium)
                    }
                }
            }
            else -> Column(Modifier.fillMaxSize()) {
                WHeader(Icons.Outlined.TrendingUp, tr("قیمت‌ها", "Prices"), list.maxOfOrNull { it.at }?.let { ago(it) })
                list.take(3).forEach { q ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        WText(q.title, 14.sp, modifier = Modifier.weight(1f))
                        WText(formatToman(q.value, Nama.faDigits) + " " + q.unit, 14.sp, FontWeight.Medium)
                        q.changePercent?.let { Spacer(Modifier.width(8.dp)); Change(it) }
                    }
                }
                if (list.isEmpty() && usdManual > 0) WText(tr("دلار (دستی): ", "USD (manual): ") + num(PersianText.group(usdManual, false)), 14.sp)
            }
        }
    }
    if (manual) ManualRateDialog { manual = false }
}

@Composable
private fun Change(p: Double) {
    val s = LocalNamaStyle.current
    val up = p >= 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (up) Icons.Outlined.TrendingUp else Icons.Outlined.TrendingDown, null, tint = if (up) s.success else s.danger, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(num(PersianText.groupDecimal(kotlin.math.abs(p), false, 1)) + "٪", color = if (up) s.success else s.danger, fontSize = 12.sp)
    }
}

// ------------------------------------------------------------------------------------------------
// Phone: battery, data, SIM codes, toggles, music, contacts
// ------------------------------------------------------------------------------------------------

@Composable
fun BatteryWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val pct by Nama.context.batteryPercent.collectAsState()
    val charging by Nama.context.charging.collectAsState()
    val color = when { charging -> s.success; pct <= 15 -> s.danger; else -> s.accent }
    WidgetCard(onClick = { Actions.start(ctx, Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.BatteryChargingFull, null, tint = color, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                WText(num(pct) + "٪", if (sc.tall) 34.sp else 22.sp, FontWeight.Light)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(s.outline)) {
                Box(Modifier.fillMaxWidth(pct / 100f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
            }
            if (sc.tall) WText(if (charging) tr("در حال شارژ", "Charging") else tr("باتری", "Battery"), 12.sp, secondary = true, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun DataUsageWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val personal by Nama.store.personal.flow.collectAsState()
    var adding by remember { mutableStateOf(false) }
    val usage = Nama.usage
    val pkg = personal.dataPackage
    val data by produceState<Pair<Long?, Long?>>(null to null, pkg) {
        value = withContext(Dispatchers.IO) {
            val today = usage.mobileBytes(usage.startOfDayMillis())
            val pkgUsed = pkg?.let { usage.mobileBytes(LocalDate.ofEpochDay(it.startEpochDay).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
            today to pkgUsed
        }
    }
    WidgetCard(onClick = { adding = true }) {
        if (!usage.hasPermission()) {
            WEmpty(Icons.Outlined.DataUsage, tr("برای آمار مصرف، دسترسی «آمار استفاده» لازم است", "Needs usage access"), tr("دسترسی", "Grant")) {
                Actions.start(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            return@WidgetCard
        }
        val left = pkg?.let { it.endEpochDay - LocalDate.now().toEpochDay() }
        val fraction = if (pkg != null && pkg.sizeMb > 0 && data.second != null) (data.second!! / (pkg.sizeMb * 1024f * 1024f)).coerceIn(0f, 1f) else null
        Column(Modifier.fillMaxSize()) {
            WHeader(Icons.Outlined.DataUsage, tr("اینترنت همراه", "Mobile data"))
            WSpacer()
            WText(data.first?.let { formatBytes(it) } ?: "—", 22.sp, FontWeight.Medium)
            WText(tr("امروز", "Today"), 11.sp, secondary = true)
            if (pkg != null && left != null) {
                if (fraction != null) Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(s.outline)) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(if (fraction > 0.85f) s.danger else s.accent))
                }
                WText((if (left >= 0) num(left) + tr(" روز از بسته مانده", " days left") else tr("بسته تمام شده", "Package ended")), 11.sp, secondary = true, color = if (left in 0..2) s.danger else null)
            }
        }
    }
    if (adding) DataPackageDialog(pkg) { adding = false }
}

@Composable
fun UssdWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val personal by Nama.store.personal.flow.collectAsState()
    var adding by remember { mutableStateOf(false) }
    val codes = Nama.remote.config.value.ussd + personal.customUssd
    WidgetCard {
        Column(Modifier.fillMaxSize()) {
            WHeader(Icons.Outlined.SimCard, tr("کدهای سیم‌کارت", "SIM codes"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                codes.take(if (sc.size.h == 1) 3 else 8).forEach { u ->
                    Text(
                        u.operator + " · " + u.title, color = s.onAccentContainer, fontSize = 12.sp, maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(s.accentContainer)
                            .combinedClickable(
                                onClick = { Actions.dialUssd(ctx, u.code) },
                                onLongClick = { if (u in personal.customUssd) Nama.store.personal.update { p -> p.copy(customUssd = p.customUssd - u) } }
                            ).padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(s.outline).clickable { adding = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.Add, null, tint = s.onSurface, modifier = Modifier.size(18.dp)) }
            }
        }
    }
    if (adding) CustomUssdDialog { adding = false }
}

@Composable
fun TogglesWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val items: List<Triple<ImageVector, String, () -> Unit>> = listOf(
        Triple(Icons.Outlined.FlashlightOn, tr("چراغ‌قوه", "Torch")) { Actions.toggleTorch(ctx); Unit },
        Triple(Icons.Outlined.Wifi, tr("وای‌فای", "Wi-Fi")) { Actions.start(ctx, Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS)); Unit },
        Triple(Icons.Outlined.Language, tr("اینترنت", "Internet")) { Actions.start(ctx, Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS)); Unit },
        Triple(Icons.Outlined.Bluetooth, tr("بلوتوث", "Bluetooth")) { Actions.start(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); Unit },
        Triple(Icons.Outlined.VolumeUp, tr("صدا", "Volume")) { Actions.start(ctx, Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_VOLUME else Settings.ACTION_SOUND_SETTINGS)); Unit },
        Triple(Icons.Outlined.CameraAlt, tr("دوربین", "Camera")) { Actions.start(ctx, Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); Unit }
    )
    WidgetCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            items.forEach { (icon, label, action) ->
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(s.accentContainer).clickable(onClick = action),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, label, tint = s.onAccentContainer, modifier = Modifier.size(22.dp)) }
            }
        }
    }
}

@Composable
fun MusicWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    WidgetCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                WText(tr("موسیقی", "Music"), 14.sp, FontWeight.Medium)
                WText(tr("آخرین پخش‌کننده", "Last media app"), 11.sp, secondary = true)
            }
            // Media controls read left to right in every language.
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(
                Icons.Outlined.SkipPrevious to KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                Icons.Outlined.PlayArrow to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                Icons.Outlined.SkipNext to KeyEvent.KEYCODE_MEDIA_NEXT
            ).forEachIndexed { i, (icon, key) ->
                val main = i == 1
                Box(
                    Modifier.padding(horizontal = 4.dp).size(if (main) 46.dp else 38.dp).clip(CircleShape)
                        .background(if (main) s.accent else s.accentContainer).clickable { Actions.mediaKey(ctx, key) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = if (main) (if (s.minimal) Color.Black else Color.White) else s.onAccentContainer, modifier = Modifier.size(if (main) 26.dp else 20.dp))
                }
            }
            }
            }
        }
    }
}

@Composable
fun ContactsWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val personal by Nama.store.personal.flow.collectAsState()
    var picking by remember { mutableStateOf(false) }
    val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val add = { if (granted) picking = true else sc.ctrl.requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS)) }
    WidgetCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            personal.favoriteContacts.take(if (sc.size.h >= 2) 5 else 4).forEach { c ->
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = { Actions.dial(ctx, c.phone) }, onLongClick = { Actions.sms(ctx, c.phone) }).padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(if (sc.size.h >= 2) 52.dp else 40.dp).clip(CircleShape).background(s.accentContainer), contentAlignment = Alignment.Center) {
                        Text(c.name.take(1), color = s.onAccentContainer, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    if (sc.size.h >= 2) WText(c.name, 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (personal.favoriteContacts.size < 5) Box(
                Modifier.size(40.dp).clip(CircleShape).background(s.outline).clickable(onClick = add),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Add, tr("افزودن مخاطب", "Add contact"), tint = s.onSurface) }
        }
    }
    if (picking) ContactPickDialog(onDismiss = { picking = false }) { c ->
        Nama.store.personal.update { it.copy(favoriteContacts = (it.favoriteContacts + c).distinctBy { x -> x.phone }.take(8)) }
    }
}
