package ir.nama.launcher.ui.home

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.nama.core.AppCategory
import ir.nama.core.Friction
import ir.nama.core.SpaceEngine
import ir.nama.launcher.Nama
import ir.nama.launcher.data.AppEntry
import ir.nama.launcher.data.AppPref
import ir.nama.launcher.data.GridItem
import ir.nama.launcher.data.WidgetType
import ir.nama.launcher.logic.SmartSort
import ir.nama.launcher.num
import ir.nama.launcher.system.Actions
import ir.nama.launcher.tr
import ir.nama.launcher.ui.common.AppIconImage
import ir.nama.launcher.ui.common.ChoiceDialog
import ir.nama.launcher.ui.common.ConfirmDialog
import ir.nama.launcher.ui.common.TextInputDialog
import ir.nama.launcher.ui.settings.SettingsActivity
import ir.nama.launcher.ui.theme.LocalNamaStyle
import ir.nama.launcher.ui.widgets.AppWidgets
import ir.nama.launcher.ui.widgets.WSize
import ir.nama.launcher.ui.widgets.WidgetCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------------------------------------
// Shared pieces
// ------------------------------------------------------------------------------------------------

private val MenuShape = RoundedCornerShape(20.dp)

@Composable
private fun NamaDropdown(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = MenuShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.widthIn(min = 220.dp),
        content = content
    )
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(text, color = c, fontSize = 14.sp) },
        leadingIcon = { Icon(icon, null, tint = if (danger) c else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
    )
}

@Composable
private fun MenuDivider() = HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

@Composable
fun SheetTitle(text: String, sub: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 12.dp)) {
        Text(text, style = MaterialTheme.typography.titleLarge)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun SheetRow(icon: ImageVector, text: String, sub: String? = null, danger: Boolean = false, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val c = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (danger) c else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(text, color = c, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        trailing?.invoke()
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

fun sizeLabel(s: WSize) = num("${s.w}×${s.h}")

// ------------------------------------------------------------------------------------------------
// App pop-up menu (long press on an app: home, dock, drawer or folder)
// ------------------------------------------------------------------------------------------------

@Composable
fun AppMenu(
    ctrl: HomeController,
    env: HomeEnv,
    e: AppEntry,
    expanded: Boolean,
    onDismiss: () -> Unit,
    inDock: Boolean = false,
    itemId: String? = null,
    folderItemId: String? = null
) {
    if (!expanded) return
    val ctx = LocalContext.current
    val shortcuts = remember(e.key) { try { Nama.apps.shortcuts(e).take(4) } catch (_: Exception) { emptyList() } }
    val layout = Nama.store.layout.value
    val onHome = layout.pages.any { p -> p.items.any { it.app == e.key || (it.folder?.keys?.contains(e.key) == true) } }
    var folderPick by remember { mutableStateOf(false) }
    NamaDropdown(expanded = !folderPick, onDismiss = onDismiss) {
        shortcuts.forEach { sc ->
            val icon = remember(sc.id) {
                try { Nama.apps.shortcutIcon(sc, ctx.resources.displayMetrics.densityDpi)?.toBitmap(72, 72)?.asImageBitmap() } catch (_: Exception) { null }
            }
            DropdownMenuItem(
                text = { Text((sc.shortLabel ?: sc.longLabel ?: "").toString(), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    if (icon != null) Image(icon, null, Modifier.size(22.dp).clip(CircleShape))
                    else Icon(Icons.Outlined.OpenInNew, null, Modifier.size(20.dp))
                },
                onClick = { Nama.apps.startShortcut(sc); onDismiss() },
                contentPadding = PaddingValues(horizontal = 16.dp)
            )
        }
        if (shortcuts.isNotEmpty()) MenuDivider()
        when {
            folderItemId != null -> MenuItem(Icons.Outlined.Logout, tr("بیرون آوردن از پوشه", "Take out of folder")) {
                ctrl.removeFromFolder(folderItemId, e.key); onDismiss()
            }
            inDock -> MenuItem(Icons.Outlined.RemoveCircleOutline, tr("برداشتن از داک", "Remove from dock")) { ctrl.removeFromDock(e.key); onDismiss() }
            itemId != null -> MenuItem(Icons.Outlined.RemoveCircleOutline, tr("برداشتن از صفحه", "Remove from home")) { ctrl.remove(itemId); onDismiss() }
            !onHome -> MenuItem(Icons.Outlined.AddToHomeScreen, tr("افزودن به صفحه اصلی", "Add to home")) {
                ctrl.addApp(e.key, ctrl.currentPage.coerceAtLeast(0)); onDismiss()
            }
        }
        if (!inDock && folderItemId == null) MenuItem(Icons.Outlined.CreateNewFolder, tr("گذاشتن در پوشه", "Put in folder")) { folderPick = true }
        if (!inDock && e.key !in layout.dock && layout.dock.size < 5) MenuItem(Icons.Outlined.Dock, tr("افزودن به داک", "Add to dock")) { ctrl.addToDock(e.key); onDismiss() }
        MenuDivider()
        MenuItem(Icons.Outlined.Tune, tr("قفل، محدودیت و…", "Lock, limits…")) { ctrl.appSettings = e; onDismiss() }
        MenuItem(Icons.Outlined.VisibilityOff, tr("پنهان کردن", "Hide")) {
            Nama.store.updatePref(e.key) { it.copy(hidden = true) }
            Actions.toast(ctx, tr("پنهان شد؛ از ته کشوی برنامه‌ها برمی‌گردد", "Hidden; find it at the bottom of the app list"))
            onDismiss()
        }
        MenuItem(Icons.Outlined.Info, tr("اطلاعات برنامه", "App info")) { Actions.appInfo(ctx, e); onDismiss() }
        if (!e.isSystem) MenuItem(Icons.Outlined.Delete, tr("حذف از گوشی", "Uninstall"), danger = true) { Actions.uninstall(ctx, e); onDismiss() }
    }
    if (folderPick) FolderPickDialog(ctrl, e) { folderPick = false; onDismiss() }
}

@Composable
private fun FolderPickDialog(ctrl: HomeController, e: AppEntry, onDone: () -> Unit) {
    val folders = Nama.store.layout.value.pages.flatMap { p -> p.items.mapNotNull { it.folder?.name } }.distinct()
    var newName by remember { mutableStateOf(false) }
    val cat = Nama.categoryName(e.category)
    AlertDialog(
        onDismissRequest = onDone,
        icon = { Icon(Icons.Outlined.Folder, null) },
        title = { Text(tr("کدام پوشه؟", "Which folder?")) },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                item { PickRow(Icons.Outlined.CreateNewFolder, tr("پوشه جدید…", "New folder…")) { newName = true } }
                if (cat !in folders) item { PickRow(Icons.Outlined.AutoAwesome, cat) { ctrl.putInFolder(e.key, cat); onDone() } }
                items(folders) { f -> PickRow(Icons.Outlined.Folder, f) { ctrl.putInFolder(e.key, f); onDone() } }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDone) { Text(tr("انصراف", "Cancel")) } }
    )
    if (newName) TextInputDialog(tr("نام پوشه", "Folder name"), onDismiss = { newName = false }) { n ->
        if (n.isNotBlank()) { ctrl.putInFolder(e.key, n.trim()); onDone() }
    }
}

@Composable
private fun PickRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

// ------------------------------------------------------------------------------------------------
// Folder and widget pop-up menus
// ------------------------------------------------------------------------------------------------

@Composable
fun FolderMenu(ctrl: HomeController, item: GridItem, expanded: Boolean, onDismiss: () -> Unit) {
    if (!expanded) return
    val f = item.folder ?: return
    var rename by remember { mutableStateOf(false) }
    NamaDropdown(expanded = !rename, onDismiss = onDismiss) {
        MenuItem(Icons.Outlined.Edit, tr("تغییر نام", "Rename")) { rename = true }
        MenuItem(Icons.Outlined.AutoAwesome, if (f.smartCategory == null) tr("پوشه هوشمند (خودش پر شود)", "Smart folder (fills itself)") else tr("هوشمند نباشد", "Turn off smart")) {
            val cat = if (f.smartCategory == null) f.keys.mapNotNull { Nama.apps.byKey(it)?.category }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key else null
            ctrl.setFolderSmart(item.id, cat); onDismiss()
        }
        MenuItem(Icons.Outlined.FolderOpen, tr("باز کردن پوشه روی صفحه", "Ungroup")) { ctrl.ungroup(item.id); onDismiss() }
        MenuItem(Icons.Outlined.RemoveCircleOutline, tr("برداشتن از صفحه", "Remove from home"), danger = true) { ctrl.remove(item.id); onDismiss() }
    }
    if (rename) TextInputDialog(tr("نام پوشه", "Folder name"), f.name, onDismiss = { rename = false; onDismiss() }) { n ->
        if (n.isNotBlank()) ctrl.renameFolder(item.id, n.trim())
    }
}

@Composable
fun WidgetMenu(ctrl: HomeController, item: GridItem, expanded: Boolean, onDismiss: () -> Unit) {
    if (!expanded) return
    val w = item.widget ?: return
    val ctx = LocalContext.current
    var spacesOpen by remember { mutableStateOf(false) }
    val sizes = remember(w.type, ctrl.cols) {
        val base = if (w.type == WidgetType.SYSTEM) listOf(WSize(2, 1), WSize(2, 2), WSize(4, 1), WSize(4, 2), WSize(4, 3), WSize(4, 4))
        else WidgetCatalog.sizes(w.type)
        (base + WSize(item.w, item.h)).map { WSize(it.w.coerceAtMost(ctrl.cols), it.h.coerceAtMost(ctrl.rows)) }.distinct()
            .sortedWith(compareBy({ it.w * it.h }, { it.w }))
    }
    NamaDropdown(expanded = !spacesOpen, onDismiss = onDismiss) {
        Text(
            tr("اندازه", "Size"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        if (sizes.size > 1) {
            FlowRow(
                Modifier.padding(horizontal = 12.dp).widthIn(max = 260.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                sizes.forEach { sz ->
                    val sel = sz.w == item.w && sz.h == item.h
                    SizeChip(sz, sel) { if (!sel && ctrl.resize(item.id, sz)) onDismiss() }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        MenuDivider()
        MenuItem(Icons.Outlined.Layers, tr("نمایش فقط در فضاهای خاص", "Show only in some spaces")) { spacesOpen = true }
        MenuItem(Icons.Outlined.Delete, tr("حذف ویجت", "Remove widget"), danger = true) {
            if (w.type == WidgetType.SYSTEM && w.appWidgetId >= 0) AppWidgets.delete(ctx, w.appWidgetId)
            ctrl.remove(item.id); onDismiss()
        }
    }
    if (spacesOpen) WidgetSpacesDialog(ctrl, item) { spacesOpen = false; onDismiss() }
}

@Composable
fun SizeChip(sz: WSize, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) cs.primaryContainer else cs.surfaceContainerHighest)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniGrid(sz, 4, 14.dp, if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(sizeLabel(sz), fontSize = 12.sp, color = if (selected) cs.onPrimaryContainer else cs.onSurface)
    }
}

/** A tiny drawing of the block a widget takes on a 4-column grid. */
@Composable
fun MiniGrid(sz: WSize, cols: Int, height: Dp, color: Color) {
    val cell = height / 4
    Box(Modifier.size(cell * cols + 1.dp, height)) {
        Box(
            Modifier.size(cell * sz.w.coerceAtMost(cols), cell * sz.h.coerceAtMost(4))
                .clip(RoundedCornerShape(2.dp)).background(color)
        )
        Box(Modifier.matchParentSize().border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun WidgetSpacesDialog(ctrl: HomeController, item: GridItem, onDone: () -> Unit) {
    val spaces by Nama.store.spaces.flow.collectAsStateWithLifecycle()
    val chosen = item.widget?.spaces ?: emptySet()
    var sel by remember { mutableStateOf(chosen) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(tr("کجا نشان داده شود؟", "Where should it show?")) },
        text = {
            Column {
                Text(tr("اگر چیزی انتخاب نشود، همیشه دیده می‌شود.", "Nothing selected means always."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf(SpaceEngine.BASE_ID to tr("بدون فضا", "No space")) + spaces.filter { it.enabled }.map { it.id to "${it.icon} ${it.name}" }).forEach { (id, label) ->
                        FilterChip(id in sel, { sel = if (id in sel) sel - id else sel + id }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = { TextButton({ ctrl.updateWidget(item.id) { it.copy(spaces = sel) }; onDone() }) { Text(tr("ذخیره", "Save")) } },
        dismissButton = { TextButton(onDone) { Text(tr("انصراف", "Cancel")) } }
    )
}

// ------------------------------------------------------------------------------------------------
// Home menu (long press on an empty spot)
// ------------------------------------------------------------------------------------------------

@Composable
fun HomeMenuSheet(ctrl: HomeController) {
    val ctx = LocalContext.current
    val close = { ctrl.homeMenuOpen = false }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigTile(Icons.Outlined.Widgets, tr("ویجت‌ها", "Widgets"), Modifier.weight(1f)) { close(); ctrl.widgetPickerOpen = true }
                BigTile(Icons.Outlined.Wallpaper, tr("والپیپر", "Wallpaper"), Modifier.weight(1f)) {
                    close(); Actions.start(ctx, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), null))
                }
                BigTile(Icons.Outlined.Layers, tr("فضاها", "Spaces"), Modifier.weight(1f)) { close(); ctrl.spaceSwitcher = true }
                BigTile(Icons.Outlined.Settings, tr("تنظیمات", "Settings"), Modifier.weight(1f)) {
                    close(); ctx.startActivity(Intent(ctx, SettingsActivity::class.java))
                }
            }
            Spacer(Modifier.height(12.dp))
            SheetRow(Icons.Outlined.AutoAwesome, tr("مرتب‌سازی خودکار برنامه‌ها", "Auto-sort apps"), tr("برنامه‌ها در پوشه‌های دسته‌بندی چیده می‌شوند", "Apps go into category folders")) {
                close(); ctrl.smartSortOpen = true
            }
            SheetRow(Icons.Outlined.Undo, tr("برگرداندن آخرین تغییر", "Undo last change")) {
                if (!Nama.store.undo()) Actions.toast(ctx, tr("تغییری برای برگرداندن نیست", "Nothing to undo")); close()
            }
            SheetRow(Icons.Outlined.History, tr("تاریخچه چیدمان", "Layout history")) { close(); ctrl.historyOpen = true }
        }
    }
}

@Composable
private fun BigTile(icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHighest).clickable(onClick = onClick).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ------------------------------------------------------------------------------------------------
// Widget picker (full screen, like Android's)
// ------------------------------------------------------------------------------------------------

@Composable
fun WidgetPicker(ctrl: HomeController, onPickSystem: (AppWidgetProviderInfo) -> Unit) {
    val ctx = LocalContext.current
    val close = { ctrl.widgetPickerOpen = false }
    BackHandler { close() }
    var tab by remember { mutableIntStateOf(0) }
    val cs = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize(), color = cs.surfaceContainerLow) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(close) { Icon(Icons.Outlined.Close, tr("بستن", "Close")) }
                Text(tr("ویجت‌ها", "Widgets"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            }
            Text(
                tr("اندازه را انتخاب کن و «افزودن» را بزن. بعداً با لمس طولانی روی ویجت می‌شود اندازه را عوض کرد.", "Pick a size and tap Add. Long-press a widget later to resize it."),
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(tab == 0, { tab = 0 }, label = { Text(tr("ویجت‌های نما", "Nama widgets")) }, leadingIcon = if (tab == 0) ({ Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)) }) else null)
                FilterChip(tab == 1, { tab = 1 }, label = { Text(tr("ویجت برنامه‌ها", "App widgets")) }, leadingIcon = if (tab == 1) ({ Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)) }) else null)
            }
            if (tab == 0) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WidgetCatalog.GROUPS.forEach { (g, names) ->
                        val types = WidgetType.entries.filter { it.group == g && it != WidgetType.SYSTEM }
                        if (types.isNotEmpty()) {
                            item(key = "h_$g") {
                                Text(tr(names.first, names.second), style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(start = 8.dp, top = 12.dp))
                            }
                            items(types, key = { it.name }) { t -> WidgetPickRow(ctrl, t, close) }
                        }
                    }
                }
            } else {
                val providers by produceState<List<AppWidgetProviderInfo>?>(null) {
                    value = withContext(Dispatchers.IO) {
                        try {
                            AppWidgetManager.getInstance(ctx).installedProviders.filter { it.provider.packageName != ctx.packageName }
                                .sortedBy { Nama.apps.byPackage(it.provider.packageName)?.label ?: it.provider.packageName }
                        } catch (e: Exception) { emptyList() }
                    }
                }
                val g = ctrl.grids[ctrl.currentPage.coerceAtLeast(0)]
                val density = ctx.resources.displayMetrics.density
                when {
                    providers == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    providers!!.isEmpty() -> Text(tr("برنامه‌ای ویجت ندارد.", "No app widgets found."), Modifier.padding(24.dp))
                    else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(providers!!, key = { it.provider.flattenToString() + it.profile.hashCode() }) { p ->
                            val label = remember(p) { try { p.loadLabel(ctx.packageManager) } catch (e: Exception) { p.provider.className.substringAfterLast('.') } }
                            val app = remember(p) { Nama.apps.byPackage(p.provider.packageName) }
                            val preview = remember(p) {
                                try { (p.loadPreviewImage(ctx, ctx.resources.displayMetrics.densityDpi) ?: p.loadIcon(ctx, ctx.resources.displayMetrics.densityDpi))?.toBitmap(320, 200)?.asImageBitmap() } catch (e: Exception) { null }
                            }
                            val span = remember(p, g) {
                                if (g != null) AppWidgets.spanFor(p, g.cellW / density, g.cellH / density, density, ctrl.cols, ctrl.rows) else null
                            }
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh)
                                    .clickable { close(); onPickSystem(p) }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(96.dp, 64.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                                    if (preview != null) Image(preview, null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(app?.label ?: p.provider.packageName, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
                                }
                                if (span != null) Text(sizeLabel(span), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPickRow(ctrl: HomeController, t: WidgetType, close: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sizes = remember(t) { WidgetCatalog.sizes(t) }
    var size by remember { mutableStateOf(sizes.first()) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(WidgetCatalog.icon(t), null, tint = cs.onPrimaryContainer, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tr(t.fa, t.en), style = MaterialTheme.typography.titleMedium)
                Text(WidgetCatalog.description(t), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sizes.forEach { sz -> SizeChip(sz, sz == size) { size = sz } }
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = {
                close()
                ctrl.addWidgetType(t, size)
            }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(tr("افزودن", "Add"))
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Spaces
// ------------------------------------------------------------------------------------------------

@Composable
fun SpaceSwitcherSheet(ctrl: HomeController) {
    val ctx = LocalContext.current
    val spaces by Nama.store.spaces.flow.collectAsStateWithLifecycle()
    val active by Nama.spaces.active.collectAsStateWithLifecycle()
    val settings by Nama.store.settings.flow.collectAsStateWithLifecycle()
    val close = { ctrl.spaceSwitcher = false }
    val cs = MaterialTheme.colorScheme
    fun choose(id: String?) {
        val leavingLocked = active?.overrides?.locked == true && id != active?.id
        if (leavingLocked) ctrl.authenticate(tr("خروج از فضای ${active?.name}", "Leave ${active?.name}")) { Nama.spaces.setManual(id); close() }
        else { Nama.spaces.setManual(id); close() }
    }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            SheetTitle(
                tr("فضاها", "Spaces"),
                tr("چیدمان و برنامه‌ها با جا و زمان عوض می‌شوند", "Layout and apps change with place and time")
            )
            SpaceRow("✦", tr("خودکار", "Automatic"), tr("بر اساس وای‌فای، ساعت و باتری", "By Wi-Fi, time and battery"), settings.manualSpaceId == null) { choose(null) }
            SpaceRow("○", tr("بدون فضا", "No space"), tr("چیدمان اصلی", "Base layout"), settings.manualSpaceId == SpaceEngine.BASE_ID) { choose(SpaceEngine.BASE_ID) }
            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 6.dp), color = cs.outlineVariant)
            spaces.filter { it.enabled }.forEach { sp ->
                SpaceRow(sp.icon, sp.name, if (active?.id == sp.id) tr("فعال", "Active") else null, active?.id == sp.id && settings.manualSpaceId != null || active?.id == sp.id) { choose(sp.id) }
            }
            SheetRow(Icons.Outlined.Edit, tr("مدیریت فضاها", "Manage spaces")) {
                val open = { ctx.startActivity(Intent(ctx, SettingsActivity::class.java).putExtra("route", "spaces")) }
                if (active?.overrides?.locked == true) ctrl.authenticate(tr("تنظیمات", "Settings")) { open() } else open()
                close()
            }
        }
    }
}

@Composable
private fun SpaceRow(icon: String, name: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) cs.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) { Text(icon, fontSize = 18.sp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = if (selected) cs.onSecondaryContainer else cs.onSurface)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Outlined.Check, null, tint = cs.primary)
    }
}

// ------------------------------------------------------------------------------------------------
// Folder (centered card, like Pixel)
// ------------------------------------------------------------------------------------------------

@Composable
fun FolderDialog(ctrl: HomeController, env: HomeEnv, target: FolderTarget) {
    val layout by Nama.store.layout.flow.collectAsStateWithLifecycle()
    val item = layout.pages.getOrNull(target.pageIndex)?.items?.firstOrNull { it.id == target.itemId }
    val folder = item?.folder
    if (item == null || folder == null) { LaunchedEffect(Unit) { ctrl.openFolder = null }; return }
    val view = LocalView.current
    val cs = MaterialTheme.colorScheme
    var rename by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val keys = folderKeys(item, env)
    Dialog(onDismissRequest = { ctrl.openFolder = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(32.dp)).background(cs.surfaceContainer)
                .padding(top = 20.dp, bottom = 12.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    folder.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(enabled = !env.locked) { rename = true }
                )
                if (folder.smartCategory != null) {
                    AssistChip(onClick = {}, label = { Text(tr("هوشمند", "Smart"), fontSize = 12.sp) }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)) })
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                GridCells.Fixed(4), Modifier.heightIn(max = 440.dp).padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(keys, key = { it }) { k ->
                    val e = env.apps[k] ?: return@items
                    Box {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).combinedClickable(
                                onClick = { ctrl.launch(e, view) },
                                onLongClick = { if (!env.locked) menuFor = k }
                            ).padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AppIconImage(e, 52.dp, env.shape, grayscale = env.gray(e), dim = env.dim(e), dot = env.dot(e))
                            Text(
                                e.label, style = MaterialTheme.typography.labelMedium, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
                            )
                        }
                        AppMenu(ctrl, env, e, expanded = menuFor == k, onDismiss = { menuFor = null }, folderItemId = if (folder.smartCategory == null) item.id else null, itemId = null)
                    }
                }
            }
            if (keys.isEmpty()) Text(tr("پوشه خالی است", "This folder is empty"), Modifier.padding(20.dp), color = cs.onSurfaceVariant)
        }
    }
    if (rename) TextInputDialog(tr("نام پوشه", "Folder name"), folder.name, onDismiss = { rename = false }) { n ->
        if (n.isNotBlank()) ctrl.renameFolder(item.id, n.trim())
    }
}

// ------------------------------------------------------------------------------------------------
// Per-app settings in Nama
// ------------------------------------------------------------------------------------------------

@Composable
fun AppSettingsDialog(e: AppEntry, onDismiss: () -> Unit) {
    val prefs by Nama.store.appPrefs.flow.collectAsStateWithLifecycle()
    val p = prefs[e.key] ?: AppPref()
    var rename by remember { mutableStateOf(false) }
    var aliases by remember { mutableStateOf(false) }
    var catPick by remember { mutableStateOf(false) }
    fun upd(f: (AppPref) -> AppPref) = Nama.store.updatePref(e.key, f)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AppIconImage(e, 44.dp, Nama.settings.iconShape ?: ir.nama.launcher.data.IconShape.CIRCLE) },
        title = { Text(e.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SwitchRow(tr("قفل با اثر انگشت یا رمز گوشی", "Lock with fingerprint or PIN"), p.locked) { v -> upd { it.copy(locked = v) } }
                SwitchRow(tr("پنهان", "Hidden"), p.hidden) { v -> upd { it.copy(hidden = v) } }
                SwitchRow(tr("آرشیو", "Archived"), p.archived) { v -> upd { it.copy(archived = v) } }
                Text(
                    tr("کمتر بازش کنم", "Help me use it less"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                )
                FrictionRow(tr("در جستجو پیدا نشود", "Hide from search"), p.friction, Friction.HIDE_FROM_SEARCH) { v -> upd { it.copy(friction = v) } }
                FrictionRow(tr("هر بار جایش عوض شود", "Move it every time"), p.friction, Friction.SHUFFLE) { v -> upd { it.copy(friction = v) } }
                FrictionRow(tr("۱۰ ثانیه مکث قبل از باز شدن", "10-second pause first"), p.friction, Friction.DELAY) { v -> upd { it.copy(friction = v) } }
                FrictionRow(tr("بپرسد «برای چی؟»", "Ask why"), p.friction, Friction.ASK_INTENTION) { v -> upd { it.copy(friction = v) } }
                FrictionRow(tr("تایپ یک جمله برای باز کردن", "Type a sentence to open"), p.friction, Friction.TYPE_SENTENCE) { v -> upd { it.copy(friction = v) } }
                FrictionRow(tr("سیاه‌وسفید بعد از سقف روزانه", "Grayscale after daily limit"), p.friction, Friction.GRAYSCALE_AFTER_LIMIT) { v -> upd { it.copy(friction = v) } }
                Text(
                    tr("سقف روزانه: ", "Daily limit: ") + if (p.dailyLimitMinutes == 0) tr("ندارد", "none") else num(p.dailyLimitMinutes) + tr(" دقیقه", " min"),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp)
                )
                Slider(value = p.dailyLimitMinutes.toFloat(), onValueChange = { v -> upd { it.copy(dailyLimitMinutes = (v / 5).toInt() * 5) } }, valueRange = 0f..240f)
                if (p.dailyLimitMinutes > 0 && !Nama.usage.hasPermission()) {
                    Text(tr("برای سقف زمانی، «دسترسی آمار استفاده» را در تنظیمات نما بدهید.", "Grant usage access in Nama settings."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SettingLinkRow(Icons.Outlined.Edit, tr("نام نمایشی", "Display name"), p.label ?: e.originalLabel) { rename = true }
                SettingLinkRow(Icons.Outlined.Search, tr("نام‌های دیگر برای جستجو", "Search aliases"), p.aliases.joinToString("، ").ifEmpty { "—" }) { aliases = true }
                SettingLinkRow(Icons.Outlined.Category, tr("دسته", "Category"), Nama.categoryName(p.category ?: e.category)) { catPick = true }
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
        ChoiceDialog(tr("دسته", "Category"), cats.map { Nama.categoryName(it) }, cats.indexOf(p.category ?: e.category), { catPick = false }) { i ->
            upd { it.copy(category = cats[i]) }
        }
    }
}

@Composable
private fun SettingLinkRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onChange)
    }
}

@Composable
private fun FrictionRow(text: String, flags: Int, flag: Int, onChange: (Int) -> Unit) {
    val on = Friction.has(flags, flag)
    val toggle = { onChange(if (on) flags and flag.inv() else flags or flag) }
    Row(Modifier.fillMaxWidth().clickable { toggle() }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(on, { toggle() })
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

// ------------------------------------------------------------------------------------------------
// Hidden apps, auto-sort, history
// ------------------------------------------------------------------------------------------------

@Composable
fun HiddenAppsSheet(ctrl: HomeController, env: HomeEnv) {
    val view = LocalView.current
    val close = { ctrl.hiddenAppsOpen = false }
    val list = env.apps.values.filter { it.pref.hidden }.sortedBy { it.label }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SheetTitle(tr("برنامه‌های پنهان", "Hidden apps"), tr("لمس: باز کردن · لمس طولانی: نمایش دوباره", "Tap: open · Long press: unhide"))
        if (list.isEmpty()) Text(tr("هیچ برنامه‌ای پنهان نیست.", "No hidden apps."), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyVerticalGrid(GridCells.Fixed(4), Modifier.heightIn(max = 480.dp).navigationBarsPadding(), contentPadding = PaddingValues(12.dp)) {
            items(list, key = { it.key }) { e ->
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).combinedClickable(
                        onClick = { close(); ctrl.doLaunch(e, view) },
                        onLongClick = { Nama.store.updatePref(e.key) { it.copy(hidden = false) } }
                    ).padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppIconImage(e, 48.dp, env.shape)
                    Text(e.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
fun SmartSortDialog(ctrl: HomeController, env: HomeEnv) {
    val result by produceState<SmartSort.Result?>(null) {
        value = withContext(Dispatchers.Default) {
            SmartSort.plan(Nama.store.layout.value, env.apps.values.toList(), Nama.usage.lastUsed(), ctrl.cols, ctrl.rows)
        }
    }
    AlertDialog(
        onDismissRequest = { ctrl.smartSortOpen = false },
        icon = { Icon(Icons.Outlined.AutoAwesome, null) },
        title = { Text(tr("مرتب‌سازی خودکار", "Auto-sort")) },
        text = {
            val r = result
            if (r == null) Text(tr("در حال بررسی برنامه‌ها…", "Looking at your apps…"))
            else Text(
                tr(
                    "${num(r.apps)} برنامه در ${num(r.folders)} پوشه بر اساس دسته و قانون‌های شما چیده می‌شوند. صفحه اول، ویجت‌ها و داک دست نمی‌خورند و از «تاریخچه چیدمان» برمی‌گردد.",
                    "${r.apps} apps go into ${r.folders} folders by category and your rules. Page 1, widgets and the dock stay. Undo any time from layout history."
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
    val history by Nama.store.history.flow.collectAsStateWithLifecycle()
    val close = { ctrl.historyOpen = false }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SheetTitle(tr("تاریخچه چیدمان", "Layout history"), tr("برگشت به هر نسخه قبلی صفحه اصلی", "Go back to any earlier home layout"))
        if (history.isEmpty()) Text(tr("هنوز تغییری ثبت نشده.", "No changes yet."), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.heightIn(max = 520.dp).navigationBarsPadding()) {
            items(history) { snap ->
                SheetRow(Icons.Outlined.History, tr("قبل از: ", "Before: ") + snap.label, ago(snap.at), trailing = {
                    Text(tr("برگرد", "Restore"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }) {
                    Nama.store.changeLayout(tr("بازگشت به نسخه قبل", "Restore")) { snap.layout }
                    close()
                }
            }
        }
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
    // Compare normalized text so a missing half-space or Arabic ي/ك never blocks the owner.
    val typedOk = ir.nama.core.PersianText.normalize(typed).replace(" ", "") == ir.nama.core.PersianText.normalize(sentence).replace(" ", "")
    val ready = left == 0 && (!askOn || (reason != null && reason != "habit")) && (!typeOn || typedOk)
    AlertDialog(
        onDismissRequest = { ctrl.gate = null },
        icon = { AppIconImage(e, 48.dp, Nama.settings.iconShape ?: ir.nama.launcher.data.IconShape.CIRCLE) },
        title = { Text(e.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (req.focus) Text(tr("در حالت تمرکز هستی و این برنامه جزو برنامه‌های مجاز نیست.", "You're in focus mode and this app isn't allowed."))
                if (req.overLimit) Text(tr("امروز ${num(req.usedMinutes)} دقیقه از آن استفاده کرده‌ای (سقف: ${num(e.pref.dailyLimitMinutes)} دقیقه).", "You've used it ${req.usedMinutes} min today (limit ${e.pref.dailyLimitMinutes})."))
                if (delayOn && left > 0) {
                    Text(tr("یک نفس عمیق… ${num(left)}", "Take a breath… $left"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Light, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                if (askOn) {
                    Text(tr("برای چی بازش می‌کنی؟", "Why are you opening it?"), style = MaterialTheme.typography.titleSmall)
                    val reasons = listOf(
                        "msg" to tr("پیام مهم", "An important message"),
                        "work" to tr("کار", "Work"),
                        "break" to tr("استراحت کوتاه", "A short break"),
                        "habit" to tr("فقط از روی عادت", "Just habit")
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        reasons.forEach { (id, label) -> FilterChip(reason == id, { reason = id }, label = { Text(label) }) }
                    }
                    if (reason == "habit") Text(tr("آفرین که صادقی. شاید الان وقتش نیست.", "Nice and honest. Maybe not now."), color = MaterialTheme.colorScheme.primary)
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
