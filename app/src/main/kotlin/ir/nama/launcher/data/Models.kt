package ir.nama.launcher.data

import ir.nama.core.AppCategory
import ir.nama.core.NewsCategory
import ir.nama.core.NewsItem
import ir.nama.core.NewsSourceDef
import ir.nama.core.SortRule
import ir.nama.core.Space
import ir.nama.core.StyleId
import ir.nama.core.UssdCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class IconShape(val fa: String, val en: String) {
    CIRCLE("دایره", "Circle"),
    SQUIRCLE("گرد نرم", "Squircle"),
    ROUNDED("مربع گرد", "Rounded square"),
    ARCH("طاق ایرانی", "Persian arch"),
    TEARDROP("قطره", "Teardrop"),
    ORIGINAL("اصلی برنامه", "Original")
}

@Serializable
enum class GestureAction(val fa: String, val en: String) {
    NONE("هیچ", "Nothing"),
    DRAWER("کشوی برنامه‌ها", "App drawer"),
    SEARCH("جستجو", "Search"),
    NOTIFICATIONS("اعلان‌ها", "Notifications"),
    LOCK_SCREEN("قفل صفحه", "Lock screen"),
    SPACE_SWITCHER("تغییر فضا", "Switch space"),
    SETTINGS("تنظیمات نما", "Nama settings"),
    EDIT_HOME("ویرایش صفحه", "Edit home")
}

@Serializable
enum class SearchEngine(val fa: String, val url: String) {
    GOOGLE("گوگل", "https://www.google.com/search?q="),
    BING("بینگ", "https://www.bing.com/search?q="),
    DUCKDUCKGO("داک‌داک‌گو", "https://duckduckgo.com/?q="),
    YANDEX("یاندکس", "https://yandex.com/search/?text=")
}

@Serializable
data class Settings(
    val onboarded: Boolean = false,
    val onboardedAt: Long = 0L,
    val language: String = "fa",
    val style: StyleId = StyleId.PERSIAN,
    val persianDigits: Boolean = true,
    val iconShape: IconShape? = null,
    val iconSizeDp: Int = 52,
    val columns: Int = 4,
    val showLabels: Boolean = true,
    val showNotificationDots: Boolean = true,
    val oneHandMode: Boolean = false,
    val reduceMotion: Boolean = false,
    /** Show the phone's own wallpaper behind the style (otherwise the style paints its own background). */
    val useSystemWallpaper: Boolean = false,
    val interests: Set<String> = emptySet(),
    val persona: String? = null,
    val cityId: String = "tehran",
    val hijriOffset: Int = -1,
    val plateLastDigit: Int? = null,
    val doubleTapAction: GestureAction = GestureAction.NONE,
    val swipeDownAction: GestureAction = GestureAction.NOTIFICATIONS,
    val swipeUpAction: GestureAction = GestureAction.DRAWER,
    val iconPack: String? = null,
    val seasonalThemes: Boolean = true,
    val nationalNetMode: Boolean = true,
    val newsPageEnabled: Boolean = true,
    /** null = the defaults from the remote config. */
    val newsSourceIds: Set<String>? = null,
    val customNewsSources: List<NewsSourceDef> = emptyList(),
    val newsCategories: Set<NewsCategory> = emptySet(),
    val newsMutedWords: List<String> = emptyList(),
    val newsCalmAlways: Boolean = false,
    val newsCalmStartMinute: Int = 22 * 60,
    val newsCalmEndMinute: Int = 8 * 60,
    val newsCalmAtNight: Boolean = false,
    val breakingNotifications: Boolean = false,
    val morningCard: Boolean = true,
    val lastMorningCardDay: Long = -1,
    val chargingClock: Boolean = false,
    val footballTeam: String = "",
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val supabaseUrl: String = "",
    val supabaseKey: String = "",
    val supabaseEmail: String = "",
    /** Manually chosen space id, SpaceEngine.BASE_ID for "no space", or null for automatic. */
    val manualSpaceId: String? = null,
    val focusUntil: Long = 0L,
    val focusApps: Set<String> = emptySet(),
    val autoInboxNewApps: Boolean = true,
    val dailyChecks: Boolean = true,
    val manualUsdRate: Long = 0L,
    val manualGoldRate: Long = 0L,
    val showSpaceBanner: Boolean = true,
    val newsLastSeen: Long = 0L
)

@Serializable
sealed class HomeItem {
    abstract val id: String

    @Serializable @SerialName("app")
    data class App(override val id: String, val key: String) : HomeItem()

    /** A folder of apps. When [smartCategory] is set the folder fills itself with that category. */
    @Serializable @SerialName("folder")
    data class Folder(
        override val id: String,
        val name: String,
        val keys: List<String> = emptyList(),
        val smartCategory: AppCategory? = null
    ) : HomeItem()
}

@Serializable
enum class WidgetSize { SMALL, WIDE }

@Serializable
enum class WidgetType(val fa: String, val en: String, val group: String, val defaultSize: WidgetSize = WidgetSize.WIDE) {
    CLOCK("ساعت", "Clock", "time"),
    CLOCK_WORDS("ساعت نوشتاری", "Clock in words", "time"),
    CALENDAR("تقویم جلالی", "Jalali calendar", "time"),
    TODAY("امروزم", "My day", "time"),
    COUNTDOWN("شمارش معکوس", "Countdown", "time", WidgetSize.SMALL),
    PRAYER("اوقات شرعی", "Prayer times", "time"),
    BIRTHDAYS("تولدهای نزدیک", "Birthdays", "time"),
    PRICES("قیمت ارز و طلا", "Prices", "money"),
    BILLS("قسط و قبض", "Bills", "money"),
    BANK_CARDS("کارت‌های بانکی", "Bank cards", "money"),
    EXPENSES("دخل‌وخرج", "Expenses", "money", WidgetSize.SMALL),
    USSD("کدهای سیم‌کارت", "SIM codes", "phone"),
    DATA_USAGE("مصرف اینترنت", "Data usage", "phone", WidgetSize.SMALL),
    BATTERY("باتری", "Battery", "phone", WidgetSize.SMALL),
    TOGGLES("میانبرهای سریع", "Quick toggles", "phone"),
    CONTACTS("مخاطب‌های محبوب", "Favorite contacts", "phone"),
    MUSIC("پخش موسیقی", "Music", "phone"),
    WEATHER("آب‌وهوا و آلودگی", "Weather & air", "city", WidgetSize.SMALL),
    BLACKOUT("خاموشی برق", "Power cuts", "city"),
    ODD_EVEN("طرح زوج و فرد", "Odd/even plates", "city", WidgetSize.SMALL),
    FOOTBALL("تیم محبوب", "My team", "city"),
    HAFEZ("فال حافظ", "Hafez", "culture"),
    POEM("شعر روز", "Poem of the day", "culture"),
    QUOTE("جمله روز", "Quote of the day", "culture", WidgetSize.SMALL),
    VERSE("آیه روز", "Verse of the day", "culture"),
    TODO("کارهای روز", "To-do", "productivity"),
    NOTES("یادداشت سریع", "Quick note", "productivity"),
    SHOPPING("لیست خرید", "Shopping list", "productivity"),
    HABITS("عادت‌ها", "Habits", "productivity"),
    POMODORO("پومودورو", "Pomodoro", "productivity", WidgetSize.SMALL),
    USAGE("آمار استفاده", "Screen time", "wellbeing", WidgetSize.SMALL),
    FOCUS("حالت تمرکز", "Focus", "wellbeing", WidgetSize.SMALL),
    SPACE("فضای فعلی", "Current space", "wellbeing", WidgetSize.SMALL),
    NOTIF_DIGEST("خلاصه اعلان‌ها", "Notification digest", "wellbeing"),
    NEWS_TICKER("نوار خبر", "News ticker", "news"),
    NEWS_DIGEST("خلاصه خبرها", "News digest", "news"),
    MORNING("کارت صبح‌بخیر", "Good morning card", "news"),
    SMART_STACK("پشته هوشمند", "Smart stack", "structure"),
    SYSTEM("ویجت برنامه", "App widget", "system")
}

@Serializable
data class WidgetInstance(
    val id: String,
    val type: WidgetType,
    val size: WidgetSize = type.defaultSize,
    val config: Map<String, String> = emptyMap(),
    /** For [WidgetType.SYSTEM]: the id allocated from the AppWidgetHost. */
    val appWidgetId: Int = -1,
    /** Visible only in these spaces (empty = always). Use "base" for "no space active". */
    val spaces: Set<String> = emptySet(),
    val heightDp: Int = 0
)

@Serializable
data class HomePage(
    val id: String,
    val widgets: List<WidgetInstance> = emptyList(),
    val items: List<HomeItem> = emptyList()
)

@Serializable
data class HomeLayout(
    val pages: List<HomePage> = listOf(HomePage("p1")),
    val dock: List<String> = emptyList()
)

@Serializable
data class AppPref(
    val hidden: Boolean = false,
    val locked: Boolean = false,
    val friction: Int = 0,
    val dailyLimitMinutes: Int = 0,
    val label: String? = null,
    val aliases: List<String> = emptyList(),
    val category: AppCategory? = null,
    val archived: Boolean = false,
    /** Seen in the "new apps" inbox. */
    val reviewed: Boolean = true
)

@Serializable
data class AppStats(
    val launches: Int = 0,
    val lastLaunch: Long = 0L,
    /** Launch count per hour of day (24 entries). */
    val hours: List<Int> = List(24) { 0 }
)

@Serializable
data class Todo(val id: String, val text: String, val done: Boolean = false, val createdAt: Long = 0L, val dueEpochDay: Long? = null)

@Serializable
data class Habit(val id: String, val name: String, val doneDays: Set<Long> = emptySet())

@Serializable
data class Countdown(val id: String, val title: String, val epochDay: Long, val yearly: Boolean = false)

@Serializable
data class Bill(
    val id: String,
    val title: String,
    val amountToman: Long = 0L,
    val dueEpochDay: Long,
    val monthly: Boolean = true,
    val paidEpochDay: Long? = null
)

@Serializable
data class Expense(val id: String, val amountToman: Long, val note: String = "", val at: Long, val income: Boolean = false)

/** A planned power cut. Either repeating on week days or on one date. */
@Serializable
data class BlackoutSlot(
    val id: String,
    val startMinute: Int,
    val endMinute: Int,
    val days: Set<Int> = emptySet(),
    val epochDay: Long? = null,
    val note: String = ""
)

@Serializable
data class DataPackage(
    val title: String = "",
    val sizeMb: Long = 0L,
    val startEpochDay: Long,
    val endEpochDay: Long
)

@Serializable
data class FavContact(val name: String, val phone: String, val lookupKey: String = "")

@Serializable
data class Pomodoro(
    val running: Boolean = false,
    val onBreak: Boolean = false,
    val endsAt: Long = 0L,
    val workMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val doneToday: Int = 0,
    val day: Long = 0L
)

@Serializable
data class Personal(
    val todos: List<Todo> = emptyList(),
    val note: String = "",
    val shopping: List<Todo> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val countdowns: List<Countdown> = emptyList(),
    val bills: List<Bill> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val blackouts: List<BlackoutSlot> = emptyList(),
    val dataPackage: DataPackage? = null,
    val favoriteContacts: List<FavContact> = emptyList(),
    val customUssd: List<UssdCode> = emptyList(),
    val readLater: List<NewsItem> = emptyList(),
    val pomodoro: Pomodoro = Pomodoro(),
    val notifiedKeys: List<String> = emptyList()
)

@Serializable
data class BankCard(
    val id: String,
    val number: String,
    val owner: String = "",
    val sheba: String = "",
    val note: String = ""
)

@Serializable
data class LayoutSnapshot(val at: Long, val label: String, val layout: HomeLayout)

@Serializable
data class Backup(
    val format: Int = 1,
    val createdAt: Long,
    val settings: Settings,
    val layout: HomeLayout,
    val spaces: List<Space>,
    val appPrefs: Map<String, AppPref>,
    val rules: List<SortRule>,
    val personal: Personal
)

/** Interests chosen during setup. */
object Interests {
    val ALL = listOf(
        "poetry" to ("شعر و ادبیات" to "Poetry"),
        "economy" to ("اقتصاد و بازار" to "Economy"),
        "crypto" to ("ارز دیجیتال" to "Crypto"),
        "tech" to ("تکنولوژی" to "Technology"),
        "sports" to ("ورزش" to "Sports"),
        "football" to ("فوتبال" to "Football"),
        "science" to ("علم" to "Science"),
        "religion" to ("مذهبی" to "Religious"),
        "health" to ("سلامت و تناسب" to "Health"),
        "movies" to ("سینما و سریال" to "Movies"),
        "music" to ("موسیقی" to "Music"),
        "books" to ("کتاب" to "Books"),
        "news" to ("اخبار روز" to "Daily news"),
        "cooking" to ("آشپزی" to "Cooking"),
        "travel" to ("سفر" to "Travel"),
        "games" to ("بازی" to "Games")
    )
}

object Personas {
    data class Persona(val id: String, val fa: String, val en: String, val hintFa: String, val hintEn: String)

    val ALL = listOf(
        Persona("student", "دانشجو / دانش‌آموز", "Student", "برنامه کلاس، شمارش معکوس امتحان، پومودورو", "Classes, exams, pomodoro"),
        Persona("employee", "کارمند", "Office worker", "فضای شرکت، تقویم، تمرکز", "Work space, calendar, focus"),
        Persona("developer", "برنامه‌نویس / فریلنسر", "Developer / freelancer", "اخبار فناوری، تمرکز عمیق", "Tech news, deep focus"),
        Persona("trader", "معامله‌گر", "Trader", "ارز، رمزارز، اخبار اقتصادی", "Currencies, crypto, markets"),
        Persona("driver", "راننده", "Driver", "نقشه، طرح ترافیک، فضای ماشین", "Maps, odd/even, car space"),
        Persona("business", "کسب‌وکار آزاد", "Business owner", "دخل‌وخرج، چک و قسط، مشتری‌ها", "Expenses, bills, customers"),
        Persona("homemaker", "خانه‌دار", "Homemaker", "خرید، آشپزی، خانواده", "Shopping, cooking, family"),
        Persona("retired", "بازنشسته", "Retired", "آیکون بزرگ، ساده و خوانا", "Big icons, simple")
    )
}
