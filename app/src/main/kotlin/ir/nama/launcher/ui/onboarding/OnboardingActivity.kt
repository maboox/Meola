package ir.nama.launcher.ui.onboarding

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import ir.nama.core.Cities
import ir.nama.core.StyleId
import ir.nama.launcher.Nama
import ir.nama.launcher.data.Interests
import ir.nama.launcher.data.Personas
import ir.nama.launcher.logic.Templates
import ir.nama.launcher.tr
import ir.nama.launcher.ui.MainActivity
import ir.nama.launcher.ui.common.ChoiceDialog
import ir.nama.launcher.ui.theme.NamaAppTheme
import ir.nama.launcher.ui.theme.StyleBackground
import ir.nama.launcher.ui.theme.Styles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class OnboardingActivity : AppCompatActivity() {
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Nama.context.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Nama.ready) { finish(); return }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            var lang by remember { mutableStateOf(Nama.settings.language) }
            NamaAppTheme(dark = isSystemInDarkTheme(), rtl = lang != "en") {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Onboarding(
                        lang = lang,
                        onLang = { l -> lang = l; Nama.store.settings.update { it.copy(language = l) } },
                        askLocation = { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                        done = { askDefault ->
                            // The home screen asks to become the default launcher itself, so the
                            // system dialog is shown on top of Nama rather than hidden behind it.
                            startActivity(
                                Intent(this, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    .putExtra(MainActivity.EXTRA_ASK_DEFAULT, askDefault)
                            )
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Onboarding(lang: String, onLang: (String) -> Unit, askLocation: () -> Unit, done: (askDefault: Boolean) -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var style by remember { mutableStateOf(StyleId.PERSIAN) }
    var interests by remember { mutableStateOf(setOf<String>()) }
    var persona by remember { mutableStateOf<String?>(null) }
    var city by remember { mutableStateOf("tehran") }
    var homeSsid by remember { mutableStateOf<String?>(null) }
    var workSsid by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val total = 5
    BackHandler(enabled = step > 0) { step-- }

    fun finish(default: Boolean) {
        if (working) return
        working = true
        scope.launch {
            // Wait (up to 10 s) for the app list so the first layout has real apps.
            withTimeoutOrNull(10_000) { Nama.apps.loaded.first { it } }
            withContext(Dispatchers.Default) {
                val apps = Nama.apps.apps.value
                val st = Nama.store
                st.settings.update {
                    it.copy(
                        onboarded = true, onboardedAt = System.currentTimeMillis(), style = style, interests = interests,
                        persona = persona, cityId = city, language = lang,
                        newsSourceIds = Templates.newsSourcesFor(interests, persona),
                        iconSizeDp = if (persona == "retired") 62 else 52,
                        columns = if (persona == "retired") 3 else 4
                    )
                }
                st.spaces.set(Templates.spacesFor(persona, homeSsid, workSsid))
                st.changeLayout(tr("راه‌اندازی", "Setup")) { Templates.initialLayout(apps, persona, interests, style) }
                // Apps installed before setup are not "new".
                st.appPrefs.update { m -> m.mapValues { (_, p) -> p.copy(reviewed = true) } }
                st.flushAll()
            }
            working = false
            done(default)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
        LinearProgressIndicator(progress = { (step + 1f) / total }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
        Text(tr("قدم ${step + 1} از $total", "Step ${step + 1} of $total"), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (step) {
                0 -> {
                    Text(tr("به نما خوش آمدی", "Welcome to Nama"), fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                    Text(
                        tr(
                            "نما صفحه اصلی گوشی را بر اساس جا، زمان و سلیقه‌ات می‌چیند. چند سؤال کوتاه می‌پرسیم تا یک چیدمان اولیه برایت بسازیم. همه چیز بعداً قابل تغییر است.",
                            "Nama arranges your home screen by place, time and taste. A few quick questions build your first layout. Everything can be changed later."
                        ),
                        fontSize = 15.sp, lineHeight = 26.sp, modifier = Modifier.padding(vertical = 12.dp)
                    )
                    Text(tr("زبان", "Language"), fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(lang == "fa", { onLang("fa") }, label = { Text("فارسی") })
                        FilterChip(lang == "en", { onLang("en") }, label = { Text("English") })
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        tr(
                            "خیالت راحت: نما خودش لانچر اصلی نمی‌شود مگر خودت بخواهی، و هر وقت بخواهی از تنظیمات گوشی می‌توانی به لانچر قبلی برگردی.",
                            "Nama only becomes your launcher if you choose it, and you can switch back anytime from phone settings."
                        ),
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp
                    )
                }
                1 -> {
                    Title(tr("کدام ظاهر؟", "Which look?"), tr("بعداً می‌توانی برای هر فضا ظاهر جدا بگذاری.", "You can set a different look per space later."))
                    StyleId.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 5.dp)) {
                            row.forEach { id -> StyleCard(id, id == style, Modifier.weight(1f)) { style = id } }
                        }
                    }
                }
                2 -> {
                    Title(tr("به چی علاقه داری؟", "What are you into?"), tr("ویجت‌ها، خبرها و چیدمان از روی این‌ها انتخاب می‌شوند.", "Widgets and news are picked from these."))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Interests.ALL.forEach { (id, names) ->
                            FilterChip(id in interests, { interests = if (id in interests) interests - id else interests + id }, label = { Text(tr(names.first, names.second)) })
                        }
                    }
                }
                3 -> {
                    Title(tr("روزهایت بیشتر چطور می‌گذرد؟", "What do your days look like?"), tr("برای فضاها و قالب صفحه اصلی.", "For spaces and the home template."))
                    Personas.ALL.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 5.dp)) {
                            row.forEach { p ->
                                val sel = persona == p.id
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                        .border(if (sel) 2.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                                        .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
                                        .clickable { persona = p.id }.padding(12.dp)
                                ) {
                                    Text(tr(p.fa, p.en), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(tr(p.hintFa, p.hintEn), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                4 -> Places(city, { city = it }, homeSsid, { homeSsid = it }, workSsid, { workSsid = it }, askLocation)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) TextButton({ step-- }) { Text(tr("قبلی", "Back")) }
            Spacer(Modifier.weight(1f))
            if (step < total - 1) {
                Button({ step++ }) { Text(tr("ادامه", "Next")) }
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Button({ finish(true) }, enabled = !working) { Text(if (working) tr("در حال ساخت…", "Building…") else tr("بساز و نما را لانچر اصلی کن", "Build & make Nama my launcher")) }
                    OutlinedButton({ finish(false) }, enabled = !working) { Text(tr("فعلاً فقط امتحانش کنم", "Just try it for now")) }
                }
            }
        }
    }
}

@Composable
private fun Title(t: String, sub: String) {
    Text(t, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
    Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
}

@Composable
private fun StyleCard(id: StyleId, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val st = Styles.build(id, null, false, false)
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp))
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
        ) {
            StyleBackground(st)
            Column(Modifier.padding(12.dp)) {
                Text(ir.nama.launcher.num("9:41"), color = if (st.pattern) st.accent2 else st.text, fontSize = 30.sp, fontFamily = st.clockFont, fontWeight = FontWeight.Light)
                Text(tr("جمعه ۱۷ مهر", "Fri 17 Mehr"), color = st.subText, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                if (st.textOnly) {
                    listOf(tr("پیام‌رسان", "Messages"), tr("نقشه", "Maps"), tr("بانک", "Bank")).forEach { Text(it, color = st.text, fontSize = 13.sp, fontWeight = FontWeight.Light) }
                } else Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(4) {
                        Box(Modifier.size(22.dp).clip(if (id == StyleId.PERSIAN) RoundedCornerShape(11.dp, 11.dp, 4.dp, 4.dp) else RoundedCornerShape(7.dp)).background(if (id == StyleId.PERSIAN) st.accent else st.card).border(1.dp, st.cardBorder, RoundedCornerShape(7.dp)))
                    }
                }
            }
        }
        Text(tr(id.fa, id.en), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Places(
    city: String, onCity: (String) -> Unit,
    homeSsid: String?, onHome: (String?) -> Unit,
    workSsid: String?, onWork: (String?) -> Unit,
    askLocation: () -> Unit
) {
    var pickCity by remember { mutableStateOf(false) }
    val ssid by Nama.context.currentSsid.collectAsState()
    val hasLoc = Nama.context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    Title(tr("شهر و مکان‌ها", "City and places"), tr("برای اوقات شرعی، آب‌وهوا و فضاهای خانه و کار. همه اختیاری است.", "For prayer times, weather and home/work spaces. All optional."))
    OutlinedButton({ pickCity = true }) { Text(tr("شهر: ", "City: ") + Cities.byId(city).let { if (Nama.isFa) it.fa else it.en }) }
    Spacer(Modifier.height(16.dp))
    Text(tr("وای‌فای خانه و محل کار", "Home and work Wi-Fi"), fontWeight = FontWeight.Bold)
    Text(
        tr(
            "اندروید برای خواندن نام وای‌فای، اجازه «موقعیت مکانی» می‌خواهد. نما مکان شما را جایی نمی‌فرستد؛ فقط نام وای‌فای را برای تشخیص فضا روی خود گوشی نگه می‌دارد.",
            "Android requires location permission to read the Wi-Fi name. Nama keeps it on the phone only, to detect spaces."
        ),
        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp
    )
    if (!hasLoc) {
        TextButton(askLocation) { Text(tr("اجازه خواندن نام وای‌فای", "Allow reading the Wi-Fi name")) }
    } else {
        Text(tr("وای‌فای فعلی: ", "Current Wi-Fi: ") + (ssid ?: tr("وصل نیست", "not connected")), fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ onHome(ssid) }, enabled = ssid != null) { Text(tr("این وای‌فای خانه است", "This is home")) }
            OutlinedButton({ onWork(ssid) }, enabled = ssid != null) { Text(tr("این وای‌فای کار است", "This is work")) }
        }
    }
    homeSsid?.let { Text("🏠 $it", fontSize = 13.sp) }
    workSsid?.let { Text("💼 $it", fontSize = 13.sp) }
    if (pickCity) ChoiceDialog(tr("شهر", "City"), Cities.ALL.map { if (Nama.isFa) it.fa else it.en }, Cities.ALL.indexOfFirst { it.id == city }, { pickCity = false }) { i ->
        onCity(Cities.ALL[i].id)
    }
}
