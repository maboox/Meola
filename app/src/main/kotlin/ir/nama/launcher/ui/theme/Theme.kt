package ir.nama.launcher.ui.theme

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import ir.nama.core.IranCalendar
import ir.nama.core.StyleId
import ir.nama.launcher.R

val Vazir = FontFamily(
    Font(R.font.vazirmatn_extralight, FontWeight.ExtraLight),
    Font(R.font.vazirmatn_light, FontWeight.Light),
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

/** Colours and shapes the home screen uses, derived from the Material colour scheme. */
@Immutable
data class NamaStyle(
    val id: StyleId,
    /** Text drawn directly on the wallpaper (labels, glance). */
    val onWallpaper: Color,
    val onWallpaperSub: Color,
    val textShadow: Shadow?,
    /** Widget cards. */
    val surface: Color,
    val surfaceHigh: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val accent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val outline: Color,
    val danger: Color,
    val success: Color,
    val warning: Color,
    /** Solid background (minimal) or null to show the wallpaper. */
    val background: Color?,
    val corner: Dp,
    /** True when the status bar icons should be light. */
    val lightContentOnWallpaper: Boolean
) {
    val minimal get() = id == StyleId.MINIMAL
}

val LocalNamaStyle = staticCompositionLocalOf {
    buildStyle(StyleId.DEFAULT, fallbackScheme(true), darkTextOnWallpaper = false)
}

fun fallbackScheme(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = Color(0xFF7FD3CB), onPrimary = Color(0xFF00201E), primaryContainer = Color(0xFF1E4D49), onPrimaryContainer = Color(0xFFA6F0E7),
    secondary = Color(0xFFB1CCC7), secondaryContainer = Color(0xFF324B48), onSecondaryContainer = Color(0xFFCDE8E3),
    tertiary = Color(0xFFE8C08A), background = Color(0xFF0F1514), surface = Color(0xFF0F1514),
    surfaceContainerLowest = Color(0xFF0A0F0E), surfaceContainerLow = Color(0xFF171D1C), surfaceContainer = Color(0xFF1B2120),
    surfaceContainerHigh = Color(0xFF252B2A), surfaceContainerHighest = Color(0xFF303635),
    onSurface = Color(0xFFDEE4E2), onSurfaceVariant = Color(0xFFBEC9C6), outline = Color(0xFF889391), outlineVariant = Color(0xFF3F4947)
) else lightColorScheme(
    primary = Color(0xFF006A64), onPrimary = Color.White, primaryContainer = Color(0xFF9EF2E8), onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF4A6360), secondaryContainer = Color(0xFFCCE8E3), onSecondaryContainer = Color(0xFF051F1D),
    tertiary = Color(0xFF7A5726), background = Color(0xFFF5FAF8), surface = Color(0xFFF5FAF8),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEFF5F3), surfaceContainer = Color(0xFFE9EFED),
    surfaceContainerHigh = Color(0xFFE3EAE7), surfaceContainerHighest = Color(0xFFDDE4E2),
    onSurface = Color(0xFF171D1C), onSurfaceVariant = Color(0xFF3F4947), outline = Color(0xFF6F7977), outlineVariant = Color(0xFFBEC9C6)
)

/** Material You colours from the wallpaper on Android 12+, a calm teal palette before that. */
fun namaColorScheme(context: Context, dark: Boolean): ColorScheme =
    if (Build.VERSION.SDK_INT >= 31) {
        try { if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) } catch (e: Exception) { fallbackScheme(dark) }
    } else fallbackScheme(dark)

val MinimalScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFE6E6E6), onPrimary = Color.Black, primaryContainer = Color(0xFF2A2A2A), onPrimaryContainer = Color(0xFFEDEDED),
    secondary = Color(0xFFBDBDBD), secondaryContainer = Color(0xFF262626), onSecondaryContainer = Color(0xFFE0E0E0),
    background = Color.Black, surface = Color.Black,
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0C0C0C), surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A), surfaceContainerHighest = Color(0xFF222222),
    onSurface = Color(0xFFEDEDED), onSurfaceVariant = Color(0xFF9E9E9E), outline = Color(0xFF5E5E5E), outlineVariant = Color(0xFF2E2E2E)
)

/** Whether the wallpaper is light enough that dark text reads better on it. */
fun wallpaperPrefersDarkText(context: Context): Boolean = try {
    if (Build.VERSION.SDK_INT >= 27) {
        val c = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        c != null && (c.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
    } else false
} catch (e: Exception) {
    false
}

fun buildStyle(id: StyleId, scheme: ColorScheme, darkTextOnWallpaper: Boolean): NamaStyle = when (id) {
    StyleId.MINIMAL -> NamaStyle(
        id = id,
        onWallpaper = Color(0xFFEDEDED), onWallpaperSub = Color(0xFF8A8A8A), textShadow = null,
        surface = Color.Transparent, surfaceHigh = Color(0xFF141414),
        onSurface = Color(0xFFEDEDED), onSurfaceVariant = Color(0xFF8A8A8A),
        accent = Color(0xFFEDEDED), accentContainer = Color(0xFF1F1F1F), onAccentContainer = Color(0xFFEDEDED),
        outline = Color(0xFF2A2A2A), danger = Color(0xFFEF9A9A), success = Color(0xFFA5D6A7), warning = Color(0xFFFFCC80),
        background = Color.Black, corner = 20.dp, lightContentOnWallpaper = true
    )
    StyleId.DEFAULT -> NamaStyle(
        id = id,
        onWallpaper = if (darkTextOnWallpaper) Color(0xFF1B1B1B) else Color.White,
        onWallpaperSub = if (darkTextOnWallpaper) Color(0xFF3A3A3A) else Color.White.copy(alpha = 0.86f),
        textShadow = if (darkTextOnWallpaper) null else Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1.5f), 6f),
        surface = scheme.surfaceContainer.copy(alpha = 0.96f),
        surfaceHigh = scheme.surfaceContainerHighest,
        onSurface = scheme.onSurface, onSurfaceVariant = scheme.onSurfaceVariant,
        accent = scheme.primary, accentContainer = scheme.primaryContainer, onAccentContainer = scheme.onPrimaryContainer,
        outline = scheme.outlineVariant,
        danger = scheme.error, success = Color(0xFF2E9E5B), warning = Color(0xFFE08A2E),
        background = null, corner = 24.dp, lightContentOnWallpaper = !darkTextOnWallpaper
    )
}

fun namaTypography(): Typography {
    val base = Typography()
    fun TextStyle.v() = copy(fontFamily = Vazir)
    return Typography(
        displayLarge = base.displayLarge.v(), displayMedium = base.displayMedium.v(), displaySmall = base.displaySmall.v(),
        headlineLarge = base.headlineLarge.v(), headlineMedium = base.headlineMedium.v(), headlineSmall = base.headlineSmall.v(),
        titleLarge = base.titleLarge.v().copy(fontWeight = FontWeight.Bold), titleMedium = base.titleMedium.v().copy(fontWeight = FontWeight.Medium),
        titleSmall = base.titleSmall.v(),
        bodyLarge = base.bodyLarge.v(), bodyMedium = base.bodyMedium.v(), bodySmall = base.bodySmall.v(),
        labelLarge = base.labelLarge.v(), labelMedium = base.labelMedium.v(), labelSmall = base.labelSmall.v()
    )
}

/** Theme for normal screens (settings, setup), with wallpaper colours when available. */
@Composable
fun NamaAppTheme(rtl: Boolean, minimal: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = minimal || isSystemInDarkTheme()
    val scheme = if (minimal) MinimalScheme else namaColorScheme(context, dark)
    val view = LocalView.current
    SideEffect {
        (view.context as? android.app.Activity)?.window?.let { w ->
            val c = WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, typography = namaTypography()) {
        CompositionLocalProvider(
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            LocalNamaStyle provides buildStyle(if (minimal) StyleId.MINIMAL else StyleId.DEFAULT, scheme, darkTextOnWallpaper = false)
        ) { content() }
    }
}

object Occasions {
    fun greeting(o: IranCalendar.Occasion, fa: Boolean): String? = when (o) {
        IranCalendar.Occasion.NOWRUZ -> if (fa) "نوروزتان پیروز" else "Happy Nowruz"
        IranCalendar.Occasion.YALDA -> if (fa) "شب یلدا مبارک" else "Happy Yalda"
        IranCalendar.Occasion.RAMADAN -> if (fa) "ماه رمضان" else "Ramadan"
        IranCalendar.Occasion.CHAHARSHANBE_SURI -> if (fa) "چهارشنبه‌سوری" else "Chaharshanbe Suri"
        IranCalendar.Occasion.MUHARRAM, IranCalendar.Occasion.NONE -> null
    }
}

/** Text style for text drawn straight on the wallpaper. */
@Composable
fun wallpaperText(size: Int, weight: FontWeight = FontWeight.Normal): TextStyle {
    val s = LocalNamaStyle.current
    return TextStyle(fontFamily = Vazir, fontSize = size.sp, fontWeight = weight, color = s.onWallpaper, shadow = s.textShadow)
}
