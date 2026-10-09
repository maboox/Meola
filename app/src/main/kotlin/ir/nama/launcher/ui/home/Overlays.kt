package ir.nama.launcher.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import ir.nama.core.Command
import ir.nama.core.CommandParser
import ir.nama.core.Friction
import ir.nama.core.PersianText
import ir.nama.core.SearchScorer
import ir.nama.launcher.Nama
import ir.nama.launcher.data.AppEntry
import ir.nama.launcher.data.Todo
import ir.nama.launcher.data.formatToman
import ir.nama.launcher.num
import ir.nama.launcher.system.Actions
import ir.nama.launcher.tr
import ir.nama.launcher.ui.common.AppIconImage
import ir.nama.launcher.ui.theme.LocalNamaStyle
import ir.nama.launcher.ui.theme.StyleBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.random.Random

// ------------------------------------------------------------------------------------------------
// App drawer
// ------------------------------------------------------------------------------------------------

private const val CAT_ALL = "all"
private const val CAT_FREQ = "freq"
private const val CAT_NEW = "new"
private const val CAT_ARCHIVE = "archive"

@Composable
fun AppDrawer(ctrl: HomeController, env: HomeEnv) {
    val s = LocalNamaStyle.current
    val view = LocalView.current
    BackHandler { ctrl.drawerOpen = false }
    var filter by remember { mutableStateOf("") }
    val chip = ctrl.drawerCategory ?: CAT_ALL
    val all = remember(env.apps) { env.apps.values.toList() }
    val visible = remember(all, env.space) { all.filter { env.visible(it) } }
    // Shuffled apps get a new random position every time the drawer opens.
    val seed = remember { Random.nextInt() }
    val stats = Nama.store.appStats.value
    val list = remember(visible, chip, filter, seed) {
        val base = when (chip) {
            CAT_ALL -> visible
            CAT_FREQ -> visible.filter { (stats[it.key]?.launches ?: 0) > 0 }.sortedByDescending { stats[it.key]?.launches ?: 0 }.take(24)
            CAT_NEW -> visible.filter { !it.pref.reviewed || System.currentTimeMillis() - it.installedAt < 7L * 86_400_000 }
                .sortedByDescending { it.installedAt }
            CAT_ARCHIVE -> all.filter { it.pref.archived && !it.pref.hidden }
            else -> visible.filter { it.category.name == chip }
        }
        val filtered = if (filter.isBlank()) base else base.map { it to SearchScorer.score(filter, it.search) }
            .filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
        shuffleFlagged(filtered, seed)
    }
    val cats = remember(visible) { visible.map { it.category }.distinct().sortedBy { it.ordinal } }
    val restricted = Nama.isRestricted(env.space)

    Box(Modifier.fillMaxSize()) {
        StyleBackground(s)
        Box(Modifier.fillMaxSize().background(if (s.dark) androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f)))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Filter field
            Row(
                Modifier.fillMaxWidth().padding(14.dp).height(44.dp).clip(RoundedCornerShape(22.dp))
                    .background(s.card).border(1.dp, s.cardBorder, RoundedCornerShape(22.dp)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⌕", color = s.subText, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (filter.isEmpty()) Text(tr("جستجو در برنامه‌ها", "Filter apps"), color = s.subText, fontSize = 14.sp)
                    BasicTextField(
                        filter, { filter = it }, singleLine = true,
                        textStyle = TextStyle(color = s.text, fontSize = 15.sp, fontFamily = ir.nama.launcher.ui.theme.Vazir),
                        cursorBrush = SolidColor(s.accent), modifier = Modifier.fillMaxWidth()
                    )
                }
                if (filter.isNotEmpty()) Text("✕", color = s.subText, modifier = Modifier.clickable { filter = "" }.padding(6.dp))
            }
            if (!restricted) {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val chips = listOf(CAT_ALL to tr("همه", "All"), CAT_FREQ to tr("پراستفاده", "Frequent"), CAT_NEW to tr("تازه‌ها", "New")) +
                        cats.map { it.name to Nama.categoryName(it) } + (CAT_ARCHIVE to tr("آرشیو", "Archive"))
                    items(chips) { (id, label) ->
                        val sel = chip == id
                        Text(
                            label, color = if (sel) s.accent else s.text, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp))
                                .background(if (sel) s.accent.copy(alpha = 0.16f) else s.card)
                                .border(1.dp, if (sel) s.accent else s.cardBorder, RoundedCornerShape(16.dp))
                                .clickable { ctrl.drawerCategory = id }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            if (env.settings.nationalNetMode && env.net == ir.nama.launcher.data.NetState.NATIONAL_ONLY) {
                Text(
                    tr("اینترنت بین‌الملل قطع است؛ برنامه‌های داخلی جلوتر آمده‌اند.", "International internet is down; domestic apps first."),
                    color = s.subText, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            val ordered = if (env.net == ir.nama.launcher.data.NetState.NATIONAL_ONLY && env.settings.nationalNetMode)
                list.sortedBy { if (it.foreign) 1 else 0 } else list
            LazyVerticalGrid(
                columns = GridCells.Fixed(env.settings.columns.coerceIn(3, 6)),
                modifier = Modifier.weight(1f).fillMaxWidth().imePadding(),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(ordered, key = { it.key }) { e ->
                    Column(
                        Modifier.combinedClickable(
                            onClick = { ctrl.launch(e, view) },
                            onLongClick = { ctrl.appMenu = AppMenuTarget(e, MenuSource.Drawer) }
                        ).padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppIconImage(e, env.settings.iconSizeDp.coerceIn(36, 64).dp, env.shape, grayscale = env.gray(e), dim = env.dim(e), dot = env.dot(e))
                        Text(
                            (if (e.pref.locked) "🔒 " else "") + e.label, color = s.iconLabel, fontSize = 11.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                if (!restricted) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        val hiddenCount = all.count { it.pref.hidden }
                        Text(
                            tr("🔒 برنامه‌های مخفی (${num(hiddenCount)})", "🔒 Hidden apps (${num(hiddenCount)})"),
                            color = s.subText, fontSize = 13.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().clickable {
                                ctrl.authenticate(tr("برنامه‌های مخفی", "Hidden apps")) { ctrl.hiddenAppsOpen = true }
                            }.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun shuffleFlagged(list: List<AppEntry>, seed: Int): List<AppEntry> {
    val flagged = list.filter { Friction.has(it.pref.friction, Friction.SHUFFLE) }
    if (flagged.isEmpty()) return list
    val rnd = Random(seed)
    val rest = list.filterNot { Friction.has(it.pref.friction, Friction.SHUFFLE) }.toMutableList()
    flagged.forEach { rest.add(rnd.nextInt(rest.size + 1), it) }
    return rest
}

// ------------------------------------------------------------------------------------------------
// Search and commands
// ------------------------------------------------------------------------------------------------

private data class Result(
    val icon: String,
    val title: String,
    val subtitle: String? = null,
    val app: AppEntry? = null,
    val shortcut: ShortcutInfo? = null,
    val action: () -> Unit
)

private data class SettingLink(val fa: String, val en: String, val keywords: String, val action: String)

private val SETTING_LINKS = listOf(
    SettingLink("وای‌فای", "Wi-Fi", "wifi وای فای اینترنت", Settings.ACTION_WIFI_SETTINGS),
    SettingLink("بلوتوث", "Bluetooth", "bluetooth بلوتوث", Settings.ACTION_BLUETOOTH_SETTINGS),
    SettingLink("نمایشگر و روشنایی", "Display", "display روشنایی صفحه نمایش brightness", Settings.ACTION_DISPLAY_SETTINGS),
    SettingLink("صدا", "Sound", "sound صدا زنگ", Settings.ACTION_SOUND_SETTINGS),
    SettingLink("باتری", "Battery", "battery باتری شارژ", Intent.ACTION_POWER_USAGE_SUMMARY),
    SettingLink("برنامه‌ها", "Apps", "apps برنامه ها اپ", Settings.ACTION_APPLICATION_SETTINGS),
    SettingLink("موقعیت مکانی", "Location", "location gps مکان موقعیت", Settings.ACTION_LOCATION_SOURCE_SETTINGS),
    SettingLink("مصرف داده", "Data usage", "data usage مصرف اینترنت داده", Settings.ACTION_DATA_USAGE_SETTINGS),
    SettingLink("تاریخ و ساعت", "Date & time", "date time تاریخ ساعت", Settings.ACTION_DATE_SETTINGS),
    SettingLink("زبان", "Language", "language زبان", Settings.ACTION_LOCALE_SETTINGS),
    SettingLink("امنیت", "Security", "security امنیت قفل", Settings.ACTION_SECURITY_SETTINGS),
    SettingLink("حافظه", "Storage", "storage حافظه", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
    SettingLink("برنامه‌های پیش‌فرض", "Default apps", "default پیش فرض لانچر", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
    SettingLink("حالت هواپیما", "Airplane mode", "airplane هواپیما پرواز", Settings.ACTION_AIRPLANE_MODE_SETTINGS),
    SettingLink("VPN", "VPN", "vpn وی پی ان فیلتر", Settings.ACTION_VPN_SETTINGS),
    SettingLink("دسترسی‌پذیری", "Accessibility", "accessibility دسترسی", Settings.ACTION_ACCESSIBILITY_SETTINGS),
    SettingLink("NFC", "NFC", "nfc", Settings.ACTION_NFC_SETTINGS),
    SettingLink("همه تنظیمات", "All settings", "settings تنظیمات", Settings.ACTION_SETTINGS)
)

@Composable
fun SearchOverlay(ctrl: HomeController, env: HomeEnv) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val view = LocalView.current
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler { ctrl.searchOpen = false }
    var query by remember { mutableStateOf(ctrl.searchInitial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        try { focus.requestFocus() } catch (_: Exception) {}
        keyboard?.show()
    }
    val prices by Nama.prices.quotes.collectAsState()
    val results by produceState(emptyList<Result>(), query, env.apps, prices) {
        delay(50)
        value = withContext(Dispatchers.Default) { buildResults(ctrl, env, query, ctx, view) }
    }

    Box(Modifier.fillMaxSize()) {
        StyleBackground(s)
        Box(Modifier.fillMaxSize().background(if (s.dark) androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f)))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp).height(48.dp).clip(RoundedCornerShape(24.dp))
                    .background(s.card).border(1.dp, s.accent.copy(alpha = 0.6f), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⌕", color = s.accent, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(tr("برنامه، مخاطب، «زنگ ۷»، «۵۰ دلار»، «۱۴۰۴/۱/۱»…", "App, contact, \"alarm 7\", \"50 usd\"…"), color = s.subText, fontSize = 13.sp, maxLines = 1)
                    BasicTextField(
                        query, { query = it }, singleLine = true,
                        textStyle = TextStyle(color = s.text, fontSize = 16.sp, fontFamily = ir.nama.launcher.ui.theme.Vazir),
                        cursorBrush = SolidColor(s.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.action?.invoke() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }
                Text("✕", color = s.subText, modifier = Modifier.clickable { if (query.isEmpty()) ctrl.searchOpen = false else query = "" }.padding(6.dp))
            }
            if (query.isBlank()) {
                SuggestedApps(ctrl, env)
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                items(results) { r -> ResultRow(r, env) }
            }
        }
    }
}

@Composable
private fun SuggestedApps(ctrl: HomeController, env: HomeEnv) {
    val s = LocalNamaStyle.current
    val view = LocalView.current
    val hour = java.time.LocalTime.now().hour
    val stats = Nama.store.appStats.value
    // Apps usually opened around this hour, then the most launched.
    val apps = remember(env.apps) {
        env.apps.values.filter { env.visible(it) && !Friction.has(it.pref.friction, Friction.HIDE_FROM_SEARCH) }
            .sortedByDescending { e ->
                val st = stats[e.key] ?: return@sortedByDescending 0.0
                val h = st.hours
                val near = (h.getOrElse(hour) { 0 } * 3 + h.getOrElse((hour + 1) % 24) { 0 } + h.getOrElse((hour + 23) % 24) { 0 }).toDouble()
                near * 4 + st.launches
            }.take(8)
    }
    if (apps.isEmpty()) return
    Text(tr("پیشنهاد برای این ساعت", "Suggested now"), color = s.subText, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp))
    LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(apps, key = { it.key }) { e ->
            Column(Modifier.width(64.dp).clickable { ctrl.launch(e, view) }, horizontalAlignment = Alignment.CenterHorizontally) {
                AppIconImage(e, 46.dp, env.shape)
                Text(e.label, color = s.iconLabel, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ResultRow(r: Result, env: HomeEnv) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
            .clickable { r.action() }.background(s.card.copy(alpha = s.card.alpha * 0.8f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            r.app != null -> AppIconImage(r.app, 38.dp, env.shape, dim = env.dim(r.app))
            r.shortcut != null -> {
                val bmp = remember(r.shortcut.id) {
                    Nama.apps.shortcutIcon(r.shortcut, ctx.resources.displayMetrics.densityDpi)?.toBitmap(96, 96)?.asImageBitmap()
                }
                if (bmp != null) Image(bmp, null, Modifier.size(34.dp)) else Text(r.icon, fontSize = 22.sp, modifier = Modifier.width(38.dp), textAlign = TextAlign.Center)
            }
            else -> Text(r.icon, fontSize = 22.sp, modifier = Modifier.width(38.dp), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, color = s.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!r.subtitle.isNullOrBlank()) Text(r.subtitle, color = s.subText, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun buildResults(ctrl: HomeController, env: HomeEnv, query: String, ctx: android.content.Context, view: android.view.View): List<Result> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val out = mutableListOf<Result>()
    val fa = Nama.isFa
    // Kids and guest spaces: only allowed apps and harmless answers, nothing that leaves the space.
    val restricted = Nama.isRestricted(env.space) || env.space?.overrides?.locked == true

    // 1) Commands
    for (c in CommandParser.parse(q)) {
        when (c) {
            is Command.Alarm -> out += Result("⏰", tr("آلارم برای ساعت ", "Alarm at ") + num("%02d:%02d".format(c.hour, c.minute)), tr("در برنامه ساعت تنظیم می‌شود", "Set in your clock app")) {
                Actions.setAlarm(ctx, c.hour, c.minute, c.label); ctrl.searchOpen = false
            }
            is Command.Timer -> out += Result("⏱", tr("تایمر ", "Timer ") + num(formatDuration(c.seconds)), null) {
                Actions.setTimer(ctx, c.seconds); ctrl.searchOpen = false
            }
            is Command.Reminder -> out += Result(
                "🔔", tr("یادآوری: ", "Remind: ") + c.text,
                (when (c.dayOffset) { 0 -> tr("امروز", "today"); 1 -> tr("فردا", "tomorrow"); else -> tr("پس‌فردا", "in 2 days") }) +
                    (c.hour?.let { " " + num("%02d:%02d".format(it, c.minute ?: 0)) } ?: "") + tr(" · به تقویم و کارهای روز اضافه می‌شود", " · added to calendar and to-dos")
            ) {
                val due = java.time.LocalDate.now().plusDays(c.dayOffset.toLong()).toEpochDay()
                Nama.store.personal.update { it.copy(todos = it.todos + Todo(UUID.randomUUID().toString(), c.text, createdAt = System.currentTimeMillis(), dueEpochDay = due)) }
                Actions.addCalendarReminder(ctx, c.text, c.dayOffset, c.hour, c.minute)
                ctrl.searchOpen = false
            }
            is Command.Calc -> out += Result("=", num(PersianText.groupDecimal(c.result, false, 6)), tr("نتیجه محاسبه · لمس برای کپی", "Result · tap to copy")) {
                Actions.copy(ctx, PersianText.groupDecimal(c.result, false, 6).replace(",", ""))
            }
            is Command.Info -> out += Result("ℹ", c.title, c.subtitle + tr(" · لمس برای کپی", " · tap to copy")) { Actions.copy(ctx, c.copyText) }
            is Command.CurrencyConvert -> {
                val toman = currencyToToman(c.symbol, c.amount)
                if (toman != null) out += Result(
                    "💱", num(PersianText.groupDecimal(c.amount, false, 4)) + " " + c.symbol + " ≈ " + formatToman(toman, Nama.faDigits) + tr(" تومان", " toman"),
                    PersianText.shortToman(toman.toLong(), Nama.faDigits) + tr(" تومان · بر اساس آخرین قیمت", " toman · latest price")
                ) { Actions.copy(ctx, Math.round(toman).toString()) }
                else out += Result("💱", tr("قیمت ${c.symbol} در دسترس نیست", "${c.symbol} price unavailable"), tr("ویجت قیمت‌ها را به‌روز کنید", "Refresh the prices widget")) {}
            }
            is Command.Call -> {}
            is Command.Ussd -> {
                val codes = Nama.remote.config.value.ussd + Nama.store.personal.value.customUssd
                codes.take(8).forEach { u -> out += Result("📶", "${u.operator} · ${u.title}", num(u.code)) { Actions.dialUssd(ctx, u.code) } }
            }
            is Command.WebSearch -> {}
        }
    }

    // 2) Apps
    val apps = env.apps.values.asSequence()
        .filter { env.visible(it) || (it.pref.archived && !restricted) }
        .filter { !Friction.has(it.pref.friction, Friction.HIDE_FROM_SEARCH) && !it.pref.hidden }
        .map { it to SearchScorer.score(q, it.search) }
        .filter { it.second > 0 }
        .sortedByDescending { it.second + (Nama.store.appStats.value[it.first.key]?.launches ?: 0).coerceAtMost(60) }
        .take(8).map { it.first }.toList()
    apps.forEach { e ->
        out += Result("", e.label, Nama.categoryName(e.category) + if (e.pref.archived) tr(" · آرشیو", " · archived") else "", app = e) { ctrl.launch(e, view) }
    }
    // Shortcuts of the best app match.
    apps.firstOrNull()?.let { top ->
        Nama.apps.shortcuts(top).take(4).forEach { sc ->
            out += Result("↗", (sc.shortLabel ?: sc.longLabel ?: "").toString(), top.label, shortcut = sc) {
                Nama.apps.startShortcut(sc); ctrl.searchOpen = false
            }
        }
    }

    if (restricted) return out.filter { it.app != null || it.icon == "=" || it.icon == "ℹ" }

    // 3) Contacts
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED && q.length >= 2) {
        val name = CommandParser.parse(q).filterIsInstance<Command.Call>().firstOrNull()?.name ?: q
        contacts(ctx, name).forEach { (n, phone) ->
            out += Result("👤", n, num(phone)) { Actions.dial(ctx, phone) }
        }
    }

    // 4) Phone settings
    val nq = PersianText.normalize(q)
    SETTING_LINKS.filter { l -> nq.length >= 2 && (PersianText.normalize(l.keywords).split(' ').any { it.startsWith(nq) } || PersianText.normalize(l.fa).contains(nq)) }
        .take(3).forEach { l -> out += Result("⚙", if (fa) l.fa else l.en, tr("تنظیمات گوشی", "Phone settings")) { Actions.settingsPanel(ctx, l.action) } }

    // 5) Nama actions
    namaActions(ctrl, ctx).filter { (title, _) -> nq.length >= 2 && PersianText.normalize(title).contains(nq) }
        .forEach { (title, act) -> out += Result("✦", title, tr("نما", "Nama"), action = act) }

    // 6) Web
    out += Result("🌐", tr("جستجوی «$q» در ${Nama.settings.searchEngine.fa}", "Search \"$q\" on the web"), null) {
        Actions.webSearch(ctx, q); ctrl.searchOpen = false
    }
    return out
}

private fun formatDuration(sec: Int): String = when {
    sec % 3600 == 0 -> "${sec / 3600} " + tr("ساعت", "h")
    sec % 60 == 0 -> "${sec / 60} " + tr("دقیقه", "min")
    else -> "$sec " + tr("ثانیه", "s")
}

fun currencyToToman(symbol: String, amount: Double): Double? {
    val p = Nama.prices
    val usd = p.price("USDT")?.value ?: Nama.settings.manualUsdRate.takeIf { it > 0 }?.toDouble()
    return when (symbol) {
        "USD", "USDT" -> usd?.times(amount)
        "BTC" -> p.price("BTC_IRT")?.value?.times(amount) ?: p.price("BTC")?.value?.let { b -> usd?.let { it * b * amount } }
        "ETH" -> p.price("ETH")?.value?.let { e -> usd?.let { it * e * amount } }
        else -> null
    }
}

private fun contacts(ctx: android.content.Context, q: String): List<Pair<String, String>> = try {
    val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(q))
    val out = mutableListOf<Pair<String, String>>()
    ctx.contentResolver.query(
        uri,
        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
        null, null, null
    )?.use { c ->
        while (c.moveToNext() && out.size < 5) {
            val n = c.getString(0) ?: continue
            val p = c.getString(1) ?: continue
            if (out.none { it.first == n }) out += n to p
        }
    }
    out
} catch (e: Exception) {
    emptyList()
}

private fun namaActions(ctrl: HomeController, ctx: android.content.Context): List<Pair<String, () -> Unit>> = listOf(
    tr("تنظیمات نما", "Nama settings") to { ctx.startActivity(Intent(ctx, ir.nama.launcher.ui.settings.SettingsActivity::class.java)) },
    tr("تغییر فضا", "Switch space") to { ctrl.searchOpen = false; ctrl.spaceSwitcher = true },
    tr("حالت تمرکز ۲۵ دقیقه", "Focus for 25 minutes") to {
        ctrl.searchOpen = false
        Nama.store.settings.update { it.copy(focusUntil = System.currentTimeMillis() + 25 * 60_000L) }
    },
    tr("مرتب‌سازی خودکار برنامه‌ها", "Auto-sort apps") to { ctrl.searchOpen = false; ctrl.smartSortOpen = true },
    tr("برگرداندن آخرین تغییر صفحه", "Undo last home change") to { ctrl.searchOpen = false; Nama.store.undo() },
    tr("تاریخچه چیدمان", "Layout history") to { ctrl.searchOpen = false; ctrl.historyOpen = true },
    tr("برنامه‌های مخفی", "Hidden apps") to {
        ctrl.searchOpen = false
        ctrl.authenticate(tr("برنامه‌های مخفی", "Hidden apps")) { ctrl.hiddenAppsOpen = true }
    },
    tr("افزودن ویجت", "Add widget") to { ctrl.searchOpen = false; ctrl.widgetPickerPage = 0 },
    tr("انتخاب لانچر پیش‌فرض", "Choose default launcher") to { Actions.openHomeSettings(ctx) }
)
