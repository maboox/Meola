package ir.nama.launcher.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ir.nama.core.Jalali
import ir.nama.core.JalaliDate
import ir.nama.core.PersianText
import ir.nama.launcher.Nama
import ir.nama.launcher.data.WidgetInstance
import ir.nama.launcher.data.WidgetType
import ir.nama.launcher.num
import ir.nama.launcher.tr
import ir.nama.launcher.ui.home.HomeController
import ir.nama.launcher.ui.home.HomeEnv
import ir.nama.launcher.ui.theme.LocalNamaStyle
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime

/** Everything a widget needs to draw itself at its current size. */
data class WScope(
    val ctrl: HomeController,
    val env: HomeEnv,
    val w: WidgetInstance,
    val itemId: String,
    val size: WSize,
    val widthDp: Float,
    val heightDp: Float
) {
    val wide get() = size.w >= 4
    val tall get() = size.h >= 2
    val small get() = size.w <= 2 && size.h <= 1
}

// ------------------------------------------------------------------------------------------------
// Shared helpers
// ------------------------------------------------------------------------------------------------

/** Ticks every minute on the minute. */
@Composable
fun rememberNow(): LocalDateTime {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            val n = LocalDateTime.now()
            value = n
            delay((60 - n.second) * 1000L - n.nano / 1_000_000 + 50)
        }
    }
    return now
}

fun weekdayName(d: LocalDate): String = if (Nama.isFa) Jalali.WEEKDAYS[Jalali.weekIndex(d)] else Jalali.WEEKDAYS_EN[Jalali.weekIndex(d)]

fun monthName(j: JalaliDate): String = if (Nama.isFa) j.monthName else Jalali.MONTHS_EN[j.month - 1]

fun jalaliLong(d: LocalDate): String {
    val j = JalaliDate.from(d)
    return num(j.day) + " " + monthName(j) + " " + num(j.year)
}

fun jalaliShort(d: LocalDate): String {
    val j = JalaliDate.from(d)
    return num(j.day) + " " + monthName(j)
}

fun hhmm(h: Int, m: Int) = num("${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}")

fun formatBytes(b: Long): String {
    val mb = b / (1024.0 * 1024.0)
    return if (mb >= 1024) num(PersianText.groupDecimal(mb / 1024.0, false, 1)) + tr(" گیگ", " GB")
    else num(PersianText.groupDecimal(mb, false, 0)) + tr(" مگ", " MB")
}

// ------------------------------------------------------------------------------------------------
// Building blocks for a consistent look
// ------------------------------------------------------------------------------------------------

/** The rounded card every widget sits on (transparent in the minimal style). */
@Composable
fun WidgetCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val s = LocalNamaStyle.current
    Box(
        modifier.fillMaxSize()
            .clip(RoundedCornerShape(s.corner))
            .background(s.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content
    )
}

/** Small header: icon and title in the accent colour. */
@Composable
fun WHeader(icon: ImageVector, title: String, trailing: String? = null) {
    val s = LocalNamaStyle.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Icon(icon, null, tint = s.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, color = s.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = s.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
fun WText(
    text: String,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Normal,
    secondary: Boolean = false,
    color: Color? = null,
    maxLines: Int = 1,
    modifier: Modifier = Modifier
) {
    val s = LocalNamaStyle.current
    Text(
        text, modifier = modifier, color = color ?: if (secondary) s.onSurfaceVariant else s.onSurface,
        fontSize = size, fontWeight = weight, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        lineHeight = (size.value * 1.45f).sp
    )
}

/** A row with a leading tonal icon, used in list-like widgets. */
@Composable
fun WRow(icon: ImageVector, title: String, sub: String? = null, trailing: String? = null, trailingColor: Color? = null, onClick: (() -> Unit)? = null) {
    val s = LocalNamaStyle.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(s.accentContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = s.onAccentContainer, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            WText(title, 14.sp, FontWeight.Medium)
            if (sub != null) WText(sub, 11.sp, secondary = true)
        }
        if (trailing != null) WText(trailing, 13.sp, FontWeight.Medium, color = trailingColor)
    }
}

/** A pill-shaped tonal button inside widgets. */
@Composable
fun WPill(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val s = LocalNamaStyle.current
    Row(
        modifier.clip(RoundedCornerShape(50)).background(s.accentContainer).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = s.onAccentContainer, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = s.onAccentContainer, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Shown when a widget needs something before it can work (a permission, a setting…). */
@Composable
fun WEmpty(icon: ImageVector, text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val s = LocalNamaStyle.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = s.accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.size(6.dp))
        WText(text, 13.sp, secondary = true, maxLines = 3)
        if (action != null && onAction != null) {
            Spacer(Modifier.size(8.dp))
            WPill(action, onClick = onAction)
        }
    }
}

@Composable
fun ColumnScope.WSpacer() = Spacer(Modifier.weight(1f))

@Composable
fun RowScope.WSpacer() = Spacer(Modifier.weight(1f))

fun Dp.min(o: Dp) = if (this < o) this else o

// ------------------------------------------------------------------------------------------------
// Dispatcher
// ------------------------------------------------------------------------------------------------

@Composable
fun WidgetContent(sc: WScope) {
    when (sc.w.type) {
        WidgetType.GLANCE -> GlanceWidget(sc)
        WidgetType.CLOCK -> ClockWidget(sc)
        WidgetType.CALENDAR -> CalendarWidget(sc)
        WidgetType.TODAY -> TodayWidget(sc)
        WidgetType.PRAYER -> PrayerWidget(sc)
        WidgetType.COUNTDOWN -> CountdownWidget(sc)
        WidgetType.BIRTHDAYS -> BirthdaysWidget(sc)
        WidgetType.WEATHER -> WeatherWidget(sc)
        WidgetType.BLACKOUT -> BlackoutWidget(sc)
        WidgetType.ODD_EVEN -> OddEvenWidget(sc)
        WidgetType.FOOTBALL -> FootballWidget(sc)
        WidgetType.PRICES -> PricesWidget(sc)
        WidgetType.BILLS -> BillsWidget(sc)
        WidgetType.BANK_CARDS -> BankCardsWidget(sc)
        WidgetType.EXPENSES -> ExpensesWidget(sc)
        WidgetType.TODO -> TodoWidget(sc, shopping = false)
        WidgetType.SHOPPING -> TodoWidget(sc, shopping = true)
        WidgetType.NOTES -> NotesWidget(sc)
        WidgetType.HABITS -> HabitsWidget(sc)
        WidgetType.POMODORO -> PomodoroWidget(sc)
        WidgetType.BATTERY -> BatteryWidget(sc)
        WidgetType.DATA_USAGE -> DataUsageWidget(sc)
        WidgetType.USSD -> UssdWidget(sc)
        WidgetType.TOGGLES -> TogglesWidget(sc)
        WidgetType.MUSIC -> MusicWidget(sc)
        WidgetType.CONTACTS -> ContactsWidget(sc)
        WidgetType.HAFEZ -> HafezWidget(sc)
        WidgetType.POEM -> PoemWidget(sc)
        WidgetType.QUOTE -> QuoteWidget(sc)
        WidgetType.VERSE -> VerseWidget(sc)
        WidgetType.NEWS -> NewsWidget(sc)
        WidgetType.NOTIF_DIGEST -> NotifDigestWidget(sc)
        WidgetType.USAGE -> UsageWidget(sc)
        WidgetType.FOCUS -> FocusWidget(sc)
        WidgetType.SPACE -> SpaceWidget(sc)
        WidgetType.SMART_STACK -> SmartStackWidget(sc)
        WidgetType.SYSTEM -> SystemWidget(sc)
    }
}

/** Picks a fitting widget for the time of day; swipe-free: tap the dots to switch. */
@Composable
private fun SmartStackWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val now = rememberNow()
    val auto = when (now.hour) {
        in 5..10 -> WidgetType.TODAY
        in 11..15 -> if ("economy" in sc.env.settings.interests || "crypto" in sc.env.settings.interests) WidgetType.PRICES else WidgetType.TODO
        in 16..19 -> WidgetType.WEATHER
        else -> WidgetType.HAFEZ
    }
    val order = listOf(auto, WidgetType.TODAY, WidgetType.WEATHER, WidgetType.PRAYER, WidgetType.HAFEZ).distinct()
    var index by remember(auto) { mutableIntStateOf(0) }
    LaunchedEffect(auto) { index = 0 }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(order[index % order.size], transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "stack") { t ->
            WidgetContent(sc.copy(w = sc.w.copy(type = t)))
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            order.indices.forEach { i ->
                Box(
                    Modifier.size(if (i == index % order.size) 7.dp else 5.dp).clip(CircleShape)
                        .background(if (i == index % order.size) s.accent else s.onSurfaceVariant.copy(alpha = 0.4f))
                        .clickable { index = i }
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Android app widgets
// ------------------------------------------------------------------------------------------------

/** Host view that turns a long press into the Nama widget menu without clicking the widget. */
class NamaHostView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null
    private var longPressed = false
    private var downX = 0f
    private var downY = 0f
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val check = Runnable {
        longPressed = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onLongPress?.invoke()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressed = false
                downX = ev.x; downY = ev.y
                postDelayed(check, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (Math.abs(ev.x - downX) > slop || Math.abs(ev.y - downY) > slop) removeCallbacks(check)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(check)
        }
        return longPressed
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (longPressed) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) longPressed = false
            return true
        }
        return super.onTouchEvent(event)
    }
}

class NamaWidgetHost(context: Context, id: Int) : AppWidgetHost(context, id) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView = NamaHostView(context)
}

object AppWidgets {
    const val HOST_ID = 0x4E414D
    private var host: NamaWidgetHost? = null
    private val views = HashMap<Int, AppWidgetHostView>()

    fun host(context: Context): AppWidgetHost =
        host ?: NamaWidgetHost(context.applicationContext, HOST_ID).also { host = it }

    fun startListening(context: Context) {
        try { host(context).startListening() } catch (e: Exception) { Log.w("Nama", "widget host start failed", e) }
    }

    fun stopListening(context: Context) {
        try { host(context).stopListening() } catch (e: Exception) { Log.w("Nama", "widget host stop failed", e) }
    }

    fun view(context: Context, id: Int): AppWidgetHostView? {
        views[id]?.let { v ->
            (v.parent as? ViewGroup)?.removeView(v)
            return v
        }
        return try {
            val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(id) ?: return null
            host(context).createView(context, id, info).also { views[id] = it }
        } catch (e: Exception) {
            Log.w("Nama", "widget view failed", e)
            null
        }
    }

    fun delete(context: Context, id: Int) {
        views.remove(id)
        try { host(context).deleteAppWidgetId(id) } catch (_: Exception) {}
    }

    fun updateSize(v: AppWidgetHostView, widthDp: Int, heightDp: Int) {
        try {
            v.updateAppWidgetOptions(Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            })
        } catch (_: Exception) {
        }
    }

    /** Grid span for an app widget, from its declared size (Android 12+ gives it directly). */
    fun spanFor(info: AppWidgetProviderInfo, cellWdp: Float, cellHdp: Float, density: Float, cols: Int, rows: Int): WSize {
        if (android.os.Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            return WSize(info.targetCellWidth.coerceIn(1, cols), info.targetCellHeight.coerceIn(1, rows))
        }
        val wdp = info.minWidth / density
        val hdp = info.minHeight / density
        return WSize(Math.ceil((wdp / cellWdp).toDouble()).toInt().coerceIn(1, cols), Math.ceil((hdp / cellHdp).toDouble()).toInt().coerceIn(1, rows))
    }
}

@Composable
private fun SystemWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(s.corner))) {
        AndroidView(
            factory = { ctx ->
                AppWidgets.view(ctx, sc.w.appWidgetId) ?: android.widget.TextView(ctx).apply {
                    text = tr("این ویجت دیگر در دسترس نیست", "This widget is no longer available")
                    setTextColor(android.graphics.Color.WHITE)
                }
            },
            update = { v ->
                if (v is AppWidgetHostView) AppWidgets.updateSize(v, sc.widthDp.toInt(), sc.heightDp.toInt())
                if (v is NamaHostView) v.onLongPress = { sc.ctrl.itemMenu = sc.ctrl.currentPage to sc.itemId }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
