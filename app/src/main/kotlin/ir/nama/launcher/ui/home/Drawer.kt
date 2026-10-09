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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.nama.core.Command
import ir.nama.core.CommandParser
import ir.nama.core.Friction
import ir.nama.core.PersianText
import ir.nama.core.SearchScorer
import ir.nama.launcher.Nama
import ir.nama.launcher.data.AppEntry
import ir.nama.launcher.data.NetState
import ir.nama.launcher.data.Todo
import ir.nama.launcher.data.formatToman
import ir.nama.launcher.num
import ir.nama.launcher.system.Actions
import ir.nama.launcher.tr
import ir.nama.launcher.ui.common.AppIconImage
import ir.nama.launcher.ui.theme.Vazir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.random.Random

// ------------------------------------------------------------------------------------------------
// App drawer
// ------------------------------------------------------------------------------------------------

private const val CAT_ALL = "all"
private const val CAT_NEW = "new"
private const val CAT_ARCHIVE = "archive"

@Composable
private fun overlayColor() = MaterialTheme.colorScheme.surfaceContainerLow

@Composable
fun AppDrawer(ctrl: HomeController, env: HomeEnv) {
    val view = LocalView.current
    val cs = MaterialTheme.colorScheme
    BackHandler { ctrl.drawerOpen = false }
    var filter by remember { mutableStateOf("") }
    val chip = ctrl.drawerCategory ?: CAT_ALL
    val all = remember(env.apps) { env.apps.values.sortedWith(compareBy<AppEntry> { it.label.lowercase() }) }
    val visible = remember(all, env.space) { all.filter { env.visible(it) } }
    // Apps with the "move it" friction get a new random position each time the drawer opens.
    val seed = remember { Random.nextInt() }
    val list = remember(visible, chip, filter, seed) {
        val base = when (chip) {
            CAT_ALL -> visible
            CAT_NEW -> visible.filter { System.currentTimeMillis() - it.installedAt < 7L * 86_400_000 }.sortedByDescending { it.installedAt }
            CAT_ARCHIVE -> all.filter { it.pref.archived && !it.pref.hidden }
            else -> visible.filter { it.category.name == chip }
        }
        val filtered = if (filter.isBlank()) base else base.map { it to SearchScorer.score(filter, it.search) }
            .filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
        shuffleFlagged(filtered, seed)
    }
    val stats = Nama.store.appStats.value
    val frequent = remember(visible) {
        visible.filter { (stats[it.key]?.launches ?: 0) > 0 && !Friction.has(it.pref.friction, Friction.SHUFFLE) }
            .sortedByDescending { stats[it.key]?.launches ?: 0 }.take(ctrl.cols.coerceAtLeast(4))
    }
    val cats = remember(visible) { visible.map { it.category }.distinct().sortedBy { it.ordinal } }
    val restricted = Nama.isRestricted(env.space)
    val national = env.settings.nationalNetMode && env.net == NetState.NATIONAL_ONLY
    val ordered = if (national) list.sortedBy { if (it.foreign) 1 else 0 } else list
    val cols = ctrl.cols
    val iconSize = env.settings.iconSizeDp.coerceIn(44, 60).dp

    Surface(Modifier.fillMaxSize(), color = overlayColor()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            SearchField(
                value = filter, onChange = { filter = it },
                hint = tr("جستجو در برنامه‌ها", "Search apps"),
                leading = Icons.Outlined.Search,
                onClose = { if (filter.isEmpty()) ctrl.drawerOpen = false else filter = "" },
                autoFocus = false
            )
            if (!restricted && filter.isEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val chips = listOf(CAT_ALL to tr("همه", "All"), CAT_NEW to tr("تازه", "New")) +
                        cats.map { it.name to Nama.categoryName(it) } + (CAT_ARCHIVE to tr("آرشیو", "Archive"))
                    items(chips, key = { it.first }) { (id, label) ->
                        FilterChip(chip == id, { ctrl.drawerCategory = id }, label = { Text(label) })
                    }
                }
            }
            if (national) {
                Text(
                    tr("اینترنت بین‌الملل قطع است؛ برنامه‌های داخلی جلوتر آمده‌اند.", "International internet is down; domestic apps first."),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                modifier = Modifier.weight(1f).fillMaxWidth().imePadding(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (chip == CAT_ALL && filter.isEmpty() && frequent.size >= cols && !restricted) {
                    items(frequent, key = { "f_" + it.key }) { e -> DrawerIcon(ctrl, env, e, iconSize, view) }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = cs.outlineVariant)
                    }
                }
                items(ordered, key = { it.key }) { e -> DrawerIcon(ctrl, env, e, iconSize, view) }
                if (ordered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(tr("چیزی پیدا نشد", "Nothing found"), color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
                }
                if (!restricted) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        val hiddenCount = all.count { it.pref.hidden }
                        if (hiddenCount > 0) TextButton(
                            onClick = { ctrl.authenticate(tr("برنامه‌های پنهان", "Hidden apps")) { ctrl.hiddenAppsOpen = true } },
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(tr("برنامه‌های پنهان (${num(hiddenCount)})", "Hidden apps (${num(hiddenCount)})"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerIcon(ctrl: HomeController, env: HomeEnv, e: AppEntry, size: androidx.compose.ui.unit.Dp, view: android.view.View) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).combinedClickable(
                onClick = { ctrl.launch(e, view) },
                onLongClick = { if (!env.locked) menu = true }
            ).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppIconImage(e, size, env.shape, grayscale = env.gray(e), dim = env.dim(e), dot = env.dot(e))
            Text(
                e.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp)
            )
        }
        AppMenu(ctrl, env, e, expanded = menu, onDismiss = { menu = false })
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

/** Rounded search field used by the drawer and the search screen. */
@Composable
private fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    leading: ImageVector,
    onClose: () -> Unit,
    autoFocus: Boolean,
    onGo: (() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (autoFocus) LaunchedEffect(Unit) {
        delay(80)
        try { focus.requestFocus() } catch (_: Exception) {}
        keyboard?.show()
    }
    Row(
        Modifier.fillMaxWidth().padding(16.dp).height(56.dp).clip(RoundedCornerShape(28.dp))
            .background(cs.surfaceContainerHighest).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(8.dp))
        Icon(leading, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = TextStyle(color = cs.onSurface, fontSize = 16.sp, fontFamily = Vazir),
                cursorBrush = SolidColor(cs.primary),
                keyboardOptions = KeyboardOptions(imeAction = if (onGo != null) ImeAction.Go else ImeAction.Search),
                keyboardActions = KeyboardActions(onGo = { onGo?.invoke() }, onSearch = { keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
        }
        IconButton(onClose) { Icon(Icons.Outlined.Close, tr("بستن", "Close"), tint = cs.onSurfaceVariant) }
    }
}

// ------------------------------------------------------------------------------------------------
// Search and commands
// ------------------------------------------------------------------------------------------------

private enum class Section(val fa: String, val en: String) {
    ANSWER("پاسخ", "Answer"), APPS("برنامه‌ها", "Apps"), SHORTCUTS("میانبرها", "Shortcuts"), CONTACTS("مخاطبین", "Contacts"),
    SETTINGS("تنظیمات", "Settings"), NAMA("نما", "Nama"), WEB("وب", "Web")
}

private data class Result(
    val section: Section,
    val icon: ImageVector,
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
    val ctx = LocalContext.current
    val view = LocalView.current
    val cs = MaterialTheme.colorScheme
    BackHandler { ctrl.searchOpen = false }
    var query by remember { mutableStateOf(ctrl.searchInitial) }
    val prices by Nama.prices.quotes.collectAsStateWithLifecycle()
    val results by produceState(emptyList<Result>(), query, env.apps, prices) {
        delay(50)
        value = withContext(Dispatchers.Default) { buildResults(ctrl, env, query, ctx, view) }
    }

    Surface(Modifier.fillMaxSize(), color = overlayColor()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            SearchField(
                value = query, onChange = { query = it },
                hint = tr("برنامه، مخاطب، «زنگ ۷»، «۵۰ دلار»…", "App, contact, \"alarm 7\", \"50 usd\"…"),
                leading = Icons.Outlined.Search,
                onClose = { if (query.isEmpty()) ctrl.searchOpen = false else query = "" },
                autoFocus = true,
                onGo = { results.firstOrNull()?.action?.invoke() }
            )
            if (query.isBlank()) {
                SuggestedApps(ctrl, env)
                Text(
                    tr("می‌توانی بنویسی: «زنگ ۶:۳۰»، «تایمر ۱۰ دقیقه»، «۲۵۰۰۰۰*۳»، «۱۰۰ دلار»، «فردا ساعت ۹ یادم بنداز…»",
                        "Try: \"alarm 6:30\", \"timer 10 min\", \"250000*3\", \"100 usd\", \"remind me tomorrow 9…\""),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                var last: Section? = null
                results.forEachIndexed { i, r ->
                    if (r.section != last && r.section != Section.WEB) {
                        val sec = r.section
                        item(key = "s_${sec.name}_$i") {
                            Text(tr(sec.fa, sec.en), style = MaterialTheme.typography.labelLarge, color = cs.primary, modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 6.dp))
                        }
                    }
                    last = r.section
                    item(key = "r_$i") { ResultRow(r, env) }
                }
            }
        }
    }
}

@Composable
private fun SuggestedApps(ctrl: HomeController, env: HomeEnv) {
    val view = LocalView.current
    val cs = MaterialTheme.colorScheme
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
            }.take(ctrl.cols)
    }
    if (apps.isEmpty()) return
    Text(tr("پیشنهاد برای این ساعت", "Suggested now"), style = MaterialTheme.typography.labelLarge, color = cs.primary, modifier = Modifier.padding(horizontal = 24.dp))
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        apps.forEach { e ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { ctrl.launch(e, view) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppIconImage(e, 48.dp, env.shape)
                Text(e.label, style = MaterialTheme.typography.labelMedium, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp))
            }
        }
    }
}

@Composable
private fun ResultRow(r: Result, env: HomeEnv) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(18.dp))
            .background(cs.surfaceContainer).clickable { r.action() }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            r.app != null -> AppIconImage(r.app, 40.dp, env.shape, dim = env.dim(r.app))
            r.shortcut != null -> {
                val bmp = remember(r.shortcut.id) {
                    try { Nama.apps.shortcutIcon(r.shortcut, ctx.resources.displayMetrics.densityDpi)?.toBitmap(96, 96)?.asImageBitmap() } catch (_: Exception) { null }
                }
                if (bmp != null) Image(bmp, null, Modifier.size(40.dp).padding(3.dp)) else ResultIcon(r.icon)
            }
            else -> ResultIcon(r.icon)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!r.subtitle.isNullOrBlank()) Text(r.subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ResultIcon(icon: ImageVector) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(40.dp).clip(CircleShape).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = cs.onSecondaryContainer, modifier = Modifier.size(20.dp))
    }
}

private fun buildResults(ctrl: HomeController, env: HomeEnv, query: String, ctx: android.content.Context, view: android.view.View): List<Result> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val out = mutableListOf<Result>()
    val fa = Nama.isFa
    // Kids and guest spaces: only allowed apps and harmless answers, nothing that leaves the space.
    val restricted = Nama.isRestricted(env.space) || env.space?.overrides?.locked == true
    val A = Section.ANSWER

    // 1) Commands and instant answers
    for (c in CommandParser.parse(q)) {
        when (c) {
            is Command.Alarm -> out += Result(A, Icons.Outlined.Alarm, tr("آلارم برای ساعت ", "Alarm at ") + num("%02d:%02d".format(c.hour, c.minute)), tr("در برنامه ساعت تنظیم می‌شود", "Set in your clock app")) {
                Actions.setAlarm(ctx, c.hour, c.minute, c.label); ctrl.searchOpen = false
            }
            is Command.Timer -> out += Result(A, Icons.Outlined.Timer, tr("تایمر ", "Timer ") + num(formatDuration(c.seconds)), null) {
                Actions.setTimer(ctx, c.seconds); ctrl.searchOpen = false
            }
            is Command.Reminder -> out += Result(
                A, Icons.Outlined.NotificationsActive, tr("یادآوری: ", "Remind: ") + c.text,
                (when (c.dayOffset) { 0 -> tr("امروز", "today"); 1 -> tr("فردا", "tomorrow"); else -> tr("پس‌فردا", "in 2 days") }) +
                    (c.hour?.let { " " + num("%02d:%02d".format(it, c.minute ?: 0)) } ?: "") + tr(" · به تقویم و کارها اضافه می‌شود", " · added to calendar and to-dos")
            ) {
                val due = java.time.LocalDate.now().plusDays(c.dayOffset.toLong()).toEpochDay()
                Nama.store.personal.update { it.copy(todos = it.todos + Todo(UUID.randomUUID().toString(), c.text, createdAt = System.currentTimeMillis(), dueEpochDay = due)) }
                Actions.addCalendarReminder(ctx, c.text, c.dayOffset, c.hour, c.minute)
                ctrl.searchOpen = false
            }
            is Command.Calc -> out += Result(A, Icons.Outlined.Calculate, num(PersianText.groupDecimal(c.result, false, 6)), tr("نتیجه محاسبه · لمس برای کپی", "Result · tap to copy")) {
                Actions.copy(ctx, PersianText.groupDecimal(c.result, false, 6).replace(",", ""))
            }
            is Command.Info -> out += Result(A, Icons.Outlined.Info, c.title, c.subtitle + tr(" · لمس برای کپی", " · tap to copy")) { Actions.copy(ctx, c.copyText) }
            is Command.CurrencyConvert -> {
                val toman = currencyToToman(c.symbol, c.amount)
                if (toman != null) out += Result(
                    A, Icons.Outlined.CurrencyExchange,
                    num(PersianText.groupDecimal(c.amount, false, 4)) + " " + c.symbol + " ≈ " + formatToman(toman, Nama.faDigits) + tr(" تومان", " toman"),
                    PersianText.shortToman(toman.toLong(), Nama.faDigits) + tr(" تومان · بر اساس آخرین قیمت", " toman · latest price")
                ) { Actions.copy(ctx, Math.round(toman).toString()) }
                else out += Result(A, Icons.Outlined.CurrencyExchange, tr("قیمت ${c.symbol} در دسترس نیست", "${c.symbol} price unavailable"), tr("ویجت قیمت‌ها را به‌روز کنید", "Refresh the prices widget")) {}
            }
            is Command.Call -> {}
            is Command.Ussd -> {
                val codes = Nama.remote.config.value.ussd + Nama.store.personal.value.customUssd
                codes.take(8).forEach { u -> out += Result(A, Icons.Outlined.SimCard, "${u.operator} · ${u.title}", num(u.code)) { Actions.dialUssd(ctx, u.code) } }
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
        .take(6).map { it.first }.toList()
    apps.forEach { e ->
        out += Result(Section.APPS, Icons.Outlined.Apps, e.label, Nama.categoryName(e.category) + if (e.pref.archived) tr(" · آرشیو", " · archived") else "", app = e) { ctrl.launch(e, view) }
    }
    // Shortcuts of the best app match.
    apps.firstOrNull()?.let { top ->
        try { Nama.apps.shortcuts(top) } catch (_: Exception) { emptyList() }.take(4).forEach { sc ->
            out += Result(Section.SHORTCUTS, Icons.Outlined.OpenInNew, (sc.shortLabel ?: sc.longLabel ?: "").toString(), top.label, shortcut = sc) {
                Nama.apps.startShortcut(sc); ctrl.searchOpen = false
            }
        }
    }

    if (restricted) return out.filter { it.section == Section.APPS || it.icon == Icons.Outlined.Calculate || it.icon == Icons.Outlined.Info }

    // 3) Contacts
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED && q.length >= 2) {
        val name = CommandParser.parse(q).filterIsInstance<Command.Call>().firstOrNull()?.name ?: q
        contacts(ctx, name).forEach { (n, phone) ->
            out += Result(Section.CONTACTS, Icons.Outlined.Person, n, num(phone)) { Actions.dial(ctx, phone) }
        }
    }

    // 4) Phone settings
    val nq = PersianText.normalize(q)
    SETTING_LINKS.filter { l -> nq.length >= 2 && (PersianText.normalize(l.keywords).split(' ').any { it.startsWith(nq) } || PersianText.normalize(l.fa).contains(nq)) }
        .take(3).forEach { l -> out += Result(Section.SETTINGS, Icons.Outlined.Settings, if (fa) l.fa else l.en, tr("تنظیمات گوشی", "Phone settings")) { Actions.settingsPanel(ctx, l.action) } }

    // 5) Nama actions
    namaActions(ctrl, ctx).filter { (title, _) -> nq.length >= 2 && PersianText.normalize(title).contains(nq) }
        .forEach { (title, act) -> out += Result(Section.NAMA, Icons.Outlined.AutoAwesome, title, null, action = act) }

    // 6) Web
    out += Result(Section.WEB, Icons.Outlined.Language, tr("جستجوی «$q» در ${Nama.settings.searchEngine.fa}", "Search \"$q\" on the web"), null) {
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
    tr("برنامه‌های پنهان", "Hidden apps") to {
        ctrl.searchOpen = false
        ctrl.authenticate(tr("برنامه‌های پنهان", "Hidden apps")) { ctrl.hiddenAppsOpen = true }
    },
    tr("افزودن ویجت", "Add widget") to { ctrl.searchOpen = false; ctrl.widgetPickerOpen = true },
    tr("انتخاب لانچر پیش‌فرض", "Choose default launcher") to { Actions.openHomeSettings(ctx) }
)
