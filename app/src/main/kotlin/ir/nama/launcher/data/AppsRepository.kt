package ir.nama.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import ir.nama.core.AppCategory
import ir.nama.core.AppClassifier
import ir.nama.core.AppFacts
import ir.nama.core.RemoteConfig
import ir.nama.core.SearchKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Raw information about one launchable activity. */
data class RawApp(
    val key: String,
    val packageName: String,
    val className: String,
    val user: UserHandle,
    val isWorkProfile: Boolean,
    val label: String,
    val systemCategory: Int,
    val isSystem: Boolean,
    val installedAt: Long,
    val installer: String?
)

/** An app as the launcher shows it, with the user's overrides applied. */
data class AppEntry(
    val key: String,
    val packageName: String,
    val className: String,
    val user: UserHandle,
    val isWorkProfile: Boolean,
    val label: String,
    val originalLabel: String,
    val category: AppCategory,
    val isSystem: Boolean,
    val installedAt: Long,
    val domestic: Boolean,
    val foreign: Boolean,
    val pref: AppPref,
    val search: SearchKey
) {
    val component: ComponentName get() = ComponentName(packageName, className)
}

class AppsRepository(
    private val context: Context,
    private val store: Store,
    remoteConfig: StateFlow<RemoteConfig>,
    private val scope: CoroutineScope
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val pm = context.packageManager
    private val infos = ConcurrentHashMap<String, LauncherActivityInfo>()

    private val raw = MutableStateFlow<List<RawApp>>(emptyList())
    val loaded = MutableStateFlow(false)

    /** Bumped when packages change so icon caches can drop stale icons. */
    val packageEvents = MutableStateFlow(0)

    val apps: StateFlow<List<AppEntry>> = combine(raw, store.appPrefs.flow, remoteConfig) { list, prefs, rc ->
        val collator = Collator.getInstance(Locale("fa"))
        list.map { r ->
            val pref = prefs[r.key] ?: AppPref()
            val facts = AppFacts(r.packageName, r.label, r.systemCategory, r.isSystem, r.installedAt, 0L, r.installer)
            val cat = pref.category ?: AppClassifier.classify(facts, rc.categoryOverrides)
            val label = pref.label?.takeIf { it.isNotBlank() } ?: r.label
            AppEntry(
                key = r.key, packageName = r.packageName, className = r.className, user = r.user,
                isWorkProfile = r.isWorkProfile, label = label, originalLabel = r.label, category = cat,
                isSystem = r.isSystem, installedAt = r.installedAt,
                domestic = AppClassifier.isDomestic(r.packageName, rc.domesticPackages),
                foreign = AppClassifier.isForeign(r.packageName, rc.foreignPackages),
                pref = pref,
                search = SearchKey(label, pref.aliases, listOf(r.label, r.packageName.substringAfterLast('.')))
            )
        }.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, emptyList())

    private var reloadJob: Job? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = scheduleReload()
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            markNew(packageName)
            scheduleReload()
        }
        override fun onPackageChanged(packageName: String, user: UserHandle) = scheduleReload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = scheduleReload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = scheduleReload()
        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) = scheduleReload()
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) = scheduleReload()
    }

    fun start() {
        try {
            launcherApps.registerCallback(callback, android.os.Handler(android.os.Looper.getMainLooper()))
        } catch (e: Exception) {
            Log.w("Nama", "LauncherApps callback failed", e)
        }
        scheduleReload(0)
    }

    private val pendingNew = java.util.Collections.synchronizedSet(HashSet<String>())

    private fun markNew(pkg: String) {
        if (!store.settings.value.autoInboxNewApps || !store.settings.value.onboarded) return
        pendingNew += pkg
    }

    fun scheduleReload(delayMs: Long = 400) {
        reloadJob?.cancel()
        reloadJob = scope.launch(Dispatchers.IO) {
            delay(delayMs)
            load()
            packageEvents.value = packageEvents.value + 1
        }
    }

    private fun load() {
        val me = Process.myUserHandle()
        val out = ArrayList<RawApp>()
        val profiles = try { launcherApps.profiles } catch (e: Exception) { listOf(me) }
        for (user in profiles) {
            val list = try { launcherApps.getActivityList(null, user) } catch (e: Exception) { emptyList() }
            val serial = try { userManager.getSerialNumberForUser(user) } catch (e: Exception) { 0L }
            for (info in list) {
                val cn = info.componentName
                if (cn.packageName == context.packageName) continue
                val key = keyOf(cn, if (user == me) null else serial)
                infos[key] = info
                val ai = info.applicationInfo
                val installedAt = try {
                    pm.getPackageInfo(cn.packageName, 0).firstInstallTime
                } catch (e: Exception) { 0L }
                val installer = try {
                    if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(cn.packageName).installingPackageName
                    else @Suppress("DEPRECATION") pm.getInstallerPackageName(cn.packageName)
                } catch (e: Exception) { null }
                out += RawApp(
                    key = key,
                    packageName = cn.packageName,
                    className = cn.className,
                    user = user,
                    isWorkProfile = user != me,
                    label = info.label?.toString()?.trim().orEmpty().ifEmpty { cn.packageName },
                    systemCategory = ai.category,
                    isSystem = ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                    installedAt = installedAt,
                    installer = installer
                )
            }
        }
        val keys = out.map { it.key }.toSet()
        infos.keys.retainAll(keys)
        if (pendingNew.isNotEmpty()) {
            val pkgs = synchronized(pendingNew) { pendingNew.toSet().also { pendingNew.clear() } }
            out.filter { it.packageName in pkgs }.forEach { r -> store.updatePref(r.key) { it.copy(reviewed = false) } }
        }
        raw.value = out
        loaded.value = true
    }

    fun info(key: String): LauncherActivityInfo? = infos[key]

    fun byKey(key: String): AppEntry? = apps.value.firstOrNull { it.key == key }

    fun byPackage(pkg: String): AppEntry? = apps.value.firstOrNull { it.packageName == pkg && !it.isWorkProfile }

    /** Finds the app that handles an intent (used to pick dock defaults such as phone and camera). */
    fun resolve(intent: android.content.Intent): AppEntry? = try {
        val ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val pkg = ri?.activityInfo?.packageName
        if (pkg == null || pkg == "android") null else byPackage(pkg)
    } catch (e: Exception) { null }

    fun hasShortcutPermission(): Boolean = try { launcherApps.hasShortcutHostPermission() } catch (e: Exception) { false }

    fun shortcuts(entry: AppEntry): List<ShortcutInfo> {
        if (!hasShortcutPermission()) return emptyList()
        return try {
            val q = LauncherApps.ShortcutQuery()
                .setPackage(entry.packageName)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            (launcherApps.getShortcuts(q, entry.user) ?: emptyList()).filter { it.isEnabled }.sortedBy { it.rank }.take(5)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun startShortcut(s: ShortcutInfo): Boolean = try {
        launcherApps.startShortcut(s, null, null)
        true
    } catch (e: Exception) {
        false
    }

    fun shortcutIcon(s: ShortcutInfo, density: Int): android.graphics.drawable.Drawable? = try {
        launcherApps.getShortcutIconDrawable(s, density)
    } catch (e: Exception) { null }

    companion object {
        fun keyOf(cn: ComponentName, userSerial: Long?): String =
            cn.packageName + "/" + cn.className + (userSerial?.let { "#$it" } ?: "")

        fun packageOf(key: String): String = key.substringBefore('/')
    }
}
