package ir.nama.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AppCategory(val fa: String, val en: String) {
    MESSAGING("پیام‌رسان و تماس", "Messaging"),
    SOCIAL("شبکه‌های اجتماعی", "Social"),
    FINANCE("بانک و مالی", "Finance"),
    SHOPPING("خرید", "Shopping"),
    FOOD("غذا", "Food"),
    TRANSPORT("نقشه و حمل‌ونقل", "Maps & Transport"),
    TRAVEL("سفر", "Travel"),
    MEDIA("فیلم و موسیقی", "Media"),
    READING("خبر و مطالعه", "News & Reading"),
    GAMES("بازی", "Games"),
    PHOTO("عکس و دوربین", "Photo"),
    PRODUCTIVITY("کار و بهره‌وری", "Productivity"),
    EDUCATION("آموزش", "Education"),
    HEALTH("سلامت و ورزش", "Health"),
    RELIGION("مذهبی", "Religion"),
    INTERNET("مرورگر و اینترنت", "Internet"),
    OPERATOR("اپراتور و سیم‌کارت", "Carrier"),
    GOVERNMENT("خدمات و دولت", "Services"),
    TOOLS("ابزارها", "Tools"),
    SYSTEM("سیستم", "System"),
    OTHER("سایر", "Other");
}

/** Facts about an installed app used for categorization and rules. */
data class AppFacts(
    val packageName: String,
    val label: String,
    /** android ApplicationInfo.category, or -1 if undefined. */
    val systemCategory: Int = -1,
    val isSystem: Boolean = false,
    val installedAtMillis: Long = 0L,
    val lastUsedMillis: Long = 0L,
    val installer: String? = null
)

object AppClassifier {
    private val EXACT: Map<String, AppCategory> = mapOf(
        "org.telegram.messenger" to AppCategory.MESSAGING,
        "org.telegram.plus" to AppCategory.MESSAGING,
        "org.thunderdog.challegram" to AppCategory.MESSAGING,
        "ir.eitaa.messenger" to AppCategory.MESSAGING,
        "ir.nasim" to AppCategory.MESSAGING,
        "mobi.mmdt.ottplus" to AppCategory.MESSAGING,
        "ir.resaneh1.iptv" to AppCategory.MESSAGING,
        "com.whatsapp" to AppCategory.MESSAGING,
        "com.whatsapp.w4b" to AppCategory.MESSAGING,
        "com.viber.voip" to AppCategory.MESSAGING,
        "com.skype.raider" to AppCategory.MESSAGING,
        "com.discord" to AppCategory.MESSAGING,
        "com.instagram.android" to AppCategory.SOCIAL,
        "com.twitter.android" to AppCategory.SOCIAL,
        "com.zhiliaoapp.musically" to AppCategory.SOCIAL,
        "com.facebook.katana" to AppCategory.SOCIAL,
        "com.linkedin.android" to AppCategory.SOCIAL,
        "com.reddit.frontpage" to AppCategory.SOCIAL,
        "com.pinterest" to AppCategory.SOCIAL,
        "com.snapchat.android" to AppCategory.SOCIAL,
        "com.google.android.youtube" to AppCategory.MEDIA,
        "com.aparat" to AppCategory.MEDIA,
        "com.spotify.music" to AppCategory.MEDIA,
        "com.netflix.mediaclient" to AppCategory.MEDIA,
        "org.videolan.vlc" to AppCategory.MEDIA,
        "com.mxtech.videoplayer.ad" to AppCategory.MEDIA,
        "com.radiojavan.androidradio" to AppCategory.MEDIA,
        "ir.fidibo" to AppCategory.READING,
        "com.taaghche.android" to AppCategory.READING,
        "com.google.android.apps.maps" to AppCategory.TRANSPORT,
        "com.waze" to AppCategory.TRANSPORT,
        "ir.balad" to AppCategory.TRANSPORT,
        "org.rajman.neshan.traffic.tehran" to AppCategory.TRANSPORT,
        "cab.snapp.passenger" to AppCategory.TRANSPORT,
        "cab.snapp.driver" to AppCategory.TRANSPORT,
        "taxi.tap30.passenger" to AppCategory.TRANSPORT,
        "taxi.tap30.driver" to AppCategory.TRANSPORT,
        "com.digikala" to AppCategory.SHOPPING,
        "ir.divar" to AppCategory.SHOPPING,
        "com.sheypoor.mobile" to AppCategory.SHOPPING,
        "ir.torob" to AppCategory.SHOPPING,
        "com.zoodfood.android" to AppCategory.FOOD,
        "ir.alibaba" to AppCategory.TRAVEL,
        "com.farsitel.bazaar" to AppCategory.TOOLS,
        "ir.mservices.market" to AppCategory.TOOLS,
        "com.android.vending" to AppCategory.TOOLS,
        "market.nobitex" to AppCategory.FINANCE,
        "com.wallex.app" to AppCategory.FINANCE,
        "ir.asanpardakht.android" to AppCategory.FINANCE,
        "ir.mci.ecareapp" to AppCategory.OPERATOR,
        "com.android.chrome" to AppCategory.INTERNET,
        "org.mozilla.firefox" to AppCategory.INTERNET,
        "com.opera.browser" to AppCategory.INTERNET,
        "com.microsoft.emmx" to AppCategory.INTERNET,
        "com.brave.browser" to AppCategory.INTERNET,
        "com.android.settings" to AppCategory.SYSTEM,
        "com.google.android.gm" to AppCategory.PRODUCTIVITY,
        "com.google.android.calendar" to AppCategory.PRODUCTIVITY,
        "com.google.android.keep" to AppCategory.PRODUCTIVITY,
        "com.google.android.apps.docs" to AppCategory.PRODUCTIVITY,
        "com.microsoft.teams" to AppCategory.PRODUCTIVITY,
        "com.Slack" to AppCategory.PRODUCTIVITY,
        "us.zoom.videomeetings" to AppCategory.PRODUCTIVITY,
        "com.google.android.apps.translate" to AppCategory.EDUCATION,
        "com.duolingo" to AppCategory.EDUCATION
    )

    private val PACKAGE_KEYWORDS: List<Pair<List<String>, AppCategory>> = listOf(
        listOf("telegram", "messenger", "eitaa", "whatsapp", "chat", "dialer", "contacts", "mms", "messaging", "incallui", "sms") to AppCategory.MESSAGING,
        listOf("instagram", "twitter", "facebook", "tiktok", "social") to AppCategory.SOCIAL,
        listOf(
            "bank", "mellat", "melli", "saderat", "tejarat", "sepah", "pasargad", "saman", "parsian", "ayandeh", "keshavarzi",
            "maskan", "refah", "sinabank", "resalat", "blubank", "wallet", "pay", "sadad", "nobitex", "wallex", "ramzinex",
            "bitpin", "exir", "crypto", "exchange", "mobilebank", "sekeh", "sekke", "kipaa", "trader", "bours", "tadbir"
        ) to AppCategory.FINANCE,
        listOf("shop", "digikala", "divar", "basalam", "torob") to AppCategory.SHOPPING,
        listOf("food", "restaurant", "zoodfood", "snappfood", "chilivery") to AppCategory.FOOD,
        listOf("map", "navigation", "neshan", "balad", "taxi", "snapp", "tapsi", "tap30", "metro") to AppCategory.TRANSPORT,
        listOf("travel", "trip", "flight", "hotel", "alibaba", "jabama", "jajiga", "booking") to AppCategory.TRAVEL,
        listOf("music", "video", "player", "youtube", "aparat", "filimo", "namava", "radio", "podcast", "movie") to AppCategory.MEDIA,
        listOf("news", "book", "reader", "fidibo", "taaghche", "magazine", "rss") to AppCategory.READING,
        listOf("camera", "gallery", "photo", "image", "editor", "snapseed", "picsart") to AppCategory.PHOTO,
        listOf("quran", "mafatih", "azan", "prayer", "namaz", "doa", "shia") to AppCategory.RELIGION,
        listOf("browser", "vpn", "proxy", "v2ray", "hiddify", "nekobox", "openvpn", "wireguard", "psiphon") to AppCategory.INTERNET,
        listOf("irancell", "myirancell", "mci", "hamrahaval", "rightel", "shatel", "mobinnet") to AppCategory.OPERATOR,
        listOf("gov", "dolat", "mygov", "tax", "adliran", "sakha", "sana", "police", "khedmat") to AppCategory.GOVERNMENT,
        listOf("learn", "education", "school", "dictionary", "translate", "course", "quiz") to AppCategory.EDUCATION,
        listOf("health", "fit", "sport", "workout", "doctor", "drug", "step", "sleep") to AppCategory.HEALTH,
        listOf("office", "docs", "sheet", "mail", "calendar", "note", "todo", "task", "drive", "slack", "zoom", "meet") to AppCategory.PRODUCTIVITY,
        listOf("game", "games", "play.games", "puzzle", "racing", "chess") to AppCategory.GAMES,
        listOf("calculator", "clock", "deskclock", "recorder", "compass", "weather", "filemanager", "files", "scanner", "flashlight", "backup", "cleaner", "security") to AppCategory.TOOLS,
        listOf("settings", "launcher", "systemui", "packageinstaller", "miui.home", "android.vending") to AppCategory.SYSTEM
    )

    private val LABEL_KEYWORDS: List<Pair<List<String>, AppCategory>> = listOf(
        listOf("بانک", "همراه بانک", "پرداخت", "کیف پول", "صرافی", "بورس", "bank", "pay", "wallet") to AppCategory.FINANCE,
        listOf("پیام", "تماس", "مخاطبین", "تلفن", "messages", "phone", "contacts") to AppCategory.MESSAGING,
        listOf("بازی", "game") to AppCategory.GAMES,
        listOf("خبر", "اخبار", "کتاب", "news", "book") to AppCategory.READING,
        listOf("نقشه", "مسیریاب", "تاکسی", "map", "taxi") to AppCategory.TRANSPORT,
        listOf("فروشگاه", "خرید", "shop", "store") to AppCategory.SHOPPING,
        listOf("غذا", "رستوران", "food") to AppCategory.FOOD,
        listOf("قرآن", "مفاتیح", "اذان", "نماز", "شرعی", "quran", "prayer") to AppCategory.RELIGION,
        listOf("دوربین", "گالری", "عکس", "camera", "gallery", "photos") to AppCategory.PHOTO,
        listOf("موزیک", "موسیقی", "فیلم", "ویدیو", "music", "video") to AppCategory.MEDIA,
        listOf("آموزش", "دیکشنری", "مترجم", "learn", "dictionary") to AppCategory.EDUCATION,
        listOf("ماشین حساب", "ساعت", "ضبط", "فایل", "calculator", "clock", "recorder", "files", "weather") to AppCategory.TOOLS,
        listOf("تنظیمات", "settings") to AppCategory.SYSTEM
    )

    /** Packages that work without international internet. */
    private val DOMESTIC_PREFIXES = listOf(
        "ir.", "com.digikala", "cab.snapp", "taxi.tap30", "com.zoodfood", "market.nobitex", "com.wallex",
        "com.farsitel.", "mobi.mmdt", "org.rajman.neshan", "com.aparat", "com.sheypoor", "com.taaghche"
    )

    /** Packages that need international internet. */
    private val FOREIGN_PREFIXES = listOf(
        "com.instagram", "com.whatsapp", "org.telegram", "org.thunderdog", "com.twitter", "com.facebook",
        "com.google.android.youtube", "com.spotify", "com.netflix", "com.zhiliaoapp", "com.snapchat",
        "com.discord", "com.reddit", "com.pinterest", "com.linkedin", "com.android.vending", "com.google.android.gm",
        "com.Slack", "us.zoom", "com.skype", "com.viber", "com.duolingo"
    )

    fun isDomestic(pkg: String, extra: Collection<String> = emptyList()): Boolean =
        DOMESTIC_PREFIXES.any { pkg.startsWith(it) } || extra.any { pkg.startsWith(it) }

    fun isForeign(pkg: String, extra: Collection<String> = emptyList()): Boolean =
        FOREIGN_PREFIXES.any { pkg.startsWith(it) } || extra.any { pkg.startsWith(it) }

    fun classify(facts: AppFacts, overrides: Map<String, AppCategory> = emptyMap()): AppCategory {
        overrides[facts.packageName]?.let { return it }
        EXACT[facts.packageName]?.let { return it }
        val pkg = facts.packageName.lowercase()
        if (facts.systemCategory == 0) return AppCategory.GAMES
        for ((keys, cat) in PACKAGE_KEYWORDS) if (keys.any { pkg.contains(it) }) return cat
        val label = PersianText.normalize(facts.label)
        for ((keys, cat) in LABEL_KEYWORDS) if (keys.any { label.contains(PersianText.normalize(it)) }) return cat
        return when (facts.systemCategory) {
            1, 2 -> AppCategory.MEDIA
            3 -> AppCategory.PHOTO
            4 -> AppCategory.SOCIAL
            5 -> AppCategory.READING
            6 -> AppCategory.TRANSPORT
            7 -> AppCategory.PRODUCTIVITY
            8 -> AppCategory.TOOLS
            else -> if (facts.isSystem) AppCategory.SYSTEM else AppCategory.OTHER
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Rules engine
// ----------------------------------------------------------------------------------------------

@Serializable
sealed class RuleCondition {
    @Serializable @SerialName("pkg") data class PackageContains(val text: String) : RuleCondition()
    @Serializable @SerialName("label") data class LabelContains(val text: String) : RuleCondition()
    @Serializable @SerialName("category") data class CategoryIs(val category: AppCategory) : RuleCondition()
    @Serializable @SerialName("installed_within") data class InstalledWithinDays(val days: Int) : RuleCondition()
    @Serializable @SerialName("unused_for") data class UnusedForDays(val days: Int) : RuleCondition()
    @Serializable @SerialName("installer") data class InstallerContains(val text: String) : RuleCondition()
    @Serializable @SerialName("system") data class IsSystem(val value: Boolean) : RuleCondition()
}

@Serializable
sealed class RuleAction {
    @Serializable @SerialName("set_category") data class SetCategory(val category: AppCategory) : RuleAction()
    @Serializable @SerialName("folder") data class PutInFolder(val name: String) : RuleAction()
    @Serializable @SerialName("archive") data object Archive : RuleAction()
    @Serializable @SerialName("hide") data object Hide : RuleAction()
    @Serializable @SerialName("friction") data class Friction(val flags: Int) : RuleAction()
}

@Serializable
data class SortRule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val matchAll: Boolean = true,
    val conditions: List<RuleCondition> = emptyList(),
    val action: RuleAction
)

object RuleEngine {
    private const val DAY = 24L * 60 * 60 * 1000

    fun matches(rule: SortRule, facts: AppFacts, category: AppCategory, now: Long): Boolean {
        if (!rule.enabled || rule.conditions.isEmpty()) return false
        val results = rule.conditions.map { c -> matches(c, facts, category, now) }
        return if (rule.matchAll) results.all { it } else results.any { it }
    }

    private fun matches(c: RuleCondition, f: AppFacts, category: AppCategory, now: Long): Boolean = when (c) {
        is RuleCondition.PackageContains -> c.text.isNotBlank() && f.packageName.contains(c.text.trim(), ignoreCase = true)
        is RuleCondition.LabelContains -> c.text.isNotBlank() && PersianText.normalize(f.label).contains(PersianText.normalize(c.text))
        is RuleCondition.CategoryIs -> category == c.category
        is RuleCondition.InstalledWithinDays -> f.installedAtMillis > 0 && now - f.installedAtMillis <= c.days * DAY
        is RuleCondition.UnusedForDays -> {
            val ref = if (f.lastUsedMillis > 0) f.lastUsedMillis else f.installedAtMillis
            ref > 0 && now - ref >= c.days * DAY
        }
        is RuleCondition.InstallerContains -> f.installer?.contains(c.text, ignoreCase = true) == true
        is RuleCondition.IsSystem -> f.isSystem == c.value
    }

    /** First matching rule per action type wins (rules are evaluated in list order). */
    fun evaluate(rules: List<SortRule>, facts: AppFacts, category: AppCategory, now: Long): List<RuleAction> {
        val out = mutableListOf<RuleAction>()
        val seen = mutableSetOf<String>()
        for (r in rules) {
            if (!matches(r, facts, category, now)) continue
            val type = r.action::class.simpleName ?: continue
            if (seen.add(type)) out += r.action
        }
        return out
    }
}

/** Friction flags for apps the user wants to use less. */
object Friction {
    const val HIDE_FROM_SEARCH = 1
    const val SHUFFLE = 2
    const val DELAY = 4
    const val TYPE_SENTENCE = 8
    const val GRAYSCALE_AFTER_LIMIT = 16
    const val ASK_INTENTION = 32

    fun has(flags: Int, flag: Int) = flags and flag != 0
}
