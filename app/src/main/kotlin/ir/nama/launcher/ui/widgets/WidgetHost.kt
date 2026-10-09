package ir.nama.launcher.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ir.nama.launcher.Nama
import ir.nama.launcher.data.WidgetInstance
import ir.nama.launcher.data.WidgetType
import ir.nama.launcher.tr
import ir.nama.launcher.ui.common.NamaCard
import ir.nama.launcher.ui.home.HomeController
import ir.nama.launcher.ui.home.HomeEnv
import ir.nama.launcher.ui.theme.LocalNamaStyle
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** Hosts Android app widgets (from other apps) for the home screen. */
object AppWidgets {
    const val HOST_ID = 0x4E414D
    private var host: AppWidgetHost? = null
    private val views = HashMap<Int, AppWidgetHostView>()

    fun host(context: Context): AppWidgetHost =
        host ?: AppWidgetHost(context.applicationContext, HOST_ID).also { host = it }

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
            val opts = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            }
            v.updateAppWidgetOptions(opts)
        } catch (_: Exception) {
        }
    }
}

/** Ticks every minute on the minute (for clocks). */
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

/** Common frame: card, title row with a ⋯ button that opens the widget menu. */
@Composable
fun WidgetFrame(
    ctrl: HomeController,
    pageIndex: Int,
    w: WidgetInstance,
    title: String?,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(14.dp),
    content: @Composable () -> Unit
) {
    val s = LocalNamaStyle.current
    NamaCard(
        Modifier.fillMaxWidth(),
        onClick = onClick,
        onLongClick = { ctrl.widgetMenu = pageIndex to w },
        padding = padding
    ) {
        if (title != null) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = s.subText, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (trailing != null) Text(trailing, color = s.subText, fontSize = 11.sp)
                Text(
                    "⋯", color = s.subText, fontSize = 16.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { ctrl.widgetMenu = pageIndex to w }.padding(horizontal = 6.dp)
                )
            }
        }
        content()
    }
}

@Composable
fun WidgetHost(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    when (w.type) {
        WidgetType.CLOCK -> ClockWidget(ctrl, env, w, pageIndex)
        WidgetType.CLOCK_WORDS -> ClockWordsWidget(ctrl, env, w, pageIndex)
        WidgetType.CALENDAR -> CalendarWidget(ctrl, env, w, pageIndex)
        WidgetType.TODAY -> TodayWidget(ctrl, env, w, pageIndex)
        WidgetType.COUNTDOWN -> CountdownWidget(ctrl, env, w, pageIndex)
        WidgetType.PRAYER -> PrayerWidget(ctrl, env, w, pageIndex)
        WidgetType.BIRTHDAYS -> BirthdaysWidget(ctrl, env, w, pageIndex)
        WidgetType.PRICES -> PricesWidget(ctrl, env, w, pageIndex)
        WidgetType.BILLS -> BillsWidget(ctrl, env, w, pageIndex)
        WidgetType.BANK_CARDS -> BankCardsWidget(ctrl, env, w, pageIndex)
        WidgetType.EXPENSES -> ExpensesWidget(ctrl, env, w, pageIndex)
        WidgetType.USSD -> UssdWidget(ctrl, env, w, pageIndex)
        WidgetType.DATA_USAGE -> DataUsageWidget(ctrl, env, w, pageIndex)
        WidgetType.BATTERY -> BatteryWidget(ctrl, env, w, pageIndex)
        WidgetType.TOGGLES -> TogglesWidget(ctrl, env, w, pageIndex)
        WidgetType.CONTACTS -> ContactsWidget(ctrl, env, w, pageIndex)
        WidgetType.MUSIC -> MusicWidget(ctrl, env, w, pageIndex)
        WidgetType.WEATHER -> WeatherWidget(ctrl, env, w, pageIndex)
        WidgetType.BLACKOUT -> BlackoutWidget(ctrl, env, w, pageIndex)
        WidgetType.ODD_EVEN -> OddEvenWidget(ctrl, env, w, pageIndex)
        WidgetType.FOOTBALL -> FootballWidget(ctrl, env, w, pageIndex)
        WidgetType.HAFEZ -> HafezWidget(ctrl, env, w, pageIndex)
        WidgetType.POEM -> PoemWidget(ctrl, env, w, pageIndex)
        WidgetType.QUOTE -> QuoteWidget(ctrl, env, w, pageIndex)
        WidgetType.VERSE -> VerseWidget(ctrl, env, w, pageIndex)
        WidgetType.TODO -> TodoWidget(ctrl, env, w, pageIndex, shopping = false)
        WidgetType.SHOPPING -> TodoWidget(ctrl, env, w, pageIndex, shopping = true)
        WidgetType.NOTES -> NotesWidget(ctrl, env, w, pageIndex)
        WidgetType.HABITS -> HabitsWidget(ctrl, env, w, pageIndex)
        WidgetType.POMODORO -> PomodoroWidget(ctrl, env, w, pageIndex)
        WidgetType.USAGE -> UsageWidget(ctrl, env, w, pageIndex)
        WidgetType.FOCUS -> FocusWidget(ctrl, env, w, pageIndex)
        WidgetType.SPACE -> SpaceWidget(ctrl, env, w, pageIndex)
        WidgetType.NOTIF_DIGEST -> NotifDigestWidget(ctrl, env, w, pageIndex)
        WidgetType.NEWS_TICKER -> NewsTickerWidget(ctrl, env, w, pageIndex)
        WidgetType.NEWS_DIGEST -> NewsDigestWidget(ctrl, env, w, pageIndex)
        WidgetType.MORNING -> MorningWidget(ctrl, env, w, pageIndex)
        WidgetType.SMART_STACK -> SmartStackWidget(ctrl, env, w, pageIndex)
        WidgetType.SYSTEM -> SystemWidget(ctrl, w, pageIndex)
    }
}

@Composable
private fun SystemWidget(ctrl: HomeController, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val height = if (w.heightDp > 0) w.heightDp else 160
    WidgetFrame(ctrl, pageIndex, w, title = null, padding = PaddingValues(4.dp)) {
        Box {
            AndroidView(
                factory = { ctx ->
                    AppWidgets.view(ctx, w.appWidgetId) ?: android.widget.TextView(ctx).apply {
                        text = tr("این ویجت دیگر در دسترس نیست", "This widget is no longer available")
                    }
                },
                update = { v -> if (v is AppWidgetHostView) AppWidgets.updateSize(v, 360, height) },
                modifier = Modifier.fillMaxWidth().height(height.dp)
            )
            Text(
                "⋯", color = s.subText, fontSize = 16.sp,
                modifier = Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(8.dp))
                    .clickable { ctrl.widgetMenu = pageIndex to w }.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * Shows one widget at a time and picks it by time of day: morning card and weather in the
 * morning, to-dos during the day, prices in market hours, poetry in the evening.
 */
@Composable
private fun SmartStackWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val now = rememberNow()
    val auto = remember(now.hour) {
        when (now.hour) {
            in 5..9 -> WidgetType.MORNING
            in 10..12 -> WidgetType.TODO
            in 13..16 -> if ("economy" in env.settings.interests || "crypto" in env.settings.interests) WidgetType.PRICES else WidgetType.TODAY
            in 17..20 -> WidgetType.WEATHER
            else -> WidgetType.HAFEZ
        }
    }
    val order = listOf(auto, WidgetType.TODAY, WidgetType.PRAYER, WidgetType.HAFEZ, WidgetType.PRICES, WidgetType.WEATHER).distinct()
    var index by remember(auto) { mutableIntStateOf(0) }
    LaunchedEffect(auto) { index = 0 }
    Column {
        AnimatedContent(order[index % order.size], label = "stack") { t ->
            WidgetHost(ctrl, env, w.copy(type = t), pageIndex)
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            order.indices.forEach { i ->
                Text(
                    if (i == index % order.size) "●" else "○", color = LocalNamaStyle.current.subText, fontSize = 9.sp,
                    modifier = Modifier.clickable { index = i }.padding(horizontal = 3.dp)
                )
            }
        }
    }
}
