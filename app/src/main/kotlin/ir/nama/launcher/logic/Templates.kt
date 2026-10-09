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
import ir.nama.core.GridMath
import ir.nama.launcher.data.FolderData
import ir.nama.launcher.data.GridItem
import ir.nama.launcher.data.HomeLayout
import ir.nama.launcher.data.HomePage
import ir.nama.launcher.data.WidgetInstance
import ir.nama.launcher.data.WidgetType
import ir.nama.launcher.tr
import ir.nama.launcher.ui.widgets.WSize
import ir.nama.launcher.ui.widgets.WidgetCatalog
import java.util.UUID

private fun id() = UUID.randomUUID().toString().take(10)

object SmartSort {
    data class Result(val layout: HomeLayout, val folders: Int, val apps: Int, val prefChanges: Map<String, (ir.nama.launcher.data.AppPref) -> ir.nama.launcher.data.AppPref>)

    /**
     * Rebuilds pages 2+ as folders by rule/category. Page 1 (the user's favorites and widgets) and the
     * dock are untouched, and widgets on later pages keep their place. Nothing is uninstalled; apps
     * are hidden or archived only when one of the user's rules says so.
     */
    fun plan(current: HomeLayout, apps: List<AppEntry>, lastUsed: Map<String, Long>, cols: Int = 4, rows: Int = 6): Result {
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
        val onFirst = first.items.flatMap { listOfNotNull(it.app) + (it.folder?.keys ?: emptyList()) }.toSet()
        val keep = onFirst + current.dock
        val groups = usable.filter { it.key !in keep }.groupBy { folderOf[it.key]!! }
        val stats = Nama.store.appStats.value
        val tiles = mutableListOf<GridItem>()
        val singles = mutableListOf<String>()
        groups.entries.sortedByDescending { it.value.size }.forEach { (name, list) ->
            val keys = list.sortedByDescending { stats[it.key]?.launches ?: 0 }.map { it.key }
            if (keys.size >= 2) tiles += GridItem(id(), 0, 0, folder = FolderData(name, keys)) else singles += keys
        }
        if (singles.size >= 2) tiles += GridItem(id(), 0, 0, folder = FolderData(tr("سایر", "Other"), singles))
        else singles.forEach { tiles += GridItem(id(), 0, 0, app = it) }

        // Later pages keep only their widgets; folders fill the free cells around them.
        val later = current.pages.drop(1).map { p -> p.copy(items = p.items.filter { it.isWidget }) }.toMutableList()
        val queue = ArrayDeque(tiles)
        var pi = 0
        while (queue.isNotEmpty()) {
            if (pi >= later.size) later += HomePage(id())
            val page = later[pi]
            val placed = page.items.toMutableList()
            while (queue.isNotEmpty()) {
                val spot = GridMath.findFree(placed.map { it.cell() }, 1, 1, cols, rows) ?: break
                placed += queue.removeFirst().copy(x = spot.first, y = spot.second)
            }
            later[pi] = page.copy(items = placed)
            pi++
        }
        val pages = listOf(first) + later.filter { it.items.isNotEmpty() }
        return Result(current.copy(pages = pages), tiles.count { it.isFolder }, usable.size, prefChanges)
    }
}

object Templates {
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

    /**
     * The first layout, kept deliberately simple: page 1 has the at-a-glance date/time strip, one
     * widget picked from the persona, and one row of favorite apps at the bottom (like a stock Android
     * home). Everything else goes into category folders on page 2.
     */
    fun initialLayout(apps: List<AppEntry>, persona: String?, interests: Set<String>, @Suppress("UNUSED_PARAMETER") style: StyleId, cols: Int = 4, rows: Int = 6): HomeLayout {
        val dock = defaultDock()
        val fav = pickFavorites(apps, dock).take(cols)
        val items = mutableListOf<GridItem>()
        items += GridItem(id(), 0, 0, cols, 2, widget = WidgetInstance(id(), WidgetType.GLANCE))
        val feature = featureWidget(persona, interests)
        val favRows = (fav.size + cols - 1) / cols
        if (feature != null && rows - 2 - favRows >= 2) {
            val size = WidgetCatalog.defaultSize(feature).let { WSize(it.w.coerceAtMost(cols), it.h.coerceAtMost(2)) }
            items += GridItem(id(), 0, 2, size.w, size.h, widget = WidgetInstance(id(), feature))
        }
        fav.forEachIndexed { i, k ->
            val row = rows - favRows + i / cols
            items += GridItem(id(), i % cols, row, app = k)
        }
        val base = HomeLayout(listOf(HomePage(id(), items)), dock)
        return SmartSort.plan(base, apps, emptyMap(), cols, rows).layout
    }

    /** One widget that fits the person, shown on page 1. */
    fun featureWidget(persona: String?, interests: Set<String>): WidgetType? = when {
        persona == "trader" || "crypto" in interests || "economy" in interests -> WidgetType.PRICES
        persona == "student" -> WidgetType.TODO
        persona == "employee" || persona == "developer" -> WidgetType.TODAY
        persona == "driver" -> WidgetType.WEATHER
        persona == "business" -> WidgetType.BILLS
        persona == "homemaker" -> WidgetType.SHOPPING
        persona == "retired" -> WidgetType.CONTACTS
        "poetry" in interests || "books" in interests -> WidgetType.HAFEZ
        "religion" in interests -> WidgetType.PRAYER
        "football" in interests || "sports" in interests -> WidgetType.FOOTBALL
        else -> WidgetType.WEATHER
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
        // Fill up with other apps (user apps first, then useful system apps like Maps or Gallery).
        val rest = apps.filter { it.key !in dock && it.key !in out && it.category != AppCategory.SYSTEM }
            .sortedBy { if (it.isSystem) 1 else 0 }
        for (e in rest) { if (out.size >= 8) break; out += e.key }
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
