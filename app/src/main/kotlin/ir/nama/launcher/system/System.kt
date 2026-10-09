package ir.nama.launcher.system

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.ContactsContract
import android.service.dreams.DreamService
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.Gravity
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import ir.nama.core.AppCategory
import ir.nama.core.IranCalendar
import ir.nama.core.Jalali
import ir.nama.core.JalaliDate
import ir.nama.core.NewsLogic
import ir.nama.core.PersianText
import ir.nama.launcher.Nama
import ir.nama.launcher.R
import ir.nama.launcher.data.Pomodoro
import ir.nama.launcher.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime

object Notifier {
    const val CH_REMINDERS = "reminders"
    const val CH_NEWS = "news"
    const val CH_FOCUS = "focus"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_REMINDERS, "یادآورها", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_NEWS, "خبرهای فوری", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_FOCUS, "پومودورو و تمرکز", NotificationManager.IMPORTANCE_HIGH))
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun post(context: Context, id: Int, channel: String, title: String, text: String, link: String? = null) {
        if (!canPost(context)) return
        try {
            ensureChannels(context)
            val intent = if (link != null) Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link))
            else Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            context.getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (e: Exception) {
            Log.w("Nama", "notify failed", e)
        }
    }
}

object Schedules {
    private const val ACTION_DAILY = "ir.nama.launcher.DAILY"
    private const val ACTION_POMODORO = "ir.nama.launcher.POMODORO"

    private fun pending(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, ScheduleReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** Inexact repeating check every ~3 hours (cheap: no exact alarms, no wakeups of note). */
    fun ensureDaily(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 15 * 60 * 1000L,
            3 * AlarmManager.INTERVAL_HOUR,
            pending(context, ACTION_DAILY)
        )
    }

    fun schedulePomodoro(context: Context, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, ACTION_POMODORO)
        // setWindow is allowed without the exact-alarm permission and is accurate enough (±1 minute).
        am.setWindow(AlarmManager.RTC_WAKEUP, at, 60_000L, pi)
    }

    fun cancelPomodoro(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, ACTION_POMODORO))
    }

    fun handles(action: String?) = action == ACTION_DAILY || action == ACTION_POMODORO
    fun isPomodoro(action: String?) = action == ACTION_POMODORO
}

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Nama.ready || !Schedules.handles(intent.action)) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(25_000) {
                    if (Schedules.isPomodoro(intent.action)) Checks.pomodoroFinished(context) else Checks.daily(context)
                }
            } catch (e: Exception) {
                Log.w("Nama", "scheduled check failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Nama.ready) return
        try {
            Schedules.ensureDaily(context)
            val p = Nama.store.personal.value.pomodoro
            if (p.running && p.endsAt > System.currentTimeMillis()) Schedules.schedulePomodoro(context, p.endsAt)
        } catch (e: Exception) {
            Log.w("Nama", "boot reschedule failed", e)
        }
    }
}

object Checks {
    private fun once(key: String): Boolean {
        val store = Nama.store
        if (key in store.personal.value.notifiedKeys) return false
        store.personal.update { it.copy(notifiedKeys = (it.notifiedKeys + key).takeLast(200)) }
        return true
    }

    suspend fun daily(context: Context) {
        val s = Nama.settings
        if (!s.dailyChecks) return
        val today = LocalDate.now()
        val epoch = today.toEpochDay()
        val p = Nama.store.personal.value
        val hour = LocalTime.now().hour
        if (hour in 8..21) {
            // Bills due today or tomorrow.
            p.bills.forEach { b ->
                val due = b.dueEpochDay
                val paid = b.paidEpochDay != null && b.paidEpochDay >= due
                if (!paid && (due == epoch || due == epoch + 1) && once("bill:${b.id}:$due")) {
                    val whenTxt = if (due == epoch) "امروز" else "فردا"
                    Notifier.post(context, b.id.hashCode(), Notifier.CH_REMINDERS, "سررسید $whenTxt: ${b.title}",
                        if (b.amountToman > 0) "${PersianText.group(b.amountToman, true)} تومان" else "یادت نرود پرداخت کنی.")
                }
            }
            // Countdowns reaching today.
            p.countdowns.forEach { c ->
                val target = if (c.yearly) nextYearly(c.epochDay, today) else c.epochDay
                if (target == epoch && once("cd:${c.id}:$epoch")) {
                    Notifier.post(context, c.id.hashCode(), Notifier.CH_REMINDERS, c.title, "امروز است! 🎉")
                }
            }
            // Data package ending soon.
            p.dataPackage?.let { d ->
                val left = d.endEpochDay - epoch
                if (left in 0..2 && once("pkg:${d.endEpochDay}:$left")) {
                    Notifier.post(context, 7001, Notifier.CH_REMINDERS, "بسته اینترنت رو به پایان است",
                        PersianText.toFaDigits("$left روز تا پایان بسته ${d.title}"))
                }
            }
            // Birthdays from contacts.
            if (hour >= 9) birthdaysToday(context).forEach { name ->
                if (once("bd:$name:$epoch")) Notifier.post(context, name.hashCode(), Notifier.CH_REMINDERS, "تولد $name 🎂", "امروز تولد $name است. یک پیام تبریک بفرست!")
            }
        }
        // Breaking news.
        if (s.breakingNotifications) {
            val fresh = Nama.news.refresh()
            fresh.filter { NewsLogic.isBreaking(it) }.take(2).forEach { n ->
                if (once("news:${n.id}")) Notifier.post(context, n.id.hashCode(), Notifier.CH_NEWS, n.sourceName, n.title, n.link)
            }
        }
    }

    fun nextYearly(epochDay: Long, from: LocalDate): Long {
        val j = JalaliDate.from(LocalDate.ofEpochDay(epochDay))
        return IranCalendar.nextSolar(from, j.month, j.day).toEpochDay()
    }

    @SuppressLint("Range")
    fun birthdaysToday(context: Context): List<String> = birthdays(context).filter { it.second == 0L }.map { it.first }

    /** (name, days until birthday) for contacts with a birthday, sorted by nearest. */
    @SuppressLint("Range")
    fun birthdays(context: Context): List<Pair<String, Long>> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val out = mutableListOf<Pair<String, Long>>()
        val today = LocalDate.now()
        try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.CommonDataKinds.Event.START_DATE),
                "${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.CommonDataKinds.Event.TYPE}=?",
                arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val date = c.getString(1) ?: continue
                    val m = Regex("(\\d{4}|-)-?(\\d{2})-(\\d{2})").find(date) ?: continue
                    val month = m.groupValues[2].toInt()
                    val day = m.groupValues[3].toInt()
                    try {
                        var next = LocalDate.of(today.year, month, day)
                        if (next.isBefore(today)) next = next.plusYears(1)
                        out += name to (next.toEpochDay() - today.toEpochDay())
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("Nama", "birthday query failed", e)
        }
        return out.distinctBy { it.first }.sortedBy { it.second }
    }

    fun pomodoroFinished(context: Context) {
        val store = Nama.store
        val p = store.personal.value.pomodoro
        if (!p.running) return
        val today = LocalDate.now().toEpochDay()
        if (!p.onBreak) {
            val done = if (p.day == today) p.doneToday + 1 else 1
            val next = Pomodoro(true, true, System.currentTimeMillis() + p.breakMinutes * 60_000L, p.workMinutes, p.breakMinutes, done, today)
            store.personal.update { it.copy(pomodoro = next) }
            Schedules.schedulePomodoro(context, next.endsAt)
            Notifier.post(context, 9001, Notifier.CH_FOCUS, "یک پومودورو تمام شد 🍅", PersianText.toFaDigits("${p.breakMinutes} دقیقه استراحت کن."))
        } else {
            store.personal.update { it.copy(pomodoro = p.copy(running = false, onBreak = false, endsAt = 0L)) }
            Notifier.post(context, 9001, Notifier.CH_FOCUS, "استراحت تمام شد", "آماده دور بعدی هستی؟")
        }
        store.personal.flushNow()
    }
}

class NamaNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        ir.nama.launcher.data.NotificationRepo.connected.value = true
        refresh()
    }

    override fun onListenerDisconnected() {
        ir.nama.launcher.data.NotificationRepo.connected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val important = isImportant(sbn)
            ir.nama.launcher.data.NotificationRepo.posted(sbn, important)
        } catch (_: Exception) {
        }
        refresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = refresh()

    private fun refresh() {
        try {
            ir.nama.launcher.data.NotificationRepo.setActive(activeNotifications?.toList() ?: emptyList())
        } catch (_: Exception) {
        }
    }

    private fun isImportant(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (n.category in listOf(android.app.Notification.CATEGORY_MESSAGE, android.app.Notification.CATEGORY_CALL, android.app.Notification.CATEGORY_ALARM, android.app.Notification.CATEGORY_REMINDER, android.app.Notification.CATEGORY_EVENT)) return true
        if (!Nama.ready) return false
        val entry = Nama.apps.byPackage(sbn.packageName) ?: return false
        return entry.category == AppCategory.FINANCE || entry.category == AppCategory.MESSAGING
    }
}

/**
 * Optional accessibility service. Its only job is locking the screen (double tap) and opening the
 * notification shade when asked. It does not read window content (canRetrieveWindowContent=false).
 */
class LockScreenService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        @Volatile var instance: LockScreenService? = null

        fun lock(): Boolean {
            val s = instance ?: return false
            return Build.VERSION.SDK_INT >= 28 && s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }

        fun notifications(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) == true
    }
}

/** A Persian clock screensaver (Settings > Display > Screen saver, where the phone offers it). */
class NamaDreamService : DreamService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var time: TextView
    private lateinit var date: TextView
    private val tick = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        val font = try { ResourcesCompat.getFont(this, R.font.lalezar) } catch (e: Exception) { Typeface.DEFAULT }
        val body = try { ResourcesCompat.getFont(this, R.font.vazirmatn_regular) } catch (e: Exception) { Typeface.DEFAULT }
        time = TextView(this).apply { textSize = 96f; setTextColor(Color.parseColor("#F1D48F")); typeface = font; gravity = Gravity.CENTER }
        date = TextView(this).apply { textSize = 22f; setTextColor(Color.parseColor("#9FB3C8")); typeface = body; gravity = Gravity.CENTER }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            addView(time); addView(date)
        }
        setContentView(root)
    }

    private fun update() {
        val now = LocalTime.now()
        val today = LocalDate.now()
        val j = JalaliDate.from(today)
        time.text = PersianText.toFaDigits("%d:%02d".format(now.hour, now.minute))
        date.text = "${Jalali.WEEKDAYS[Jalali.weekIndex(today)]} ${j.formatLong()}"
    }

    override fun onDreamingStarted() { super.onDreamingStarted(); handler.post(tick) }
    override fun onDreamingStopped() { handler.removeCallbacks(tick); super.onDreamingStopped() }
}
