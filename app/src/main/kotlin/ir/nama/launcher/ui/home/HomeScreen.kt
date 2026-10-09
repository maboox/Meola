package ir.nama.launcher.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.nama.core.Friction
import ir.nama.core.GridMath
import ir.nama.core.Space
import ir.nama.launcher.Nama
import ir.nama.launcher.data.AppEntry
import ir.nama.launcher.data.GestureAction
import ir.nama.launcher.data.GridItem
import ir.nama.launcher.data.HomePage
import ir.nama.launcher.data.IconMode
import ir.nama.launcher.data.IconShape
import ir.nama.launcher.data.NetState
import ir.nama.launcher.data.Settings
import ir.nama.launcher.system.Actions
import ir.nama.launcher.tr
import ir.nama.launcher.ui.common.AppIconImage
import ir.nama.launcher.ui.theme.LocalNamaStyle
import ir.nama.launcher.ui.theme.NamaStyle
import ir.nama.launcher.ui.theme.wallpaperText
import ir.nama.launcher.ui.widgets.WScope
import ir.nama.launcher.ui.widgets.WSize
import ir.nama.launcher.ui.widgets.WidgetContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Everything the home screen needs to draw one frame, gathered once at the root. */
data class HomeEnv(
    val settings: Settings,
    val space: Space?,
    val apps: Map<String, AppEntry>,
    val counts: Map<String, Int>,
    val usage: Map<String, Long>,
    val net: NetState,
    val shape: IconShape,
    val reduceMotion: Boolean,
    val iconMode: IconMode
) {
    fun visible(e: AppEntry) = Nama.isVisible(e, space)
    fun dim(e: AppEntry) = settings.nationalNetMode && net == NetState.NATIONAL_ONLY && e.foreign
    fun gray(e: AppEntry): Boolean {
        val limit = e.pref.dailyLimitMinutes
        return limit > 0 && Friction.has(e.pref.friction, Friction.GRAYSCALE_AFTER_LIMIT) && (usage[e.packageName] ?: 0L) >= limit * 60_000L
    }
    fun dot(e: AppEntry) = settings.showNotificationDots && (counts[e.packageName] ?: 0) > 0
    val locked get() = Nama.isRestricted(space) || space?.overrides?.locked == true

    /** Whether a grid item is shown in the current space. */
    fun itemVisible(i: GridItem): Boolean = when {
        i.app != null -> apps[i.app]?.let { visible(it) } == true
        i.folder != null -> folderKeys(i, this).isNotEmpty() || i.folder.smartCategory != null
        i.widget != null -> i.widget.spaces.isEmpty() || (space?.id ?: "base") in i.widget.spaces
        else -> false
    }
}

fun folderKeys(item: GridItem, env: HomeEnv): List<String> {
    val f = item.folder ?: return emptyList()
    val base = if (f.smartCategory != null) {
        val stats = Nama.store.appStats.value
        env.apps.values.filter { it.category == f.smartCategory }.sortedByDescending { stats[it.key]?.launches ?: 0 }.map { it.key }
    } else f.keys
    return base.filter { k -> env.apps[k]?.let { env.visible(it) } == true }
}

@Composable
fun HomeScreen(ctrl: HomeController, env: HomeEnv) {
    if (Nama.isRestricted(env.space)) {
        RestrictedHome(ctrl, env)
        return
    }
    val layout by Nama.store.layout.flow.collectAsStateWithLifecycle()
    val newsOn = env.settings.newsPageEnabled && env.space?.overrides?.hideNews != true
    val offset = if (newsOn) 1 else 0
    val pageCount = layout.pages.size + offset
    val pager = rememberPagerState(initialPage = offset) { pageCount }
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    var rootWidth by remember { mutableStateOf(0f) }

    LaunchedEffect(pager, offset) {
        snapshotFlow { pager.currentPage }.collect { ctrl.currentPage = it - offset }
    }
    LaunchedEffect(Unit) {
        ctrl.homePressed.collect { if (pager.currentPage != offset) pager.animateScrollToPage(offset) }
    }
    // While dragging, holding an item at the screen edge flips to the next page (adding one if needed).
    val dragging = ctrl.drag != null
    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect
        val edge = with(density) { 28.dp.toPx() }
        var since = 0L
        while (ctrl.drag != null) {
            val d = ctrl.drag ?: break
            val dir = when {
                d.pointer.x < edge -> if (rtl) 1 else -1
                rootWidth > 0 && d.pointer.x > rootWidth - edge -> if (rtl) -1 else 1
                else -> 0
            }
            if (dir == 0) since = 0L
            else if (since == 0L) since = System.currentTimeMillis()
            else if (System.currentTimeMillis() - since > 550) {
                val target = pager.currentPage + dir
                if (target >= offset) {
                    if (target >= pager.pageCount) { ctrl.addEmptyPage(); delay(60) }
                    pager.animateScrollToPage(target.coerceAtMost(pager.pageCount - 1))
                }
                since = 0L
                delay(450)
            }
            delay(40)
        }
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { rootWidth = it.size.width.toFloat() }) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                beyondViewportPageCount = 2,
                userScrollEnabled = !dragging,
                key = { i -> if (newsOn && i == 0) "news" else layout.pages.getOrNull(i - offset)?.id ?: "p$i" }
            ) { index ->
                if (newsOn && index == 0) NewsPage(ctrl, env)
                else layout.pages.getOrNull(index - offset)?.let { GridPage(ctrl, env, it, index - offset) }
            }
            PageIndicator(pageCount, pager.currentPage, newsOn) { i -> scope.launch { pager.animateScrollToPage(i) } }
            Dock(ctrl, env, layout.dock)
            SearchBar(ctrl)
        }
        DragLayer(ctrl, env)
    }
}

// ------------------------------------------------------------------------------------------------
// Page indicator, dock, search bar
// ------------------------------------------------------------------------------------------------

@Composable
private fun PageIndicator(count: Int, current: Int, newsOn: Boolean, onClick: (Int) -> Unit) {
    val s = LocalNamaStyle.current
    if (count <= 1) { Spacer(Modifier.height(10.dp)); return }
    Row(Modifier.fillMaxWidth().height(18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val active = i == current
            if (newsOn && i == 0) {
                Icon(Icons.Outlined.Article, tr("اخبار", "News"), tint = s.onWallpaper.copy(alpha = if (active) 1f else 0.55f), modifier = Modifier.padding(horizontal = 4.dp).size(12.dp).clickable { onClick(0) })
            } else {
                val w by animateFloatAsState(if (active) 16f else 6f, label = "dot")
                Box(
                    Modifier.padding(horizontal = 3.dp).height(6.dp).width(w.dp).clip(RoundedCornerShape(3.dp))
                        .background(s.onWallpaper.copy(alpha = if (active) 0.95f else 0.45f)).clickable { onClick(i) }
                )
            }
        }
    }
}

@Composable
private fun Dock(ctrl: HomeController, env: HomeEnv, dock: List<String>) {
    val view = LocalView.current
    val keys = (env.space?.overrides?.dockApps ?: dock).mapNotNull { env.apps[it] }.filter { env.visible(it) }
    if (keys.isEmpty()) return
    val size = env.settings.iconSizeDp.coerceIn(44, 64).dp
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEach { e ->
            Box {
                Box(
                    Modifier.clip(CircleShape).combinedClickable(
                        onClick = { ctrl.launch(e, view) },
                        onLongClick = {
                            if (!env.locked) {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                ctrl.dockMenu = e.key
                            }
                        }
                    )
                ) { AppIconImage(e, size, env.shape, dim = env.dim(e), grayscale = env.gray(e), dot = env.dot(e)) }
                AppMenu(ctrl, env, e, expanded = ctrl.dockMenu == e.key, onDismiss = { ctrl.dockMenu = null }, inDock = true)
            }
        }
    }
}

@Composable
private fun SearchBar(ctrl: HomeController) {
    val s = LocalNamaStyle.current
    val bg = if (s.minimal) Color(0xFF161616) else s.surfaceHigh.copy(alpha = 0.94f)
    val fg = s.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp).height(52.dp)
            .clip(RoundedCornerShape(26.dp)).background(bg).clickable { ctrl.openSearch() }.padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(12.dp))
        Icon(Icons.Outlined.Search, null, tint = s.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(tr("جستجو یا فرمان", "Search or command"), color = fg, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable { ctrl.drawerOpen = true },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.Apps, tr("همه برنامه‌ها", "All apps"), tint = fg, modifier = Modifier.size(22.dp)) }
    }
}

// ------------------------------------------------------------------------------------------------
// The grid
// ------------------------------------------------------------------------------------------------

@Composable
private fun GridPage(ctrl: HomeController, env: HomeEnv, page: HomePage, pageIndex: Int) {
    val view = LocalView.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val cols = ctrl.cols
    val rows = ctrl.rows
    val envNow by rememberUpdatedState(env)
    val pageNow by rememberUpdatedState(page)
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 2.dp)) {
        val cellW = maxWidth / cols
        val cellH = maxHeight / rows
        var origin by remember { mutableStateOf(Offset.Zero) }
        val density = LocalDensity.current
        Box(
            Modifier.fillMaxSize()
                .onGloballyPositioned { c ->
                    val b = c.boundsInWindow()
                    origin = Offset(b.left, b.top)
                    ctrl.grids[pageIndex] = GridGeom(b, b.width / cols, b.height / rows, cols, rows, rtl)
                }
                .pointerInput(pageIndex, cols, rows) {
                    detectTapGestures(
                        onLongPress = { pos ->
                            val g = ctrl.grids[pageIndex] ?: return@detectTapGestures
                            val cell = g.cellAt(pos + origin) ?: return@detectTapGestures
                            val occupied = GridMath.at(pageNow.items.filter { envNow.itemVisible(it) }.map { it.cell() }, cell.first, cell.second) != null
                            if (!occupied && !envNow.locked) {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                ctrl.homeMenuOpen = true
                            }
                        },
                        onDoubleTap = { runGesture(ctrl, envNow.settings.doubleTapAction, view) }
                    )
                }
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onVerticalDrag = { _, d -> total += d },
                        onDragEnd = {
                            val th = 60.dp.toPx()
                            if (total < -th) runGesture(ctrl, envNow.settings.swipeUpAction, view)
                            else if (total > th) runGesture(ctrl, envNow.settings.swipeDownAction, view)
                        }
                    )
                },
            contentAlignment = AbsoluteAlignment.TopLeft
        ) {
            page.items.filter { env.itemVisible(it) }.forEach { item ->
                val vx = if (rtl) cols - item.x - item.w else item.x
                val xPx = with(density) { (cellW * vx).roundToPx() }
                val yPx = with(density) { (cellH * item.y).roundToPx() }
                Box(
                    Modifier.absoluteOffset { IntOffset(xPx, yPx) }
                        .size(cellW * item.w, cellH * item.h)
                ) {
                    GridItemView(ctrl, env, item, pageIndex, cellW * item.w, cellH * item.h)
                }
            }
        }
    }
}

@Composable
private fun GridItemView(ctrl: HomeController, env: HomeEnv, item: GridItem, pageIndex: Int, width: Dp, height: Dp) {
    val view = LocalView.current
    var topLeft by remember { mutableStateOf(Offset.Zero) }
    var sizePx by remember { mutableStateOf(Size.Zero) }
    val itemNow by rememberUpdatedState(item)
    val envNow by rememberUpdatedState(env)
    val beingDragged = ctrl.drag?.item?.id == item.id
    val pressed = ctrl.itemMenu == (pageIndex to item.id)
    val scale by animateFloatAsState(if (pressed) 1.04f else 1f, label = "press")
    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { topLeft = it.positionInWindow(); sizePx = Size(it.size.width.toFloat(), it.size.height.toFloat()) }
            .alpha(if (beingDragged) 0f else 1f)
            .scale(scale)
            .pointerInput(item.id, pageIndex) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var cancelled = false
                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        waitForUpOrCancellation().also { if (it == null) cancelled = true }
                    }
                    val it0 = itemNow
                    if (up != null) {
                        if (!it0.isWidget) { up.consume(); onItemTap(ctrl, envNow, it0, pageIndex, view) }
                        return@awaitEachGesture
                    }
                    if (cancelled || envNow.locked) return@awaitEachGesture
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    ctrl.itemMenu = pageIndex to it0.id
                    var dragging = false
                    val slop = viewConfiguration.touchSlop * 1.5f
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        ch.consume()
                        if (!ch.pressed) break
                        val window = topLeft + ch.position
                        if (!dragging && (ch.position - down.position).getDistance() > slop) {
                            dragging = true
                            ctrl.itemMenu = null
                            ctrl.drag = DragState(it0, pageIndex, window, down.position, sizePx)
                        }
                        if (dragging) ctrl.drag = ctrl.drag?.copy(pointer = window)
                    }
                    if (dragging) ctrl.drop()
                }
            }
    ) {
        ItemContent(ctrl, env, item, pageIndex, width, height)
        when {
            item.app != null -> env.apps[item.app]?.let { e ->
                AppMenu(ctrl, env, e, expanded = pressed, onDismiss = { ctrl.itemMenu = null }, itemId = item.id)
            }
            item.folder != null -> FolderMenu(ctrl, item, expanded = pressed, onDismiss = { ctrl.itemMenu = null })
            item.widget != null -> WidgetMenu(ctrl, item, expanded = pressed, onDismiss = { ctrl.itemMenu = null })
        }
    }
}

private fun onItemTap(ctrl: HomeController, env: HomeEnv, item: GridItem, pageIndex: Int, view: android.view.View) {
    when {
        item.app != null -> env.apps[item.app]?.let { ctrl.launch(it, view) }
        item.folder != null -> ctrl.openFolder = FolderTarget(pageIndex, item.id)
    }
}

/** Draws an item: app icon with label, folder, or widget. */
@Composable
fun ItemContent(ctrl: HomeController, env: HomeEnv, item: GridItem, pageIndex: Int, width: Dp, height: Dp) {
    val s = LocalNamaStyle.current
    val iconSize = env.settings.iconSizeDp.coerceIn(40, 72).dp.let { if (it > width - 8.dp) width - 8.dp else it }
    when {
        item.app != null -> {
            val e = env.apps[item.app] ?: return
            IconWithLabel(env, iconSize, if (e.pref.locked) "🔒 " + e.label else e.label) {
                AppIconImage(e, iconSize, env.shape, grayscale = env.gray(e), dim = env.dim(e), dot = env.dot(e))
            }
        }
        item.folder != null -> IconWithLabel(env, iconSize, item.folder.name) { FolderIcon(item, env, iconSize, s) }
        item.widget != null -> Box(Modifier.fillMaxSize().padding(5.dp)) {
            WidgetContent(WScope(ctrl, env, item.widget, item.id, WSize(item.w, item.h), (width - 10.dp).value, (height - 10.dp).value))
        }
    }
}

@Composable
private fun IconWithLabel(env: HomeEnv, iconSize: Dp, label: String, icon: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        icon()
        if (env.settings.showLabels) {
            Text(
                label, style = wallpaperText(12), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 5.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
fun FolderIcon(item: GridItem, env: HomeEnv, size: Dp, s: NamaStyle) {
    val keys = folderKeys(item, env).take(4)
    val bg = if (s.minimal) Color(0xFF1C1C1C) else s.surfaceHigh.copy(alpha = 0.92f)
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        val mini = size * 0.32f
        Column(verticalArrangement = Arrangement.spacedBy(size * 0.04f)) {
            keys.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(size * 0.04f)) {
                    row.forEach { k -> env.apps[k]?.let { AppIconImage(it, mini, env.shape) } }
                }
            }
        }
        if (keys.any { k -> env.apps[k]?.let { env.dot(it) } == true }) {
            Box(Modifier.align(Alignment.TopEnd).size(size / 4.5f).clip(CircleShape).background(s.accent))
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Drag layer: the lifted item and where it will land
// ------------------------------------------------------------------------------------------------

@Composable
private fun DragLayer(ctrl: HomeController, env: HomeEnv) {
    val d = ctrl.drag ?: return
    val s = LocalNamaStyle.current
    val density = LocalDensity.current
    val target = ctrl.dropTarget(d)
    val geom = target?.let { ctrl.grids[it.page] }
    Box(Modifier.fillMaxSize(), contentAlignment = AbsoluteAlignment.TopLeft) {
        if (target != null && geom != null) {
            val r: Rect = if (target.mergeInto != null) geom.rect(target.x, target.y, 1, 1) else geom.rect(target.x, target.y, d.item.w, d.item.h)
            Box(
                Modifier.absoluteOffset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                    .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
                    .padding(4.dp)
                    .clip(RoundedCornerShape(s.corner))
                    .background(s.onWallpaper.copy(alpha = if (target.mergeInto != null) 0.28f else 0.14f))
                    .border(1.5.dp, s.onWallpaper.copy(alpha = 0.5f), RoundedCornerShape(s.corner))
            )
        }
        val tl = d.pointer - d.grab
        Box(
            Modifier.absoluteOffset { IntOffset(tl.x.roundToInt(), tl.y.roundToInt()) }
                .size(with(density) { d.size.width.toDp() }, with(density) { d.size.height.toDp() })
                .scale(1.06f).alpha(0.92f)
        ) {
            ItemContent(ctrl, env, d.item, d.fromPage, with(density) { d.size.width.toDp() }, with(density) { d.size.height.toDp() })
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Kids / restricted spaces: a simple grid of allowed apps
// ------------------------------------------------------------------------------------------------

@Composable
private fun RestrictedHome(ctrl: HomeController, env: HomeEnv) {
    val s = LocalNamaStyle.current
    val view = LocalView.current
    val apps = env.apps.values.filter { env.visible(it) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(s.surfaceHigh).clickable { ctrl.spaceSwitcher = true }.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Lock, null, tint = s.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(env.space?.name ?: "", color = s.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(GridCells.Fixed(3), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(apps, key = { it.key }) { e ->
                Column(Modifier.clickable { ctrl.launch(e, view) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIconImage(e, 72.dp, env.shape)
                    Text(e.label, style = wallpaperText(14), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

fun runGesture(ctrl: HomeController, a: GestureAction, view: android.view.View) {
    val ctx = ctrl.activity
    val space = Nama.spaces.active.value
    val locked = Nama.isRestricted(space) || space?.overrides?.locked == true
    if (locked && (a == GestureAction.SETTINGS || a == GestureAction.EDIT_HOME)) return
    when (a) {
        GestureAction.NONE -> return
        GestureAction.DRAWER -> ctrl.drawerOpen = true
        GestureAction.SEARCH -> ctrl.openSearch()
        GestureAction.NOTIFICATIONS -> Actions.expandNotifications(ctx)
        GestureAction.LOCK_SCREEN -> Actions.lockScreen(ctx)
        GestureAction.SPACE_SWITCHER -> ctrl.spaceSwitcher = true
        GestureAction.SETTINGS -> ctx.startActivity(android.content.Intent(ctx, ir.nama.launcher.ui.settings.SettingsActivity::class.java))
        GestureAction.EDIT_HOME -> ctrl.homeMenuOpen = true
    }
    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
}

/** Fades and slides an overlay in from the bottom. */
@Composable
fun BottomOverlay(visible: Boolean, reduceMotion: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) fadeIn() else slideInVertically { it / 4 } + fadeIn(),
        exit = if (reduceMotion) fadeOut() else slideOutVertically { it / 4 } + fadeOut()
    ) { content() }
}
