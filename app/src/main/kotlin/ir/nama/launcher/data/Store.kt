package ir.nama.launcher.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import ir.nama.core.SortRule
import ir.nama.core.Space
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

val NamaJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}

/**
 * One JSON file backed by a StateFlow. Writes are debounced and atomic, so a crash while saving
 * never leaves a half-written file. A file that cannot be read is moved aside and defaults are used.
 */
class Section<T>(
    private val dir: File,
    private val name: String,
    private val serializer: KSerializer<T>,
    private val default: () -> T,
    scope: CoroutineScope
) {
    private val file = AtomicFile(File(dir, "$name.json"))
    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<T> = _flow.asStateFlow()
    val value: T get() = _flow.value

    init {
        @OptIn(FlowPreview::class)
        _flow.drop(1).debounce(400).onEach { write(it) }.launchIn(scope)
    }

    private fun load(): T {
        val base = file.baseFile
        if (!base.exists()) return default()
        return try {
            val text = file.readFully().toString(Charsets.UTF_8)
            NamaJson.decodeFromString(serializer, text)
        } catch (e: Exception) {
            Log.e("NamaStore", "Could not read $name, using defaults", e)
            try {
                base.copyTo(File(dir, "$name.corrupt-${System.currentTimeMillis()}.json"), overwrite = true)
            } catch (_: Exception) {
            }
            default()
        }
    }

    @Synchronized
    private fun write(value: T) {
        var out: java.io.FileOutputStream? = null
        try {
            out = file.startWrite()
            out.write(NamaJson.encodeToString(serializer, value).toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            Log.e("NamaStore", "Could not save $name", e)
            if (out != null) file.failWrite(out)
        }
    }

    fun set(v: T) { _flow.value = v }
    fun update(f: (T) -> T) = _flow.update(f)

    /** Writes immediately (used before risky operations and on pause). */
    fun flushNow() = write(_flow.value)
}

class Store(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir = File(context.filesDir, "nama").apply { mkdirs() }

    val settings = Section(dir, "settings", Settings.serializer(), { Settings() }, scope)
    val layout = Section(dir, "layout", HomeLayout.serializer(), { HomeLayout() }, scope)
    val spaces = Section(dir, "spaces", ListSerializer(Space.serializer()), { emptyList() }, scope)
    val appPrefs = Section(dir, "app_prefs", MapSerializer(String.serializer(), AppPref.serializer()), { emptyMap() }, scope)
    val appStats = Section(dir, "app_stats", MapSerializer(String.serializer(), AppStats.serializer()), { emptyMap() }, scope)
    val rules = Section(dir, "rules", ListSerializer(SortRule.serializer()), { DefaultRules.all() }, scope)
    val personal = Section(dir, "personal", Personal.serializer(), { Personal() }, scope)
    val history = Section(dir, "history", ListSerializer(LayoutSnapshot.serializer()), { emptyList() }, scope)

    fun flushAll() {
        settings.flushNow(); layout.flushNow(); spaces.flushNow(); appPrefs.flushNow()
        appStats.flushNow(); rules.flushNow(); personal.flushNow(); history.flushNow()
    }

    fun pref(key: String): AppPref = appPrefs.value[key] ?: AppPref()

    fun updatePref(key: String, f: (AppPref) -> AppPref) =
        appPrefs.update { it + (key to f(it[key] ?: AppPref())) }

    /** Saves the current layout to history (max 30 entries), then applies the change. */
    fun changeLayout(label: String, f: (HomeLayout) -> HomeLayout) {
        val before = layout.value
        val after = f(before)
        if (after == before) return
        history.update { (listOf(LayoutSnapshot(System.currentTimeMillis(), label, before)) + it).take(30) }
        layout.set(after)
    }

    /** Changes that should not create an undo step (e.g. shuffling an app after launch). */
    fun changeLayoutSilently(f: (HomeLayout) -> HomeLayout) = layout.update(f)

    fun undo(): Boolean {
        val last = history.value.firstOrNull() ?: return false
        history.update { it.drop(1) }
        layout.set(last.layout)
        return true
    }

    fun recordLaunch(key: String) {
        val now = System.currentTimeMillis()
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        appStats.update { m ->
            val s = m[key] ?: AppStats()
            val hours = if (s.hours.size == 24) s.hours.toMutableList() else MutableList(24) { 0 }
            hours[hour] = hours[hour] + 1
            m + (key to s.copy(launches = s.launches + 1, lastLaunch = now, hours = hours))
        }
    }

    fun backup(): Backup = Backup(
        createdAt = System.currentTimeMillis(),
        settings = settings.value.copy(supabaseKey = ""),
        layout = layout.value,
        spaces = spaces.value,
        appPrefs = appPrefs.value,
        rules = rules.value,
        personal = personal.value
    )

    fun restore(b: Backup) {
        changeLayout("بازیابی پشتیبان") { b.layout }
        val keep = settings.value
        settings.set(b.settings.copy(supabaseKey = keep.supabaseKey, onboarded = true))
        spaces.set(b.spaces)
        appPrefs.set(b.appPrefs)
        rules.set(b.rules)
        personal.set(b.personal)
        flushAll()
    }
}

object DefaultRules {
    fun all(): List<SortRule> = listOf(
        SortRule(
            id = "archive_unused",
            name = "آرشیو برنامه‌هایی که ۶۰ روز باز نشده‌اند",
            enabled = false,
            conditions = listOf(ir.nama.core.RuleCondition.UnusedForDays(60), ir.nama.core.RuleCondition.IsSystem(false)),
            action = ir.nama.core.RuleAction.Archive
        ),
        SortRule(
            id = "banks_folder",
            name = "همه بانک‌ها در پوشه «بانک»",
            enabled = true,
            conditions = listOf(ir.nama.core.RuleCondition.CategoryIs(ir.nama.core.AppCategory.FINANCE)),
            action = ir.nama.core.RuleAction.PutInFolder("بانک و مالی")
        )
    )
}
