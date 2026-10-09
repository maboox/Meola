package ir.nama.launcher.system

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import ir.nama.launcher.Nama
import ir.nama.launcher.data.AppEntry
import ir.nama.launcher.tr
import java.time.LocalDate
import java.time.ZoneId

object Actions {
    fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        toast(context, tr("برنامه‌ای برای این کار پیدا نشد.", "No app can do this."))
        false
    } catch (e: SecurityException) {
        toast(context, tr("اجازه این کار داده نشده است.", "Not allowed."))
        false
    }

    /** Starts an app with a reveal animation from its icon. */
    fun launchApp(context: Context, e: AppEntry, source: View? = null, bounds: Rect? = null): Boolean = try {
        val la = context.getSystemService(LauncherApps::class.java)
        val opts = source?.let {
            ActivityOptions.makeClipRevealAnimation(it, bounds?.left ?: 0, bounds?.top ?: 0, bounds?.width() ?: it.width, bounds?.height() ?: it.height).toBundle()
        }
        la.startMainActivity(e.component, e.user, bounds, opts)
        Nama.store.recordLaunch(e.key)
        true
    } catch (ex: Exception) {
        toast(context, tr("باز کردن برنامه ممکن نشد.", "Could not open the app."))
        false
    }

    fun appInfo(context: Context, e: AppEntry) {
        try {
            context.getSystemService(LauncherApps::class.java).startAppDetailsActivity(e.component, e.user, null, null)
        } catch (ex: Exception) {
            start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${e.packageName}")))
        }
    }

    fun uninstall(context: Context, e: AppEntry) {
        @Suppress("DEPRECATION")
        start(context, Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:${e.packageName}")).putExtra(Intent.EXTRA_USER, e.user))
    }

    @SuppressLint("WrongConstant", "PrivateApi")
    fun expandNotifications(context: Context) {
        val ok = try {
            val sbm = context.getSystemService("statusbar")
            val m = Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel")
            m.invoke(sbm)
            true
        } catch (e: Throwable) {
            false
        }
        if (!ok && !LockScreenService.notifications()) {
            toast(context, tr("برای باز کردن اعلان‌ها، دسترسی «قفل صفحه» را در تنظیمات نما روشن کنید.", "Enable the lock-screen service in Nama settings."))
        }
    }

    fun lockScreen(context: Context): Boolean {
        if (LockScreenService.lock()) return true
        toast(context, tr("برای قفل با دو ضربه، دسترسی آن را در تنظیمات نما › دسترسی‌ها روشن کنید.", "Enable the lock-screen service in Nama settings › Permissions."))
        return false
    }

    fun setAlarm(context: Context, hour: Int, minute: Int, label: String? = null) {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        label?.let { i.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        start(context, i)
    }

    fun setTimer(context: Context, seconds: Int) {
        start(context, Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, seconds).putExtra(AlarmClock.EXTRA_SKIP_UI, false))
    }

    fun addCalendarReminder(context: Context, title: String, dayOffset: Int, hour: Int?, minute: Int?) {
        val date = LocalDate.now().plusDays(dayOffset.toLong())
        val start = date.atTime(hour ?: 9, minute ?: 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val i = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 30 * 60 * 1000)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, hour == null)
        start(context, i)
    }

    /** Opens the dialer with a USSD code; the user presses call. # must be encoded. */
    fun dialUssd(context: Context, code: String) {
        start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(code))))
    }

    fun dial(context: Context, phone: String) = start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))))
    fun sms(context: Context, phone: String) = start(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone))))

    fun openUrl(context: Context, url: String) = start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    fun webSearch(context: Context, q: String) = openUrl(context, Nama.settings.searchEngine.url + Uri.encode(q))

    fun copy(context: Context, text: String, sensitive: Boolean = false, label: String = "Nama") {
        try {
            val cm = context.getSystemService(ClipboardManager::class.java)
            val clip = ClipData.newPlainText(label, text)
            if (sensitive && Build.VERSION.SDK_INT >= 24) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(if (Build.VERSION.SDK_INT >= 33) android.content.ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
                }
            }
            cm.setPrimaryClip(clip)
            if (Build.VERSION.SDK_INT < 33) toast(context, tr("کپی شد", "Copied"))
        } catch (e: Exception) {
            toast(context, tr("کپی ممکن نشد", "Copy failed"))
        }
    }

    fun share(context: Context, text: String) {
        start(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
    }

    /** Asks Android to make Nama the home app, or opens the default-apps screen. */
    fun requestDefaultLauncher(activity: Activity, requestCode: Int = 4242) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = activity.getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    @Suppress("DEPRECATION")
                    activity.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), requestCode)
                    return
                }
            }
        } catch (_: Exception) {
        }
        openHomeSettings(activity)
    }

    fun openHomeSettings(context: Context) {
        if (!start(context, Intent(Settings.ACTION_HOME_SETTINGS))) start(context, Intent(Settings.ACTION_SETTINGS))
    }

    fun isDefaultLauncher(context: Context): Boolean = try {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = context.packageManager.resolveActivity(i, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        ri?.activityInfo?.packageName == context.packageName
    } catch (e: Exception) { false }

    fun mediaKey(context: Context, keyCode: Int) {
        try {
            val am = context.getSystemService(AudioManager::class.java)
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (_: Exception) {
        }
    }

    private var torchOn = false
    private var torchCallbackRegistered = false

    fun toggleTorch(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(CameraManager::class.java)
            if (!torchCallbackRegistered) {
                cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { torchOn = enabled }
                }, null)
                torchCallbackRegistered = true
            }
            val id = cm.cameraIdList.firstOrNull { cid ->
                cm.getCameraCharacteristics(cid).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false
            torchOn = !torchOn
            cm.setTorchMode(id, torchOn)
            true
        } catch (e: Exception) {
            toast(context, tr("چراغ‌قوه در دسترس نیست", "Flashlight unavailable"))
            false
        }
    }

    fun settingsPanel(context: Context, action: String) = start(context, Intent(action))
}
