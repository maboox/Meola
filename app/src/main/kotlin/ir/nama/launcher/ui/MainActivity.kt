package ir.nama.launcher.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ir.nama.core.StyleId
import ir.nama.launcher.CrashGuard
import ir.nama.launcher.Nama
import ir.nama.launcher.data.IconMode
import ir.nama.launcher.data.IconShape
import ir.nama.launcher.data.NotificationRepo
import ir.nama.launcher.data.WidgetInstance
import ir.nama.launcher.data.WidgetType
import ir.nama.launcher.logic.Templates
import ir.nama.launcher.system.Actions
import ir.nama.launcher.tr
import ir.nama.launcher.ui.common.LocalIconMode
import ir.nama.launcher.ui.home.AppDrawer
import ir.nama.launcher.ui.home.AppSettingsDialog
import ir.nama.launcher.ui.home.BottomOverlay
import ir.nama.launcher.ui.home.FolderDialog
import ir.nama.launcher.ui.home.GateDialog
import ir.nama.launcher.ui.home.HiddenAppsSheet
import ir.nama.launcher.ui.home.HistorySheet
import ir.nama.launcher.ui.home.HomeController
import ir.nama.launcher.ui.home.HomeEnv
import ir.nama.launcher.ui.home.HomeMenuSheet
import ir.nama.launcher.ui.home.HomeScreen
import ir.nama.launcher.ui.home.SearchOverlay
import ir.nama.launcher.ui.home.SmartSortDialog
import ir.nama.launcher.ui.home.SpaceSwitcherSheet
import ir.nama.launcher.ui.home.WidgetPicker
import ir.nama.launcher.ui.onboarding.OnboardingActivity
import ir.nama.launcher.ui.theme.LocalNamaStyle
import ir.nama.launcher.ui.theme.MinimalScheme
import ir.nama.launcher.ui.theme.buildStyle
import ir.nama.launcher.ui.theme.namaColorScheme
import ir.nama.launcher.ui.theme.namaTypography
import ir.nama.launcher.ui.theme.wallpaperPrefersDarkText
import ir.nama.launcher.ui.widgets.AppWidgets
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var ctrl: HomeController
    private val handler = Handler(Looper.getMainLooper())
    /** Bumped on resume so wallpaper-dependent colours are re-read after the wallpaper changes. */
    private val wallpaperTick = mutableIntStateOf(0)

    private var pendingWidgetId = -1
    private var pendingWidgetPage = 0
    private var pendingProvider: AppWidgetProviderInfo? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (Nama.ready) Nama.context.refresh()
    }

    private val bindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) configureOrAddWidget() else cancelPendingWidget()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (CrashGuard.inSafeMode(this) || !Nama.ready) {
            startActivity(Intent(this, SafeModeActivity::class.java))
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ctrl = HomeController(this)
        ctrl.permissionRequester = { perms -> try { permissionLauncher.launch(perms) } catch (e: Exception) { Log.w("Nama", "permission request failed", e) } }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            // Home never closes on back; back only closes overlays.
            override fun handleOnBackPressed() { ctrl.closeAll() }
        })

        setContent { HomeRoot(ctrl, wallpaperTick) { info -> pickSystemWidget(info, ctrl.currentPage.coerceAtLeast(0)) } }

        // A layout from an older version could not be read: rebuild a fresh one instead of an empty home.
        if (!Nama.store.layout.existedOnLoad && Nama.settings.onboarded) lifecycleScope.launch {
            try {
                val apps = withTimeoutOrNull(10_000) { Nama.apps.apps.first { it.isNotEmpty() } } ?: Nama.apps.apps.value
                val s = Nama.settings
                if (Nama.store.layout.value.pages.all { it.items.isEmpty() }) {
                    Nama.store.changeLayoutSilently { Templates.initialLayout(apps, s.persona, s.interests, s.style, ctrl.cols, ctrl.rows) }
                }
            } catch (e: Exception) {
                Log.w("Nama", "layout rebuild failed", e)
            }
        }

        if (!Nama.settings.onboarded) startActivity(Intent(this, OnboardingActivity::class.java))
        else if (intent?.getBooleanExtra(EXTRA_ASK_DEFAULT, false) == true && !Actions.isDefaultLauncher(this)) {
            Actions.requestDefaultLauncher(this)
        }

        // Charging clock: open the nightstand screen when the charger is connected while home is visible.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                Nama.context.charging.drop(1).filter { it }.collect {
                    if (Nama.settings.chargingClock) startActivity(Intent(this@MainActivity, NightstandActivity::class.java))
                }
            }
        }
        // Clear stale icons when packages change (updates can change icons).
        lifecycleScope.launch {
            Nama.apps.packageEvents.drop(1).collect { Nama.icons.clear() }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::ctrl.isInitialized) AppWidgets.startListening(this)
    }

    override fun onStop() {
        super.onStop()
        if (::ctrl.isInitialized) AppWidgets.stopListening(this)
    }

    override fun onResume() {
        super.onResume()
        if (!::ctrl.isInitialized) return
        wallpaperTick.intValue++
        try {
            Nama.context.refresh()
            Nama.remote.refreshIfStale()
            Nama.net.checkIfStale()
            lifecycleScope.launch { Nama.usage.refreshToday() }
        } catch (e: Exception) {
            Log.w("Nama", "resume refresh failed", e)
        }
        // If the home screen survives 20 seconds, earlier crashes were not a loop.
        handler.postDelayed({ CrashGuard.markHealthy(this) }, 20_000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
        if (Nama.ready) Nama.store.flushAll()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!::ctrl.isInitialized) return
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            // Pressing home while home: close whatever is open, otherwise go to the first page.
            if (!ctrl.closeAll()) ctrl.homePressed.tryEmit(Unit)
        }
    }

    // --------------------------------------------------------------------------------------------
    // Android app widgets
    // --------------------------------------------------------------------------------------------

    private fun pickSystemWidget(info: AppWidgetProviderInfo, page: Int) {
        try {
            val id = AppWidgets.host(this).allocateAppWidgetId()
            pendingWidgetId = id
            pendingWidgetPage = page
            pendingProvider = info
            val ok = AppWidgetManager.getInstance(this).bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)
            if (ok) configureOrAddWidget()
            else bindLauncher.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            )
        } catch (e: Exception) {
            Log.w("Nama", "widget bind failed", e)
            Actions.toast(this, tr("افزودن این ویجت ممکن نشد.", "Could not add this widget."))
            cancelPendingWidget()
        }
    }

    private fun configureOrAddWidget() {
        val info = pendingProvider ?: return
        if (info.configure != null) {
            try {
                AppWidgets.host(this).startAppWidgetConfigureActivityForResult(this, pendingWidgetId, 0, REQ_CONFIGURE, null)
                return
            } catch (e: Exception) {
                Log.w("Nama", "widget configure failed, adding without it", e)
            }
        }
        addPendingWidget()
    }

    @Deprecated("Needed for AppWidgetHost.startAppWidgetConfigureActivityForResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CONFIGURE) {
            if (resultCode == RESULT_OK) addPendingWidget() else cancelPendingWidget()
        }
    }

    private fun addPendingWidget() {
        val info = pendingProvider ?: return
        val id = pendingWidgetId
        val density = resources.displayMetrics.density
        val g = ctrl.grids[pendingWidgetPage] ?: ctrl.grids.values.firstOrNull()
        val span = if (g != null) AppWidgets.spanFor(info, g.cellW / density, g.cellH / density, density, ctrl.cols, ctrl.rows)
        else AppWidgets.spanFor(info, 90f, 110f, density, ctrl.cols, ctrl.rows)
        val ok = ctrl.addWidget(WidgetInstance(HomeController.newId(), WidgetType.SYSTEM, appWidgetId = id), span, pendingWidgetPage)
        if (!ok) AppWidgets.delete(this, id)
        pendingProvider = null
        pendingWidgetId = -1
    }

    private fun cancelPendingWidget() {
        if (pendingWidgetId >= 0) AppWidgets.delete(this, pendingWidgetId)
        pendingProvider = null
        pendingWidgetId = -1
    }

    companion object {
        private const val REQ_CONFIGURE = 7311
        const val EXTRA_ASK_DEFAULT = "ask_default"
    }
}

@Composable
private fun HomeRoot(ctrl: HomeController, wallpaperTick: androidx.compose.runtime.MutableIntState, pickSystemWidget: (AppWidgetProviderInfo) -> Unit) {
    val activity = ctrl.activity
    val settings by Nama.store.settings.flow.collectAsStateWithLifecycle()
    val space by Nama.spaces.active.collectAsStateWithLifecycle()
    val apps by Nama.apps.apps.collectAsStateWithLifecycle()
    val counts by NotificationRepo.counts.collectAsStateWithLifecycle()
    val usage by Nama.usage.today.collectAsStateWithLifecycle()
    val net by Nama.net.state.collectAsStateWithLifecycle()

    val styleId = space?.overrides?.style ?: settings.style
    val minimal = styleId == StyleId.MINIMAL
    val systemDark = isSystemInDarkTheme()
    val tick = wallpaperTick.intValue
    val darkText = remember(tick) { wallpaperPrefersDarkText(activity) }
    // Menus and sheets follow the phone's dark mode; minimal is always black.
    val scheme = remember(minimal, systemDark, tick) { if (minimal) MinimalScheme else namaColorScheme(activity, systemDark) }
    val style = remember(styleId, scheme, darkText) { buildStyle(styleId, scheme, darkText && !minimal) }
    val iconMode = when {
        minimal -> IconMode(IconMode.Kind.MONO, fg = 0xFFE6E6E6.toInt(), bg = 0)
        settings.themedIcons -> IconMode(IconMode.Kind.THEMED, fg = scheme.onPrimaryContainer.toArgb(), bg = scheme.primaryContainer.toArgb())
        else -> IconMode.NORMAL
    }
    val shape = settings.iconShape ?: IconShape.CIRCLE
    val appMap = remember(apps) { apps.associateBy { it.key } }
    val env = HomeEnv(
        settings, space, appMap, counts, usage, net, shape,
        settings.reduceMotion || space?.overrides?.reduceMotion == true,
        iconMode
    )
    val view = LocalView.current
    val overlayOpen = ctrl.drawerOpen || ctrl.searchOpen || ctrl.widgetPickerOpen
    SideEffect {
        val c = WindowCompat.getInsetsController(activity.window, view)
        val lightBars = if (overlayOpen) !systemDark && !minimal else !style.lightContentOnWallpaper
        c.isAppearanceLightStatusBars = lightBars
        c.isAppearanceLightNavigationBars = lightBars
    }
    // Newly installed apps get an icon on the home screen, like stock Android.
    LaunchedEffect(apps) {
        if (!settings.onboarded) return@LaunchedEffect
        apps.filter { !it.pref.reviewed }.forEach { e ->
            if (settings.autoInboxNewApps && !e.pref.hidden) ctrl.addApp(e.key, 1, quiet = true)
            Nama.store.updatePref(e.key) { it.copy(reviewed = true) }
        }
    }
    // Keep every item inside the grid when the user changes rows or columns.
    LaunchedEffect(settings.columns, settings.rows) { ctrl.normalizeLayout() }
    // Announce space changes.
    LaunchedEffect(space?.id) {
        val sp = space
        if (settings.showSpaceBanner && settings.onboarded) {
            ctrl.banner = if (sp != null) tr("فضای «${sp.name}» فعال شد", "\"${sp.name}\" space is on") else null
            delay(2800)
            ctrl.banner = null
        }
    }
    val rtl = settings.language != "en"
    key(settings.language) {
        MaterialTheme(colorScheme = scheme, typography = namaTypography()) {
            CompositionLocalProvider(
                LocalNamaStyle provides style,
                LocalIconMode provides iconMode,
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                Box(Modifier.fillMaxSize()) {
                    style.background?.let { bg -> Box(Modifier.fillMaxSize().background(bg)) }
                    HomeScreen(ctrl, env)
                    BottomOverlay(ctrl.drawerOpen, env.reduceMotion) { AppDrawer(ctrl, env) }
                    BottomOverlay(ctrl.searchOpen, env.reduceMotion) { SearchOverlay(ctrl, env) }
                    BottomOverlay(ctrl.widgetPickerOpen, env.reduceMotion) { WidgetPicker(ctrl, pickSystemWidget) }

                    ctrl.openFolder?.let { FolderDialog(ctrl, env, it) }
                    // Editing is not offered inside kids/guest spaces.
                    if (env.locked) {
                        if (ctrl.homeMenuOpen || ctrl.appSettings != null) { ctrl.homeMenuOpen = false; ctrl.appSettings = null }
                    } else {
                        if (ctrl.homeMenuOpen) HomeMenuSheet(ctrl)
                        ctrl.appSettings?.let { e -> AppSettingsDialog(e) { ctrl.appSettings = null } }
                        if (ctrl.hiddenAppsOpen) HiddenAppsSheet(ctrl, env)
                        if (ctrl.smartSortOpen) SmartSortDialog(ctrl, env)
                        if (ctrl.historyOpen) HistorySheet(ctrl)
                    }
                    if (ctrl.spaceSwitcher) SpaceSwitcherSheet(ctrl)
                    ctrl.gate?.let { GateDialog(ctrl, it) }

                    AnimatedVisibility(
                        visible = ctrl.banner != null, enter = fadeIn(), exit = fadeOut(),
                        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp)
                    ) {
                        Text(
                            ctrl.banner ?: "", color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.inverseSurface)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
