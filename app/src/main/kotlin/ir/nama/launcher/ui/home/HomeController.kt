package ir.nama.launcher.ui.home

import android.view.View
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import ir.nama.core.Friction
import ir.nama.launcher.Nama
import ir.nama.launcher.data.AppEntry
import ir.nama.launcher.data.HomeItem
import ir.nama.launcher.data.HomeLayout
import ir.nama.launcher.data.WidgetInstance
import ir.nama.launcher.system.Actions
import ir.nama.launcher.tr
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.UUID
import kotlin.random.Random

/** Where an app menu was opened from, so the menu can offer the right actions. */
sealed class MenuSource {
    data class Home(val pageIndex: Int, val itemId: String) : MenuSource()
    data class Folder(val pageIndex: Int, val folderId: String) : MenuSource()
    data object Dock : MenuSource()
    data object Drawer : MenuSource()
}

data class AppMenuTarget(val entry: AppEntry, val source: MenuSource)
data class FolderTarget(val pageIndex: Int, val folderId: String)
data class GateRequest(
    val entry: AppEntry,
    val focus: Boolean,
    val friction: Int,
    val overLimit: Boolean,
    val usedMinutes: Long,
    val view: View?
)

class HomeController(val activity: FragmentActivity) {
    var drawerOpen by mutableStateOf(false)
    var searchOpen by mutableStateOf(false)
    var searchInitial by mutableStateOf("")
    var appMenu by mutableStateOf<AppMenuTarget?>(null)
    var openFolder by mutableStateOf<FolderTarget?>(null)
    var homeMenuPage by mutableStateOf<Int?>(null)
    var widgetPickerPage by mutableStateOf<Int?>(null)
    var widgetMenu by mutableStateOf<Pair<Int, WidgetInstance>?>(null)
    var spaceSwitcher by mutableStateOf(false)
    var inboxOpen by mutableStateOf(false)
    var hiddenAppsOpen by mutableStateOf(false)
    var smartSortOpen by mutableStateOf(false)
    var historyOpen by mutableStateOf(false)
    var gate by mutableStateOf<GateRequest?>(null)
    var banner by mutableStateOf<String?>(null)
    var drawerCategory by mutableStateOf<String?>(null)

    /** Set by the activity: asks Android for runtime permissions. */
    var permissionRequester: ((Array<String>) -> Unit)? = null

    fun requestPermissions(perms: Array<String>) {
        permissionRequester?.invoke(perms)
    }

    /** Emitted when the home button is pressed while already home. */
    val homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun closeAll(): Boolean {
        val any = drawerOpen || searchOpen || appMenu != null || openFolder != null || homeMenuPage != null ||
            widgetPickerPage != null || widgetMenu != null || spaceSwitcher || inboxOpen || hiddenAppsOpen ||
            smartSortOpen || historyOpen || gate != null
        drawerOpen = false; searchOpen = false; appMenu = null; openFolder = null; homeMenuPage = null
        widgetPickerPage = null; widgetMenu = null; spaceSwitcher = false; inboxOpen = false
        hiddenAppsOpen = false; smartSortOpen = false; historyOpen = false; gate = null
        return any
    }

    fun openSearch(initial: String = "") {
        searchInitial = initial
        searchOpen = true
    }

    // ----------------------------------------------------------------------------------------
    // Launching
    // ----------------------------------------------------------------------------------------

    fun launch(e: AppEntry, view: View? = null) {
        if (e.pref.locked) {
            authenticate(tr("باز کردن ${e.label}", "Open ${e.label}")) { checkGate(e, view) }
        } else {
            checkGate(e, view)
        }
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
        if (ok) {
            drawerOpen = false
            searchOpen = false
            openFolder = null
        }
    }

    /** Moves an app to a random spot so it cannot be opened on autopilot. */
    fun shuffle(key: String) {
        Nama.store.changeLayoutSilently { l -> shuffled(l, key) }
    }

    private fun shuffled(l: HomeLayout, key: String): HomeLayout {
        // Inside a folder: move to a random position in the same folder.
        l.pages.forEachIndexed { pi, p ->
            p.items.forEach { item ->
                if (item is HomeItem.Folder && key in item.keys && item.keys.size > 1) {
                    val keys = item.keys.toMutableList().apply { remove(key); add(Random.nextInt(size + 1), key) }
                    return l.copy(pages = l.pages.mapIndexed { i, pg ->
                        if (i != pi) pg else pg.copy(items = pg.items.map { if (it.id == item.id) item.copy(keys = keys) else it })
                    })
                }
            }
        }
        val from = l.pages.indexOfFirst { p -> p.items.any { it is HomeItem.App && it.key == key } }
        if (from < 0) return l
        val item = l.pages[from].items.first { it is HomeItem.App && it.key == key }
        val pages = l.pages.map { it.copy(items = it.items.filterNot { x -> x.id == item.id }) }.toMutableList()
        val to = Random.nextInt(pages.size)
        val target = pages[to].items.toMutableList()
        target.add(Random.nextInt(target.size + 1), item)
        pages[to] = pages[to].copy(items = target)
        return l.copy(pages = pages)
    }

    // ----------------------------------------------------------------------------------------
    // Authentication (locked apps, hidden apps, leaving kids/guest spaces)
    // ----------------------------------------------------------------------------------------

    /**
     * Asks for fingerprint/face or the phone's PIN. If the phone has no screen lock at all the
     * action runs anyway (with a note), so nothing can ever lock the owner out.
     */
    fun authenticate(title: String, onSuccess: () -> Unit) {
        val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        val can = try { BiometricManager.from(activity).canAuthenticate(authenticators) } catch (e: Exception) { -1 }
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            Actions.toast(activity, tr("قفل صفحه برای گوشی تنظیم نشده؛ بدون تأیید باز شد.", "No screen lock is set, opened without a check."))
            onSuccess()
            return
        }
        try {
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            })
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setAllowedAuthenticators(authenticators)
                    .build()
            )
        } catch (e: Exception) {
            onSuccess()
        }
    }

    // ----------------------------------------------------------------------------------------
    // Layout helpers
    // ----------------------------------------------------------------------------------------

    fun addToHome(key: String, pageIndex: Int? = null) {
        Nama.store.changeLayout(tr("افزودن به صفحه", "Add to home")) { l ->
            if (l.pages.any { p -> p.items.any { it is HomeItem.App && it.key == key } }) return@changeLayout l
            val idx = (pageIndex ?: l.pages.indexOfFirst { it.items.size < 20 }.takeIf { it >= 0 } ?: 0).coerceIn(0, l.pages.lastIndex)
            l.copy(pages = l.pages.mapIndexed { i, p -> if (i == idx) p.copy(items = p.items + HomeItem.App(newId(), key)) else p })
        }
    }

    fun addToDock(key: String) = Nama.store.changeLayout(tr("افزودن به داک", "Add to dock")) { l ->
        if (key in l.dock) l else l.copy(dock = (l.dock + key).takeLast(5))
    }

    fun removeFromDock(key: String) = Nama.store.changeLayout(tr("حذف از داک", "Remove from dock")) { l -> l.copy(dock = l.dock - key) }

    fun removeItem(pageIndex: Int, itemId: String) = Nama.store.changeLayout(tr("حذف از صفحه", "Remove from home")) { l ->
        l.copy(pages = l.pages.mapIndexed { i, p -> if (i == pageIndex) p.copy(items = p.items.filterNot { it.id == itemId }) else p })
    }

    fun removeFromFolder(pageIndex: Int, folderId: String, key: String) = Nama.store.changeLayout(tr("خارج کردن از پوشه", "Remove from folder")) { l ->
        l.copy(pages = l.pages.mapIndexed { i, p ->
            if (i != pageIndex) p else {
                val items = p.items.flatMap { item ->
                    if (item is HomeItem.Folder && item.id == folderId) {
                        val rest = item.keys - key
                        val folderPart = if (rest.isEmpty() && item.smartCategory == null) emptyList() else listOf(item.copy(keys = rest))
                        folderPart + HomeItem.App(newId(), key)
                    } else listOf(item)
                }
                p.copy(items = items)
            }
        })
    }

    fun moveItemToPage(fromPage: Int, itemId: String, toPage: Int) = Nama.store.changeLayout(tr("جابه‌جایی", "Move")) { l ->
        val item = l.pages.getOrNull(fromPage)?.items?.firstOrNull { it.id == itemId } ?: return@changeLayout l
        var pages = l.pages
        if (toPage >= pages.size) pages = pages + ir.nama.launcher.data.HomePage(newId())
        l.copy(pages = pages.mapIndexed { i, p ->
            when (i) {
                fromPage -> p.copy(items = p.items.filterNot { it.id == itemId })
                toPage -> p.copy(items = p.items + item)
                else -> p
            }
        })
    }

    fun putInFolder(key: String, folderName: String, pageIndex: Int) = Nama.store.changeLayout(tr("افزودن به پوشه", "Add to folder")) { l ->
        val pi = pageIndex.coerceIn(0, l.pages.lastIndex)
        val existing = l.pages.withIndex().firstNotNullOfOrNull { (i, p) ->
            p.items.firstOrNull { it is HomeItem.Folder && it.name == folderName }?.let { i to it as HomeItem.Folder }
        }
        // Remove the app as a loose item from every page.
        val cleaned = l.pages.map { p -> p.copy(items = p.items.filterNot { it is HomeItem.App && it.key == key }) }
        if (existing != null) {
            val (fi, folder) = existing
            l.copy(pages = cleaned.mapIndexed { i, p ->
                if (i == fi) p.copy(items = p.items.map { if (it.id == folder.id) folder.copy(keys = (folder.keys - key) + key) else it }) else p
            })
        } else {
            l.copy(pages = cleaned.mapIndexed { i, p -> if (i == pi) p.copy(items = p.items + HomeItem.Folder(newId(), folderName, listOf(key))) else p })
        }
    }

    companion object {
        fun newId() = UUID.randomUUID().toString().take(10)
    }
}
