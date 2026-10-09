package ir.nama.launcher.ui.home

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import ir.nama.core.AppCategory
import ir.nama.core.Friction
import ir.nama.core.SpaceEngine
import ir.nama.core.StyleId
import ir.nama.launcher.Nama
import ir.nama.launcher.data.AppEntry
import ir.nama.launcher.data.HomeItem
import ir.nama.launcher.data.HomePage
import ir.nama.launcher.data.WidgetInstance
import ir.nama.launcher.data.WidgetSize
import ir.nama.launcher.data.WidgetType
import ir.nama.launcher.logic.SmartSort
import ir.nama.launcher.logic.Templates
import ir.nama.launcher.num
import ir.nama.launcher.system.Actions
import ir.nama.launcher.tr
import ir.nama.launcher.ui.common.AppIconImage
import ir.nama.launcher.ui.common.ChipsSelector
import ir.nama.launcher.ui.common.TextInputDialog
import ir.nama.launcher.ui.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
private fun SheetTitle(text: String, sub: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SheetAction(icon: String, text: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 18.sp, modifier = Modifier.width(32.dp))
        Text(text, fontSize = 15.sp, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}

// ------------------------------------------------------------------------------------------------
// App menu
// ------------------------------------------------------------------------------------------------

@Composable
fun AppMenuSheet(ctrl: HomeController, env: HomeEnv, target: AppMenuTarget) {
    val e = env.apps[target.entry.key] ?: target.entry
    val ctx = LocalContext.current
    var settingsOpen by remember { mutableStateOf(false) }
    var folderPick by remember { mutableStateOf(false) }
    var pagePick by remember { mutableStateOf(false) }
    val shortcuts = remember(e.key) { Nama.apps.shortcuts(e) }
    val layout = Nama.store.layout.value
    val onHome = layout.pages.any { p -> p.items.any { (it is HomeItem.App && it.key == e.key) || (it is HomeItem.Folder && e.key in it.keys) } }
    val close = { ctrl.appMenu = null }

    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIconImage(e, 48.dp, env.shape)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(e.label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(Nama.categoryName(e.category) + (if (e.domestic) tr(" · داخلی", " · domestic") else ""), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (shortcuts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                shortcuts.forEach { sc ->
                    val icon = remember(sc.id) { Nama.apps.shortcutIcon(sc, ctx.resources.displayMetrics.densityDpi)?.toBitmap(72, 72)?.asImageBitmap() }
                    Row(
                        Modifier.fillMaxWidth().clickable { Nama.apps.startShortcut(sc); close() }.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (icon != null) Image(icon, null, Modifier.size(24.dp)) else Text("↗", modifier = Modifier.width(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text((sc.shortLabel ?: sc.longLabel ?: "").toString(), fontSize = 14.sp)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
            when (val src = target.source) {
                is MenuSource.Home -> {
                    SheetAction("➖", tr("برداشتن از صفحه", "Remove from home")) { ctrl.removeItem(src.pageIndex, src.itemId); close() }
                    SheetAction("📁", tr("گذاشتن در پوشه…", "Put in folder…")) { folderPick = true }
                    SheetAction("↔", tr("انتقال به صفحه…", "Move to page…")) { pagePick = true }
                    if (e.key !in layout.dock) SheetAction("⬇", tr("افزودن به داک", "Add to dock")) { ctrl.addToDock(e.key); close() }
                }
                is MenuSource.Folder -> {
                    SheetAction("📤", tr("خارج کردن از پوشه", "Take out of folder")) { ctrl.removeFromFolder(src.pageIndex, src.folderId, e.key); close() }
                }
                is MenuSource.Dock -> SheetAction("➖", tr("برداشتن از داک", "Remove from dock")) { ctrl.removeFromDock(e.key); close() }
                is MenuSource.Drawer -> {
                    if (!onHome) SheetAction("➕", tr("افزودن به صفحه اصلی", "Add to home")) { ctrl.addToHome(e.key); close() }
                    SheetAction("📁", tr("گذاشتن در پوشه…", "Put in folder…")) { folderPick = true }
                    if (e.key !in layout.dock) SheetAction("⬇", tr("افزودن به داک", "Add to dock")) { ctrl.addToDock(e.key); close() }
                }
            }
            if (e.pref.archived) SheetAction("📦", tr("بیرون آوردن از آرشیو", "Unarchive")) { Nama.store.updatePref(e.key) { it.copy(archived = false) }; close() }
            SheetAction("⚙", tr("تنظیمات این برنامه در نما (قفل، اصطکاک، سقف زمان…)", "Nama settings for this app (lock, friction, limit…)")) { settingsOpen = true }
            SheetAction(if (e.pref.hidden) "👁" else "🙈", if (e.pref.hidden) tr("نمایش دوباره", "Unhide") else tr("پنهان کردن", "Hide")) {
                Nama.store.updatePref(e.key) { it.copy(hidden = !it.hidden) }; close()
            }
            SheetAction("ℹ", tr("اطلاعات برنامه", "App info")) { Actions.appInfo(ctx, e); close() }
            if (!e.isSystem) SheetAction("🗑", tr("حذف برنامه از گوشی", "Uninstall"), danger = true) { Actions.uninstall(ctx, e); close() }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (settingsOpen) AppSettingsDialog(e) { settingsOpen = false }
    if (folderPick) FolderPickDialog(ctrl, e, (target.source as? MenuSource.Home)?.pageIndex ?: 0) { folderPick = false; close() }
    if (pagePick) {
        val src = target.source as? MenuSource.Home
        AlertDialog(
            onDismissRequest = { pagePick = false },
            title = { Text(tr("انتقال به صفحه", "Move to page")) },
            text = {
                Column {
                    (0..layout.pages.size).forEach { i ->
                        Text(
                            if (i < layout.pages.size) tr("صفحه ${num(i + 1)}", "Page ${i + 1}") else tr("صفحه جدید", "New page"),
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (src != null) ctrl.moveItemToPage(src.pageIndex, src.itemId, i)
                                pagePick = false; close()
                            }.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton({ pagePick = false }) { Text(tr("انصراف", "Cancel")) } }
        )
    }
}

@Composable
private fun FolderPickDialog(ctrl: HomeController, e: AppEntry, pageIndex: Int, onDone: () -> Unit) {
    val folders = Nama.store.layout.value.pages.flatMap { p -> p.items.filterIsInstance<HomeItem.Folder>() }.map { it.name }.distinct()
    var newName by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(tr("کدام پوشه؟", "Which folder?")) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                item {
                    Text("➕ " + tr("پوشه جدید", "New folder"), Modifier.fillMaxWidth().clickable { newName = true }.padding(12.dp), fontWeight = FontWeight.Medium)
                }
                item {
                    Text("✦ " + Nama.categoryName(e.category), Modifier.fillMaxWidth().clickable { ctrl.putInFolder(e.key, Nama.categoryName(e.category), pageIndex); onDone() }.padding(12.dp))
                }
                items(folders) { f ->
                    Text("📁 $f", Modifier.fillMaxWidth().clickable { ctrl.putInFolder(e.key, f, pageIndex); onDone() }.padding(12.dp))
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onDone) { Text(tr("انصراف", "Cancel")) } }
    )
    if (newName) TextInputDialog(tr("نام پوشه", "Folder name"), onDismiss = { newName = false }) { n ->
        if (n.isNotBlank()) { ctrl.putInFolder(e.key, n, pageIndex); onDone() }
    }
}

/** Per-app settings: hidden, locked, friction flags, daily limit, name, aliases, category. */
@Composable
fun AppSettingsDialog(e: AppEntry, onDismiss: () -> Unit) {
    val prefs by Nama.store.appPrefs.flow.collectAsState()
    val p = prefs[e.key] ?: ir.nama.launcher.data.AppPref()
    var rename by remember { mutableStateOf(false) }
    var aliases by remember { mutableStateOf(false) }
    var catPick by remember { mutableStateOf(false) }
    fun upd(f: (ir.nama.launcher.data.AppPref) -> ir.nama.launcher.data.AppPref) = Nama.store.updatePref(e.key, f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(e.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SwitchRow(tr("پنهان", "Hidden"), p.hidden) { v -> upd { it.copy(hidden = v) } }
                SwitchRow(tr("قفل با اثر انگشت / رمز گوشی", "Lock with fingerprint / PIN"), p.locked) { v -> upd { it.copy(locked = v) } }
                SwitchRow(tr("آرشیو", "Archived"), p.archived) { v -> upd { it.copy(archived = v) } }
                Spacer(Modifier.height(8.dp))
                Text(tr("حالت اصطکاک (برای اپ‌هایی که می‌خواهی کمتر بازشان کنی)", "Friction (for apps you want to use less)"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                FrictionRow(tr("در جستجو پیدا نشود", "Hide from search"), p.friction, Friction.HIDE_FROM_SEARCH) { v -> upd { it.copy(friction = v) } }
                FrictionRow(tr("هر بار جایش عوض شود", "Move it every time"), p.friction, Friction.SHUFFLE) { v -> upd { it.copy(friction = v) } }
                FrictionRow(tr("۱۰ ثانیه مکث قبل از باز شدن", "10-second pause before opening"), p.friction, Friction.DELAY) { v -> upd { it.copy(friction = v) } }
                FrictionRow(tr("بپرس «برای چی بازش می‌کنی؟»", "Ask why I'm opening it"), p.friction, Friction.ASK_INTENTION) { v -> upd { it.copy(friction = v) } }
                FrictionRow(tr("تایپ یک جمله برای باز کردن", "Type a sentence to open"), p.friction, Friction.TYPE_SENTENCE) { v -> upd { it.copy(friction = v) } }
                FrictionRow(tr("سیاه‌وسفید بعد از سقف زمانی", "Grayscale after the daily limit"), p.friction, Friction.GRAYSCALE_AFTER_LIMIT) { v -> upd { it.copy(friction = v) } }
                Text(
                    tr("سقف استفاده روزانه: ", "Daily limit: ") + if (p.dailyLimitMinutes == 0) tr("ندارد", "none") else num(p.dailyLimitMinutes) + tr(" دقیقه", " min"),
                    fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)
                )
                Slider(
                    value = p.dailyLimitMinutes.toFloat(), onValueChange = { v -> upd { it.copy(dailyLimitMinutes = (v / 5).toInt() * 5) } },
                    valueRange = 0f..240f
                )
                if (p.dailyLimitMinutes > 0 && !Nama.usage.hasPermission()) {
                    Text(tr("برای سقف زمانی، «دسترسی آمار استفاده» را در تنظیمات نما › دسترسی‌ها بدهید.", "Grant usage access in Nama settings › Permissions."), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                TextButton({ rename = true }) { Text(tr("تغییر نام نمایشی", "Rename") + (p.label?.let { " ($it)" } ?: "")) }
                TextButton({ aliases = true }) { Text(tr("نام‌های دیگر برای جستجو", "Search aliases") + if (p.aliases.isNotEmpty()) " (${p.aliases.joinToString("، ")})" else "") }
                TextButton({ catPick = true }) { Text(tr("دسته: ", "Category: ") + Nama.categoryName(p.category ?: e.category)) }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(tr("بستن", "Close")) } }
    )
    if (rename) TextInputDialog(tr("نام نمایشی", "Display name"), p.label ?: e.originalLabel, onDismiss = { rename = false }) { v ->
        upd { it.copy(label = v.ifBlank { null }) }
    }
    if (aliases) TextInputDialog(tr("نام‌های دیگر (با ویرگول جدا کنید)", "Aliases (comma separated)"), p.aliases.joinToString("، "), onDismiss = { aliases = false }) { v ->
        upd { it.copy(aliases = v.split(',', '،').map { s -> s.trim() }.filter { s -> s.isNotEmpty() }) }
    }
    if (catPick) {
        val cats = AppCategory.entries
        ir.nama.launcher.ui.common.ChoiceDialog(tr("دسته", "Category"), cats.map { Nama.categoryName(it) }, cats.indexOf(p.category ?: e.category), { catPick = false }) { i ->
            upd { it.copy(category = cats[i]) }
        }
    }
}

@Composable
fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked, onChange)
    }
}

@Composable
private fun FrictionRow(text: String, flags: Int, flag: Int, onChange: (Int) -> Unit) {
    val on = Friction.has(flags, flag)
    Row(Modifier.fillMaxWidth().clickable { onChange(if (on) flags and flag.inv() else flags or flag) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(on, { onChange(if (on) flags and flag.inv() else flags or flag) })
        Text(text, fontSize = 13.sp)
    }
}

// ------------------------------------------------------------------------------------------------
// Folder
// ------------------------------------------------------------------------------------------------

@Composable
fun FolderDialog(ctrl: HomeController, env: HomeEnv, target: FolderTarget) {
    val layout by Nama.store.layout.flow.collectAsState()
    val folder = layout.pages.getOrNull(target.pageIndex)?.items?.firstOrNull { it.id == target.folderId } as? HomeItem.Folder
    if (folder == null) { ctrl.openFolder = null; return }
    val view = LocalView.current
    var rename by remember { mutableStateOf(false) }
    val keys = folderKeys(folder, env)
    AlertDialog(
        onDismissRequest = { ctrl.openFolder = null },
        title = {
            Text(folder.name + if (folder.smartCategory != null) tr(" · هوشمند", " · smart") else "", Modifier.clickable { rename = true })
        },
        text = {
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(keys, key = { it }) { k ->
                    val e = env.apps[k] ?: return@items
                    Column(
                        Modifier.combinedClickable(
                            onClick = { ctrl.launch(e, view) },
                            onLongClick = { ctrl.appMenu = AppMenuTarget(e, MenuSource.Folder(target.pageIndex, folder.id)) }
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppIconImage(e, 48.dp, env.shape, grayscale = env.gray(e), dim = env.dim(e), dot = env.dot(e))
                        Text(e.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        },
        confirmButton = {
            TextButton({
                Nama.store.changeLayout(tr("پوشه هوشمند", "Smart folder")) { l ->
                    l.copy(pages = l.pages.map { p ->
                        p.copy(items = p.items.map {
                            if (it.id == folder.id && it is HomeItem.Folder) {
                                val cat = if (it.smartCategory == null) keys.mapNotNull { k -> env.apps[k]?.category }.groupingBy { c -> c }.eachCount().maxByOrNull { e -> e.value }?.key else null
                                it.copy(smartCategory = cat)
                            } else it
                        })
                    })
                }
            }) { Text(if (folder.smartCategory == null) tr("هوشمند کن", "Make smart") else tr("هوشمند نباشد", "Not smart")) }
        },
        dismissButton = {
            TextButton({
                Nama.store.changeLayout(tr("باز کردن پوشه", "Ungroup")) { l ->
                    l.copy(pages = l.pages.mapIndexed { i, p ->
                        if (i != target.pageIndex) p else p.copy(items = p.items.flatMap {
                            if (it.id == folder.id) keys.map { k -> HomeItem.App(HomeController.newId(), k) } else listOf(it)
                        })
                    })
                }
                ctrl.openFolder = null
            }) { Text(tr("باز کردن پوشه", "Ungroup")) }
        }
    )
    if (rename) TextInputDialog(tr("نام پوشه", "Folder name"), folder.name, onDismiss = { rename = false }) { n ->
        if (n.isNotBlank()) Nama.store.changeLayout(tr("تغییر نام پوشه", "Rename folder")) { l ->
            l.copy(pages = l.pages.map { p -> p.copy(items = p.items.map { if (it.id == folder.id && it is HomeItem.Folder) it.copy(name = n) else it }) })
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Home menu (long press on empty space)
// ------------------------------------------------------------------------------------------------

@Composable
fun HomeMenuSheet(ctrl: HomeController, pageIndex: Int) {
    val ctx = LocalContext.current
    val close = { ctrl.homeMenuPage = null }
    var stylePick by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val layout = Nama.store.layout.value
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
            SheetTitle(tr("ویرایش صفحه ${num(pageIndex + 1)}", "Edit page ${pageIndex + 1}"))
            SheetAction("🧩", tr("افزودن ویجت", "Add widget")) { ctrl.widgetPickerPage = pageIndex; close() }
            SheetAction("🎨", tr("سبک ظاهری", "Style")) { stylePick = true }
            SheetAction("✨", tr("مرتب‌سازی خودکار برنامه‌ها", "Auto-sort apps")) { ctrl.smartSortOpen = true; close() }
            SheetAction("🗂", tr("فضاها", "Spaces")) { ctrl.spaceSwitcher = true; close() }
            SheetAction("📄", tr("صفحه جدید", "New page")) {
                Nama.store.changeLayout(tr("صفحه جدید", "New page")) { l -> l.copy(pages = l.pages + HomePage(HomeController.newId())) }
                close()
            }
            if (layout.pages.size > 1) SheetAction("🗑", tr("حذف این صفحه", "Delete this page"), danger = true) { confirmDelete = true }
            SheetAction("↩", tr("برگرداندن آخرین تغییر", "Undo last change")) { if (!Nama.store.undo()) Actions.toast(ctx, tr("تغییری برای برگرداندن نیست", "Nothing to undo")); close() }
            SheetAction("🕘", tr("تاریخچه چیدمان", "Layout history")) { ctrl.historyOpen = true; close() }
            SheetAction("🖼", tr("والپیپر گوشی", "Phone wallpaper")) {
                Actions.start(ctx, android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SET_WALLPAPER), null)); close()
            }
            SheetAction("⚙", tr("تنظیمات نما", "Nama settings")) { ctx.startActivity(android.content.Intent(ctx, SettingsActivity::class.java)); close() }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (stylePick) {
        val styles = StyleId.entries
        ir.nama.launcher.ui.common.ChoiceDialog(tr("سبک ظاهری", "Style"), styles.map { tr(it.fa, it.en) }, styles.indexOf(Nama.settings.style), { stylePick = false }) { i ->
            Nama.store.settings.update { it.copy(style = styles[i]) }
            close()
        }
    }
    if (confirmDelete) ir.nama.launcher.ui.common.ConfirmDialog(
        tr("حذف صفحه", "Delete page"),
        tr("برنامه‌ها و پوشه‌های این صفحه از صفحه اصلی برداشته می‌شوند (از گوشی حذف نمی‌شوند). از «تاریخچه چیدمان» قابل برگشت است.", "Apps on this page are removed from home only. You can undo from layout history."),
        tr("حذف", "Delete"), {
            Nama.store.changeLayout(tr("حذف صفحه", "Delete page")) { l -> if (l.pages.size <= 1) l else l.copy(pages = l.pages.filterIndexed { i, _ -> i != pageIndex }) }
            close()
        }, { confirmDelete = false }, danger = true
    )
}

// ------------------------------------------------------------------------------------------------
// Widget picker and widget menu
// ------------------------------------------------------------------------------------------------

private val GROUPS = listOf(
    "time" to ("زمان و تقویم" to "Time & calendar"),
    "money" to ("مالی" to "Money"),
    "phone" to ("گوشی و سیم‌کارت" to "Phone & SIM"),
    "city" to ("شهر و زندگی" to "City & life"),
    "culture" to ("فرهنگ و حال خوب" to "Culture"),
    "productivity" to ("بهره‌وری" to "Productivity"),
    "wellbeing" to ("تمرکز و آرامش" to "Wellbeing"),
    "news" to ("اخبار" to "News"),
    "structure" to ("ساختاری" to "Structure")
)

@Composable
fun WidgetPickerSheet(ctrl: HomeController, pageIndex: Int, onPickSystem: (AppWidgetProviderInfo) -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val close = { ctrl.widgetPickerPage = null }
    fun add(type: WidgetType) {
        Nama.store.changeLayout(tr("افزودن ویجت", "Add widget")) { l ->
            l.copy(pages = l.pages.mapIndexed { i, p -> if (i == pageIndex) p.copy(widgets = p.widgets + Templates.widget(type)) else p })
        }
        close()
    }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        TabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text(tr("ویجت‌های نما", "Nama widgets")) })
            Tab(tab == 1, { tab = 1 }, text = { Text(tr("ویجت برنامه‌ها", "App widgets")) })
        }
        if (tab == 0) {
            LazyColumn(Modifier.heightIn(max = 560.dp).navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
                GROUPS.forEach { (g, names) ->
                    item { Text(tr(names.first, names.second), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp)) }
                    items(WidgetType.entries.filter { it.group == g }) { t ->
                        Row(Modifier.fillMaxWidth().clickable { add(t) }.padding(horizontal = 20.dp, vertical = 11.dp)) {
                            Text("＋ ", color = MaterialTheme.colorScheme.primary)
                            Text(tr(t.fa, t.en), fontSize = 15.sp)
                        }
                    }
                }
            }
        } else {
            val providers by produceState(emptyList<AppWidgetProviderInfo>()) {
                value = withContext(Dispatchers.IO) {
                    try {
                        AppWidgetManager.getInstance(ctx).getInstalledProviders()
                            .sortedBy { it.provider.packageName }
                    } catch (e: Exception) { emptyList() }
                }
            }
            if (providers.isEmpty()) Text(tr("در حال بارگذاری…", "Loading…"), Modifier.padding(20.dp))
            LazyColumn(Modifier.heightIn(max = 560.dp).navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(providers, key = { it.provider.flattenToString() + it.profile.hashCode() }) { p ->
                    val label = remember(p) { try { p.loadLabel(ctx.packageManager) } catch (e: Exception) { p.provider.className } }
                    val app = remember(p) { Nama.apps.byPackage(p.provider.packageName)?.label ?: p.provider.packageName }
                    val preview = remember(p) {
                        try { p.loadPreviewImage(ctx, ctx.resources.displayMetrics.densityDpi)?.toBitmap(240, 140)?.asImageBitmap() } catch (e: Exception) { null }
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { onPickSystem(p); close() }.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (preview != null) Image(preview, null, Modifier.size(88.dp, 52.dp).clip(RoundedCornerShape(8.dp)))
                        else Box(Modifier.size(88.dp, 52.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(label, fontSize = 14.sp, maxLines = 2)
                            Text(app, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WidgetMenuSheet(ctrl: HomeController, pageIndex: Int, w: WidgetInstance, onRemoveSystem: (Int) -> Unit) {
    val close = { ctrl.widgetMenu = null }
    val spaces by Nama.store.spaces.flow.collectAsState()
    fun update(f: (WidgetInstance) -> WidgetInstance?) {
        Nama.store.changeLayout(tr("ویرایش ویجت", "Edit widget")) { l ->
            l.copy(pages = l.pages.mapIndexed { i, p ->
                if (i != pageIndex) p else p.copy(widgets = p.widgets.mapNotNull { if (it.id == w.id) f(it) else it })
            })
        }
    }
    fun move(delta: Int) {
        Nama.store.changeLayout(tr("جابه‌جایی ویجت", "Move widget")) { l ->
            l.copy(pages = l.pages.mapIndexed { i, p ->
                if (i != pageIndex) p else {
                    val list = p.widgets.toMutableList()
                    val idx = list.indexOfFirst { it.id == w.id }
                    val to = (idx + delta).coerceIn(0, list.lastIndex)
                    if (idx >= 0 && to != idx) { val x = list.removeAt(idx); list.add(to, x) }
                    p.copy(widgets = list)
                }
            })
        }
    }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
            SheetTitle(tr(w.type.fa, w.type.en))
            SheetAction("⬆", tr("بالاتر", "Move up")) { move(-1) }
            SheetAction("⬇", tr("پایین‌تر", "Move down")) { move(1) }
            if (w.type != WidgetType.SYSTEM) {
                SheetAction("⇔", if (w.size == WidgetSize.SMALL) tr("پهن", "Make wide") else tr("کوچک (نصف عرض)", "Make small (half width)")) {
                    update { it.copy(size = if (it.size == WidgetSize.SMALL) WidgetSize.WIDE else WidgetSize.SMALL) }
                }
            } else {
                Text(tr("ارتفاع", "Height"), Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
                Slider(
                    value = (if (w.heightDp > 0) w.heightDp else 160).toFloat(), valueRange = 80f..480f,
                    onValueChange = { v -> update { it.copy(heightDp = v.toInt()) } },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
            Text(tr("فقط در این فضاها نشان بده (هیچ = همیشه)", "Show only in these spaces (none = always)"), Modifier.padding(horizontal = 20.dp, vertical = 6.dp), fontSize = 13.sp)
            Box(Modifier.padding(horizontal = 16.dp)) {
                ChipsSelector(listOf(SpaceEngine.BASE_ID) + spaces.map { it.id }, w.spaces, { id ->
                    if (id == SpaceEngine.BASE_ID) tr("بدون فضا", "No space") else spaces.firstOrNull { it.id == id }?.let { "${it.icon} ${it.name}" } ?: id
                }) { id -> update { it.copy(spaces = if (id in it.spaces) it.spaces - id else it.spaces + id) } }
            }
            SheetAction("🗑", tr("حذف ویجت", "Remove widget"), danger = true) {
                if (w.type == WidgetType.SYSTEM && w.appWidgetId >= 0) onRemoveSystem(w.appWidgetId)
                update { null }
                close()
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Spaces
// ------------------------------------------------------------------------------------------------

@Composable
fun SpaceSwitcherSheet(ctrl: HomeController) {
    val ctx = LocalContext.current
    val spaces by Nama.store.spaces.flow.collectAsState()
    val active by Nama.spaces.active.collectAsState()
    val settings by Nama.store.settings.flow.collectAsState()
    val close = { ctrl.spaceSwitcher = false }
    fun choose(id: String?) {
        val leavingLocked = active?.overrides?.locked == true && id != active?.id
        if (leavingLocked) {
            ctrl.authenticate(tr("خروج از فضای ${active?.name}", "Leave ${active?.name}")) { Nama.spaces.setManual(id); close() }
        } else {
            Nama.spaces.setManual(id); close()
        }
    }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
            SheetTitle(tr("فضاها", "Spaces"), tr("فعلی: ", "Now: ") + (active?.let { "${it.icon} ${it.name}" } ?: tr("بدون فضا", "No space")) +
                if (settings.manualSpaceId == null) tr(" · خودکار", " · automatic") else tr(" · دستی", " · manual"))
            SheetAction("🤖", tr("خودکار (بر اساس جا و زمان)", "Automatic (by place and time)")) { choose(null) }
            SheetAction("○", tr("بدون فضا (چیدمان پایه)", "No space (base layout)")) { choose(SpaceEngine.BASE_ID) }
            HorizontalDivider()
            spaces.filter { it.enabled }.forEach { sp ->
                SheetAction(sp.icon, sp.name + if (active?.id == sp.id) "  ✓" else "") { choose(sp.id) }
            }
            SheetAction("✎", tr("مدیریت فضاها…", "Manage spaces…")) {
                if (active?.overrides?.locked == true) ctrl.authenticate(tr("تنظیمات", "Settings")) {
                    ctx.startActivity(android.content.Intent(ctx, SettingsActivity::class.java).putExtra("route", "spaces"))
                } else ctx.startActivity(android.content.Intent(ctx, SettingsActivity::class.java).putExtra("route", "spaces"))
                close()
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ------------------------------------------------------------------------------------------------
// New apps inbox, hidden apps, smart sort, history
// ------------------------------------------------------------------------------------------------

@Composable
fun InboxSheet(ctrl: HomeController, env: HomeEnv) {
    val close = { ctrl.inboxOpen = false }
    val list = env.apps.values.filter { !it.pref.reviewed && !it.pref.hidden }
    if (list.isEmpty()) { close(); return }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SheetTitle(tr("برنامه‌های تازه", "New apps"), tr("برای هر کدام جا پیشنهاد شده؛ با یک لمس تأیید کن.", "A place is suggested for each; confirm with one tap."))
        LazyColumn(Modifier.heightIn(max = 520.dp).navigationBarsPadding()) {
            items(list, key = { it.key }) { e ->
                val cat = Nama.categoryName(e.category)
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIconImage(e, 40.dp, env.shape)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.label, fontWeight = FontWeight.Medium)
                            Text(tr("پیشنهاد: پوشه «$cat»", "Suggested: folder \"$cat\""), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        fun done() = Nama.store.updatePref(e.key) { it.copy(reviewed = true) }
                        Button({ ctrl.putInFolder(e.key, cat, 1.coerceAtMost(Nama.store.layout.value.pages.lastIndex)); done() }) { Text(tr("پوشه $cat", "Folder $cat"), fontSize = 12.sp) }
                        OutlinedButton({ ctrl.addToHome(e.key, 0); done() }) { Text(tr("صفحه اول", "First page"), fontSize = 12.sp) }
                        OutlinedButton({ done() }) { Text(tr("فقط در کشو", "Drawer only"), fontSize = 12.sp) }
                        TextButton({ Nama.store.updatePref(e.key) { it.copy(reviewed = true, hidden = true) } }) { Text(tr("پنهان", "Hide"), fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

@Composable
fun HiddenAppsSheet(ctrl: HomeController, env: HomeEnv) {
    val view = LocalView.current
    val close = { ctrl.hiddenAppsOpen = false }
    val list = env.apps.values.filter { it.pref.hidden }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SheetTitle(tr("برنامه‌های مخفی", "Hidden apps"), tr("لمس: باز کردن · لمس طولانی: نمایش دوباره", "Tap: open · Long press: unhide"))
        if (list.isEmpty()) Text(tr("هیچ برنامه‌ای مخفی نیست.", "No hidden apps."), Modifier.padding(20.dp))
        LazyVerticalGrid(GridCells.Fixed(4), Modifier.heightIn(max = 480.dp).navigationBarsPadding(), contentPadding = PaddingValues(12.dp)) {
            items(list, key = { it.key }) { e ->
                Column(
                    Modifier.combinedClickable(
                        onClick = { close(); ctrl.doLaunch(e, view) },
                        onLongClick = { Nama.store.updatePref(e.key) { it.copy(hidden = false) } }
                    ).padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppIconImage(e, 46.dp, env.shape)
                    Text(e.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun SmartSortDialog(ctrl: HomeController, env: HomeEnv) {
    val result by produceState<SmartSort.Result?>(null) {
        value = withContext(Dispatchers.Default) {
            val lastUsed = Nama.usage.lastUsed()
            SmartSort.plan(Nama.store.layout.value, env.apps.values.toList(), lastUsed)
        }
    }
    AlertDialog(
        onDismissRequest = { ctrl.smartSortOpen = false },
        title = { Text(tr("مرتب‌سازی خودکار", "Auto-sort")) },
        text = {
            val r = result
            if (r == null) Text(tr("در حال بررسی برنامه‌ها…", "Looking at your apps…"))
            else Text(
                tr(
                    "${num(r.apps)} برنامه در ${num(r.folders)} پوشه بر اساس دسته و قانون‌های شما چیده می‌شوند. صفحه اول، ویجت‌ها و داک دست نمی‌خورند. هر وقت خواستی از «تاریخچه چیدمان» برگرد.",
                    "${r.apps} apps go into ${r.folders} folders by category and your rules. Page 1, widgets and the dock stay. You can undo from layout history."
                )
            )
        },
        confirmButton = {
            TextButton(enabled = result != null, onClick = {
                val r = result ?: return@TextButton
                r.prefChanges.forEach { (k, f) -> Nama.store.updatePref(k, f) }
                Nama.store.changeLayout(tr("مرتب‌سازی خودکار", "Auto-sort")) { r.layout }
                ctrl.smartSortOpen = false
            }) { Text(tr("مرتب کن", "Sort")) }
        },
        dismissButton = { TextButton({ ctrl.smartSortOpen = false }) { Text(tr("انصراف", "Cancel")) } }
    )
}

@Composable
fun HistorySheet(ctrl: HomeController) {
    val history by Nama.store.history.flow.collectAsState()
    val close = { ctrl.historyOpen = false }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SheetTitle(tr("تاریخچه چیدمان", "Layout history"), tr("برگشت به هر نسخه قبلی صفحه اصلی", "Go back to any earlier home layout"))
        if (history.isEmpty()) Text(tr("هنوز تغییری ثبت نشده.", "No changes yet."), Modifier.padding(20.dp))
        LazyColumn(Modifier.heightIn(max = 520.dp).navigationBarsPadding()) {
            items(history) { snap ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        Nama.store.changeLayout(tr("بازگشت به نسخه قبل", "Restore")) { snap.layout }
                        close()
                    }.padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("قبل از: ", "Before: ") + snap.label)
                        Text(ago(snap.at), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(tr("برگرد", "Restore"), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

fun ago(at: Long): String {
    val m = (System.currentTimeMillis() - at) / 60_000
    return when {
        m < 1 -> tr("همین الان", "just now")
        m < 60 -> num(m) + tr(" دقیقه پیش", " min ago")
        m < 60 * 24 -> num(m / 60) + tr(" ساعت پیش", " h ago")
        else -> num(m / 1440) + tr(" روز پیش", " days ago")
    }
}

// ------------------------------------------------------------------------------------------------
// Friction gate
// ------------------------------------------------------------------------------------------------

@Composable
fun GateDialog(ctrl: HomeController, req: GateRequest) {
    val e = req.entry
    val delayOn = Friction.has(req.friction, Friction.DELAY) || req.focus || req.overLimit
    val askOn = Friction.has(req.friction, Friction.ASK_INTENTION)
    val typeOn = Friction.has(req.friction, Friction.TYPE_SENTENCE)
    val total = when {
        Friction.has(req.friction, Friction.DELAY) -> 10
        req.focus || req.overLimit -> 5
        else -> 0
    }
    var left by remember { mutableIntStateOf(total) }
    LaunchedEffect(req) { while (left > 0) { delay(1000); left-- } }
    var reason by remember { mutableStateOf<String?>(null) }
    val sentence = tr("من آگاهانه ${e.label} را باز می‌کنم", "I am opening ${e.label} on purpose")
    var typed by remember { mutableStateOf("") }
    val ready = left == 0 && (!askOn || (reason != null && reason != "habit")) && (!typeOn || typed.trim() == sentence)
    AlertDialog(
        onDismissRequest = { ctrl.gate = null },
        title = { Text(e.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (req.focus) Text(tr("در حالت تمرکز هستی. این برنامه جزو برنامه‌های مجاز نیست.", "You're in focus mode. This app isn't on the allowed list."))
                if (req.overLimit) Text(tr("امروز ${num(req.usedMinutes)} دقیقه از آن استفاده کرده‌ای (سقف: ${num(e.pref.dailyLimitMinutes)} دقیقه).", "You've used it ${req.usedMinutes} min today (limit ${e.pref.dailyLimitMinutes})."))
                if (delayOn && left > 0) {
                    Text(tr("یک نفس عمیق… ${num(left)}", "Take a breath… $left"), fontSize = 22.sp, fontWeight = FontWeight.Light, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                if (askOn) {
                    Text(tr("برای چی بازش می‌کنی؟", "Why are you opening it?"), fontWeight = FontWeight.Medium)
                    val reasons = listOf(
                        "msg" to tr("پیام مهم", "An important message"),
                        "work" to tr("کار", "Work"),
                        "break" to tr("استراحت کوتاه", "A short break"),
                        "habit" to tr("فقط از روی عادت", "Just habit")
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        reasons.forEach { (id, label) -> FilterChip(reason == id, { reason = id }, label = { Text(label) }) }
                    }
                    if (reason == "habit") Text(tr("آفرین که صادقی 🙂 شاید الان وقتش نیست.", "Nice, honest 🙂 Maybe not now."), color = MaterialTheme.colorScheme.primary)
                }
                if (typeOn) {
                    Text(tr("برای باز کردن این جمله را تایپ کن:", "Type this sentence to open:"))
                    Text(sentence, fontWeight = FontWeight.Bold)
                    OutlinedTextField(typed, { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(enabled = ready, onClick = { ctrl.doLaunch(e, req.view) }) { Text(tr("باز کن", "Open")) } },
        dismissButton = { TextButton({ ctrl.gate = null }) { Text(tr("منصرف شدم", "Never mind")) } }
    )
}
