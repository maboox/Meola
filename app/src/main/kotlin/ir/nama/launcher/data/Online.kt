package ir.nama.launcher.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import android.util.Xml
import ir.nama.core.Cities
import ir.nama.core.JsonPath
import ir.nama.core.NewsCategory
import ir.nama.core.NewsItem
import ir.nama.core.NewsLogic
import ir.nama.core.NewsSourceDef
import ir.nama.core.PersianText
import ir.nama.core.RemoteConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Net {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36 Nama/1.0"

    suspend fun get(url: String, timeoutMs: Int = 12_000, headers: Map<String, String> = emptyMap()): String? =
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", UA)
                    setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
                    headers.forEach { (k, v) -> setRequestProperty(k, v) }
                }
                if (conn.responseCode !in 200..299) return@withContext null
                conn.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
            } catch (e: Exception) {
                null
            } finally {
                conn?.disconnect()
            }
        }

    suspend fun send(
        url: String,
        method: String,
        body: String?,
        headers: Map<String, String>,
        timeoutMs: Int = 15_000
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            code to (stream?.use { it.readBytes() }?.toString(Charsets.UTF_8) ?: "")
        } catch (e: Exception) {
            -1 to (e.message ?: "network error")
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun reachable(url: String, timeoutMs: Int = 6_000): Boolean = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", UA)
            }
            conn.responseCode in 100..499
        } catch (e: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }
}

/** Small JSON file cache in the app's cache directory. */
class FileCache(context: Context) {
    private val dir = File(context.cacheDir, "nama").apply { mkdirs() }
    fun <T> read(name: String, s: KSerializer<T>): T? = try {
        val f = File(dir, "$name.json")
        if (f.exists()) NamaJson.decodeFromString(s, f.readText()) else null
    } catch (e: Exception) {
        null
    }

    fun <T> write(name: String, s: KSerializer<T>, v: T) {
        try {
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(NamaJson.encodeToString(s, v))
            tmp.renameTo(File(dir, "$name.json"))
        } catch (e: Exception) {
            Log.w("Nama", "cache write failed: $name", e)
        }
    }
}

class RemoteConfigRepo(private val context: Context, private val cache: FileCache, private val scope: CoroutineScope) {
    private val _config = MutableStateFlow(loadLocal())
    val config: StateFlow<RemoteConfig> = _config.asStateFlow()
    private var lastFetch = 0L

    private fun bundled(): RemoteConfig = try {
        context.assets.open("remote.json").use { NamaJson.decodeFromString(RemoteConfig.serializer(), it.readBytes().toString(Charsets.UTF_8)) }
    } catch (e: Exception) {
        Log.e("Nama", "bundled config unreadable", e)
        RemoteConfig()
    }

    private fun loadLocal(): RemoteConfig {
        val b = bundled()
        val c = cache.read("remote_config", RemoteConfig.serializer())
        return if (c != null && c.version >= b.version) c else b
    }

    fun refreshIfStale() {
        val now = System.currentTimeMillis()
        if (now - lastFetch < 6 * 60 * 60 * 1000L) return
        lastFetch = now
        scope.launch {
            for (url in URLS) {
                val text = Net.get(url) ?: continue
                val parsed = try { NamaJson.decodeFromString(RemoteConfig.serializer(), text) } catch (e: Exception) { null } ?: continue
                if (parsed.version >= _config.value.version) {
                    _config.value = parsed
                    cache.write("remote_config", RemoteConfig.serializer(), parsed)
                }
                break
            }
        }
    }

    companion object {
        val URLS = listOf(
            "https://raw.githubusercontent.com/maboox/Meola/main/config/remote.json",
            "https://cdn.jsdelivr.net/gh/maboox/Meola@main/config/remote.json"
        )
    }
}

@Serializable
data class PriceQuote(
    val symbol: String,
    val title: String,
    val value: Double,
    val changePercent: Double? = null,
    val unit: String,
    val at: Long
)

class PricesRepo(private val cache: FileCache, private val config: StateFlow<RemoteConfig>, private val scope: CoroutineScope) {
    private val _quotes = MutableStateFlow(cache.read("prices", ListSerializer(PriceQuote.serializer())) ?: emptyList())
    val quotes: StateFlow<List<PriceQuote>> = _quotes.asStateFlow()
    val loading = MutableStateFlow(false)
    val failed = MutableStateFlow(false)
    private val mutex = Mutex()

    fun refreshIfStale(maxAgeMs: Long = 15 * 60 * 1000L) {
        val newest = _quotes.value.maxOfOrNull { it.at } ?: 0L
        if (System.currentTimeMillis() - newest < maxAgeMs) return
        scope.launch { refresh() }
    }

    suspend fun refresh() = mutex.withLock {
        val defs = config.value.priceSources
        if (defs.isEmpty()) return@withLock
        loading.value = true
        val now = System.currentTimeMillis()
        val byUrl = defs.groupBy { it.url }
        val results = coroutineScope {
            byUrl.map { (url, list) ->
                async {
                    val text = Net.get(url) ?: return@async emptyList<PriceQuote>()
                    val root = try { Json.parseToJsonElement(text) } catch (e: Exception) { null } ?: return@async emptyList()
                    list.mapNotNull { d ->
                        val v = JsonPath.number(root, d.valuePath) ?: return@mapNotNull null
                        val ch = d.changePath?.let { JsonPath.number(root, it) }
                        PriceQuote(d.symbol, d.title, v / d.divisor, ch, d.unit, now)
                    }
                }
            }.awaitAll().flatten()
        }
        loading.value = false
        failed.value = results.isEmpty()
        if (results.isNotEmpty()) {
            val merged = (results + _quotes.value.filter { old -> results.none { it.symbol == old.symbol } })
            val order = defs.map { it.symbol }
            _quotes.value = merged.sortedBy { q -> order.indexOf(q.symbol).let { if (it < 0) 999 else it } }
            cache.write("prices", ListSerializer(PriceQuote.serializer()), _quotes.value)
        }
    }

    fun price(symbol: String): PriceQuote? = _quotes.value.firstOrNull { it.symbol == symbol }
}

@Serializable
data class Weather(
    val cityId: String,
    val tempC: Double,
    val code: Int,
    val maxC: Double? = null,
    val minC: Double? = null,
    val aqi: Int? = null,
    val pm25: Double? = null,
    val at: Long
)

class WeatherRepo(private val cache: FileCache, private val scope: CoroutineScope) {
    private val _weather = MutableStateFlow(cache.read("weather", Weather.serializer()))
    val weather: StateFlow<Weather?> = _weather.asStateFlow()
    private val mutex = Mutex()

    fun refreshIfStale(cityId: String) {
        val w = _weather.value
        if (w != null && w.cityId == cityId && System.currentTimeMillis() - w.at < 30 * 60 * 1000L) return
        scope.launch { refresh(cityId) }
    }

    suspend fun refresh(cityId: String) = mutex.withLock {
        val c = Cities.byId(cityId)
        val f = Net.get(
            "https://api.open-meteo.com/v1/forecast?latitude=${c.lat}&longitude=${c.lng}" +
                "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
        ) ?: return@withLock
        val a = Net.get(
            "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${c.lat}&longitude=${c.lng}&current=us_aqi,pm2_5"
        )
        try {
            val root = Json.parseToJsonElement(f)
            val temp = JsonPath.number(root, "current.temperature_2m") ?: return@withLock
            val code = JsonPath.number(root, "current.weather_code")?.toInt() ?: 0
            val max = JsonPath.number(root, "daily.temperature_2m_max.0")
            val min = JsonPath.number(root, "daily.temperature_2m_min.0")
            var aqi: Int? = null
            var pm: Double? = null
            if (a != null) {
                val ar: JsonElement = Json.parseToJsonElement(a)
                aqi = JsonPath.number(ar, "current.us_aqi")?.toInt()
                pm = JsonPath.number(ar, "current.pm2_5")
            }
            val w = Weather(cityId, temp, code, max, min, aqi, pm, System.currentTimeMillis())
            _weather.value = w
            cache.write("weather", Weather.serializer(), w)
        } catch (e: Exception) {
            Log.w("Nama", "weather parse failed", e)
        }
    }

    companion object {
        fun describe(code: Int, fa: Boolean): Pair<String, String> = when (code) {
            0 -> "☀" to if (fa) "آفتابی" else "Clear"
            1, 2 -> "⛅" to if (fa) "نیمه‌ابری" else "Partly cloudy"
            3 -> "☁" to if (fa) "ابری" else "Cloudy"
            45, 48 -> "🌫" to if (fa) "مه" else "Fog"
            51, 53, 55, 56, 57 -> "🌦" to if (fa) "نم‌نم باران" else "Drizzle"
            61, 63, 65, 66, 67, 80, 81, 82 -> "🌧" to if (fa) "بارانی" else "Rain"
            71, 73, 75, 77, 85, 86 -> "❄" to if (fa) "برفی" else "Snow"
            95, 96, 99 -> "⛈" to if (fa) "رعدوبرق" else "Thunderstorm"
            else -> "•" to ""
        }

        fun aqiLabel(aqi: Int, fa: Boolean): String = when {
            aqi <= 50 -> if (fa) "پاک" else "Good"
            aqi <= 100 -> if (fa) "قابل قبول" else "Moderate"
            aqi <= 150 -> if (fa) "ناسالم برای گروه‌های حساس" else "Unhealthy for sensitive groups"
            aqi <= 200 -> if (fa) "ناسالم" else "Unhealthy"
            aqi <= 300 -> if (fa) "بسیار ناسالم" else "Very unhealthy"
            else -> if (fa) "خطرناک" else "Hazardous"
        }
    }
}

class NewsRepo(
    private val cache: FileCache,
    private val config: StateFlow<RemoteConfig>,
    private val store: Store,
    private val scope: CoroutineScope
) {
    private val _items = MutableStateFlow(cache.read("news", ListSerializer(NewsItem.serializer())) ?: emptyList())
    val items: StateFlow<List<NewsItem>> = _items.asStateFlow()
    val loading = MutableStateFlow(false)
    val sourceErrors = MutableStateFlow<Set<String>>(emptySet())
    private var lastFetch = 0L
    private val mutex = Mutex()

    fun allSources(): List<NewsSourceDef> = config.value.newsSources + store.settings.value.customNewsSources

    fun enabledSources(): List<NewsSourceDef> {
        val ids = store.settings.value.newsSourceIds
        val all = allSources()
        return if (ids == null) all.filter { it.enabledByDefault } else all.filter { it.id in ids }
    }

    fun refreshIfStale(maxAgeMs: Long = 30 * 60 * 1000L) {
        if (System.currentTimeMillis() - lastFetch < maxAgeMs) return
        scope.launch { refresh() }
    }

    /** Returns newly seen items. */
    suspend fun refresh(): List<NewsItem> = mutex.withLock {
        lastFetch = System.currentTimeMillis()
        val sources = enabledSources()
        if (sources.isEmpty()) return@withLock emptyList()
        loading.value = true
        val errors = mutableSetOf<String>()
        val fetched = coroutineScope {
            sources.map { src ->
                async {
                    val r = withTimeoutOrNull(20_000) { fetch(src) }
                    if (r.isNullOrEmpty()) synchronized(errors) { errors += src.id }
                    r ?: emptyList()
                }
            }.awaitAll().flatten()
        }
        loading.value = false
        sourceErrors.value = errors
        val oldIds = _items.value.map { it.id }.toHashSet()
        val fresh = fetched.filter { it.id !in oldIds }
        val keepIds = sources.map { it.id }.toSet()
        val merged = NewsLogic.dedupe(fetched + _items.value.filter { it.sourceId in keepIds }).take(400)
        _items.value = merged
        cache.write("news", ListSerializer(NewsItem.serializer()), merged)
        fresh
    }

    private suspend fun fetch(src: NewsSourceDef): List<NewsItem> {
        val text = Net.get(src.url) ?: return emptyList()
        return try {
            if (src.type == "telegram") parseTelegram(text, src) else parseFeed(text, src)
        } catch (e: Exception) {
            Log.w("Nama", "feed parse failed ${src.id}", e)
            emptyList()
        }
    }

    private fun parseFeed(xml: String, src: NewsSourceDef): List<NewsItem> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(xml.reader())
        val out = mutableListOf<NewsItem>()
        var inItem = false
        var title = ""; var link = ""; var date = ""; var desc = ""; var image: String? = null
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name.lowercase()
                    when {
                        name == "item" || name == "entry" -> {
                            inItem = true; title = ""; link = ""; date = ""; desc = ""; image = null
                        }
                        !inItem -> {}
                        name == "title" -> title = safeText(parser)
                        name == "link" -> {
                            val href = parser.getAttributeValue(null, "href")
                            if (href != null) { if (link.isEmpty()) link = href } else link = safeText(parser)
                        }
                        name == "pubdate" || name == "updated" || name == "published" || name == "dc:date" -> date = safeText(parser)
                        name == "description" || name == "summary" || name == "content:encoded" ->
                            if (desc.isEmpty()) desc = safeText(parser)
                        name == "enclosure" || name == "media:content" || name == "media:thumbnail" -> {
                            val u = parser.getAttributeValue(null, "url")
                            if (image == null && u != null) image = u
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name.lowercase()
                    if ((name == "item" || name == "entry") && inItem) {
                        inItem = false
                        val t = NewsLogic.stripHtml(title)
                        if (t.isNotBlank() && link.isNotBlank()) {
                            out += NewsItem(
                                id = src.id + ":" + link.trim().hashCode(),
                                title = t,
                                link = link.trim(),
                                sourceId = src.id,
                                sourceName = src.name,
                                category = src.category,
                                publishedAt = parseDate(date),
                                summary = NewsLogic.stripHtml(desc).take(280),
                                imageUrl = image
                            )
                        }
                    }
                }
            }
            ev = parser.next()
        }
        return out.take(40)
    }

    private fun safeText(p: XmlPullParser): String = try { p.nextText() ?: "" } catch (e: Exception) { "" }

    private fun parseTelegram(html: String, src: NewsSourceDef): List<NewsItem> {
        val out = mutableListOf<NewsItem>()
        val blocks = html.split("tgme_widget_message_wrap").drop(1)
        for (b in blocks) {
            val post = Regex("data-post=\"([^\"]+)\"").find(b)?.groupValues?.get(1) ?: continue
            val textHtml = Regex("tgme_widget_message_text[^>]*>(.*?)</div>", RegexOption.DOT_MATCHES_ALL).find(b)?.groupValues?.get(1) ?: continue
            val text = NewsLogic.stripHtml(textHtml)
            if (text.isBlank()) continue
            val dt = Regex("datetime=\"([^\"]+)\"").find(b)?.groupValues?.get(1)
            val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }?.take(160) ?: continue
            out += NewsItem(
                id = src.id + ":" + post,
                title = firstLine,
                link = "https://t.me/$post",
                sourceId = src.id,
                sourceName = src.name,
                category = src.category,
                publishedAt = dt?.let { parseDate(it) } ?: System.currentTimeMillis(),
                summary = text.removePrefix(firstLine).trim().take(280)
            )
        }
        return out.takeLast(30)
    }

    private fun parseDate(s: String): Long {
        val t = s.trim()
        if (t.isEmpty()) return System.currentTimeMillis()
        try { return ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() } catch (_: Exception) {}
        try { return OffsetDateTime.parse(t).toInstant().toEpochMilli() } catch (_: Exception) {}
        for (f in listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz", "yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd HH:mm:ss")) {
            try { return SimpleDateFormat(f, Locale.US).parse(t)?.time ?: continue } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }

    fun filtered(categories: Set<NewsCategory>, muted: List<String>, calm: Boolean): List<NewsItem> =
        NewsLogic.filter(_items.value, ir.nama.core.NewsFilter(categories, muted, calm))

    fun clear() {
        _items.value = emptyList()
        cache.write("news", ListSerializer(NewsItem.serializer()), emptyList())
    }
}

enum class NetState { UNKNOWN, NORMAL, NATIONAL_ONLY, OFFLINE }

/** Detects when only the domestic (national) internet works. */
class ConnectivityRepo(private val context: Context, private val config: StateFlow<RemoteConfig>, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(NetState.UNKNOWN)
    val state: StateFlow<NetState> = _state.asStateFlow()
    private var lastCheck = 0L
    private val mutex = Mutex()

    fun start() {
        try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = checkSoon()
                override fun onLost(network: Network) { _state.value = NetState.OFFLINE }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) checkSoon()
                }
            })
        } catch (e: Exception) {
            Log.w("Nama", "network callback failed", e)
        }
    }

    private fun checkSoon() {
        lastCheck = 0L
        checkIfStale()
    }

    fun checkIfStale() {
        if (System.currentTimeMillis() - lastCheck < 10 * 60 * 1000L) return
        lastCheck = System.currentTimeMillis()
        scope.launch { check() }
    }

    suspend fun check() = mutex.withLock {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val active = cm.activeNetwork
        if (active == null) { _state.value = NetState.OFFLINE; return@withLock }
        val cfg = config.value
        val intl = coroutineScope { cfg.intlProbeUrls.map { async { Net.reachable(it) } }.awaitAll().any { it } }
        if (intl) { _state.value = NetState.NORMAL; return@withLock }
        val domestic = coroutineScope { cfg.domesticProbeUrls.map { async { Net.reachable(it) } }.awaitAll().any { it } }
        _state.value = if (domestic) NetState.NATIONAL_ONLY else NetState.OFFLINE
    }
}

/** Toman amounts for display. */
fun formatToman(v: Double, persian: Boolean): String =
    if (v >= 1000) PersianText.group(Math.round(v), persian) else PersianText.groupDecimal(v, persian, 2)
