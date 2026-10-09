package ir.nama.launcher.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import ir.nama.core.Cities
import ir.nama.core.Jalali
import ir.nama.core.JalaliDate
import ir.nama.core.PersianText
import ir.nama.core.PrayTimes
import ir.nama.launcher.CrashGuard
import ir.nama.launcher.Nama
import ir.nama.launcher.R
import ir.nama.launcher.ui.onboarding.OnboardingActivity
import ir.nama.launcher.ui.settings.SettingsActivity
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** App drawer icon: setup the first time, settings afterwards. */
class EntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = when {
            CrashGuard.inSafeMode(this) || !Nama.ready -> SafeModeActivity::class.java
            !Nama.settings.onboarded -> OnboardingActivity::class.java
            else -> SettingsActivity::class.java
        }
        startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}

/**
 * Safe mode. Runs in its own process with plain Android views and touches none of Nama's data
 * loading code, so it keeps working even if the home screen crashes. It always offers a way out:
 * pick another launcher, open any app, or reset Nama's layout.
 */
class SafeModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            setBackgroundColor(Color.parseColor("#121716"))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        fun text(t: String, size: Float, color: String = "#E8ECEA", bold: Boolean = false) = TextView(this).apply {
            text = t; textSize = size; setTextColor(Color.parseColor(color))
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, pad / 3, 0, pad / 3)
        }
        fun button(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t; isAllCaps = false; setOnClickListener { onClick() }
        }
        root.addView(text("حالت امن نما", 22f, "#5FD0C6", bold = true))
        root.addView(text("نما چند بار پشت سر هم به مشکل خورد، برای همین در حالت امن باز شد. گوشی شما سالم است و همه برنامه‌ها سر جایشان هستند.", 15f))
        root.addView(button("انتخاب یک لانچر دیگر (پیش‌فرض گوشی)") {
            try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (e: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        })
        root.addView(button("دوباره امتحان کن (خروج از حالت امن)") {
            CrashGuard.leaveSafeMode(this)
            restartHome()
        })
        root.addView(button("بازنشانی چیدمان نما و امتحان دوباره") {
            // Moves the saved layout aside (it is kept as a backup file), then restarts.
            try {
                val dir = File(filesDir, "nama")
                File(dir, "layout.json").takeIf { it.exists() }?.renameTo(File(dir, "layout.backup-${System.currentTimeMillis()}.json"))
            } catch (_: Exception) {
            }
            CrashGuard.leaveSafeMode(this)
            restartHome()
        })
        root.addView(button("کپی گزارش خطا") {
            try {
                val cm = getSystemService(android.content.ClipboardManager::class.java)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Nama crash", CrashGuard.lastTrace(this)))
                Toast.makeText(this, "کپی شد", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
            }
        })
        root.addView(text("برنامه‌ها", 16f, "#F1D48F", bold = true))
        // A plain list of apps so the phone stays usable from here.
        val la = getSystemService(LauncherApps::class.java)
        val apps = try {
            la.getActivityList(null, Process.myUserHandle()).filter { it.componentName.packageName != packageName }.sortedBy { it.label.toString() }
        } catch (e: Exception) { emptyList() }
        val list = ListView(this)
        list.adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, apps.map { it.label.toString() }) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(Color.parseColor("#E8ECEA"))
                return v
            }
        }
        list.setOnItemClickListener { _, _, pos, _ ->
            try { la.startMainActivity(apps[pos].componentName, Process.myUserHandle(), null, null) } catch (_: Exception) {}
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun restartHome() {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        try { startActivity(i) } catch (_: Exception) {}
        finish()
        // Restart the main process so it reloads cleanly.
        Process.killProcess(Process.myPid())
    }
}

/** Big Persian clock while charging (nightstand). Tap anywhere to close. */
class NightstandActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var time: TextView
    private lateinit var date: TextView
    private lateinit var extra: TextView

    private val tick = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 20_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val clock = try { ResourcesCompat.getFont(this, R.font.lalezar) } catch (e: Exception) { Typeface.DEFAULT }
        val body = try { ResourcesCompat.getFont(this, R.font.vazirmatn_light) } catch (e: Exception) { Typeface.DEFAULT }
        time = TextView(this).apply { textSize = 110f; setTextColor(Color.parseColor("#F1D48F")); typeface = clock; gravity = Gravity.CENTER }
        date = TextView(this).apply { textSize = 24f; setTextColor(Color.parseColor("#C9D6E2")); typeface = body; gravity = Gravity.CENTER }
        extra = TextView(this).apply { textSize = 16f; setTextColor(Color.parseColor("#7F93A8")); typeface = body; gravity = Gravity.CENTER }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            addView(time); addView(date); addView(extra)
            setOnClickListener { finish() }
        }
        setContentView(root)
    }

    private fun update() {
        val now = LocalTime.now()
        val today = LocalDate.now()
        time.text = PersianText.toFaDigits("%d:%02d".format(now.hour, now.minute))
        date.text = "${Jalali.WEEKDAYS[Jalali.weekIndex(today)]} ${JalaliDate.from(today).formatLong()}"
        val lines = mutableListOf<String>()
        if (Nama.ready) {
            val pct = Nama.context.batteryPercent.value
            lines += PersianText.toFaDigits("⚡ $pct٪")
            val c = Cities.byId(Nama.settings.cityId)
            val t = PrayTimes.compute(today, c.lat, c.lng)
            lines += "اذان صبح " + PrayTimes.format(t.getValue(PrayTimes.Time.FAJR))
        }
        extra.text = lines.joinToString("   ·   ")
    }

    override fun onResume() { super.onResume(); handler.post(tick) }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }
}
