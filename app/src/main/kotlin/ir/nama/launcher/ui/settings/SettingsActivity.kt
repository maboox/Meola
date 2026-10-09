package ir.nama.launcher.ui.settings

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ir.nama.core.AppCategory
import ir.nama.core.Cities
import ir.nama.core.NewsCategory
import ir.nama.core.NewsSourceDef
import ir.nama.core.RuleAction
import ir.nama.core.RuleCondition
import ir.nama.core.SortRule
import ir.nama.core.Space
import ir.nama.core.StyleId
import ir.nama.core.Trigger
import ir.nama.launcher.CrashGuard
import ir.nama.launcher.Nama
import ir.nama.launcher.context.SpaceManager
import ir.nama.launcher.data.Backup
import ir.nama.launcher.data.GestureAction
import ir.nama.launcher.data.IconShape
import ir.nama.launcher.data.NamaJson
import ir.nama.launcher.data.NotificationRepo
import ir.nama.launcher.data.SearchEngine
import ir.nama.launcher.logic.SmartSort
import ir.nama.launcher.logic.Templates
import ir.nama.launcher.num
import ir.nama.launcher.system.Actions
import ir.nama.launcher.system.LockScreenService
import ir.nama.launcher.system.Notifier
import ir.nama.launcher.tr
import ir.nama.launcher.ui.SafeModeActivity
import ir.nama.launcher.ui.common.AppIconImage
import ir.nama.launcher.ui.common.AppPickerDialog
import ir.nama.launcher.ui.common.ChipsSelector
import ir.nama.launcher.ui.common.ChoiceDialog
import ir.nama.launcher.ui.common.ConfirmDialog
import ir.nama.launcher.ui.common.TextInputDialog
import ir.nama.launcher.ui.common.TimeDialog
import ir.nama.launcher.ui.common.minuteText
import ir.nama.launcher.ui.home.AppSettingsDialog
import ir.nama.launcher.ui.home.ago
import ir.nama.launcher.ui.onboarding.OnboardingActivity
import ir.nama.launcher.ui.theme.NamaAppTheme
import kotlinx.coroutines.launch
import java.util.UUID

class SettingsActivity : AppCompatActivity() {
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (Nama.ready) Nama.context.refresh()
        recompose.value++
    }
    private val exportDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { export(it) } }
    private val importDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { import(it) } }
    private val recompose = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Nama.ready) { startActivity(Intent(this, SafeModeActivity::class.java)); finish(); return }
        val start = intent.getStringExtra("route") ?: "main"
        setContent {
            val settings by Nama.store.settings.flow.collectAsState()
            NamaAppTheme(rtl = settings.language != "en", minimal = settings.style == StyleId.MINIMAL) {
                @Suppress("UNUSED_VARIABLE") val tick = recompose.value
                SettingsRoot(start, this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        recompose.value++
    }

    fun ask(vararg perms: String) = permissions.launch(arrayOf(*perms))
    fun exportBackup() = exportDoc.launch("nama-backup-${System.currentTimeMillis() / 1000}.json")
    fun importBackup() = importDoc.launch(arrayOf("application/json", "text/plain", "*/*"))

    private fun export(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(NamaJson.encodeToString(Backup.serializer(), Nama.store.backup()).toByteArray()) }
            Actions.toast(this, tr("پشتیبان ذخیره شد", "Backup saved"))
        } catch (e: Exception) {
            Actions.toast(this, tr("ذخیره ممکن نشد", "Could not save"))
        }
    }

    private fun import(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return
            val b = NamaJson.decodeFromString(Backup.serializer(), text)
            Nama.store.restore(b)
            Actions.toast(this, tr("بازیابی شد", "Restored"))
        } catch (e: Exception) {
            Actions.toast(this, tr("این فایل پشتیبان نما نیست یا خراب است", "Not a valid Nama backup"))
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Building blocks
// ------------------------------------------------------------------------------------------------

private fun LazyListScope.header(text: String) = item {
    Text(text, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp))
}

@Composable
private fun Item(title: String, sub: String? = null, icon: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) Text(icon, fontSize = 20.sp, modifier = Modifier.width(36.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (sub != null) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun SwitchItem(title: String, sub: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (sub != null) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun <T> ChoiceItem(title: String, options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Item(title, label(selected)) { open = true }
    if (open) ChoiceDialog(title, options.map(label), options.indexOf(selected), { open = false }) { onPick(options[it]) }
}

@Composable
private fun SliderItem(title: String, value: Int, range: IntRange, step: Int = 1, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text("$title: ${num(value)}", fontSize = 15.sp)
        Slider(value.toFloat(), { onChange(((it / step).toInt() * step).coerceIn(range)) }, valueRange = range.first.toFloat()..range.last.toFloat())
    }
}

private fun upd(f: (ir.nama.launcher.data.Settings) -> ir.nama.launcher.data.Settings) = Nama.store.settings.update(f)

// ------------------------------------------------------------------------------------------------
// Navigation
// ------------------------------------------------------------------------------------------------

@Composable
private fun SettingsRoot(start: String, activity: SettingsActivity) {
    var stack by remember { mutableStateOf(listOf(start)) }
    val route = stack.last()
    fun go(r: String) { stack = stack + r }
    fun back() { if (stack.size > 1) stack = stack.dropLast(1) else activity.finish() }
    BackHandler { back() }
    val title = when {
        route == "main" -> tr("تنظیمات نما", "Nama settings")
        route == "appearance" -> tr("ظاهر", "Appearance")
        route == "home" -> tr("صفحه اصلی و ژست‌ها", "Home & gestures")
        route == "spaces" -> tr("فضاها", "Spaces")
        route.startsWith("space:") -> tr("ویرایش فضا", "Edit space")
        route == "rules" -> tr("قانون‌های مرتب‌سازی", "Sorting rules")
        route == "apps" -> tr("برنامه‌ها", "Apps")
        route == "news" -> tr("اخبار", "News")
        route == "wellbeing" -> tr("تمرکز و آمار استفاده", "Focus & screen time")
        route == "permissions" -> tr("دسترسی‌ها", "Permissions")
        route == "backup" -> tr("پشتیبان و همگام‌سازی", "Backup & sync")
        route == "safety" -> tr("ایمنی و بازگشت", "Safety & recovery")
        route == "about" -> tr("درباره نما", "About Nama")
        else -> ""
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton({ back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("بازگشت", "Back")) } }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when {
                route == "main" -> MainScreen(::go)
                route == "appearance" -> AppearanceScreen()
                route == "home" -> HomeSettingsScreen()
                route == "spaces" -> SpacesScreen(::go)
                route.startsWith("space:") -> SpaceEditScreen(route.removePrefix("space:"), activity) { back() }
                route == "rules" -> RulesScreen()
                route == "apps" -> AppsScreen()
                route == "news" -> NewsScreen()
                route == "wellbeing" -> WellbeingScreen()
                route == "permissions" -> PermissionsScreen(activity)
                route == "backup" -> BackupScreen(activity)
                route == "safety" -> SafetyScreen(activity)
                route == "about" -> AboutScreen()
            }
        }
    }
}

@Composable
private fun MainScreen(go: (String) -> Unit) {
    val ctx = LocalContext.current
    val isDefault = remember { Actions.isDefaultLauncher(ctx) }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        if (!isDefault) item {
            Item("⭐ " + tr("نما را لانچر اصلی کن", "Make Nama the default launcher"), tr("الان لانچر دیگری پیش‌فرض است. هر وقت خواستی برگرد.", "Another launcher is default now. You can switch back anytime.")) {
                (ctx as? android.app.Activity)?.let { Actions.requestDefaultLauncher(it) }
            }
        }
        item { Item(tr("ظاهر", "Appearance"), tr("سبک، آیکون، فونت، اعداد فارسی", "Style, icons, digits"), "🎨") { go("appearance") } }
        item { Item(tr("صفحه اصلی و ژست‌ها", "Home & gestures"), tr("دو ضربه، کشیدن، صفحه خبر، شهر", "Double tap, swipes, news page, city"), "👆") { go("home") } }
        item { Item(tr("فضاها", "Spaces"), tr("چیدمان متفاوت در خانه، کار، شب، ماشین…", "Different layouts at home, work, night…"), "🗂") { go("spaces") } }
        item { Item(tr("قانون‌های مرتب‌سازی", "Sorting rules"), tr("پوشه‌بندی و آرشیو خودکار", "Automatic folders and archive"), "✨") { go("rules") } }
        item { Item(tr("برنامه‌ها", "Apps"), tr("مخفی، قفل، اصطکاک، سقف زمان، نام دیگر", "Hidden, locked, friction, limits"), "📱") { go("apps") } }
        item { Item(tr("اخبار", "News"), tr("منبع‌ها، دسته‌ها، کلمه‌های ممنوع، حالت آرامش", "Sources, categories, muted words, calm"), "📰") { go("news") } }
        item { Item(tr("تمرکز و آمار استفاده", "Focus & screen time"), tr("گزارش هفتگی، حالت تمرکز", "Weekly report, focus mode"), "⏳") { go("wellbeing") } }
        item { Item(tr("دسترسی‌ها", "Permissions"), tr("همه اختیاری؛ ببین هر کدام برای چیست", "All optional; see what each is for"), "🔐") { go("permissions") } }
        item { Item(tr("پشتیبان و همگام‌سازی", "Backup & sync"), tr("فایل، تاریخچه، Supabase", "File, history, Supabase"), "💾") { go("backup") } }
        item { Item(tr("ایمنی و بازگشت", "Safety & recovery"), tr("لانچر قبلی، حالت امن، بازنشانی", "Previous launcher, safe mode, reset"), "🛟") { go("safety") } }
        item { Item(tr("درباره نما", "About Nama"), null, "ℹ") { go("about") } }
    }
}

@Composable
private fun AppearanceScreen() {
    val s by Nama.store.settings.flow.collectAsState()
    val packs = remember { Nama.icons.packs.available() }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        header(tr("سبک", "Style"))
        item { ChoiceItem(tr("سبک پایه", "Base style"), StyleId.entries, s.style, { tr(it.fa, it.en) }) { v -> upd { it.copy(style = v) } } }
        item { SwitchItem(tr("آیکون‌های هم‌رنگ", "Themed icons"), tr("آیکون‌ها با رنگ والپیپر (برنامه‌هایی که پشتیبانی کنند)", "Icons tinted from the wallpaper (apps that support it)"), s.themedIcons) { v -> upd { it.copy(themedIcons = v) }; Nama.icons.clear() } }
        header(tr("آیکون‌ها", "Icons"))
        item {
            val opts = listOf<IconShape?>(null) + IconShape.entries
            ChoiceItem(tr("شکل آیکون", "Icon shape"), opts, s.iconShape, { it?.let { sh -> tr(sh.fa, sh.en) } ?: tr("پیش‌فرض (دایره)", "Default (circle)") }) { v -> upd { it.copy(iconShape = v) }; Nama.icons.clear() }
        }
        item { SliderItem(tr("اندازه آیکون", "Icon size"), s.iconSizeDp, 36..72, 2) { v -> upd { it.copy(iconSizeDp = v) } } }
        item { SliderItem(tr("تعداد ستون صفحه اصلی", "Home columns"), s.columns.coerceIn(4, 5), 4..5) { v -> upd { it.copy(columns = v) } } }
        item { SliderItem(tr("تعداد ردیف صفحه اصلی", "Home rows"), s.rows.coerceIn(5, 7), 5..7) { v -> upd { it.copy(rows = v) } } }
        item { SwitchItem(tr("نام زیر آیکون", "Labels"), null, s.showLabels) { v -> upd { it.copy(showLabels = v) } } }
        item { SwitchItem(tr("نقطه اعلان روی آیکون", "Notification dots"), tr("نیاز به دسترسی اعلان‌ها", "Needs notification access"), s.showNotificationDots) { v -> upd { it.copy(showNotificationDots = v) } } }
        item {
            val opts = listOf<String?>(null) + packs.map { it.packageName }
            ChoiceItem(tr("بسته آیکون", "Icon pack"), opts, s.iconPack, { p -> p?.let { id -> packs.firstOrNull { it.packageName == id }?.label ?: id } ?: tr("بدون بسته", "None") }) { v -> upd { it.copy(iconPack = v) }; Nama.icons.clear() }
        }
        header(tr("زبان و اعداد", "Language & digits"))
        item { ChoiceItem(tr("زبان", "Language"), listOf("fa", "en"), s.language, { if (it == "fa") "فارسی" else "English" }) { v -> upd { it.copy(language = v) } } }
        item { SwitchItem(tr("اعداد فارسی", "Persian digits"), null, s.persianDigits) { v -> upd { it.copy(persianDigits = v) } } }
        header(tr("راحتی", "Comfort"))
        item { SwitchItem(tr("حالت یک‌دستی", "One-hand mode"), tr("آیکون‌ها پایین صفحه جمع می‌شوند", "Icons gather at the bottom"), s.oneHandMode) { v -> upd { it.copy(oneHandMode = v) } } }
        item { SwitchItem(tr("کاهش انیمیشن", "Reduce motion"), null, s.reduceMotion) { v -> upd { it.copy(reduceMotion = v) } } }
    }
}

@Composable
private fun HomeSettingsScreen() {
    val s by Nama.store.settings.flow.collectAsState()
    var plate by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        header(tr("ژست‌ها", "Gestures"))
        val actions = GestureAction.entries
        item { ChoiceItem(tr("دو ضربه روی صفحه", "Double tap"), actions, s.doubleTapAction, { tr(it.fa, it.en) }) { v -> upd { it.copy(doubleTapAction = v) } } }
        item { ChoiceItem(tr("کشیدن به بالا", "Swipe up"), actions, s.swipeUpAction, { tr(it.fa, it.en) }) { v -> upd { it.copy(swipeUpAction = v) } } }
        item { ChoiceItem(tr("کشیدن به پایین", "Swipe down"), actions, s.swipeDownAction, { tr(it.fa, it.en) }) { v -> upd { it.copy(swipeDownAction = v) } } }
        header(tr("صفحه اصلی", "Home"))
        item { SwitchItem(tr("صفحه اخبار", "News page"), tr("با کشیدن از صفحه اول", "Swipe from the first page"), s.newsPageEnabled) { v -> upd { it.copy(newsPageEnabled = v) } } }
        item { SwitchItem(tr("نشان دادن فضای فعال", "Show the active space"), null, s.showSpaceBanner) { v -> upd { it.copy(showSpaceBanner = v) } } }
        item { SwitchItem(tr("برنامه‌های تازه روی صفحه اصلی", "New apps on home screen"), tr("آیکون برنامه تازه نصب‌شده به صفحه اضافه شود", "Add an icon when an app is installed"), s.autoInboxNewApps) { v -> upd { it.copy(autoInboxNewApps = v) } } }
        item { SwitchItem(tr("ساعت هنگام شارژ", "Clock while charging"), tr("وقتی شارژر وصل شد و روی صفحه اصلی هستی", "When charging on the home screen"), s.chargingClock) { v -> upd { it.copy(chargingClock = v) } } }
        item { SwitchItem(tr("حالت اینترنت ملی", "National internet mode"), tr("قطع اینترنت بین‌الملل را تشخیص بده و برنامه‌های خارجی را کم‌رنگ کن", "Detect international outages and dim foreign apps"), s.nationalNetMode) { v -> upd { it.copy(nationalNetMode = v) } } }
        item { ChoiceItem(tr("موتور جستجوی وب", "Web search engine"), SearchEngine.entries, s.searchEngine, { it.fa }) { v -> upd { it.copy(searchEngine = v) } } }
        header(tr("شهر و تقویم", "City & calendar"))
        item { ChoiceItem(tr("شهر", "City"), Cities.ALL.map { it.id }, s.cityId, { id -> Cities.byId(id).let { if (Nama.isFa) it.fa else it.en } }) { v -> upd { it.copy(cityId = v) } } }
        item { ChoiceItem(tr("تنظیم تاریخ قمری", "Lunar date adjustment"), listOf(-2, -1, 0, 1, 2), s.hijriOffset, { num(if (it > 0) "+$it" else "$it") + tr(" روز", " days") }) { v -> upd { it.copy(hijriOffset = v) } } }
        item { Item(tr("رقم آخر پلاک (طرح زوج و فرد)", "Plate last digit"), s.plateLastDigit?.let { num(it) } ?: tr("تنظیم نشده", "Not set")) { plate = true } }
        item { SwitchItem(tr("بررسی‌های روزانه", "Daily checks"), tr("یادآور قسط، تولد، پایان بسته اینترنت", "Bills, birthdays, data package reminders"), s.dailyChecks) { v -> upd { it.copy(dailyChecks = v) } } }
    }
    if (plate) TextInputDialog(tr("رقم آخر پلاک", "Last digit"), numeric = true, onDismiss = { plate = false }) { v ->
        val d = ir.nama.core.PersianText.toEnDigits(v).toIntOrNull()
        upd { it.copy(plateLastDigit = d?.takeIf { x -> x in 0..9 }) }
    }
}

// ------------------------------------------------------------------------------------------------
// Spaces
// ------------------------------------------------------------------------------------------------

@Composable
private fun SpacesScreen(go: (String) -> Unit) {
    val spaces by Nama.store.spaces.flow.collectAsState()
    val active by Nama.spaces.active.collectAsState()
    var addOpen by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                tr(
                    "هر فضا فقط تفاوت‌هایش با چیدمان پایه را نگه می‌دارد: سبک، برنامه‌های پنهان، داک، ویجت‌ها. اگر چند فضا همزمان فعال شوند، آن که اولویت بالاتری دارد (عدد کمتر) برنده است.",
                    "Each space stores only its differences from the base layout. If several match, the lowest priority number wins."
                ),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp), lineHeight = 21.sp
            )
        }
        item { Item(tr("فعال الان: ", "Active now: ") + (active?.let { "${it.icon} ${it.name}" } ?: tr("بدون فضا", "No space")), null) }
        items(spaces.sortedBy { it.priority }, key = { it.id }) { sp ->
            Row(Modifier.fillMaxWidth().clickable { go("space:${sp.id}") }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(sp.icon, fontSize = 22.sp, modifier = Modifier.width(36.dp))
                Column(Modifier.weight(1f)) {
                    Text(sp.name, fontSize = 15.sp)
                    Text(describeTriggers(sp) + " · " + tr("اولویت ", "priority ") + num(sp.priority), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(sp.enabled, { v -> Nama.spaces.upsert(sp.copy(enabled = v)) })
            }
        }
        item { Button({ addOpen = true }, Modifier.padding(20.dp)) { Text(tr("＋ فضای تازه", "＋ New space")) } }
    }
    if (addOpen) {
        AlertDialog(
            onDismissRequest = { addOpen = false },
            title = { Text(tr("از کدام الگو؟", "From which template?")) },
            text = {
                LazyColumn {
                    items(SpaceManager.PRESETS) { p ->
                        Column(Modifier.fillMaxWidth().clickable {
                            val sp = p.build()
                            Nama.spaces.upsert(sp)
                            addOpen = false
                            go("space:${sp.id}")
                        }.padding(vertical = 10.dp)) {
                            Text("${p.icon} " + tr(p.fa, p.en))
                            Text(p.descFa, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item {
                        Text("＋ " + tr("فضای خالی", "Empty space"), Modifier.fillMaxWidth().clickable {
                            val sp = Space("s_" + UUID.randomUUID().toString().take(8), tr("فضای من", "My space"), "★")
                            Nama.spaces.upsert(sp); addOpen = false; go("space:${sp.id}")
                        }.padding(vertical = 10.dp))
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton({ addOpen = false }) { Text(tr("انصراف", "Cancel")) } }
        )
    }
}

private fun describeTriggers(sp: Space): String {
    if (sp.triggers.isEmpty()) return tr("فقط دستی", "Manual only")
    return sp.triggers.joinToString(if (sp.matchAll) tr(" و ", " and ") else tr(" یا ", " or ")) { t -> describeTrigger(t) }
}

private fun describeTrigger(t: Trigger): String = when (t) {
    is Trigger.Wifi -> tr("وای‌فای ", "Wi-Fi ") + (t.ssids.joinToString("، ").ifBlank { tr("(انتخاب نشده)", "(not set)") })
    is Trigger.Time -> minuteText(t.startMinute) + "–" + minuteText(t.endMinute) + if (t.days.size < 7) " (" + t.days.sorted().joinToString("") { ir.nama.core.Jalali.WEEKDAYS_SHORT[it] } + ")" else ""
    is Trigger.Bluetooth -> tr("بلوتوث ", "Bluetooth ") + (t.devices.joinToString("، ").ifBlank { tr("(انتخاب نشده)", "(not set)") })
    is Trigger.Charging -> tr("در حال شارژ", "Charging")
    is Trigger.BatteryBelow -> tr("باتری زیر ", "Battery below ") + num(t.percent) + "٪"
    is Trigger.Headphones -> tr("هدفون وصل", "Headphones")
    is Trigger.Location -> tr("نزدیک ", "Near ") + t.label.ifBlank { tr("مکان", "place") } + " (" + num(t.radiusMeters) + tr(" متر)", " m)")
    is Trigger.Holiday -> tr("روز تعطیل", "Holiday")
    is Trigger.Workday -> tr("روز کاری", "Workday")
    is Trigger.Ramadan -> tr("ماه رمضان", "Ramadan")
    is Trigger.NoInternationalInternet -> tr("قطع اینترنت بین‌الملل", "No international internet")
}

@Composable
private fun SpaceEditScreen(id: String, activity: SettingsActivity, onDeleted: () -> Unit) {
    val spaces by Nama.store.spaces.flow.collectAsState()
    val sp = spaces.firstOrNull { it.id == id } ?: run { onDeleted(); return }
    fun save(f: (Space) -> Space) = Nama.spaces.upsert(f(sp))
    fun saveO(f: (ir.nama.core.SpaceOverrides) -> ir.nama.core.SpaceOverrides) = save { it.copy(overrides = f(it.overrides)) }
    var rename by remember { mutableStateOf(false) }
    var addTrigger by remember { mutableStateOf(false) }
    var editTrigger by remember { mutableStateOf<Int?>(null) }
    var pickApps by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val o = sp.overrides
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item { Item(tr("نام و نشان", "Name & icon"), "${sp.icon} ${sp.name}") { rename = true } }
        item { SwitchItem(tr("فعال", "Enabled"), null, sp.enabled) { v -> save { it.copy(enabled = v) } } }
        item { SliderItem(tr("اولویت (کمتر = مهم‌تر)", "Priority (lower wins)"), sp.priority, 0..99) { v -> save { it.copy(priority = v) } } }
        item { Item(tr("فعال کردن دستی همین الان", "Turn on now (manual)"), null) { Nama.spaces.setManual(sp.id); Actions.toast(activity, tr("فعال شد", "On")) } }
        header(tr("کی فعال شود؟", "When is it on?"))
        item { SwitchItem(tr("همه شرط‌ها لازم است", "All conditions needed"), if (sp.matchAll) tr("«و»", "AND") else tr("«یا»: یکی کافی است", "OR: any one"), sp.matchAll) { v -> save { it.copy(matchAll = v) } } }
        if (sp.triggers.isEmpty()) item { Item(tr("بدون شرط: فقط دستی فعال می‌شود", "No conditions: manual only"), null) }
        items(sp.triggers.size) { i ->
            Row(Modifier.fillMaxWidth().clickable { editTrigger = i }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("• " + describeTrigger(sp.triggers[i]), Modifier.weight(1f))
                TextButton({ save { it.copy(triggers = it.triggers.filterIndexed { j, _ -> j != i }) } }) { Text(tr("حذف", "Remove")) }
            }
        }
        item { TextButton({ addTrigger = true }, Modifier.padding(horizontal = 12.dp)) { Text(tr("＋ افزودن شرط", "＋ Add condition")) } }
        header(tr("در این فضا چه عوض شود؟", "What changes in this space?"))
        item {
            val opts = listOf<StyleId?>(null) + StyleId.entries
            ChoiceItem(tr("سبک", "Style"), opts, o.style, { it?.let { st -> tr(st.fa, st.en) } ?: tr("مثل پایه", "Same as base") }) { v -> saveO { it.copy(style = v) } }
        }
        item { SwitchItem(tr("بدون انیمیشن", "No animations"), null, o.reduceMotion) { v -> saveO { it.copy(reduceMotion = v) } } }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(tr("دسته‌های پنهان", "Hidden categories"), fontSize = 15.sp)
                ChipsSelector(AppCategory.entries, o.hiddenCategories, { Nama.categoryName(it) }) { c ->
                    saveO { it.copy(hiddenCategories = if (c in it.hiddenCategories) it.hiddenCategories - c else it.hiddenCategories + c) }
                }
            }
        }
        item { Item(tr("برنامه‌های پنهان", "Hidden apps"), num(o.hiddenApps.size)) { pickApps = "hidden" } }
        item { Item(tr("برنامه‌های جلوتر در صفحه اول", "Pinned on page 1"), num(o.pinnedApps.size)) { pickApps = "pinned" } }
        item { Item(tr("داک مخصوص این فضا", "Dock for this space"), o.dockApps?.size?.let { num(it) } ?: tr("مثل پایه", "Same as base")) { pickApps = "dock" } }
        item { Item(tr("فقط این برنامه‌ها مجاز باشند (کودک/مهمان)", "Allow only these apps (kids/guest)"), if (o.allowOnlyApps.isEmpty() && o.allowOnlyCategories.isEmpty()) tr("بدون محدودیت", "No restriction") else num(o.allowOnlyApps.size) + tr(" برنامه", " apps")) { pickApps = "allow" } }
        item { Item(tr("اصطکاک اضافه برای این برنامه‌ها", "Extra friction for these apps"), num(o.extraFrictionApps.size)) { pickApps = "friction" } }
        item { SwitchItem(tr("پنهان کردن برنامه‌های خارجی", "Hide foreign apps"), tr("مناسب قطعی اینترنت بین‌الملل", "Useful during international outages"), o.hideForeignApps) { v -> saveO { it.copy(hideForeignApps = v) } } }
        item { SwitchItem(tr("خبرها آرام (بدون سیاسی و نگران‌کننده)", "Calm news"), null, o.calmNews) { v -> saveO { it.copy(calmNews = v) } } }
        item { SwitchItem(tr("بدون صفحه خبر", "No news page"), null, o.hideNews) { v -> saveO { it.copy(hideNews = v) } } }
        item { SwitchItem(tr("قفل خروج (کودک/مهمان)", "Lock exit (kids/guest)"), tr("برای خروج، اثر انگشت یا رمز گوشی لازم است", "Leaving needs fingerprint or PIN"), o.locked) { v -> saveO { it.copy(locked = v) } } }
        item { TextButton({ confirmDelete = true }, Modifier.padding(12.dp)) { Text(tr("حذف این فضا", "Delete space"), color = MaterialTheme.colorScheme.error) } }
    }
    if (rename) {
        var name by remember { mutableStateOf(sp.name) }
        var icon by remember { mutableStateOf(sp.icon) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text(tr("نام و نشان", "Name & icon")) },
            text = { Column { OutlinedTextField(name, { name = it }, label = { Text(tr("نام", "Name")) }); OutlinedTextField(icon, { icon = it.take(2) }, label = { Text(tr("نشان (ایموجی)", "Icon (emoji)")) }) } },
            confirmButton = { TextButton({ save { it.copy(name = name.ifBlank { it.name }, icon = icon.ifBlank { "●" }) }; rename = false }) { Text(tr("ذخیره", "Save")) } }
        )
    }
    if (addTrigger) TriggerDialog(null, activity, onDismiss = { addTrigger = false }) { t -> save { it.copy(triggers = it.triggers + t) } }
    editTrigger?.let { i ->
        TriggerDialog(sp.triggers.getOrNull(i), activity, onDismiss = { editTrigger = null }) { t -> save { it.copy(triggers = it.triggers.mapIndexed { j, x -> if (j == i) t else x }) } }
    }
    when (pickApps) {
        "hidden" -> AppPickerDialog(tr("پنهان در این فضا", "Hidden here"), o.hiddenApps, onDismiss = { pickApps = null }) { set -> saveO { it.copy(hiddenApps = set) } }
        "pinned" -> AppPickerDialog(tr("جلوتر در صفحه اول", "Pinned first"), o.pinnedApps.toSet(), onDismiss = { pickApps = null }) { set -> saveO { it.copy(pinnedApps = set.toList()) } }
        "dock" -> AppPickerDialog(tr("داک (حداکثر ۵)", "Dock (max 5)"), o.dockApps?.toSet() ?: emptySet(), onDismiss = { pickApps = null }) { set -> saveO { it.copy(dockApps = if (set.isEmpty()) null else set.toList().take(5)) } }
        "allow" -> AppPickerDialog(tr("فقط این‌ها مجاز", "Only these allowed"), o.allowOnlyApps, onDismiss = { pickApps = null }) { set -> saveO { it.copy(allowOnlyApps = set, allowOnlyCategories = if (set.isEmpty()) it.allowOnlyCategories else emptySet()) } }
        "friction" -> AppPickerDialog(tr("اصطکاک اضافه", "Extra friction"), o.extraFrictionApps, onDismiss = { pickApps = null }) { set -> saveO { it.copy(extraFrictionApps = set) } }
    }
    if (confirmDelete) ConfirmDialog(tr("حذف فضا", "Delete space"), tr("فضای «${sp.name}» حذف شود؟", "Delete \"${sp.name}\"?"), tr("حذف", "Delete"), {
        Nama.spaces.delete(sp.id); onDeleted()
    }, { confirmDelete = false }, danger = true)
}

@Composable
private fun TriggerDialog(initial: Trigger?, activity: SettingsActivity, onDismiss: () -> Unit, onSave: (Trigger) -> Unit) {
    val ctx = LocalContext.current
    val kinds = listOf("wifi", "time", "bluetooth", "charging", "battery", "headphones", "location", "holiday", "workday", "ramadan", "nointl")
    var kind by remember {
        mutableStateOf(
            when (initial) {
                is Trigger.Wifi -> "wifi"; is Trigger.Time -> "time"; is Trigger.Bluetooth -> "bluetooth"; is Trigger.Charging -> "charging"
                is Trigger.BatteryBelow -> "battery"; is Trigger.Headphones -> "headphones"; is Trigger.Location -> "location"
                is Trigger.Holiday -> "holiday"; is Trigger.Workday -> "workday"; is Trigger.Ramadan -> "ramadan"; is Trigger.NoInternationalInternet -> "nointl"
                null -> "wifi"
            }
        )
    }
    var text by remember { mutableStateOf(when (initial) { is Trigger.Wifi -> initial.ssids.joinToString(", "); is Trigger.Bluetooth -> initial.devices.joinToString(", "); is Trigger.Location -> initial.label; else -> "" }) }
    var start by remember { mutableStateOf((initial as? Trigger.Time)?.startMinute ?: 8 * 60) }
    var end by remember { mutableStateOf((initial as? Trigger.Time)?.endMinute ?: 17 * 60) }
    var days by remember { mutableStateOf((initial as? Trigger.Time)?.days ?: (0..6).toSet()) }
    var percent by remember { mutableStateOf((initial as? Trigger.BatteryBelow)?.percent ?: 20) }
    var radius by remember { mutableStateOf((initial as? Trigger.Location)?.radiusMeters ?: 200) }
    var lat by remember { mutableStateOf((initial as? Trigger.Location)?.lat) }
    var lng by remember { mutableStateOf((initial as? Trigger.Location)?.lng) }
    var timePick by remember { mutableStateOf(0) }
    val ssid by Nama.context.currentSsid.collectAsState()
    val hasLoc = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val names = mapOf(
        "wifi" to tr("وای‌فای", "Wi-Fi"), "time" to tr("زمان و روز", "Time & days"), "bluetooth" to tr("بلوتوث", "Bluetooth"),
        "charging" to tr("شارژ", "Charging"), "battery" to tr("باتری کم", "Low battery"), "headphones" to tr("هدفون", "Headphones"),
        "location" to tr("مکان", "Location"), "holiday" to tr("تعطیلی", "Holiday"), "workday" to tr("روز کاری", "Workday"),
        "ramadan" to tr("رمضان", "Ramadan"), "nointl" to tr("اینترنت ملی", "National internet")
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("شرط", "Condition")) },
        text = {
            LazyColumn {
                item { ChipsSelector(kinds, setOf(kind), { names[it] ?: it }) { kind = it } }
                item { Spacer(Modifier.height(10.dp)) }
                item {
                    when (kind) {
                        "wifi" -> Column {
                            if (!hasLoc) TextButton({ activity.ask(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION) }) { Text(tr("اجازه خواندن نام وای‌فای", "Allow reading Wi-Fi name")) }
                            else if (ssid != null) TextButton({ text = listOf(text, ssid!!).filter { it.isNotBlank() }.joinToString(", ") }) { Text(tr("افزودن وای‌فای فعلی: ", "Add current: ") + ssid) }
                            OutlinedTextField(text, { text = it }, label = { Text(tr("نام وای‌فای‌ها (با ویرگول)", "Wi-Fi names (comma separated)")) })
                        }
                        "time" -> Column {
                            Row { TextButton({ timePick = 1 }) { Text(tr("از ", "From ") + minuteText(start)) }; TextButton({ timePick = 2 }) { Text(tr("تا ", "To ") + minuteText(end)) } }
                            ChipsSelector((0..6).toList(), days, { ir.nama.core.Jalali.WEEKDAYS[it] }) { d -> days = if (d in days) days - d else days + d }
                        }
                        "bluetooth" -> Column {
                            if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                TextButton({ activity.ask(Manifest.permission.BLUETOOTH_CONNECT) }) { Text(tr("اجازه دیدن دستگاه‌های بلوتوث", "Allow Bluetooth devices")) }
                            }
                            val paired = remember { Nama.context.pairedBluetoothNames() }
                            if (paired.isNotEmpty()) ChipsSelector(paired, text.split(',').map { it.trim() }.toSet(), { it }) { n ->
                                val cur = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
                                if (n in cur) cur -= n else cur += n
                                text = cur.joinToString(", ")
                            }
                            OutlinedTextField(text, { text = it }, label = { Text(tr("نام دستگاه‌ها", "Device names")) })
                        }
                        "battery" -> SliderItem(tr("زیر درصد", "Below %"), percent, 5..50, 5) { percent = it }
                        "location" -> Column {
                            if (!hasLoc) TextButton({ activity.ask(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION) }) { Text(tr("اجازه موقعیت مکانی", "Allow location")) }
                            else TextButton({
                                val l = Nama.context.currentLocation()
                                if (l != null) { lat = l.latitude; lng = l.longitude } else Actions.toast(ctx, tr("موقعیت در دسترس نیست؛ GPS را روشن کنید", "Location unavailable"))
                            }) { Text(if (lat != null) tr("✓ مکان فعلی ثبت شد", "✓ Current place saved") else tr("همین‌جا را ثبت کن", "Use where I am")) }
                            OutlinedTextField(text, { text = it }, label = { Text(tr("نام مکان", "Place name")) })
                            SliderItem(tr("شعاع (متر)", "Radius (m)"), radius, 100..2000, 50) { radius = it }
                        }
                        else -> Text(tr("این شرط تنظیم دیگری ندارد.", "No options for this condition."))
                    }
                }
            }
        },
        confirmButton = {
            TextButton({
                val list = text.split(',', '،').map { it.trim() }.filter { it.isNotEmpty() }
                val t: Trigger? = when (kind) {
                    "wifi" -> Trigger.Wifi(list)
                    "time" -> Trigger.Time(start, end, days.ifEmpty { (0..6).toSet() })
                    "bluetooth" -> Trigger.Bluetooth(list)
                    "charging" -> Trigger.Charging
                    "battery" -> Trigger.BatteryBelow(percent)
                    "headphones" -> Trigger.Headphones
                    "location" -> if (lat != null && lng != null) Trigger.Location(lat!!, lng!!, radius, text) else null
                    "holiday" -> Trigger.Holiday
                    "workday" -> Trigger.Workday
                    "ramadan" -> Trigger.Ramadan
                    else -> Trigger.NoInternationalInternet
                }
                if (t != null) { onSave(t); onDismiss() } else Actions.toast(ctx, tr("اول مکان را ثبت کنید", "Save a place first"))
            }) { Text(tr("ذخیره", "Save")) }
        },
        dismissButton = { TextButton(onDismiss) { Text(tr("انصراف", "Cancel")) } }
    )
    when (timePick) {
        1 -> TimeDialog(start, tr("شروع", "Start"), { timePick = 0 }) { start = it }
        2 -> TimeDialog(end, tr("پایان", "End"), { timePick = 0 }) { end = it }
    }
}

// ------------------------------------------------------------------------------------------------
// Rules
// ------------------------------------------------------------------------------------------------

@Composable
private fun RulesScreen() {
    val rules by Nama.store.rules.flow.collectAsState()
    val apps by Nama.apps.apps.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var sortConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                tr("قانون‌ها به ترتیب بررسی می‌شوند. «مرتب کن» صفحه‌های بعد از صفحه اول را بر اساس این قانون‌ها و دسته‌ها پوشه‌بندی می‌کند. هیچ برنامه‌ای حذف نمی‌شود.",
                    "Rules run in order. \"Sort now\" rebuilds pages after page 1 into folders. Nothing is uninstalled."),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp), lineHeight = 21.sp
            )
        }
        items(rules, key = { it.id }) { r ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, fontSize = 15.sp)
                    Text(describeRule(r), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(r.enabled, { v -> Nama.store.rules.update { l -> l.map { if (it.id == r.id) it.copy(enabled = v) else it } } })
                TextButton({ Nama.store.rules.update { l -> l.filterNot { it.id == r.id } } }) { Text("✕") }
            }
        }
        item {
            Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton({ adding = true }) { Text(tr("＋ قانون تازه", "＋ New rule")) }
                Button({ sortConfirm = true }) { Text(tr("مرتب کن", "Sort now")) }
            }
        }
    }
    if (adding) RuleDialog { adding = false }
    if (sortConfirm) ConfirmDialog(
        tr("مرتب‌سازی خودکار", "Auto-sort"), tr("صفحه‌های بعد از صفحه اول بازچینی می‌شوند. از تاریخچه چیدمان قابل برگشت است.", "Pages after page 1 are rebuilt. Undo from layout history."),
        tr("مرتب کن", "Sort"), {
            scope.launch {
                val r = SmartSort.plan(Nama.store.layout.value, apps, Nama.usage.lastUsed(), Nama.settings.columns.coerceIn(4, 5), Nama.settings.rows.coerceIn(5, 7))
                r.prefChanges.forEach { (k, f) -> Nama.store.updatePref(k, f) }
                Nama.store.changeLayout(tr("مرتب‌سازی خودکار", "Auto-sort")) { r.layout }
                Actions.toast(ctx, tr("${num(r.folders)} پوشه ساخته شد", "${r.folders} folders made"))
            }
        }, { sortConfirm = false }
    )
}

private fun describeRule(r: SortRule): String {
    val conds = r.conditions.joinToString(if (r.matchAll) tr(" و ", " and ") else tr(" یا ", " or ")) { c ->
        when (c) {
            is RuleCondition.PackageContains -> tr("بسته شامل «${c.text}»", "package has \"${c.text}\"")
            is RuleCondition.LabelContains -> tr("نام شامل «${c.text}»", "name has \"${c.text}\"")
            is RuleCondition.CategoryIs -> tr("دسته ", "category ") + Nama.categoryName(c.category)
            is RuleCondition.InstalledWithinDays -> tr("نصب در ${num(c.days)} روز اخیر", "installed in last ${c.days} days")
            is RuleCondition.UnusedForDays -> tr("${num(c.days)} روز استفاده نشده", "unused ${c.days} days")
            is RuleCondition.InstallerContains -> tr("نصب از ${c.text}", "installed from ${c.text}")
            is RuleCondition.IsSystem -> if (c.value) tr("برنامه سیستمی", "system app") else tr("برنامه کاربر", "user app")
        }
    }
    val act = when (val a = r.action) {
        is RuleAction.SetCategory -> tr("← دسته ", "→ category ") + Nama.categoryName(a.category)
        is RuleAction.PutInFolder -> tr("← پوشه «${a.name}»", "→ folder \"${a.name}\"")
        is RuleAction.Archive -> tr("← آرشیو", "→ archive")
        is RuleAction.Hide -> tr("← پنهان", "→ hide")
        is RuleAction.Friction -> tr("← اصطکاک", "→ friction")
    }
    return "$conds $act"
}

@Composable
private fun RuleDialog(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var condKind by remember { mutableStateOf("label") }
    var condText by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf(AppCategory.FINANCE) }
    var days by remember { mutableStateOf("30") }
    var actKind by remember { mutableStateOf("folder") }
    var folder by remember { mutableStateOf("") }
    var actCat by remember { mutableStateOf(AppCategory.OTHER) }
    var catPick by remember { mutableStateOf(0) }
    val condNames = mapOf("label" to tr("نام شامل", "Name has"), "pkg" to tr("بسته شامل", "Package has"), "category" to tr("دسته", "Category"), "unused" to tr("استفاده نشده", "Unused"), "new" to tr("تازه نصب", "Newly installed"), "installer" to tr("نصب از", "Installer"))
    val actNames = mapOf("folder" to tr("پوشه", "Folder"), "category" to tr("تغییر دسته", "Set category"), "archive" to tr("آرشیو", "Archive"), "hide" to tr("پنهان", "Hide"))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("قانون تازه", "New rule")) },
        text = {
            LazyColumn {
                item { OutlinedTextField(name, { name = it }, label = { Text(tr("نام قانون", "Rule name")) }) }
                item { Text(tr("اگر…", "If…"), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                item { ChipsSelector(condNames.keys.toList(), setOf(condKind), { condNames[it]!! }) { condKind = it } }
                item {
                    when (condKind) {
                        "label", "pkg", "installer" -> OutlinedTextField(condText, { condText = it }, label = { Text(tr("متن (مثلاً بانک، bazaar)", "Text (e.g. bank)")) })
                        "category" -> TextButton({ catPick = 1 }) { Text(Nama.categoryName(cat)) }
                        else -> OutlinedTextField(days, { days = it }, label = { Text(tr("تعداد روز", "Days")) })
                    }
                }
                item { Text(tr("آنگاه…", "Then…"), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                item { ChipsSelector(actNames.keys.toList(), setOf(actKind), { actNames[it]!! }) { actKind = it } }
                item {
                    when (actKind) {
                        "folder" -> OutlinedTextField(folder, { folder = it }, label = { Text(tr("نام پوشه", "Folder name")) })
                        "category" -> TextButton({ catPick = 2 }) { Text(Nama.categoryName(actCat)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton({
                val d = ir.nama.core.PersianText.toEnDigits(days).toIntOrNull() ?: 30
                val cond: RuleCondition = when (condKind) {
                    "label" -> RuleCondition.LabelContains(condText)
                    "pkg" -> RuleCondition.PackageContains(condText)
                    "installer" -> RuleCondition.InstallerContains(condText)
                    "category" -> RuleCondition.CategoryIs(cat)
                    "unused" -> RuleCondition.UnusedForDays(d)
                    else -> RuleCondition.InstalledWithinDays(d)
                }
                val act: RuleAction = when (actKind) {
                    "folder" -> RuleAction.PutInFolder(folder.ifBlank { tr("پوشه", "Folder") })
                    "category" -> RuleAction.SetCategory(actCat)
                    "archive" -> RuleAction.Archive
                    else -> RuleAction.Hide
                }
                val rule = SortRule("r_" + UUID.randomUUID().toString().take(8), name.ifBlank { tr("قانون من", "My rule") }, conditions = listOf(cond), action = act)
                Nama.store.rules.update { it + rule }
                onDismiss()
            }) { Text(tr("ذخیره", "Save")) }
        },
        dismissButton = { TextButton(onDismiss) { Text(tr("انصراف", "Cancel")) } }
    )
    if (catPick != 0) {
        val cats = AppCategory.entries
        ChoiceDialog(tr("دسته", "Category"), cats.map { Nama.categoryName(it) }, cats.indexOf(if (catPick == 1) cat else actCat), { catPick = 0 }) { i ->
            if (catPick == 1) cat = cats[i] else actCat = cats[i]
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Apps
// ------------------------------------------------------------------------------------------------

@Composable
private fun AppsScreen() {
    val apps by Nama.apps.apps.collectAsState()
    var q by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<ir.nama.launcher.data.AppEntry?>(null) }
    val list = remember(apps, q) {
        if (q.isBlank()) apps else apps.map { it to ir.nama.core.SearchScorer.score(q, it.search) }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
    }
    Column {
        OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text(tr("جستجو", "Search")) }, modifier = Modifier.fillMaxWidth().padding(16.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(list, key = { it.key }) { e ->
                Row(Modifier.fillMaxWidth().clickable { open = e }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIconImage(e, 36.dp, IconShape.CIRCLE)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.label)
                        val tags = listOfNotNull(
                            Nama.categoryName(e.category),
                            if (e.pref.hidden) tr("مخفی", "hidden") else null,
                            if (e.pref.locked) tr("قفل", "locked") else null,
                            if (e.pref.archived) tr("آرشیو", "archived") else null,
                            if (e.pref.friction != 0) tr("اصطکاک", "friction") else null,
                            if (e.pref.dailyLimitMinutes > 0) num(e.pref.dailyLimitMinutes) + tr(" دقیقه", " min") else null
                        )
                        Text(tags.joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    open?.let { e -> AppSettingsDialog(e) { open = null } }
}

// ------------------------------------------------------------------------------------------------
// News
// ------------------------------------------------------------------------------------------------

@Composable
private fun NewsScreen() {
    val s by Nama.store.settings.flow.collectAsState()
    val errors by Nama.news.sourceErrors.collectAsState()
    var addOpen by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var calmTime by remember { mutableStateOf(0) }
    val all = Nama.news.allSources()
    val enabled = Nama.news.enabledSources().map { it.id }.toSet()
    fun toggle(id: String) {
        val cur = enabled.toMutableSet()
        if (id in cur) cur -= id else cur += id
        upd { it.copy(newsSourceIds = cur) }
        Nama.news.refreshIfStale(0)
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        header(tr("منبع‌ها", "Sources"))
        items(all, key = { it.id }) { src ->
            Row(Modifier.fillMaxWidth().clickable { toggle(src.id) }.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(src.name + if (src.type == "telegram") " (تلگرام)" else "")
                    Text(tr(src.category.fa, src.category.en) + if (src.id in errors) tr(" · دریافت ناموفق", " · failed") else "", fontSize = 12.sp,
                        color = if (src.id in errors) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(src.id in enabled, { toggle(src.id) })
            }
        }
        item { TextButton({ addOpen = true }, Modifier.padding(horizontal = 12.dp)) { Text(tr("＋ منبع دلخواه (RSS یا کانال عمومی تلگرام)", "＋ Custom source (RSS or public Telegram channel)")) } }
        header(tr("فیلترها", "Filters"))
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(tr("فقط این دسته‌ها (هیچ = همه)", "Only these categories (none = all)"), fontSize = 14.sp)
                ChipsSelector(NewsCategory.entries, s.newsCategories, { tr(it.fa, it.en) }) { c ->
                    upd { it.copy(newsCategories = if (c in it.newsCategories) it.newsCategories - c else it.newsCategories + c) }
                }
            }
        }
        item { Item(tr("کلمه‌های ممنوع", "Muted words"), s.newsMutedWords.joinToString("، ").ifBlank { tr("هیچ", "None") }) { muted = true } }
        header(tr("حالت آرامش", "Calm mode"))
        item { SwitchItem(tr("همیشه آرام", "Always calm"), tr("بدون خبرهای سیاسی و نگران‌کننده", "No politics or alarming news"), s.newsCalmAlways) { v -> upd { it.copy(newsCalmAlways = v) } } }
        item { SwitchItem(tr("آرام در ساعت‌های شب", "Calm at night"), minuteText(s.newsCalmStartMinute) + "–" + minuteText(s.newsCalmEndMinute), s.newsCalmAtNight) { v -> upd { it.copy(newsCalmAtNight = v) } } }
        item { Row(Modifier.padding(horizontal = 12.dp)) { TextButton({ calmTime = 1 }) { Text(tr("شروع ", "Start ") + minuteText(s.newsCalmStartMinute)) }; TextButton({ calmTime = 2 }) { Text(tr("پایان ", "End ") + minuteText(s.newsCalmEndMinute)) } } }
        item { SwitchItem(tr("اعلان فقط برای خبرهای فوری", "Notify for breaking news only"), tr("هر چند ساعت بررسی می‌شود", "Checked every few hours"), s.breakingNotifications) { v -> upd { it.copy(breakingNotifications = v) } } }
        item { TextButton({ Nama.news.clear(); Nama.news.refreshIfStale(0) }, Modifier.padding(horizontal = 12.dp)) { Text(tr("پاک کردن و دریافت دوباره", "Clear and refetch")) } }
    }
    if (muted) TextInputDialog(tr("کلمه‌های ممنوع (با ویرگول)", "Muted words (comma separated)"), s.newsMutedWords.joinToString("، "), onDismiss = { muted = false }) { v ->
        upd { it.copy(newsMutedWords = v.split(',', '،').map { w -> w.trim() }.filter { w -> w.isNotEmpty() }) }
    }
    when (calmTime) {
        1 -> TimeDialog(s.newsCalmStartMinute, tr("شروع", "Start"), { calmTime = 0 }) { v -> upd { it.copy(newsCalmStartMinute = v) } }
        2 -> TimeDialog(s.newsCalmEndMinute, tr("پایان", "End"), { calmTime = 0 }) { v -> upd { it.copy(newsCalmEndMinute = v) } }
    }
    if (addOpen) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var cat by remember { mutableStateOf(NewsCategory.GENERAL) }
        AlertDialog(
            onDismissRequest = { addOpen = false },
            title = { Text(tr("منبع دلخواه", "Custom source")) },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text(tr("نام", "Name")) }, singleLine = true)
                    OutlinedTextField(url, { url = it }, label = { Text(tr("آدرس RSS یا @کانال تلگرام", "RSS URL or @telegram_channel")) }, singleLine = true)
                    ChipsSelector(NewsCategory.entries, setOf(cat), { tr(it.fa, it.en) }) { cat = it }
                }
            },
            confirmButton = {
                TextButton({
                    val u = url.trim()
                    val isTg = u.startsWith("@") || u.contains("t.me/")
                    val finalUrl = if (isTg) "https://t.me/s/" + u.removePrefix("@").substringAfterLast("t.me/").removePrefix("s/").trim('/') else u
                    if (name.isNotBlank() && (isTg || u.startsWith("http"))) {
                        val src = NewsSourceDef("c_" + UUID.randomUUID().toString().take(6), name.trim(), finalUrl, cat, if (isTg) "telegram" else "rss")
                        upd { it.copy(customNewsSources = it.customNewsSources + src, newsSourceIds = (Nama.news.enabledSources().map { e -> e.id } + src.id).toSet()) }
                        Nama.news.refreshIfStale(0)
                    }
                    addOpen = false
                }) { Text(tr("افزودن", "Add")) }
            },
            dismissButton = { TextButton({ addOpen = false }) { Text(tr("انصراف", "Cancel")) } }
        )
    }
}

// ------------------------------------------------------------------------------------------------
// Wellbeing
// ------------------------------------------------------------------------------------------------

@Composable
private fun WellbeingScreen() {
    val ctx = LocalContext.current
    val s by Nama.store.settings.flow.collectAsState()
    var picking by remember { mutableStateOf(false) }
    val report by produceState<Pair<Map<String, Long>, Map<String, Long>>?>(null) {
        value = Nama.usage.thisWeek() to Nama.usage.lastWeek()
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        if (!Nama.usage.hasPermission()) {
            item {
                Item(tr("دادن دسترسی آمار استفاده", "Grant usage access"), tr("برای گزارش هفتگی، سقف زمان و آرشیو برنامه‌های بی‌استفاده", "For reports, limits and archiving unused apps"), "🔓") {
                    Actions.start(ctx, Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
        } else report?.let { (week, last) ->
            val total = week.values.sum()
            val lastTotal = last.values.sum()
            header(tr("این هفته", "This week"))
            item {
                val hours = total / 3_600_000.0
                val diff = (total - lastTotal) / 3_600_000.0
                Text(
                    tr("این هفته ${num(ir.nama.core.PersianText.groupDecimal(hours, false, 1))} ساعت با گوشی بودی", "${"%.1f".format(hours)} hours this week") +
                        if (lastTotal > 0) {
                            if (diff < 0) tr("؛ ${num(ir.nama.core.PersianText.groupDecimal(-diff, false, 1))} ساعت کمتر از هفته قبل 👏", "; ${"%.1f".format(-diff)} h less than last week 👏")
                            else tr("؛ ${num(ir.nama.core.PersianText.groupDecimal(diff, false, 1))} ساعت بیشتر از هفته قبل.", "; ${"%.1f".format(diff)} h more than last week.")
                        } else "",
                    fontSize = 16.sp, modifier = Modifier.padding(20.dp), lineHeight = 26.sp
                )
            }
            items(week.entries.sortedByDescending { it.value }.take(10).toList()) { (pkg, ms) ->
                val e = Nama.apps.byPackage(pkg) ?: return@items
                val prev = last[pkg] ?: 0L
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIconImage(e, 32.dp, IconShape.CIRCLE)
                    Spacer(Modifier.width(10.dp))
                    Text(e.label, Modifier.weight(1f))
                    Text(num(ms / 60_000) + tr(" دقیقه", " min") + if (prev > 0) (if (ms > prev) " ↑" else " ↓") else "", fontSize = 13.sp)
                }
            }
            val top = week.entries.maxByOrNull { it.value }
            if (top != null && (top.value / 60_000) > 300) item {
                val name = Nama.apps.byPackage(top.key)?.label ?: top.key
                Text(
                    tr("پیشنهاد: برای «$name» حالت اصطکاک یا سقف روزانه بگذار (از تنظیمات › برنامه‌ها).", "Tip: add friction or a daily limit to \"$name\" (Settings › Apps)."),
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(20.dp)
                )
            }
        }
        header(tr("حالت تمرکز", "Focus mode"))
        item { Item(tr("برنامه‌های مجاز در تمرکز", "Apps allowed in focus"), num(s.focusApps.size)) { picking = true } }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(25, 50, 90).forEach { m ->
                    OutlinedButton({ upd { it.copy(focusUntil = System.currentTimeMillis() + m * 60_000L) }; Actions.toast(ctx, tr("تمرکز شروع شد", "Focus started")) }) { Text(num(m) + tr(" دقیقه", " min")) }
                }
            }
        }
        if (s.focusUntil > System.currentTimeMillis()) item { TextButton({ upd { it.copy(focusUntil = 0L) } }, Modifier.padding(horizontal = 12.dp)) { Text(tr("پایان تمرکز", "End focus")) } }
    }
    if (picking) AppPickerDialog(tr("مجاز در تمرکز", "Allowed in focus"), s.focusApps, onDismiss = { picking = false }) { set -> upd { it.copy(focusApps = set) } }
}

// ------------------------------------------------------------------------------------------------
// Permissions
// ------------------------------------------------------------------------------------------------

@Composable
private fun PermissionsScreen(activity: SettingsActivity) {
    val ctx = LocalContext.current
    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val lockOn = LockScreenService.instance != null
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                tr("همه دسترسی‌ها اختیاری‌اند. بدون هیچ‌کدام هم نما کار می‌کند؛ فقط آن قابلیت خاص خاموش می‌ماند. هیچ داده‌ای از گوشی به جایی فرستاده نمی‌شود.",
                    "All permissions are optional; without them only that feature is off. No data leaves your phone."),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp), lineHeight = 21.sp
            )
        }
        item {
            PermRow(tr("لانچر پیش‌فرض", "Default launcher"), tr("برای اینکه دکمه خانه نما را باز کند", "So the home button opens Nama"), Actions.isDefaultLauncher(ctx)) { Actions.requestDefaultLauncher(activity) }
        }
        item {
            PermRow(tr("موقعیت مکانی", "Location"), tr("فقط برای خواندن نام وای‌فای و فضاهای مکانی", "Only for the Wi-Fi name and place-based spaces"), granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
                activity.ask(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        if (Build.VERSION.SDK_INT >= 31) item {
            PermRow(tr("بلوتوث", "Bluetooth"), tr("برای فضای ماشین و هدفون", "For car and headphone spaces"), granted(Manifest.permission.BLUETOOTH_CONNECT)) { activity.ask(Manifest.permission.BLUETOOTH_CONNECT) }
        }
        item { PermRow(tr("مخاطبین", "Contacts"), tr("جستجوی مخاطب، تولدها، مخاطب‌های محبوب", "Contact search, birthdays, favorites"), granted(Manifest.permission.READ_CONTACTS)) { activity.ask(Manifest.permission.READ_CONTACTS) } }
        if (Build.VERSION.SDK_INT >= 33) item {
            PermRow(tr("اعلان‌های نما", "Nama notifications"), tr("یادآور قسط، پومودورو، خبر فوری", "Bill reminders, pomodoro, breaking news"), granted(Manifest.permission.POST_NOTIFICATIONS)) {
                Notifier.ensureChannels(ctx); activity.ask(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        item {
            PermRow(tr("آمار استفاده", "Usage access"), tr("گزارش هفتگی، سقف زمانی، آرشیو بی‌استفاده‌ها، مصرف اینترنت", "Reports, limits, unused apps, data usage"), Nama.usage.hasPermission()) {
                Actions.start(ctx, Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
        item {
            PermRow(tr("دسترسی اعلان‌ها", "Notification access"), tr("نقطه روی آیکون و خلاصه اعلان‌ها (فقط روی گوشی)", "Icon dots and digest (on device only)"), NotificationRepo.hasAccess(ctx)) {
                val i = if (Build.VERSION.SDK_INT >= 30) Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(AndroidSettings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(ctx, "ir.nama.launcher.system.NamaNotificationListener").flattenToString())
                else Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                if (!Actions.start(ctx, i)) Actions.start(ctx, Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        item {
            PermRow(
                tr("قفل صفحه با دو ضربه (دسترسی‌پذیری)", "Double-tap to lock (accessibility)"),
                tr("فقط برای قفل صفحه و باز کردن اعلان‌ها. محتوای صفحه خوانده نمی‌شود.", "Only locks the screen and opens notifications. Reads nothing."),
                lockOn
            ) { Actions.start(ctx, Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
    }
}

@Composable
private fun PermRow(title: String, sub: String, granted: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (granted) "✓" else tr("بده", "Grant"), color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------------------------------------------
// Backup & sync
// ------------------------------------------------------------------------------------------------

@Composable
private fun BackupScreen(activity: SettingsActivity) {
    val s by Nama.store.settings.flow.collectAsState()
    val history by Nama.store.history.flow.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var supa by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        header(tr("فایل", "File"))
        item { Item(tr("ذخیره پشتیبان در فایل", "Save backup to file"), tr("چیدمان، فضاها، قانون‌ها، یادداشت‌ها (بدون کارت‌های بانکی)", "Layout, spaces, rules, notes (no bank cards)"), "⬇") { activity.exportBackup() } }
        item { Item(tr("بازیابی از فایل", "Restore from file"), tr("چیدمان فعلی در تاریخچه می‌ماند", "Current layout stays in history"), "⬆") { activity.importBackup() } }
        item { Item(tr("اشتراک چیدمان", "Share layout"), tr("کد چیدمان را برای دوستت بفرست", "Send your layout as text"), "↗") { Actions.share(ctx, NamaJson.encodeToString(ir.nama.launcher.data.HomeLayout.serializer(), Nama.store.layout.value)) } }
        header(tr("تاریخچه چیدمان", "Layout history"))
        item { Item(tr("${num(history.size)} نسخه ذخیره شده", "${history.size} versions saved"), history.firstOrNull()?.let { tr("آخرین: ", "Latest: ") + ago(it.at) }) }
        item { Item(tr("برگرداندن آخرین تغییر", "Undo last change"), null, "↩") { if (Nama.store.undo()) Actions.toast(ctx, tr("برگشت", "Undone")) } }
        header("Supabase")
        item { Item(tr("همگام‌سازی ابری (اختیاری)", "Cloud sync (optional)"), if (Nama.supabase.signedIn()) tr("وارد شده: ", "Signed in: ") + s.supabaseEmail else tr("تنظیم نشده", "Not set up"), "☁") { supa = true } }
        if (Nama.supabase.signedIn()) {
            item {
                Item(tr("ارسال پشتیبان به ابر", "Upload backup"), null, "⤴") {
                    scope.launch { status = Nama.supabase.upload() ?: tr("ارسال شد ✓", "Uploaded ✓") }
                }
            }
            item {
                Item(tr("دریافت پشتیبان از ابر", "Download backup"), null, "⤵") {
                    scope.launch {
                        val (b, err) = Nama.supabase.download()
                        if (b != null) { Nama.store.restore(b); status = tr("بازیابی شد ✓", "Restored ✓") } else status = err
                    }
                }
            }
        }
        status?.let { item { Text(it, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.primary) } }
    }
    if (supa) {
        var url by remember { mutableStateOf(s.supabaseUrl) }
        var key by remember { mutableStateOf(s.supabaseKey) }
        var email by remember { mutableStateOf(s.supabaseEmail) }
        var pass by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        fun go(create: Boolean) {
            upd { it.copy(supabaseUrl = url.trim(), supabaseKey = key.trim()) }
            busy = true
            scope.launch {
                val err = Nama.supabase.signIn(email.trim(), pass, create)
                busy = false
                status = err ?: tr("وارد شدید ✓", "Signed in ✓")
                if (err == null) supa = false
            }
        }
        AlertDialog(
            onDismissRequest = { supa = false },
            title = { Text("Supabase") },
            text = {
                Column {
                    Text(tr("آدرس پروژه و کلید anon را از پنل Supabase بردارید. ساخت جدول در docs/SUPABASE.md توضیح داده شده.", "Project URL and anon key from the Supabase dashboard. See docs/SUPABASE.md."), fontSize = 12.sp)
                    OutlinedTextField(url, { url = it }, label = { Text("URL") }, singleLine = true)
                    OutlinedTextField(key, { key = it }, label = { Text("anon key") }, singleLine = true)
                    OutlinedTextField(email, { email = it }, label = { Text(tr("ایمیل", "Email")) }, singleLine = true)
                    OutlinedTextField(pass, { pass = it }, label = { Text(tr("رمز", "Password")) }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                }
            },
            confirmButton = { TextButton({ go(false) }, enabled = !busy) { Text(if (busy) "…" else tr("ورود", "Sign in")) } },
            dismissButton = { TextButton({ go(true) }, enabled = !busy) { Text(tr("ساخت حساب", "Create account")) } }
        )
    }
}

// ------------------------------------------------------------------------------------------------
// Safety
// ------------------------------------------------------------------------------------------------

@Composable
private fun SafetyScreen(activity: SettingsActivity) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(0) }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                tr(
                    "نما هیچ‌وقت راه برگشت را نمی‌بندد: از تنظیمات گوشی › برنامه‌های پیش‌فرض › برنامه صفحه اصلی می‌توانی هر وقت لانچر قبلی را برگردانی. اگر نما چند بار پشت سر هم به مشکل بخورد، خودش در «حالت امن» باز می‌شود.",
                    "Nama never locks you in: Phone settings › Default apps › Home app switches back anytime. After repeated crashes Nama opens in safe mode."
                ),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp), lineHeight = 21.sp
            )
        }
        item { Item(tr("انتخاب لانچر دیگر", "Choose another launcher"), tr("صفحه «برنامه صفحه اصلی» در تنظیمات گوشی", "Phone's Home app settings"), "🏠") { Actions.openHomeSettings(ctx) } }
        item { Item(tr("باز کردن حالت امن", "Open safe mode"), tr("لیست ساده برنامه‌ها و راه خروج", "A plain app list and a way out"), "🛟") { ctx.startActivity(Intent(ctx, SafeModeActivity::class.java)) } }
        item { Item(tr("راه‌اندازی دوباره", "Run setup again"), tr("چیدمان فعلی در تاریخچه می‌ماند", "Current layout stays in history"), "🔄") { ctx.startActivity(Intent(ctx, OnboardingActivity::class.java)) } }
        item { Item(tr("بازسازی چیدمان", "Rebuild layout"), tr("از روی علاقه‌ها و شغلی که انتخاب کردی", "From your interests and persona"), "🧱") { confirm = 1 } }
        item { Item(tr("بازنشانی کامل نما", "Reset Nama completely"), tr("همه تنظیمات و چیدمان پاک می‌شود (کارت‌های بانکی هم)", "Erases all settings and layout"), "⚠") { confirm = 2 } }
    }
    when (confirm) {
        1 -> ConfirmDialog(tr("بازسازی چیدمان", "Rebuild layout"), tr("چیدمان تازه ساخته می‌شود؛ از تاریخچه قابل برگشت است.", "A new layout is built; undo from history."), tr("بساز", "Build"), {
            val s = Nama.settings
            Nama.store.changeLayout(tr("بازسازی چیدمان", "Rebuild")) { Templates.initialLayout(Nama.apps.apps.value, s.persona, s.interests, s.style, s.columns.coerceIn(4, 5), s.rows.coerceIn(5, 7)) }
        }, { confirm = 0 })
        2 -> ConfirmDialog(tr("بازنشانی کامل", "Full reset"), tr("مطمئنی؟ این کار برگشت ندارد.", "Are you sure? This cannot be undone."), tr("پاک کن", "Erase"), {
            Nama.store.settings.set(ir.nama.launcher.data.Settings())
            Nama.store.layout.set(ir.nama.launcher.data.HomeLayout())
            Nama.store.spaces.set(emptyList())
            Nama.store.appPrefs.set(emptyMap())
            Nama.store.personal.set(ir.nama.launcher.data.Personal())
            Nama.store.rules.set(ir.nama.launcher.data.DefaultRules.all())
            Nama.cards.set(emptyList())
            Nama.store.flushAll()
            CrashGuard.leaveSafeMode(ctx)
            ctx.startActivity(Intent(ctx, OnboardingActivity::class.java))
            activity.finish()
        }, { confirm = 0 }, danger = true)
    }
}

@Composable
private fun AboutScreen() {
    val ctx = LocalContext.current
    val version = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { "" } }
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("نما", "Nama"), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(tr("نسخه ", "Version ") + num(version ?: ""))
        Text(tr(
            "لانچر فارسی با فضاهای هوشمند، مرتب‌سازی خودکار، حالت اصطکاک و ویجت‌های ایرانی. همه داده‌ها روی خود گوشی می‌ماند؛ فقط خبر، قیمت و آب‌وهوا از اینترنت گرفته می‌شود.",
            "A Persian launcher with smart spaces, auto-sorting, friction mode and Iranian widgets. Your data stays on the phone."
        ), lineHeight = 24.sp)
        Text(tr("فونت‌ها: وزیرمتن و لاله‌زار (مجوز OFL).", "Fonts: Vazirmatn and Lalezar (OFL)."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(tr("آب‌وهوا: Open-Meteo. قیمت‌ها: نوبیتکس.", "Weather: Open-Meteo. Prices: Nobitex."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Text(tr("تنظیمات منبع‌ها از گیت‌هاب پروژه به‌روز می‌شود.", "Source settings update from the project's GitHub."), fontSize = 12.sp)
    }
}
