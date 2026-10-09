package ir.nama.launcher.ui.theme

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.nama.core.IranCalendar
import ir.nama.core.StyleId
import ir.nama.launcher.R
import ir.nama.launcher.data.IconShape
import kotlin.math.cos
import kotlin.math.sin

val Vazir = FontFamily(
    Font(R.font.vazirmatn_extralight, FontWeight.ExtraLight),
    Font(R.font.vazirmatn_light, FontWeight.Light),
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)
val Lalezar = FontFamily(Font(R.font.lalezar, FontWeight.Normal))

@Immutable
data class NamaStyle(
    val id: StyleId,
    val dark: Boolean,
    val text: Color,
    val subText: Color,
    val card: Color,
    val cardBorder: Color,
    val accent: Color,
    val accent2: Color,
    val danger: Color,
    val success: Color,
    val iconLabel: Color,
    val defaultShape: IconShape,
    val clockFont: FontFamily,
    val cardRadius: Dp,
    /** Minimal style shows apps as a text list. */
    val textOnly: Boolean,
    val background: Brush?,
    val pattern: Boolean,
    val scrim: Color
)

object Styles {
    fun build(id: StyleId, wallpaperAccent: Color?, useSystemWallpaper: Boolean, darkTint: Boolean): NamaStyle = when (id) {
        StyleId.GLASS -> NamaStyle(
            id = id, dark = true,
            text = Color.White, subText = Color.White.copy(alpha = 0.78f),
            card = Color.White.copy(alpha = 0.16f), cardBorder = Color.White.copy(alpha = 0.24f),
            accent = wallpaperAccent ?: Color(0xFFFFB36B), accent2 = Color(0xFF7FE0D6),
            danger = Color(0xFFFF8A80), success = Color(0xFFA5F2B4), iconLabel = Color.White,
            defaultShape = IconShape.SQUIRCLE, clockFont = Vazir, cardRadius = 22.dp, textOnly = false,
            background = if (useSystemWallpaper) null else Brush.verticalGradient(
                listOf(Color(0xFF1E5A63), Color(0xFF123C4A), Color(0xFF0B2530))
            ),
            pattern = false,
            scrim = if (darkTint) Color.Black.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.12f)
        )
        StyleId.MINIMAL -> NamaStyle(
            id = id, dark = true,
            text = Color(0xFFE9ECE9), subText = Color(0xFF8C948F),
            card = Color.Transparent, cardBorder = Color(0xFF2A302C),
            accent = Color(0xFF9AD1C3), accent2 = Color(0xFFC9D1CB),
            danger = Color(0xFFE57373), success = Color(0xFF9AD1C3), iconLabel = Color(0xFFE9ECE9),
            defaultShape = IconShape.CIRCLE, clockFont = Vazir, cardRadius = 14.dp, textOnly = true,
            background = if (useSystemWallpaper) null else Brush.verticalGradient(listOf(Color(0xFF0D0F0E), Color(0xFF0D0F0E))),
            pattern = false,
            scrim = Color.Black.copy(alpha = if (useSystemWallpaper) 0.72f else 0f)
        )
        StyleId.DASHBOARD -> NamaStyle(
            id = id, dark = darkTint,
            text = if (darkTint) Color(0xFFE6EAE7) else Color(0xFF1F2522),
            subText = if (darkTint) Color(0xFF9AA5A1) else Color(0xFF66706B),
            card = if (darkTint) Color(0xFF1B2120) else Color.White,
            cardBorder = if (darkTint) Color(0xFF2B3331) else Color(0xFFE1E5E2),
            accent = Color(0xFF0F7C79), accent2 = Color(0xFFE08A2E),
            danger = Color(0xFFC0392B), success = Color(0xFF1E8A4C),
            iconLabel = if (darkTint) Color(0xFFE6EAE7) else Color(0xFF1F2522),
            defaultShape = IconShape.ROUNDED, clockFont = Vazir, cardRadius = 16.dp, textOnly = false,
            background = if (useSystemWallpaper) null else Brush.verticalGradient(
                if (darkTint) listOf(Color(0xFF131716), Color(0xFF131716)) else listOf(Color(0xFFECEEE9), Color(0xFFE6E9E3))
            ),
            pattern = false,
            scrim = if (useSystemWallpaper) (if (darkTint) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.55f)) else Color.Transparent
        )
        StyleId.PERSIAN -> NamaStyle(
            id = id, dark = true,
            text = Color(0xFFF3EAD7), subText = Color(0xFFD9CFB8),
            card = Color(0xFF09142C).copy(alpha = 0.58f), cardBorder = Color(0xFFD4AF6A).copy(alpha = 0.38f),
            accent = Color(0xFF5FD0C6), accent2 = Color(0xFFF1D48F),
            danger = Color(0xFFFF9C8A), success = Color(0xFF8EE3B0), iconLabel = Color(0xFFE9E0CA),
            defaultShape = IconShape.ARCH, clockFont = Lalezar, cardRadius = 16.dp, textOnly = false,
            background = if (useSystemWallpaper) null else Brush.verticalGradient(
                listOf(Color(0xFF1C3A73), Color(0xFF10224A), Color(0xFF0B1834))
            ),
            pattern = true,
            scrim = if (darkTint) Color.Black.copy(alpha = 0.45f) else if (useSystemWallpaper) Color(0xFF0B1834).copy(alpha = 0.55f) else Color.Transparent
        )
    }

    /** Seasonal accent for Persian occasions. */
    fun seasonal(base: NamaStyle, occasion: IranCalendar.Occasion): NamaStyle = when (occasion) {
        IranCalendar.Occasion.NOWRUZ -> base.copy(accent = Color(0xFF7BC67B))
        IranCalendar.Occasion.YALDA -> base.copy(accent = Color(0xFFE0544B), accent2 = Color(0xFFF1D48F))
        IranCalendar.Occasion.RAMADAN -> base.copy(accent2 = Color(0xFFF1D48F))
        IranCalendar.Occasion.CHAHARSHANBE_SURI -> base.copy(accent = Color(0xFFFF9F43))
        IranCalendar.Occasion.MUHARRAM -> base.copy(accent = Color(0xFF7FA38F), accent2 = Color(0xFFB8B8B8))
        IranCalendar.Occasion.NONE -> base
    }

    fun wallpaperAccent(context: Context): Color? = try {
        if (Build.VERSION.SDK_INT >= 27) {
            val c = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            c?.primaryColor?.toArgb()?.let { argb ->
                val col = Color(argb)
                // Keep it light enough to read on dark glass.
                val lum = 0.299f * col.red + 0.587f * col.green + 0.114f * col.blue
                if (lum < 0.45f) Color(
                    red = (col.red + 0.5f).coerceAtMost(1f), green = (col.green + 0.5f).coerceAtMost(1f),
                    blue = (col.blue + 0.5f).coerceAtMost(1f)
                ) else col
            }
        } else null
    } catch (e: Exception) {
        null
    }

    fun occasionGreeting(o: IranCalendar.Occasion, fa: Boolean): String? = when (o) {
        IranCalendar.Occasion.NOWRUZ -> if (fa) "🌱 نوروزتان پیروز" else "🌱 Happy Nowruz"
        IranCalendar.Occasion.YALDA -> if (fa) "🍉 شب یلدا مبارک" else "🍉 Happy Yalda"
        IranCalendar.Occasion.RAMADAN -> if (fa) "🌙 ماه رمضان" else "🌙 Ramadan"
        IranCalendar.Occasion.CHAHARSHANBE_SURI -> if (fa) "🔥 چهارشنبه‌سوری" else "🔥 Chaharshanbe Suri"
        IranCalendar.Occasion.MUHARRAM, IranCalendar.Occasion.NONE -> null
    }
}

val LocalNamaStyle = staticCompositionLocalOf { Styles.build(StyleId.PERSIAN, null, false, false) }

fun namaTypography(): Typography {
    val base = Typography()
    fun TextStyle.v() = copy(fontFamily = Vazir)
    return Typography(
        displayLarge = base.displayLarge.v(), displayMedium = base.displayMedium.v(), displaySmall = base.displaySmall.v(),
        headlineLarge = base.headlineLarge.v(), headlineMedium = base.headlineMedium.v(), headlineSmall = base.headlineSmall.v(),
        titleLarge = base.titleLarge.v(), titleMedium = base.titleMedium.v(), titleSmall = base.titleSmall.v(),
        bodyLarge = base.bodyLarge.v(), bodyMedium = base.bodyMedium.v(), bodySmall = base.bodySmall.v(),
        labelLarge = base.labelLarge.v(), labelMedium = base.labelMedium.v(), labelSmall = base.labelSmall.v()
    )
}

fun schemeFor(style: NamaStyle): ColorScheme = if (style.dark) darkColorScheme(
    primary = style.accent, secondary = style.accent2, tertiary = style.accent2,
    background = Color(0xFF121716), surface = Color(0xFF1A201F), surfaceVariant = Color(0xFF253030),
    surfaceContainer = Color(0xFF1E2524), surfaceContainerHigh = Color(0xFF232B2A), surfaceContainerLow = Color(0xFF181E1D),
    onPrimary = Color(0xFF0B1A19), onBackground = Color(0xFFE8ECEA), onSurface = Color(0xFFE8ECEA),
    onSurfaceVariant = Color(0xFFB4C0BC), error = style.danger
) else lightColorScheme(
    primary = style.accent, secondary = style.accent2, tertiary = style.accent2,
    background = Color(0xFFFAFAF8), surface = Color.White, surfaceVariant = Color(0xFFEEF1EE),
    onPrimary = Color.White, onBackground = Color(0xFF1D2321), onSurface = Color(0xFF1D2321),
    onSurfaceVariant = Color(0xFF55605B), error = style.danger
)

/** Theme for normal screens (settings, onboarding). */
@Composable
fun NamaAppTheme(dark: Boolean, rtl: Boolean, content: @Composable () -> Unit) {
    val style = Styles.build(if (dark) StyleId.MINIMAL else StyleId.DASHBOARD, null, false, dark)
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF4CC3BD), secondary = Color(0xFFF1D48F), background = Color(0xFF121716),
        surface = Color(0xFF1A201F), surfaceVariant = Color(0xFF253030), onPrimary = Color(0xFF062321),
        surfaceContainer = Color(0xFF1E2524), surfaceContainerHigh = Color(0xFF232B2A), surfaceContainerLow = Color(0xFF181E1D)
    ) else lightColorScheme(
        primary = Color(0xFF0F7C79), secondary = Color(0xFFB07A22), background = Color(0xFFFAFAF8),
        surface = Color.White, surfaceVariant = Color(0xFFEEF1EE), onPrimary = Color.White,
        surfaceContainer = Color(0xFFF2F4F2), surfaceContainerHigh = Color(0xFFECEFEC), surfaceContainerLow = Color(0xFFF6F7F5)
    )
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        (view.context as? android.app.Activity)?.window?.let { w ->
            val c = androidx.core.view.WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, typography = namaTypography()) {
        CompositionLocalProvider(
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            LocalNamaStyle provides style
        ) { content() }
    }
}

/** Paints the style background, the girih pattern for the Persian style, and a readability scrim. */
@Composable
fun StyleBackground(style: NamaStyle, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .then(if (style.background != null) Modifier.background(style.background) else Modifier)
            .then(if (style.pattern) Modifier.girih(style.accent2.copy(alpha = 0.12f)) else Modifier)
            .background(style.scrim)
    )
}

/** Eight-point star lattice (a simple girih) drawn once per size. */
fun Modifier.girih(color: Color, tile: Dp = 56.dp): Modifier = drawWithCache {
    val t = tile.toPx()
    val path = Path()
    fun star(cx: Float, cy: Float, r: Float, inner: Float, rotate: Double) {
        for (i in 0 until 16) {
            val ang = rotate + i * Math.PI / 8
            val rr = if (i % 2 == 0) r else inner
            val x = cx + (rr * cos(ang)).toFloat()
            val y = cy + (rr * sin(ang)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }
    var y = 0f
    while (y < size.height + t) {
        var x = 0f
        while (x < size.width + t) {
            star(x + t / 2, y + t / 2, t * 0.40f, t * 0.17f, -Math.PI / 2)
            path.moveTo(x, y); path.lineTo(x + t * 0.12f, y + t * 0.12f)
            path.moveTo(x + t, y); path.lineTo(x + t * 0.88f, y + t * 0.12f)
            x += t
        }
        y += t
    }
    val stroke = Stroke(width = 1.dp.toPx())
    onDrawBehind { drawPath(path, color, style = stroke) }
}

val ClockStyle = TextStyle(fontFamily = Vazir, fontWeight = FontWeight.Light, fontSize = 56.sp)
