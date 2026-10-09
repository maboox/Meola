package ir.nama.launcher.logic

import android.content.Intent
import android.provider.MediaStore
import ir.nama.core.AppCategory
import ir.nama.core.AppFacts
import ir.nama.core.NewsCategory
import ir.nama.core.RuleAction
import ir.nama.core.RuleEngine
import ir.nama.core.Space
import ir.nama.core.StyleId
import ir.nama.core.Trigger
import ir.nama.launcher.Nama
import ir.nama.launcher.context.SpaceManager
import ir.nama.launcher.data.AppEntry
import ir.nama.launcher.data.HomeItem
import ir.nama.launcher.data.HomeLayout
import ir.nama.launcher.data.HomePage
import ir.nama.launcher.data.WidgetInstance
import ir.nama.launcher.data.WidgetSize
import ir.nama.launcher.data.WidgetType
import ir.nama.launcher.tr
import java.util.UUID

private fun id() = UUID.randomUUID().toString().take(10)

object SmartSort {
    data class Result(val layout: HomeLayout, val folders: Int, val apps: Int, val prefChanges: Map<String, (ir.nama.launcher.data.AppPref) -> ir.nama.launcher.data.AppPref>)

    /**
     * Rebuilds pages 2+ as folders by rule/category. Page 1 keeps its widgets and loose apps
     * (the user's favorites); the dock is untouched. Nothing is uninstalled or hidden unless a rule says so.
     */
    fun plan(current: HomeLayout, apps: List<AppEntry>, lastUsed: Map<String, Long>): Result {
        val now = System.currentTimeMillis()
        val rules = Nama.store.rules.value
        val prefChanges = mutableMapOf<String, (ir.nama.launcher.data.AppPref) -> ir.nama.launcher.data.AppPref>()
        val folderOf = mutableMapOf<String, String>()
        val usable = mutableListOf<AppEntry>()
        for (e in apps) {
            if (e.pref.hidden || e.pref.archived) continue
            val facts = AppFacts(e.packageName, e.label, -1, e.isSystem, e.installedAt, lastUsed[e.packageName] ?: 0L)
            var cat = e.category
            var drop = false
            for (a in RuleEngine.evaluate(rules, facts, cat, now)) {
                when (a) {
                    is RuleAction.SetCategory -> { cat = a.category; prefChanges[e.key] = { p -> p.copy(category = a.category) } }
                    is RuleAction.PutInFolder -> folderOf[e.key] = a.name
                    is RuleAction.Archive -> { prefChanges[e.key] = { p -> p.copy(archived = true) }; drop = true }
                    is RuleAction.Hide -> { prefChanges[e.key] = { p -> p.copy(hidden = true) }; drop = true }
                    is RuleAction.Friction -> prefChanges[e.key] = { p -> p.copy(friction = p.friction or a.flags) }
                }
            }
            if (drop) continue
            if (e.key !in folderOf) folderOf[e.key] = Nama.categoryName(cat)
            usable += e
        }
        val first = current.pages.firstOrNull() ?: HomePage(id())
        val favorites = first.items.filterIsInstance<HomeItem.App>().map { it.key }.toSet()
        val keep = favorites + current.dock
        val groups = usable.filter { it.key !in keep }.groupBy { folderOf[it.key]!! }
        val stats = Nama.store.appStats.value
        val folders = mutableListOf<HomeItem>()
        val singles = mutableListOf<String>()
        groups.entries.sortedByDescending { it.value.size }.forEach { (name, list) ->
            val keys = list.sortedByDescending { stats[it.key]?.launches ?: 0 }.map { it.key }
            if (keys.size >= 2) folders += HomeItem.Folder(id(), name, keys) else singles += keys
        }
        if (singles.size >= 2) folders += HomeItem.Folder(id(), tr("سایر", "Other"), singles)
        else singles.forEach { folders += HomeItem.App(id(), it) }

        val newPages = mutableListOf(first.copy(items = first.items.filter { it is HomeItem.App }))
        folders.chunked(20).forEachIndexed { i, chunk ->
            val old = current.pages.getOrNull(i + 1)
            newPages += HomePage(old?.id ?: id(), widgets = old?.widgets ?: emptyList(), items = chunk)
        }
        // Keep widgets that lived on pages beyond the new page count.
        val extraWidgets = current.pages.drop(newPages.size).flatMap { it.widgets }
        if (extraWidgets.isNotEmpty()) newPages[newPages.lastIndex] = newPages.last().let { it.copy(widgets = it.widgets + extraWidgets) }
        return Result(current.copy(pages = newPages), folders.count { it is HomeItem.Folder }, usable.size, prefChanges)
    }
}

object Templates {
    fun widget(type: WidgetType, size: WidgetSize = type.defaultSize, config: Map<String, String> = emptyMap(), spaces: Set<String> = emptySet()) =
        WidgetInstance(id(), type, size, config, spaces = spaces)

    /** Widgets for page 1 from the persona and interests. */
    fun widgetsFor(persona: String?, interests: Set<String>, style: StyleId): List<WidgetInstance> {
        val w = mutableListOf<WidgetInstance>()
        w += widget(if (style == StyleId.MINIMAL) WidgetType.CLOCK_WORDS else WidgetType.CLOCK)
        w += widget(WidgetType.MORNING)
        when (persona) {
            "student" -> { w += widget(WidgetType.TODO); w += widget(WidgetType.POMODORO); w += widget(WidgetType.COUNTDOWN) }
            "employee" -> { w += widget(WidgetType.TODAY); w += widget(WidgetType.TODO) }
            "developer" -> { w += widget(WidgetType.TODAY); w += widget(WidgetType.POMODORO); w += widget(WidgetType.USAGE) }
            "trader" -> { w += widget(WidgetType.PRICES); w += widget(WidgetType.NEWS_TICKER) }
            "driver" -> { w += widget(WidgetType.ODD_EVEN); w += widget(WidgetType.WEATHER); w += widget(WidgetType.MUSIC) }
            "business" -> { w += widget(WidgetType.BILLS); w += widget(WidgetType.EXPENSES); w += widget(WidgetType.BANK_CARDS) }
            "homemaker" -> { w += widget(WidgetType.SHOPPING); w += widget(WidgetType.BIRTHDAYS) }
            "retired" -> { w += widget(WidgetType.CONTACTS); w += widget(WidgetType.PRAYER) }
            else -> w += widget(WidgetType.TODAY)
        }
        if ("poetry" in interests || "books" in interests) w += widget(WidgetType.HAFEZ)
        if (("economy" in interests || "crypto" in interests) && w.none { it.type == WidgetType.PRICES }) w += widget(WidgetType.PRICES)
        if ("religion" in interests && w.none { it.type == WidgetType.PRAYER }) w += widget(WidgetType.PRAYER)
        if ("football" in interests || "sports" in interests) w += widget(WidgetType.FOOTBALL)
        if ("health" in interests) w += widget(WidgetType.HABITS)
        if (w.none { it.type == WidgetType.WEATHER }) w += widget(WidgetType.WEATHER)
        if (w.none { it.type == WidgetType.CALENDAR } && style == StyleId.DASHBOARD) w += widget(WidgetType.CALENDAR, WidgetSize.SMALL)
        // Pair small widgets nicely.
        return w.distinctBy { it.type }
    }

    /** Page 2 widgets: tools that are useful but not needed at first glance. */
    fun secondPageWidgets(interests: Set<String>): List<WidgetInstance> {
        val w = mutableListOf(
            widget(WidgetType.CALENDAR), widget(WidgetType.USSD), widget(WidgetType.BATTERY), widget(WidgetType.DATA_USAGE),
            widget(WidgetType.BLACKOUT)
        )
        if ("news" in interests || "tech" in interests) w += widget(WidgetType.NEWS_DIGEST)
        return w
    }

    fun newsSourcesFor(interests: Set<String>, persona: String?): Set<String> {
        val cats = mutableSetOf(NewsCategory.GENERAL)
        if ("tech" in interests || persona == "developer") cats += NewsCategory.TECH
        if ("economy" in interests || persona == "trader" || persona == "business") cats += NewsCategory.ECONOMY
        if ("crypto" in interests || persona == "trader") cats += NewsCategory.CRYPTO
        if ("sports" in interests || "football" in interests) cats += NewsCategory.SPORTS
        if ("science" in interests) cats += NewsCategory.SCIENCE
        if ("news" in interests) cats += NewsCategory.POLITICS
        val sources = Nama.remote.config.value.newsSources
        val picked = sources.filter { it.category in cats }.groupBy { it.category }.flatMap { (_, l) -> l.take(2) }
        return picked.map { it.id }.toSet()
    }

    /** Builds the first layout: favorites and widgets on page 1, folders by category after that. */
    fun initialLayout(apps: List<AppEntry>, persona: String?, interests: Set<String>, style: StyleId): HomeLayout {
        val dock = defaultDock()
        val fav = pickFavorites(apps, dock).take(if (style == StyleId.MINIMAL) 6 else 8)
        val page1 = HomePage(id(), widgetsFor(persona, interests, style), fav.map { HomeItem.App(id(), it) })
        val base = HomeLayout(listOf(page1, HomePage(id(), secondPageWidgets(interests))), dock)
        val r = SmartSort.plan(base, apps, emptyMap())
        // The second page keeps its tool widgets in front of the folders.
        return r.layout.copy(pages = r.layout.pages.mapIndexed { i, p ->
            if (i == 1) p.copy(widgets = base.pages[1].widgets) else p
        })
    }

    fun defaultDock(): List<String> {
        val apps = Nama.apps
        val picks = listOfNotNull(
            apps.resolve(Intent(Intent.ACTION_DIAL)),
            apps.resolve(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)),
            apps.resolve(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)),
            apps.resolve(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        )
        return picks.map { it.key }.distinct()
    }

    private fun pickFavorites(apps: List<AppEntry>, dock: List<String>): List<String> {
        val order = listOf(
            AppCategory.MESSAGING, AppCategory.FINANCE, AppCategory.TRANSPORT, AppCategory.SOCIAL, AppCategory.SHOPPING,
            AppCategory.MEDIA, AppCategory.PHOTO, AppCategory.PRODUCTIVITY, AppCategory.FOOD, AppCategory.READING
        )
        val out = mutableListOf<String>()
        for (c in order) {
            apps.filter { it.category == c && it.key !in dock && !it.isSystem }.take(if (c == AppCategory.MESSAGING) 2 else 1)
                .forEach { out += it.key }
        }
        // Fill with any user app if the phone has few known apps.
        apps.filter { !it.isSystem && it.key !in dock && it.key !in out }.take(8).forEach { if (out.size < 8) out += it.key }
        return out
    }

    /** Spaces created from the persona during setup. Wi-Fi names are filled in if known. */
    fun spacesFor(persona: String?, homeSsid: String?, workSsid: String?): List<Space> {
        fun preset(key: String) = SpaceManager.PRESETS.first { it.key == key }.build()
        val out = mutableListOf<Space>()
        out += preset("sleep")
        out += preset("battery")
        val home = preset("home")
        out += if (homeSsid != null) home.copy(triggers = listOf(Trigger.Wifi(listOf(homeSsid)))) else home.copy(enabled = false)
        if (persona in listOf("employee", "developer", "business", "trader") || workSsid != null) {
            val work = preset("work")
            out += if (workSsid != null) work.copy(triggers = work.triggers + Trigger.Wifi(listOf(workSsid))) else work
        }
        if (persona == "student") out += preset("university").let { it.copy(enabled = workSsid != null, triggers = listOfNotNull(workSsid?.let { s -> Trigger.Wifi(listOf(s)) })) }
        if (persona == "driver") out += preset("car").copy(enabled = false)
        out += preset("ramadan")
        out += preset("national").copy(enabled = false)
        out += preset("kids")
        out += preset("guest")
        return out
    }
}
