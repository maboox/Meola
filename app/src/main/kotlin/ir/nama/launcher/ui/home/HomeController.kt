package ir.nama.launcher.ui.home

import android.view.View
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import ir.nama.core.Cell
import ir.nama.core.Friction
import ir.nama.core.GridMath
import ir.nama.launcher.Nama
import ir.nama.launcher.data.AppEntry
import ir.nama.launcher.data.FolderData
import ir.nama.launcher.data.GridItem
import ir.nama.launcher.data.HomeLayout
import ir.nama.launcher.data.HomePage
import ir.nama.launcher.data.WidgetInstance
import ir.nama.launcher.data.WidgetType
import ir.nama.launcher.system.Actions
import ir.nama.launcher.tr
import ir.nama.launcher.ui.widgets.WSize
import ir.nama.launcher.ui.widgets.WidgetCatalog
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.UUID
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

data class GateRequest(val entry: AppEntry, val focus: Boolean, val friction: Int, val overLimit: Boolean, val usedMinutes: Long, val view: View?)
data class FolderTarget(val pageIndex: Int, val itemId: String)

/** Where a page's grid sits on screen, so drags can be mapped to cells. */
data class GridGeom(val bounds: Rect, val cellW: Float, val cellH: Float, val cols: Int, val rows: Int, val rtl: Boolean) {
    /** Grid cell under a window position. */
    fun cellAt(p: Offset): Pair<Int, Int>? {
        if (!bounds.contains(p)) return null
        val vx = floor((p.x - bounds.left) / cellW).toInt().coerceIn(0, cols - 1)
        val y = floor((p.y - bounds.top) / cellH).toInt().coerceIn(0, rows - 1)
        return (if (rtl) cols - 1 - vx else vx) to y
    }

    /** Cell for an item of width [w] whose top-left corner is at [topLeft] (window coordinates). */
    fun snap(topLeft: Offset, w: Int, h: Int): Pair<Int, Int> {
        val vx = ((topLeft.x - bounds.left) / cellW).roundToInt().coerceIn(0, cols - w)
        val y = ((topLeft.y - bounds.top) / cellH).roundToInt().coerceIn(0, rows - h)
        return (if (rtl) cols - w - vx else vx) to y
    }

    /** Window rectangle of a cell range. */
    fun rect(x: Int, y: Int, w: Int, h: Int): Rect {
        val vx = if (rtl) cols - x - w else x
        val left = bounds.left + vx * cellW
        val top = bounds.top + y * cellH
        return Rect(left, top, left + w * cellW, top + h * cellH)
    }
}

/** An item being dragged. [pointer] and [grab] are in window coordinates. */
data class DragState(val item: GridItem, val fromPage: Int, val pointer: Offset, val grab: Offset, val size: Size)

class HomeController(val activity: FragmentActivity) {
    var drawerOpen by mutableStateOf(false)
    var searchOpen by mutableStateOf(false)
    var searchInitial by mutableStateOf("")
    /** Item whose pop-up menu is open (page index, item id). */
    var itemMenu by mutableStateOf<Pair<Int, String>?>(null)
    var dockMenu by mutableStateOf<String?>(null)
    var drawerMenu by mutableStateOf<AppEntry?>(null)
    var appSettings by mutableStateOf<AppEntry?>(null)
    var openFolder by mutableStateOf<FolderTarget?>(null)
    var homeMenuOpen by mutableStateOf(false)
    var widgetPickerOpen by mutableStateOf(false)
    var spaceSwitcher by mutableStateOf(false)
    var hiddenAppsOpen by mutableStateOf(false)
    var smartSortOpen by mutableStateOf(false)
    var historyOpen by mutableStateOf(false)
    var gate by mutableStateOf<GateRequest?>(null)
    var banner by mutableStateOf<String?>(null)
    var drawerCategory by mutableStateOf<String?>(null)
    var drag by mutableStateOf<DragState?>(null)
    /** Index (in layout.pages) of the page the user is looking at; -1 on the news page. */
    var currentPage by mutableStateOf(0)
    val grids = mutableStateMapOf<Int, GridGeom>()

    var permissionRequester: ((Array<String>) -> Unit)? = null
    fun requestPermissions(perms: Array<String>) { permissionRequester?.invoke(perms) }

    /** Emitted when the home button is pressed while already home. */
    val homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val cols get() = Nama.settings.columns.coerceIn(4, 5)
    val rows get() = Nama.settings.rows.coerceIn(5, 7)

    fun closeAll(): Boolean {
        val any = drawerOpen || searchOpen || itemMenu != null || dockMenu != null || drawerMenu != null || appSettings != null ||
            openFolder != null || homeMenuOpen || widgetPickerOpen || spaceSwitcher || hiddenAppsOpen || smartSortOpen ||
            historyOpen || gate != null
        drawerOpen = false; searchOpen = false; itemMenu = null; dockMenu = null; drawerMenu = null; appSettings = null
        openFolder = null; homeMenuOpen = false; widgetPickerOpen = false; spaceSwitcher = false
        hiddenAppsOpen = false; smartSortOpen = false; historyOpen = false; gate = null; drag = null
        return any
    }

    fun openSearch(initial: String = "") {
        searchInitial = initial
        searchOpen = true
    }

    // ------------------------------------------------------------------------------------------
    // Launching (lock, focus, friction)
    // ------------------------------------------------------------------------------------------

    fun launch(e: AppEntry, view: View? = null) {
        if (e.pref.locked) authenticate(tr("باز کردن ${e.label}", "Open ${e.label}")) { checkGate(e, view) }
        else checkGate(e, view)
    }

    private fun checkGate(e: AppEntry, view: View?) {
        val s = Nama.settings
        val now = System.currentTimeMillis()
        val space = Nama.spaces.active.value
        val focus = s.focusUntil > now && s.focusApps.isNotEmpty() && e.key !in s.focusApps
        var fr = e.pref.friction
        if (space != null && e.key in space.overrides.extraFrictionApps) fr = fr or Friction.DELAY or Friction.ASK_INTENTION
        val used = (Nama.usage.today.value[e.packageName] ?: 0L) / 60_000L
        val overLimit = e.pref.dailyLimitMinutes > 0 && used >= e.pref.dailyLimitMinutes
        val needsGate = focus || overLimit ||
            Friction.has(fr, Friction.DELAY) || Friction.has(fr, Friction.ASK_INTENTION) || Friction.has(fr, Friction.TYPE_SENTENCE)
        if (needsGate) gate = GateRequest(e, focus, fr, overLimit, used, view) else doLaunch(e, view)
    }

    fun doLaunch(e: AppEntry, view: View?) {
        gate = null
        val ok = Actions.launchApp(activity, e, view)
        if (ok && Friction.has(e.pref.friction, Friction.SHUFFLE)) shuffle(e.key)
        if (ok) { drawerOpen = false; searchOpen = false; openFolder = null }
    }

    /**
     * Asks for fingerprint/face or the phone's PIN. If the phone has no screen lock the action runs
     * anyway (with a note), so nothing can ever lock the owner out.
     */
    fun authenticate(title: String, onSuccess: () -> Unit) {
        val auth = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        val can = try { BiometricManager.from(activity).canAuthenticate(auth) } catch (e: Exception) { -1 }
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            Actions.toast(activity, tr("قفل صفحه برای گوشی تنظیم نشده؛ بدون تأیید باز شد.", "No screen lock is set, opened without a check."))
            onSuccess(); return
        }
        try {
            BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            }).authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title).setAllowedAuthenticators(auth).build())
        } catch (e: Exception) {
            onSuccess()
        }
    }

    // ------------------------------------------------------------------------------------------
    // Grid editing
    // ------------------------------------------------------------------------------------------

    private fun cells(p: HomePage) = p.items.map { it.cell() }

    /** First page (starting at [startPage]) with room for w×h; adds a page if all are full. */
    private fun placeSomewhere(l: HomeLayout, w: Int, h: Int, startPage: Int, fromBottom: Boolean = false): Triple<HomeLayout, Int, Pair<Int, Int>> {
        val order = (startPage until l.pages.size) + (0 until startPage.coerceAtMost(l.pages.size))
        for (pi in order) {
            val spot = GridMath.findFree(cells(l.pages[pi]), w, h, cols, rows, fromBottom)
            if (spot != null) return Triple(l, pi, spot)
        }
        val nl = l.copy(pages = l.pages + HomePage(newId()))
        return Triple(nl, nl.pages.lastIndex, 0 to 0)
    }

    private fun withItem(l: HomeLayout, page: Int, item: GridItem): HomeLayout =
        l.copy(pages = l.pages.mapIndexed { i, p -> if (i == page) p.copy(items = p.items + item) else p })

    private fun pageOf(l: HomeLayout, id: String) = l.pages.indexOfFirst { p -> p.items.any { it.id == id } }

    fun addApp(key: String, page: Int = currentPage.coerceAtLeast(0), quiet: Boolean = false) {
        Nama.store.changeLayout(tr("افزودن به صفحه", "Add to home")) { l ->
            if (l.pages.any { p -> p.items.any { it.app == key } }) return@changeLayout l
            val (nl, pi, spot) = placeSomewhere(l, 1, 1, page.coerceIn(0, l.pages.lastIndex))
            withItem(nl, pi, GridItem(newId(), spot.first, spot.second, app = key))
        }
        if (!quiet) Actions.toast(activity, tr("به صفحه اصلی اضافه شد", "Added to home"))
    }

    fun addWidget(w: WidgetInstance, size: WSize, page: Int = currentPage.coerceAtLeast(0)): Boolean {
        var placedPage = -1
        Nama.store.changeLayout(tr("افزودن ویجت", "Add widget")) { l ->
            val ww = size.w.coerceAtMost(cols)
            val hh = size.h.coerceAtMost(rows)
            val (nl, pi, spot) = placeSomewhere(l, ww, hh, page.coerceIn(0, l.pages.lastIndex))
            placedPage = pi
            withItem(nl, pi, GridItem(newId(), spot.first, spot.second, ww, hh, widget = w))
        }
        if (placedPage >= 0 && placedPage != page) Actions.toast(activity, tr("در صفحه ${ir.nama.launcher.num(placedPage + 1)} جا شد", "Placed on page ${placedPage + 1}"))
        return placedPage >= 0
    }

    fun addWidgetType(type: WidgetType, size: WSize = WidgetCatalog.defaultSize(type)) =
        addWidget(WidgetInstance(newId(), type), size)

    fun remove(itemId: String) = Nama.store.changeLayout(tr("حذف از صفحه", "Remove")) { l ->
        l.copy(pages = l.pages.map { p -> p.copy(items = p.items.filterNot { it.id == itemId }) })
    }

    /** Changes a widget's size, keeping its place when it fits, otherwise finding room. */
    fun resize(itemId: String, size: WSize): Boolean {
        var ok = false
        Nama.store.changeLayout(tr("تغییر اندازه", "Resize")) { l ->
            val pi = pageOf(l, itemId)
            if (pi < 0) return@changeLayout l
            val page = l.pages[pi]
            val item = page.items.first { it.id == itemId }
            val w = size.w.coerceAtMost(cols)
            val h = size.h.coerceAtMost(rows)
            val others = page.items.filterNot { it.id == itemId }.map { it.cell() }
            val x0 = item.x.coerceAtMost(cols - w)
            val y0 = item.y.coerceAtMost(rows - h)
            val spot = if (GridMath.canPlace(others, Cell(itemId, x0, y0, w, h), cols, rows)) x0 to y0
            else GridMath.findFree(others, w, h, cols, rows)
            if (spot == null) return@changeLayout l
            ok = true
            l.copy(pages = l.pages.mapIndexed { i, p ->
                if (i != pi) p else p.copy(items = p.items.map { if (it.id == itemId) it.copy(x = spot.first, y = spot.second, w = w, h = h) else it })
            })
        }
        if (!ok) Actions.toast(activity, tr("این اندازه در این صفحه جا نمی‌شود", "No room for this size on this page"))
        return ok
    }

    fun updateWidget(itemId: String, f: (WidgetInstance) -> WidgetInstance) = Nama.store.changeLayout(tr("ویرایش ویجت", "Edit widget")) { l ->
        l.copy(pages = l.pages.map { p -> p.copy(items = p.items.map { if (it.id == itemId && it.widget != null) it.copy(widget = f(it.widget)) else it }) })
    }

    fun addToDock(key: String) = Nama.store.changeLayout(tr("افزودن به داک", "Add to dock")) { l ->
        if (key in l.dock) l else l.copy(dock = (l.dock + key).takeLast(5))
    }

    fun removeFromDock(key: String) = Nama.store.changeLayout(tr("حذف از داک", "Remove from dock")) { l -> l.copy(dock = l.dock - key) }

    fun renameFolder(itemId: String, name: String) = Nama.store.changeLayout(tr("تغییر نام پوشه", "Rename folder")) { l ->
        l.copy(pages = l.pages.map { p -> p.copy(items = p.items.map { if (it.id == itemId && it.folder != null) it.copy(folder = it.folder.copy(name = name)) else it }) })
    }

    fun setFolderSmart(itemId: String, cat: ir.nama.core.AppCategory?) = Nama.store.changeLayout(tr("پوشه هوشمند", "Smart folder")) { l ->
        l.copy(pages = l.pages.map { p -> p.copy(items = p.items.map { if (it.id == itemId && it.folder != null) it.copy(folder = it.folder.copy(smartCategory = cat)) else it }) })
    }

    /** Replaces a folder with its apps, placed in free cells. */
    fun ungroup(itemId: String) = Nama.store.changeLayout(tr("باز کردن پوشه", "Ungroup")) { l ->
        val pi = pageOf(l, itemId)
        if (pi < 0) return@changeLayout l
        val folder = l.pages[pi].items.first { it.id == itemId }
        var nl = l.copy(pages = l.pages.mapIndexed { i, p ->
            if (i != pi) p else p.copy(items = p.items.map { if (it.id == itemId) it.copy(folder = null, app = folder.folder!!.keys.firstOrNull()) else it })
        })
        if (folder.folder!!.keys.isEmpty()) nl = nl.copy(pages = nl.pages.map { p -> p.copy(items = p.items.filterNot { it.id == itemId }) })
        for (k in folder.folder.keys.drop(1)) {
            val (l2, p2, spot) = placeSomewhere(nl, 1, 1, pi)
            nl = withItem(l2, p2, GridItem(newId(), spot.first, spot.second, app = k))
        }
        nl
    }

    fun removeFromFolder(itemId: String, key: String) = Nama.store.changeLayout(tr("خارج کردن از پوشه", "Take out of folder")) { l ->
        val pi = pageOf(l, itemId)
        if (pi < 0) return@changeLayout l
        var nl = l.copy(pages = l.pages.mapIndexed { i, p ->
            if (i != pi) p else p.copy(items = p.items.mapNotNull {
                if (it.id != itemId || it.folder == null) it
                else {
                    val rest = it.folder.keys - key
                    when {
                        rest.isEmpty() && it.folder.smartCategory == null -> null
                        rest.size == 1 && it.folder.smartCategory == null -> it.copy(folder = null, app = rest.first())
                        else -> it.copy(folder = it.folder.copy(keys = rest))
                    }
                }
            })
        })
        val (l2, p2, spot) = placeSomewhere(nl, 1, 1, pi)
        nl = withItem(l2, p2, GridItem(newId(), spot.first, spot.second, app = key))
        nl
    }

    /** Puts an app in the folder called [name] (creating it next to the app if needed). */
    fun putInFolder(key: String, name: String) = Nama.store.changeLayout(tr("افزودن به پوشه", "Add to folder")) { l ->
        val loose = l.pages.flatMap { it.items }.firstOrNull { it.app == key }
        val cleaned = l.copy(pages = l.pages.map { p -> p.copy(items = p.items.filterNot { it.app == key }) })
        val existingPage = cleaned.pages.indexOfFirst { p -> p.items.any { it.folder?.name == name } }
        if (existingPage >= 0) {
            cleaned.copy(pages = cleaned.pages.mapIndexed { i, p ->
                if (i != existingPage) p else p.copy(items = p.items.map {
                    if (it.folder?.name == name) it.copy(folder = it.folder.copy(keys = (it.folder.keys - key) + key)) else it
                })
            })
        } else if (loose != null) {
            val pi = pageOf(l, loose.id)
            withItem(cleaned, pi, loose.copy(app = null, folder = FolderData(name, listOf(key))))
        } else {
            val (nl, pi, spot) = placeSomewhere(cleaned, 1, 1, currentPage.coerceAtLeast(0))
            withItem(nl, pi, GridItem(newId(), spot.first, spot.second, folder = FolderData(name, listOf(key))))
        }
    }

    /** Moves an app to a random free cell so it can't be opened on autopilot. */
    fun shuffle(key: String) = Nama.store.changeLayoutSilently { l ->
        val from = l.pages.indexOfFirst { p -> p.items.any { it.app == key } }
        if (from < 0) {
            // Inside a folder: reorder within it.
            l.copy(pages = l.pages.map { p ->
                p.copy(items = p.items.map { it2 ->
                    val f = it2.folder
                    if (f != null && key in f.keys && f.keys.size > 1) {
                        val keys = f.keys.toMutableList().apply { remove(key); add(Random.nextInt(size + 1), key) }
                        it2.copy(folder = f.copy(keys = keys))
                    } else it2
                })
            })
        } else {
            val item = l.pages[from].items.first { it.app == key }
            val without = l.copy(pages = l.pages.map { p -> p.copy(items = p.items.filterNot { it.id == item.id }) })
            val candidates = without.pages.indices.shuffled()
            var result = l
            for (pi in candidates) {
                val free = (0 until rows).flatMap { y -> (0 until cols).map { x -> x to y } }
                    .filter { (x, y) -> GridMath.canPlace(cells(without.pages[pi]), Cell("s", x, y), cols, rows, null) }
                if (free.isNotEmpty()) {
                    val (x, y) = free.random()
                    result = withItem(without, pi, item.copy(x = x, y = y))
                    break
                }
            }
            result
        }
    }

    // ------------------------------------------------------------------------------------------
    // Drag and drop
    // ------------------------------------------------------------------------------------------

    /** Where the dragged item would land: (page, x, y, merge target id or null), or null if nowhere. */
    fun dropTarget(d: DragState): DropTarget? {
        val page = currentPage
        if (page < 0) return null
        val g = grids[page] ?: return null
        val items = Nama.store.layout.value.pages.getOrNull(page)?.items ?: return null
        val single = d.item.w == 1 && d.item.h == 1 && !d.item.isWidget
        // Dropping an app onto another app or folder makes or fills a folder.
        if (d.item.isApp) {
            val under = g.cellAt(d.pointer)?.let { (cx, cy) -> GridMath.at(items.map { it.cell() }, cx, cy) }
            val target = under?.let { c -> items.first { it.id == c.id } }
            if (target != null && target.id != d.item.id && (target.isApp || target.isFolder)) {
                val r = g.rect(target.x, target.y, 1, 1)
                val center = r.center
                if ((d.pointer - center).getDistance() < g.cellW * 0.42f) return DropTarget(page, target.x, target.y, target.id)
            }
        }
        val (x, y) = if (single) (g.cellAt(d.pointer) ?: return null) else g.snap(d.pointer - d.grab, d.item.w, d.item.h)
        val others = items.filterNot { it.id == d.item.id }.map { it.cell() }
        val ok = GridMath.canPlace(others, Cell(d.item.id, x, y, d.item.w, d.item.h), cols, rows)
        return if (ok) DropTarget(page, x, y, null) else null
    }

    data class DropTarget(val page: Int, val x: Int, val y: Int, val mergeInto: String?)

    fun drop() {
        val d = drag ?: return
        drag = null
        val t = dropTarget(d)
        if (t == null) {
            pruneEmptyPages()
            return
        }
        Nama.store.changeLayout(tr("جابه‌جایی", "Move")) { l ->
            val removed = l.copy(pages = l.pages.map { p -> p.copy(items = p.items.filterNot { it.id == d.item.id }) })
            if (t.mergeInto != null) {
                val key = d.item.app ?: return@changeLayout l
                removed.copy(pages = removed.pages.mapIndexed { i, p ->
                    if (i != t.page) p else p.copy(items = p.items.map { target ->
                        when {
                            target.id != t.mergeInto -> target
                            target.folder != null -> target.copy(folder = target.folder.copy(keys = (target.folder.keys - key) + key))
                            target.app != null -> {
                                val name = Nama.apps.byKey(target.app)?.category?.let { c -> Nama.categoryName(c) } ?: tr("پوشه", "Folder")
                                target.copy(app = null, folder = FolderData(name, listOf(target.app, key)))
                            }
                            else -> target
                        }
                    })
                })
            } else {
                withItem(removed, t.page, d.item.copy(x = t.x, y = t.y))
            }
        }
        pruneEmptyPages()
    }

    /** Adds an empty page at the end (used when dragging past the last page). */
    fun addEmptyPage() = Nama.store.changeLayoutSilently { l -> l.copy(pages = l.pages + HomePage(newId())) }

    /** Removes empty pages at the end (never the first page). */
    fun pruneEmptyPages() = Nama.store.changeLayoutSilently { l ->
        var pages = l.pages
        while (pages.size > 1 && pages.last().items.isEmpty()) pages = pages.dropLast(1)
        if (pages.size == l.pages.size) l else l.copy(pages = pages)
    }

    /**
     * Makes every page fit the current grid size (after the user changes rows or columns): items
     * that no longer fit move to a free spot, or to a new page. Nothing is ever dropped.
     */
    fun normalizeLayout() = Nama.store.changeLayoutSilently { l ->
        var changed = false
        val pages = mutableListOf<HomePage>()
        val homeless = mutableListOf<GridItem>()
        for (p in l.pages) {
            val (placed, rest) = GridMath.normalize(p.items.map { it.cell() }, cols, rows)
            val byId = p.items.associateBy { it.id }
            val items = placed.mapNotNull { c -> byId[c.id]?.let { if (it.x != c.x || it.y != c.y || it.w != c.w || it.h != c.h) { changed = true; it.copy(x = c.x, y = c.y, w = c.w, h = c.h) } else it } }
            if (rest.isNotEmpty()) changed = true
            rest.mapNotNullTo(homeless) { byId[it.id] }
            pages += p.copy(items = items)
        }
        if (!changed) return@changeLayoutSilently l
        var nl = l.copy(pages = pages)
        for (item in homeless) {
            val w = item.w.coerceAtMost(cols)
            val h = item.h.coerceAtMost(rows)
            val (l2, pi, spot) = placeSomewhere(nl, w, h, 0)
            nl = withItem(l2, pi, item.copy(x = spot.first, y = spot.second, w = w, h = h))
        }
        nl
    }

    companion object {
        fun newId() = UUID.randomUUID().toString().take(10)
    }
}
