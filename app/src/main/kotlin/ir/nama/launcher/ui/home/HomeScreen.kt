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
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.nama.core.Space
import ir.nama.launcher.Nama
import ir.nama.launcher.data.AppEntry
import ir.nama.launcher.data.GestureAction
import ir.nama.launcher.data.HomeItem
import ir.nama.launcher.data.HomePage
import ir.nama.launcher.data.IconShape
import ir.nama.launcher.data.NetState
import ir.nama.launcher.data.NotificationRepo
import ir.nama.launcher.data.Settings
import ir.nama.launcher.data.WidgetSize
import ir.nama.launcher.num
import ir.nama.launcher.system.Actions
import ir.nama.launcher.tr
import ir.nama.launcher.ui.common.AppIconImage
import ir.nama.launcher.ui.theme.LocalNamaStyle
import ir.nama.launcher.ui.theme.NamaStyle
import ir.nama.launcher.ui.theme.Styles
import ir.nama.launcher.ui.widgets.WidgetHost
import kotlinx.coroutines.launch
import kotlin.math.abs
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
    val reduceMotion: Boolean
) {
    fun visible(e: AppEntry) = Nama.isVisible(e, space)
    fun dim(e: AppEntry) = settings.nationalNetMode && net == NetState.NATIONAL_ONLY && e.foreign
    fun gray(e: AppEntry): Boolean {
        val limit = e.pref.dailyLimitMinutes
        return limit > 0 && ir.nama.core.Friction.has(e.pref.friction, ir.nama.core.Friction.GRAYSCALE_AFTER_LIMIT) &&
            (usage[e.packageName] ?: 0L) >= limit * 60_000L
    }
    fun dot(e: AppEntry) = settings.showNotificationDots && (counts[e.packageName] ?: 0) > 0
}

@Composable
fun HomeScreen(ctrl: HomeController, env: HomeEnv) {
    val layout by Nama.store.layout.flow.collectAsStateWithLifecycle()
    val style = LocalNamaStyle.current
    val newsOn = env.settings.newsPageEnabled && env.space?.overrides?.hideNews != true && !Nama.isRestricted(env.space)
    val offset = if (newsOn) 1 else 0
    val pageCount = layout.pages.size + offset
    val pager = rememberPagerState(initialPage = offset) { pageCount }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        ctrl.homePressed.collect {
            if (pager.currentPage != offset) pager.animateScrollToPage(offset)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            beyondViewportPageCount = 1,
            key = { i -> if (newsOn && i == 0) "news" else layout.pages.getOrNull(i - offset)?.id ?: "p$i" }
        ) { index ->
            if (newsOn && index == 0) {
                NewsPage(ctrl, env)
            } else {
                val pi = index - offset
                val page = layout.pages.getOrNull(pi)
                if (page != null) HomePageView(ctrl, env, page, pi)
            }
        }
        if (pageCount > 1) PageDots(pageCount, pager.currentPage, newsOn) { i -> scope.launch { pager.animateScrollToPage(i) } }
        Column(Modifier.pointerInput(Unit) {
            var total = 0f
            detectVerticalDragGestures(
                onDragStart = { total = 0f },
                onVerticalDrag = { _, d -> total += d },
                onDragEnd = { if (total < -60f) ctrl.drawerOpen = true }
            )
        }) {
            SearchPill(ctrl, style)
            Dock(ctrl, env, layout.dock)
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, newsOn: Boolean, onClick: (Int) -> Unit) {
    val s = LocalNamaStyle.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            val active = i == current
            Box(
                Modifier.padding(horizontal = 3.dp)
                    .size(if (active) 8.dp else 6.dp)
                    .clip(if (newsOn && i == 0) RoundedCornerShape(2.dp) else CircleShape)
                    .background(if (active) s.accent else s.subText.copy(alpha = 0.45f))
                    .clickable { onClick(i) }
            )
        }
    }
}

@Composable
private fun SearchPill(ctrl: HomeController, s: NamaStyle) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { SearchBox(ctrl, s) }
        Spacer(Modifier.width(8.dp))
        // Always-visible way into the app drawer (swiping up also works at the end of a page).
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(if (s.textOnly) s.text.copy(alpha = 0.06f) else s.card)
                .then(if (s.textOnly) Modifier else Modifier.border(1.dp, s.cardBorder, CircleShape))
                .clickable { ctrl.drawerOpen = true },
            contentAlignment = Alignment.Center
        ) { Text("⋮⋮", color = s.text, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun SearchBox(ctrl: HomeController, s: NamaStyle) {
    Box(
        Modifier.fillMaxWidth().height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (s.textOnly) s.text.copy(alpha = 0.06f) else s.card)
            .then(if (s.textOnly) Modifier else Modifier.border(1.dp, s.cardBorder, RoundedCornerShape(20.dp)))
            .clickable { ctrl.openSearch() },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            "⌕  " + tr("جستجو یا فرمان… «زنگ ۷»", "Search or command… \"alarm 7\""),
            color = s.subText, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun Dock(ctrl: HomeController, env: HomeEnv, dock: List<String>) {
    val s = LocalNamaStyle.current
    val override = env.space?.overrides?.dockApps
    val keys = (override ?: dock).mapNotNull { env.apps[it] }.filter { env.visible(it) }
    if (keys.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        return
    }
    val view = LocalView.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(26.dp))
            .then(if (s.textOnly) Modifier else Modifier.background(s.card).border(1.dp, s.cardBorder, RoundedCornerShape(26.dp)))
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEach { e ->
            if (s.textOnly) {
                Text(
                    e.label, color = s.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).combinedClickable(
                        onClick = { ctrl.launch(e, view) },
                        onLongClick = { ctrl.appMenu = AppMenuTarget(e, MenuSource.Dock) }
                    ).padding(vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                Box(
                    Modifier.combinedClickable(
                        onClick = { ctrl.launch(e, view) },
                        onLongClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            ctrl.appMenu = AppMenuTarget(e, MenuSource.Dock)
                        }
                    )
                ) {
                    AppIconImage(e, env.settings.iconSizeDp.dp, env.shape, dim = env.dim(e), grayscale = env.gray(e), dot = env.dot(e))
                }
            }
        }
    }
}

/** Collects the on-screen rectangles of items so empty-space gestures can tell them apart. */
class HitRegistry {
    val rects = mutableStateMapOf<String, Rect>()
    fun hits(windowPos: Offset) = rects.values.any { it.contains(windowPos) }
}

@Composable
private fun HomePageView(ctrl: HomeController, env: HomeEnv, page: HomePage, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val view = LocalView.current
    val registry = remember { HitRegistry() }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val threshold = with(density) { 70.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    val envNow by rememberUpdatedState(env)

    // Swipe up at the bottom / down at the top of the page triggers the configured gestures.
    val nested = remember(env.settings.swipeUpAction, env.settings.swipeDownAction) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) pull += available.y
                return Offset.Zero
            }
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                val p = pull
                pull = 0f
                if (p < -threshold) runGesture(ctrl, env.settings.swipeUpAction, view)
                else if (p > threshold) runGesture(ctrl, env.settings.swipeDownAction, view)
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { coords = it }
            .pointerInput(env.settings.doubleTapAction, pageIndex) {
                detectTapGestures(
                    onLongPress = { pos ->
                        val c = coords ?: return@detectTapGestures
                        val locked = Nama.isRestricted(envNow.space) || envNow.space?.overrides?.locked == true
                        if (!registry.hits(c.localToWindow(pos)) && !locked) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            ctrl.homeMenuPage = pageIndex
                        }
                    },
                    onDoubleTap = { pos ->
                        val c = coords ?: return@detectTapGestures
                        if (!registry.hits(c.localToWindow(pos))) runGesture(ctrl, env.settings.doubleTapAction, view)
                    }
                )
            }
    ) {
        Column(
            Modifier.fillMaxSize().nestedScroll(nested).verticalScroll(scroll).padding(horizontal = 12.dp),
            verticalArrangement = if (env.settings.oneHandMode) Arrangement.Bottom else Arrangement.Top
        ) {
            if (env.settings.oneHandMode) Spacer(Modifier.height(120.dp))
            if (pageIndex == 0) PageHeader(ctrl, env, registry)
            WidgetsArea(ctrl, env, page, pageIndex, registry)
            Spacer(Modifier.height(8.dp))
            val items = remember(page.items, env) { visibleItems(page.items, env, pageIndex) }
            if (s.textOnly) TextList(ctrl, env, items, pageIndex, registry)
            else IconGrid(ctrl, env, items, pageIndex, registry)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Items to show: hidden apps removed, folders keep visible apps, pinned space apps first on page 1. */
private fun visibleItems(items: List<HomeItem>, env: HomeEnv, pageIndex: Int): List<HomeItem> {
    val out = mutableListOf<HomeItem>()
    if (pageIndex == 0) {
        env.space?.overrides?.pinnedApps?.forEach { k ->
            val e = env.apps[k]
            if (e != null && env.visible(e)) out += HomeItem.App("pin_$k", k)
        }
    }
    val pinned = out.map { (it as HomeItem.App).key }.toSet()
    for (item in items) {
        when (item) {
            is HomeItem.App -> {
                val e = env.apps[item.key] ?: continue
                if (env.visible(e) && item.key !in pinned) out += item
            }
            is HomeItem.Folder -> {
                val keys = folderKeys(item, env)
                if (keys.isNotEmpty()) out += item
            }
        }
    }
    return out
}

fun folderKeys(f: HomeItem.Folder, env: HomeEnv): List<String> {
    val base = if (f.smartCategory != null) {
        val stats = Nama.store.appStats.value
        env.apps.values.filter { it.category == f.smartCategory }
            .sortedByDescending { stats[it.key]?.launches ?: 0 }.map { it.key }
    } else f.keys
    return base.filter { k -> env.apps[k]?.let { env.visible(it) } == true }
}

fun runGesture(ctrl: HomeController, a: GestureAction, view: android.view.View) {
    val ctx = ctrl.activity
    val space = Nama.spaces.active.value
    val locked = Nama.isRestricted(space) || space?.overrides?.locked == true
    if (locked && (a == GestureAction.SETTINGS || a == GestureAction.EDIT_HOME)) return
    when (a) {
        GestureAction.NONE -> {}
        GestureAction.DRAWER -> ctrl.drawerOpen = true
        GestureAction.SEARCH -> ctrl.openSearch()
        GestureAction.NOTIFICATIONS -> Actions.expandNotifications(ctx)
        GestureAction.LOCK_SCREEN -> Actions.lockScreen(ctx)
        GestureAction.SPACE_SWITCHER -> ctrl.spaceSwitcher = true
        GestureAction.SETTINGS -> ctx.startActivity(android.content.Intent(ctx, ir.nama.launcher.ui.settings.SettingsActivity::class.java))
        GestureAction.EDIT_HOME -> ctrl.homeMenuPage = 0
    }
    if (a != GestureAction.NONE) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
}

@Composable
private fun PageHeader(ctrl: HomeController, env: HomeEnv, registry: HitRegistry) {
    val s = LocalNamaStyle.current
    val occasion = remember { ir.nama.core.IranCalendar.occasion(java.time.LocalDate.now(), env.settings.hijriOffset) }
    val greeting = if (env.settings.seasonalThemes) Styles.occasionGreeting(occasion, Nama.isFa) else null
    val apps = env.apps.values
    val inbox = apps.count { !it.pref.reviewed && !it.pref.hidden }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (env.settings.showSpaceBanner || env.space != null) {
                Chip(
                    text = env.space?.let { "${it.icon} ${it.name}" } ?: tr("● بدون فضا", "● No space"),
                    s = s, registry = registry, id = "chip_space"
                ) { ctrl.spaceSwitcher = true }
            }
            if (greeting != null) Chip(greeting, s, registry, "chip_occasion") {}
            if (inbox > 0 && !Nama.isRestricted(env.space)) {
                Chip(tr("✦ ${num(inbox)} برنامه تازه", "✦ ${num(inbox)} new apps"), s, registry, "chip_inbox", highlight = true) { ctrl.inboxOpen = true }
            }
        }
        if (env.settings.focusUntil > System.currentTimeMillis()) {
            val left = (env.settings.focusUntil - System.currentTimeMillis()) / 60_000
            Chip(tr("◎ حالت تمرکز · ${num(left)} دقیقه مانده", "◎ Focus · ${num(left)} min left"), s, registry, "chip_focus") {
                Nama.store.settings.update { it.copy(focusUntil = 0L) }
            }
        }
        if (env.settings.nationalNetMode && env.net == NetState.NATIONAL_ONLY) {
            Chip(tr("🌐 اینترنت بین‌الملل در دسترس نیست؛ برنامه‌های خارجی کم‌رنگ شدند", "🌐 International internet is down; foreign apps are dimmed"), s, registry, "chip_net") {}
        }
    }
}

@Composable
private fun Chip(text: String, s: NamaStyle, registry: HitRegistry, id: String, highlight: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        color = if (highlight) s.accent else s.text,
        fontSize = 12.sp,
        maxLines = 2,
        modifier = Modifier
            .onGloballyPositioned { registry.rects[id] = it.boundsInWindow() }
            .clip(RoundedCornerShape(14.dp))
            .background(s.card)
            .border(1.dp, if (highlight) s.accent.copy(alpha = 0.6f) else s.cardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun WidgetsArea(ctrl: HomeController, env: HomeEnv, page: HomePage, pageIndex: Int, registry: HitRegistry) {
    val activeId = env.space?.id ?: "base"
    val widgets = page.widgets.filter { it.spaces.isEmpty() || activeId in it.spaces }
    if (widgets.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var i = 0
        while (i < widgets.size) {
            val w = widgets[i]
            val next = widgets.getOrNull(i + 1)
            if (w.size == WidgetSize.SMALL && next?.size == WidgetSize.SMALL) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).onGloballyPositioned { registry.rects[w.id] = it.boundsInWindow() }) { WidgetHost(ctrl, env, w, pageIndex) }
                    Box(Modifier.weight(1f).onGloballyPositioned { registry.rects[next.id] = it.boundsInWindow() }) { WidgetHost(ctrl, env, next, pageIndex) }
                }
                i += 2
            } else {
                Box(Modifier.fillMaxWidth().onGloballyPositioned { registry.rects[w.id] = it.boundsInWindow() }) { WidgetHost(ctrl, env, w, pageIndex) }
                i += 1
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Icon grid with long-press menu and drag-to-reorder / drop-on-icon-to-make-folder
// ------------------------------------------------------------------------------------------------

@Composable
private fun IconGrid(ctrl: HomeController, env: HomeEnv, items: List<HomeItem>, pageIndex: Int, registry: HitRegistry) {
    if (items.isEmpty()) return
    val s = LocalNamaStyle.current
    val cols = env.settings.columns.coerceIn(3, 6)
    val iconDp = env.settings.iconSizeDp.coerceIn(36, 72)
    val labelDp = if (env.settings.showLabels) 30 else 4
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val view = LocalView.current
    val currentEnv by rememberUpdatedState(env)
    val currentItems by rememberUpdatedState(items)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth.toFloat()
        val cellW = widthPx / cols
        val cellH = with(density) { (iconDp + labelDp + 14).dp.toPx() }
        val rows = (items.size + cols - 1) / cols
        var dragId by remember { mutableStateOf<String?>(null) }
        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        var pressedId by remember { mutableStateOf<String?>(null) }

        fun cellX(i: Int): Float { val c = i % cols; return if (rtl) widthPx - (c + 1) * cellW else c * cellW }
        fun cellY(i: Int): Float = (i / cols) * cellH

        Box(Modifier.fillMaxWidth().height(with(density) { (rows * cellH).toDp() })) {
            items.forEachIndexed { index, item ->
                val dragging = dragId == item.id
                val x = cellX(index) + if (dragging) dragOffset.x else 0f
                val y = cellY(index) + if (dragging) dragOffset.y else 0f
                val scale by animateFloatAsState(if (dragging || pressedId == item.id) 1.12f else 1f, label = "press")
                Box(
                    Modifier
                        .zIndex(if (dragging) 10f else 0f)
                        .width(with(density) { cellW.toDp() })
                        .height(with(density) { cellH.toDp() })
                        .offsetAbsolute(x.roundToInt(), y.roundToInt())
                        .onGloballyPositioned { registry.rects[item.id] = it.boundsInWindow() }
                        .pointerInput(item.id, index, items.size) {
                            // One detector for tap, long-press menu and drag, so a long press never also launches.
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var cancelled = false
                                val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    waitForUpOrCancellation().also { if (it == null) cancelled = true }
                                }
                                if (up != null) {
                                    up.consume()
                                    onTap(ctrl, currentEnv, item, pageIndex, view)
                                    return@awaitEachGesture
                                }
                                if (cancelled) return@awaitEachGesture
                                pressedId = item.id
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                var moved = false
                                var total = Offset.Zero
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) { ch.consume(); break }
                                    val d = ch.positionChange()
                                    ch.consume()
                                    if (item.id.startsWith("pin_")) continue
                                    total += d
                                    if (!moved && (abs(total.x) > 18f || abs(total.y) > 18f)) {
                                        moved = true
                                        dragId = item.id
                                    }
                                    if (moved) dragOffset = total
                                }
                                dragId = null; dragOffset = Offset.Zero; pressedId = null
                                if (!moved) openMenu(ctrl, currentEnv, item, pageIndex)
                                else drop(item, index, total, currentItems, pageIndex, cols, cellW, cellH, rtl, widthPx)
                            }
                        }
                        .scale(scale),
                    contentAlignment = Alignment.TopCenter
                ) {
                    GridCell(ctrl, env, item, pageIndex, iconDp, s)
                }
            }
        }
    }
}

private fun Modifier.offsetAbsolute(x: Int, y: Int): Modifier =
    this.absoluteOffset { IntOffset(x, y) }

private fun onTap(ctrl: HomeController, env: HomeEnv, item: HomeItem, pageIndex: Int, view: android.view.View) {
    when (item) {
        is HomeItem.App -> env.apps[item.key]?.let { ctrl.launch(it, view) }
        is HomeItem.Folder -> ctrl.openFolder = FolderTarget(pageIndex, item.id)
    }
}

private fun openMenu(ctrl: HomeController, env: HomeEnv, item: HomeItem, pageIndex: Int) {
    when (item) {
        is HomeItem.App -> env.apps[item.key]?.let {
            ctrl.appMenu = AppMenuTarget(it, if (item.id.startsWith("pin_")) MenuSource.Drawer else MenuSource.Home(pageIndex, item.id))
        }
        is HomeItem.Folder -> ctrl.openFolder = FolderTarget(pageIndex, item.id)
    }
}

/** Reorders or merges items after a drag. Works on the stored layout, not on the filtered list. */
private fun drop(
    item: HomeItem, fromIndex: Int, off: Offset, visible: List<HomeItem>, pageIndex: Int,
    cols: Int, cellW: Float, cellH: Float, rtl: Boolean, widthPx: Float
) {
    val c0 = fromIndex % cols
    val startX = if (rtl) widthPx - (c0 + 1) * cellW else c0 * cellW
    val cx = startX + cellW / 2 + off.x
    val cy = (fromIndex / cols) * cellH + cellH / 2 + off.y
    val colAbs = (cx / cellW).toInt().coerceIn(0, cols - 1)
    val col = if (rtl) cols - 1 - colAbs else colAbs
    val row = (cy / cellH).toInt().coerceAtLeast(0)
    val target = (row * cols + col).coerceIn(0, visible.lastIndex)
    if (target == fromIndex) return
    val targetItem = visible[target]
    // Distance from the target cell's center decides between "merge into folder" and "move".
    val tc = target % cols
    val tcx = (if (rtl) widthPx - (tc + 1) * cellW else tc * cellW) + cellW / 2
    val tcy = (target / cols) * cellH + cellH / 2
    val nearCenter = abs(cx - tcx) < cellW * 0.28f && abs(cy - tcy) < cellH * 0.28f
    val store = Nama.store
    if (nearCenter && item is HomeItem.App && !targetItem.id.startsWith("pin_")) {
        store.changeLayout(tr("ساخت پوشه", "Make folder")) { l ->
            l.copy(pages = l.pages.mapIndexed { i, p ->
                if (i != pageIndex) p else {
                    val without = p.items.filterNot { it.id == item.id }
                    p.copy(items = without.map { t ->
                        when {
                            t.id != targetItem.id -> t
                            t is HomeItem.Folder -> t.copy(keys = (t.keys - item.key) + item.key)
                            t is HomeItem.App -> HomeItem.Folder(HomeController.newId(), Nama.apps.byKey(t.key)?.category?.let { Nama.categoryName(it) } ?: tr("پوشه", "Folder"), listOf(t.key, item.key))
                            else -> t
                        }
                    })
                }
            })
        }
        return
    }
    store.changeLayout(tr("جابه‌جایی", "Move")) { l ->
        l.copy(pages = l.pages.mapIndexed { i, p ->
            if (i != pageIndex) p else {
                val list = p.items.toMutableList()
                val from = list.indexOfFirst { it.id == item.id }
                if (from < 0) return@mapIndexed p
                val moving = list.removeAt(from)
                val anchor = list.indexOfFirst { it.id == targetItem.id }
                val insertAt = when {
                    anchor < 0 -> list.size
                    target > fromIndex -> anchor + 1
                    else -> anchor
                }.coerceIn(0, list.size)
                list.add(insertAt, moving)
                p.copy(items = list)
            }
        })
    }
}

@Composable
private fun GridCell(ctrl: HomeController, env: HomeEnv, item: HomeItem, pageIndex: Int, iconDp: Int, s: NamaStyle) {
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (item) {
            is HomeItem.App -> {
                val e = env.apps[item.key]
                if (e != null) {
                    AppIconImage(e, iconDp.dp, env.shape, grayscale = env.gray(e), dim = env.dim(e), dot = env.dot(e))
                    if (env.settings.showLabels) Label(if (e.pref.locked) "🔒 " + e.label else e.label, s)
                }
            }
            is HomeItem.Folder -> {
                FolderIcon(item, env, iconDp, s)
                if (env.settings.showLabels) Label(item.name, s)
            }
        }
    }
}

@Composable
private fun Label(text: String, s: NamaStyle) {
    Text(
        text, color = s.iconLabel, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp)
    )
}

@Composable
fun FolderIcon(f: HomeItem.Folder, env: HomeEnv, iconDp: Int, s: NamaStyle) {
    val keys = folderKeys(f, env).take(4)
    val shape = RoundedCornerShape((iconDp / 4).dp)
    Box(
        Modifier.size(iconDp.dp).clip(shape).background(s.card).border(1.dp, s.cardBorder, shape).padding((iconDp / 9).dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            keys.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    row.forEach { k -> env.apps[k]?.let { AppIconImage(it, ((iconDp - iconDp / 4.5f) / 2 - 1).dp, env.shape) } }
                }
            }
        }
        val dots = keys.any { k -> env.apps[k]?.let { env.dot(it) } == true }
        if (dots) Box(Modifier.align(Alignment.TopEnd).size(7.dp).clip(CircleShape).background(s.accent))
    }
}

@Composable
private fun TextList(ctrl: HomeController, env: HomeEnv, items: List<HomeItem>, pageIndex: Int, registry: HitRegistry) {
    val s = LocalNamaStyle.current
    val view = LocalView.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEach { item ->
            val (title, sub) = when (item) {
                is HomeItem.App -> {
                    val e = env.apps[item.key] ?: return@forEach
                    val strike = env.gray(e) || ir.nama.core.Friction.has(e.pref.friction, ir.nama.core.Friction.DELAY)
                    (if (e.pref.locked) "🔒 " else "") + e.label to (if (strike) tr("اصطکاک روشن", "friction on") else null)
                }
                is HomeItem.Folder -> item.name + " ›" to null
            }
            Row(
                Modifier.fillMaxWidth()
                    .onGloballyPositioned { registry.rects[item.id] = it.boundsInWindow() }
                    .combinedClickable(
                        onClick = {
                            when (item) {
                                is HomeItem.App -> env.apps[item.key]?.let { ctrl.launch(it, view) }
                                is HomeItem.Folder -> ctrl.openFolder = FolderTarget(pageIndex, item.id)
                            }
                        },
                        onLongClick = { openMenu(ctrl, env, item, pageIndex) }
                    ).padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val e = (item as? HomeItem.App)?.let { env.apps[it.key] }
                Text(
                    title, color = if (e != null && (env.dim(e) || env.gray(e))) s.subText else s.text,
                    fontSize = 22.sp, fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (sub != null) Text("  $sub", color = s.subText, fontSize = 10.sp)
                if (e != null && env.dot(e)) Text("  •", color = s.accent, fontSize = 18.sp)
            }
        }
    }
}

/** Fades and slides an overlay in from the bottom. */
@Composable
fun BottomOverlay(visible: Boolean, reduceMotion: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) fadeIn() else slideInVertically { it / 3 } + fadeIn(),
        exit = if (reduceMotion) fadeOut() else slideOutVertically { it / 3 } + fadeOut()
    ) { content() }
}
