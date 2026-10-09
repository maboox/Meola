package ir.nama.core

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate

@Serializable
data class City(val id: String, val fa: String, val en: String, val lat: Double, val lng: Double)

object Cities {
    val ALL = listOf(
        City("tehran", "تهران", "Tehran", 35.6892, 51.3890),
        City("mashhad", "مشهد", "Mashhad", 36.2605, 59.6168),
        City("isfahan", "اصفهان", "Isfahan", 32.6546, 51.6680),
        City("karaj", "کرج", "Karaj", 35.8400, 50.9391),
        City("shiraz", "شیراز", "Shiraz", 29.5918, 52.5837),
        City("tabriz", "تبریز", "Tabriz", 38.0962, 46.2738),
        City("qom", "قم", "Qom", 34.6399, 50.8759),
        City("ahvaz", "اهواز", "Ahvaz", 31.3183, 48.6706),
        City("kermanshah", "کرمانشاه", "Kermanshah", 34.3142, 47.0650),
        City("urmia", "ارومیه", "Urmia", 37.5527, 45.0761),
        City("rasht", "رشت", "Rasht", 37.2808, 49.5832),
        City("zahedan", "زاهدان", "Zahedan", 29.4963, 60.8629),
        City("kerman", "کرمان", "Kerman", 30.2839, 57.0834),
        City("hamadan", "همدان", "Hamadan", 34.7983, 48.5148),
        City("yazd", "یزد", "Yazd", 31.8974, 54.3569),
        City("ardabil", "اردبیل", "Ardabil", 38.2498, 48.2933),
        City("bandarabbas", "بندرعباس", "Bandar Abbas", 27.1832, 56.2666),
        City("arak", "اراک", "Arak", 34.0954, 49.7013),
        City("zanjan", "زنجان", "Zanjan", 36.6765, 48.4963),
        City("sanandaj", "سنندج", "Sanandaj", 35.3219, 46.9862),
        City("qazvin", "قزوین", "Qazvin", 36.2797, 50.0049),
        City("khorramabad", "خرم‌آباد", "Khorramabad", 33.4878, 48.3558),
        City("gorgan", "گرگان", "Gorgan", 36.8456, 54.4393),
        City("sari", "ساری", "Sari", 36.5633, 53.0601),
        City("bushehr", "بوشهر", "Bushehr", 28.9234, 50.8203),
        City("birjand", "بیرجند", "Birjand", 32.8649, 59.2262),
        City("bojnurd", "بجنورد", "Bojnurd", 37.4747, 57.3290),
        City("ilam", "ایلام", "Ilam", 33.6374, 46.4227),
        City("shahrekord", "شهرکرد", "Shahrekord", 32.3256, 50.8644),
        City("yasuj", "یاسوج", "Yasuj", 30.6682, 51.5880),
        City("semnan", "سمنان", "Semnan", 35.5729, 53.3971),
        City("kish", "کیش", "Kish", 26.5325, 53.9800)
    )

    fun byId(id: String): City = ALL.firstOrNull { it.id == id } ?: ALL.first()
}

object BankCards {
    private val BINS = mapOf(
        "603799" to "ملی", "589210" to "سپه", "627353" to "تجارت", "585983" to "تجارت", "610433" to "ملت",
        "991975" to "ملت", "603769" to "صادرات", "627412" to "اقتصاد نوین", "622106" to "پارسیان",
        "639194" to "پارسیان", "627884" to "پارسیان", "502229" to "پاسارگاد", "639347" to "پاسارگاد",
        "639607" to "سرمایه", "621986" to "سامان", "627488" to "کارآفرین", "502910" to "کارآفرین",
        "639346" to "سینا", "589463" to "رفاه", "603770" to "کشاورزی", "639217" to "کشاورزی",
        "628023" to "مسکن", "627648" to "توسعه صادرات", "207177" to "توسعه صادرات", "627961" to "صنعت و معدن",
        "502908" to "توسعه تعاون", "627760" to "پست بانک", "636214" to "آینده", "502806" to "شهر",
        "504706" to "شهر", "504172" to "رسالت", "505785" to "ایران زمین", "606373" to "قرض‌الحسنه مهر ایران",
        "505416" to "گردشگری", "636949" to "حکمت ایرانیان", "639370" to "مهر اقتصاد", "585947" to "خاورمیانه",
        "507677" to "نور", "628157" to "توسعه", "505801" to "کوثر", "606256" to "ملل", "636795" to "مرکزی"
    )

    fun digitsOnly(s: String) = PersianText.toEnDigits(s).filter { it.isDigit() }

    fun bankName(card: String): String? = BINS[digitsOnly(card).take(6)]

    fun isValidLuhn(card: String): Boolean {
        val d = digitsOnly(card)
        if (d.length != 16) return false
        var sum = 0
        for ((i, c) in d.reversed().withIndex()) {
            var v = c - '0'
            if (i % 2 == 1) { v *= 2; if (v > 9) v -= 9 }
            sum += v
        }
        return sum % 10 == 0
    }

    fun format(card: String, persian: Boolean = true): String =
        PersianText.digits(digitsOnly(card).chunked(4).joinToString("-"), persian)

    fun mask(card: String, persian: Boolean = true): String {
        val d = digitsOnly(card)
        if (d.length < 8) return format(card, persian)
        val masked = d.take(6) + "*".repeat(d.length - 10) + d.takeLast(4)
        return PersianText.digits(masked.chunked(4).joinToString("-"), persian)
    }

    /** Normalizes a Sheba (IBAN) number: "IR" + 24 digits. */
    fun normalizeSheba(s: String): String {
        val d = digitsOnly(s)
        return if (d.isEmpty()) "" else "IR$d"
    }

    fun isValidSheba(s: String): Boolean {
        val n = normalizeSheba(s)
        if (n.length != 26) return false
        // IBAN mod-97 check: move "IR" + check digits to the end, letters to numbers (I=18, R=27).
        val rearranged = n.substring(4) + "1827" + n.substring(2, 4)
        var rem = 0
        for (c in rearranged) rem = (rem * 10 + (c - '0')) % 97
        return rem == 1
    }
}

@Serializable
data class UssdCode(val operator: String, val title: String, val code: String)

object Ussd {
    /** Conservative defaults; the remote config can add or correct codes. */
    val DEFAULTS = listOf(
        UssdCode("همراه اول", "موجودی و اعتبار", "*140*11#"),
        UssdCode("همراه اول", "خرید بسته اینترنت", "*100#"),
        UssdCode("ایرانسل", "موجودی و اعتبار", "*141*1#"),
        UssdCode("ایرانسل", "خرید بسته اینترنت", "*555#"),
        UssdCode("رایتل", "موجودی و اعتبار", "*140#")
    )
}

/** Tehran odd/even traffic scheme. Days are configurable from the remote config. */
@Serializable
data class OddEvenRule(
    /** Persian week indices (0 = Saturday) on which plates ending in an even digit may enter. */
    val evenDays: Set<Int> = setOf(0, 2, 4),
    val oddDays: Set<Int> = setOf(1, 3),
    val startMinute: Int = 6 * 60 + 30,
    val endMinute: Int = 19 * 60
) {
    enum class Status { ALLOWED, NOT_ALLOWED, FREE }

    fun status(date: LocalDate, plateLastDigit: Int, holiday: Boolean): Status {
        if (holiday || date.dayOfWeek == DayOfWeek.FRIDAY) return Status.FREE
        val w = Jalali.weekIndex(date)
        val even = plateLastDigit % 2 == 0
        return when {
            w in evenDays -> if (even) Status.ALLOWED else Status.NOT_ALLOWED
            w in oddDays -> if (!even) Status.ALLOWED else Status.NOT_ALLOWED
            else -> Status.FREE
        }
    }
}

@Serializable
data class Poem(val poet: String, val line1: String, val line2: String)

object Poems {
    val HAFEZ = listOf(
        Poem("حافظ", "بیا تا گل برافشانیم و می در ساغر اندازیم", "فلک را سقف بشکافیم و طرحی نو دراندازیم"),
        Poem("حافظ", "الا یا ایها الساقی ادر کأسا و ناولها", "که عشق آسان نمود اول ولی افتاد مشکل‌ها"),
        Poem("حافظ", "یوسف گم گشته بازآید به کنعان غم مخور", "کلبه احزان شود روزی گلستان غم مخور"),
        Poem("حافظ", "دوش دیدم که ملائک در میخانه زدند", "گل آدم بسرشتند و به پیمانه زدند"),
        Poem("حافظ", "صبا به لطف بگو آن غزال رعنا را", "که سر به کوه و بیابان تو داده‌ای ما را"),
        Poem("حافظ", "سال‌ها دل طلب جام جم از ما می‌کرد", "آن چه خود داشت ز بیگانه تمنا می‌کرد"),
        Poem("حافظ", "در ازل پرتو حسنت ز تجلی دم زد", "عشق پیدا شد و آتش به همه عالم زد"),
        Poem("حافظ", "مژده ای دل که مسیحا نفسی می‌آید", "که ز انفاس خوشش بوی کسی می‌آید"),
        Poem("حافظ", "دل می‌رود ز دستم صاحب‌دلان خدا را", "دردا که راز پنهان خواهد شد آشکارا"),
        Poem("حافظ", "روز وصل دوستداران یاد باد", "یاد باد آن روزگاران یاد باد"),
        Poem("حافظ", "ما ز یاران چشم یاری داشتیم", "خود غلط بود آن چه ما پنداشتیم"),
        Poem("حافظ", "شنیده‌ام سخنی خوش که پیر کنعان گفت", "فراق یار نه آن می‌کند که بتوان گفت"),
        Poem("حافظ", "هر آن که جانب اهل وفا نگه دارد", "خداش در همه حال از بلا نگه دارد"),
        Poem("حافظ", "رونق عهد شباب است دگر بستان را", "می‌رسد مژده گل بلبل خوش الحان را"),
        Poem("حافظ", "ساقی به نور باده برافروز جام ما", "مطرب بگو که کار جهان شد به کام ما"),
        Poem("حافظ", "هرگز نمیرد آن که دلش زنده شد به عشق", "ثبت است بر جریده عالم دوام ما"),
        Poem("حافظ", "زلف آشفته و خوی کرده و خندان لب و مست", "پیرهن چاک و غزل‌خوان و صراحی در دست"),
        Poem("حافظ", "اگر آن ترک شیرازی به دست آرد دل ما را", "به خال هندویش بخشم سمرقند و بخارا را")
    )

    val OTHERS = listOf(
        Poem("سعدی", "بنی آدم اعضای یکدیگرند", "که در آفرینش ز یک گوهرند"),
        Poem("سعدی", "چو عضوی به درد آورد روزگار", "دگر عضوها را نماند قرار"),
        Poem("سعدی", "به جهان خرم از آنم که جهان خرم از اوست", "عاشقم بر همه عالم که همه عالم از اوست"),
        Poem("سعدی", "من از آن روز که دربند توام آزادم", "پادشاهم که به دست تو اسیر افتادم"),
        Poem("مولوی", "بشنو این نی چون شکایت می‌کند", "از جدایی‌ها حکایت می‌کند"),
        Poem("مولوی", "هر کسی کو دور ماند از اصل خویش", "باز جوید روزگار وصل خویش"),
        Poem("مولوی", "مرده بدم زنده شدم گریه بدم خنده شدم", "دولت عشق آمد و من دولت پاینده شدم"),
        Poem("خیام", "این قافله عمر عجب می‌گذرد", "دریاب دمی که با طرب می‌گذرد"),
        Poem("خیام", "از دی که گذشت هیچ از او یاد مکن", "فردا که نیامده‌ست فریاد مکن"),
        Poem("خیام", "بر نامده و گذشته بنیاد مکن", "حالی خوش باش و عمر بر باد مکن"),
        Poem("فردوسی", "توانا بود هر که دانا بود", "ز دانش دل پیر برنا بود")
    )

    val QUOTES = listOf(
        "کار امروز را به فردا میفکن.",
        "قطره قطره جمع گردد، وانگهی دریا شود.",
        "هر که بامش بیش، برفش بیشتر.",
        "از تو حرکت، از خدا برکت.",
        "صبر تلخ است ولیکن بر شیرین دارد.",
        "دانش چراغ راه است.",
        "کم گوی و گزیده گوی چون دُر."
    )

    val VERSES = listOf(
        Poem("رعد، ۲۸", "أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ", "آگاه باشید که با یاد خدا دل‌ها آرام می‌گیرد."),
        Poem("شرح، ۶", "إِنَّ مَعَ الْعُسْرِ يُسْرًا", "به راستی که با سختی، آسانی است."),
        Poem("طلاق، ۳", "وَمَنْ يَتَوَكَّلْ عَلَى اللَّهِ فَهُوَ حَسْبُهُ", "و هر کس بر خدا توکل کند، خدا برایش کافی است.")
    )

    /** Deterministic pick for the day so the widget stays stable during the day. */
    fun <T> ofDay(list: List<T>, epochDay: Long, salt: Int = 0): T =
        list[Math.floorMod(epochDay * 31 + salt, list.size.toLong()).toInt()]
}

// ----------------------------------------------------------------------------------------------
// Remote configuration (fetched from the public GitHub repo, with a bundled fallback)
// ----------------------------------------------------------------------------------------------

@Serializable
enum class NewsCategory(val fa: String, val en: String) {
    GENERAL("عمومی", "General"),
    POLITICS("سیاسی", "Politics"),
    ECONOMY("اقتصادی", "Economy"),
    CRYPTO("ارز دیجیتال", "Crypto"),
    TECH("فناوری", "Tech"),
    SCIENCE("علمی", "Science"),
    SPORTS("ورزشی", "Sports"),
    CULTURE("فرهنگ و ادب", "Culture"),
    HEALTH("سلامت", "Health")
}

@Serializable
data class NewsSourceDef(
    val id: String,
    val name: String,
    val url: String,
    val category: NewsCategory = NewsCategory.GENERAL,
    /** "rss" (RSS/Atom) or "telegram" (public channel web preview t.me/s/<name>). */
    val type: String = "rss",
    val enabledByDefault: Boolean = false
)

@Serializable
data class PriceSourceDef(
    val symbol: String,
    val title: String,
    val url: String,
    /** Dot path into the JSON response, e.g. "stats.usdt-rls.latest". */
    val valuePath: String,
    val changePath: String? = null,
    /** Value is divided by this (e.g. 10 to turn rial into toman). */
    val divisor: Double = 1.0,
    val unit: String = "تومان"
)

@Serializable
data class RemoteConfig(
    val version: Int = 0,
    val newsSources: List<NewsSourceDef> = emptyList(),
    val priceSources: List<PriceSourceDef> = emptyList(),
    val ussd: List<UssdCode> = Ussd.DEFAULTS,
    val oddEven: OddEvenRule = OddEvenRule(),
    val extraHafez: List<Poem> = emptyList(),
    val extraPoems: List<Poem> = emptyList(),
    val categoryOverrides: Map<String, AppCategory> = emptyMap(),
    val domesticPackages: List<String> = emptyList(),
    val foreignPackages: List<String> = emptyList(),
    /** URLs used to check whether international and domestic internet are reachable. */
    val intlProbeUrls: List<String> = listOf("https://www.cloudflare.com/cdn-cgi/trace", "https://www.gstatic.com/generate_204"),
    val domesticProbeUrls: List<String> = listOf("https://www.aparat.com/", "https://www.digikala.com/"),
    val message: String? = null
)

object JsonPath {
    /** Minimal dot-path lookup on kotlinx JsonElement trees ("a.b.0.c"). */
    fun get(root: kotlinx.serialization.json.JsonElement, path: String): kotlinx.serialization.json.JsonElement? {
        var cur: kotlinx.serialization.json.JsonElement? = root
        for (part in path.split('.')) {
            cur = when (val c = cur) {
                is kotlinx.serialization.json.JsonObject -> c[part]
                is kotlinx.serialization.json.JsonArray -> part.toIntOrNull()?.let { c.getOrNull(it) }
                else -> null
            }
            if (cur == null) return null
        }
        return cur
    }

    fun number(root: kotlinx.serialization.json.JsonElement, path: String): Double? {
        val e = get(root, path) as? kotlinx.serialization.json.JsonPrimitive ?: return null
        return e.content.replace(",", "").toDoubleOrNull()
    }
}
