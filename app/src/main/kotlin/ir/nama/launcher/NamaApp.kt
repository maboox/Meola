package ir.nama.launcher

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import ir.nama.core.AppCategory
import ir.nama.core.PersianText
import ir.nama.core.Space
import ir.nama.core.StyleId
import ir.nama.launcher.context.ContextMonitor
import ir.nama.launcher.context.SpaceManager
import ir.nama.launcher.data.AppEntry
import ir.nama.launcher.data.AppsRepository
import ir.nama.launcher.data.ConnectivityRepo
import ir.nama.launcher.data.FileCache
import ir.nama.launcher.data.IconLoader
import ir.nama.launcher.data.IconPackManager
import ir.nama.launcher.data.NewsRepo
import ir.nama.launcher.data.PricesRepo
import ir.nama.launcher.data.RemoteConfigRepo
import ir.nama.launcher.data.SecureCards
import ir.nama.launcher.data.Settings
import ir.nama.launcher.data.Store
import ir.nama.launcher.data.SupabaseSync
import ir.nama.launcher.data.UsageRepo
import ir.nama.launcher.data.WeatherRepo
import ir.nama.launcher.system.Schedules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File

class NamaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
        // The safe-mode activity runs in its own process and must not load anything that could crash.
        if (currentProcessName().endsWith(":safe")) return
        // In safe mode nothing is loaded, so even a crash during start-up cannot lock the phone.
        if (CrashGuard.inSafeMode(this)) return
        try {
            Nama.init(this)
        } catch (e: Throwable) {
            Log.e("Nama", "start-up failed, entering safe mode", e)
            CrashGuard.enterSafeMode(this)
        }
    }

    private fun currentProcessName(): String = try {
        if (Build.VERSION.SDK_INT >= 28) Application.getProcessName()
        else File("/proc/self/cmdline").readText().trim('\u0000', ' ', '\n')
    } catch (e: Exception) {
        packageName
    }
}

/** Application-wide services. Created once in [NamaApp]. */
object Nama {
    lateinit var app: Application; private set
    lateinit var store: Store; private set
    lateinit var cache: FileCache; private set
    lateinit var remote: RemoteConfigRepo; private set
    lateinit var apps: AppsRepository; private set
    lateinit var icons: IconLoader; private set
    lateinit var prices: PricesRepo; private set
    lateinit var weather: WeatherRepo; private set
    lateinit var news: NewsRepo; private set
    lateinit var net: ConnectivityRepo; private set
    lateinit var usage: UsageRepo; private set
    lateinit var cards: SecureCards; private set
    lateinit var context: ContextMonitor; private set
    lateinit var spaces: SpaceManager; private set
    lateinit var supabase: SupabaseSync; private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile var ready = false; private set

    fun init(app: Application) {
        this.app = app
        store = Store(app)
        cache = FileCache(app)
        remote = RemoteConfigRepo(app, cache, scope)
        apps = AppsRepository(app, store, remote.config, scope)
        icons = IconLoader(app, apps, IconPackManager(app))
        prices = PricesRepo(cache, remote.config, scope)
        weather = WeatherRepo(cache, scope)
        news = NewsRepo(cache, remote.config, store, scope)
        net = ConnectivityRepo(app, remote.config, scope)
        usage = UsageRepo(app)
        cards = SecureCards(app)
        context = ContextMonitor(app, store, net.state)
        spaces = SpaceManager(store, context.snapshot, scope)
        supabase = SupabaseSync(store)
        ready = true

        apps.start()
        try { context.start() } catch (e: Exception) { Log.w("Nama", "context monitor failed", e) }
        net.start()
        net.state.onEach { context.onNetState(it) }.launchIn(scope)
        try { Schedules.ensureDaily(app) } catch (e: Exception) { Log.w("Nama", "schedule failed", e) }
    }

    val settings: Settings get() = store.settings.value
    val isFa: Boolean get() = !ready || settings.language != "en"
    val faDigits: Boolean get() = !ready || settings.persianDigits

    fun effectiveStyle(space: Space?): StyleId = space?.overrides?.style ?: settings.style

    /** Whether an app should appear on home and in the main drawer list right now. */
    fun isVisible(e: AppEntry, space: Space?): Boolean {
        if (e.pref.hidden || e.pref.archived) return false
        val o = space?.overrides ?: return true
        if (o.allowOnlyApps.isNotEmpty() || o.allowOnlyCategories.isNotEmpty()) {
            return e.key in o.allowOnlyApps || e.category in o.allowOnlyCategories
        }
        if (e.key in o.hiddenApps) return false
        if (e.category in o.hiddenCategories) return false
        if (o.hideForeignApps && e.foreign) return false
        return true
    }

    fun isRestricted(space: Space?): Boolean {
        val o = space?.overrides ?: return false
        return o.allowOnlyApps.isNotEmpty() || o.allowOnlyCategories.isNotEmpty()
    }

    fun categoryName(c: AppCategory) = if (isFa) c.fa else c.en
}

fun tr(fa: String, en: String): String = if (Nama.isFa) fa else en

/** Shows a number (or text containing digits) with Persian or Latin digits according to settings. */
fun num(v: Any?): String = PersianText.digits(v?.toString() ?: "", Nama.faDigits)

/**
 * Detects crash loops. Two crashes within 90 seconds switch Nama to safe mode on the next start,
 * which shows a plain app list and a button to pick another launcher, so the phone is never stuck.
 * State is kept in small files (not SharedPreferences) because safe mode runs in another process.
 */
object CrashGuard {
    private fun dir(context: Context) = File(context.filesDir, "guard").apply { mkdirs() }
    private fun safeFlag(context: Context) = File(dir(context), "safe_mode")
    private fun timesFile(context: Context) = File(dir(context), "crash_times")
    private fun traceFile(context: Context) = File(dir(context), "last_trace")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { record(app, e) } catch (_: Throwable) {}
            previous?.uncaughtException(t, e)
        }
    }

    private fun record(context: Context, e: Throwable) {
        val now = System.currentTimeMillis()
        val old = try { timesFile(context).readText() } catch (_: Exception) { "" }
        val times = old.split(',').mapNotNull { it.trim().toLongOrNull() }.filter { now - it < 90_000 } + now
        timesFile(context).writeText(times.joinToString(","))
        traceFile(context).writeText(Log.getStackTraceString(e).take(8000))
        if (times.size >= 2) safeFlag(context).writeText(now.toString())
    }

    fun inSafeMode(context: Context): Boolean = safeFlag(context).exists()

    fun lastTrace(context: Context): String = try { traceFile(context).readText() } catch (_: Exception) { "" }

    fun leaveSafeMode(context: Context) {
        safeFlag(context).delete()
        timesFile(context).delete()
    }

    fun enterSafeMode(context: Context) {
        safeFlag(context).writeText(System.currentTimeMillis().toString())
    }

    /** Called after the home screen has been stable for a while. */
    fun markHealthy(context: Context) {
        try { timesFile(context).delete() } catch (_: Exception) {}
    }
}
