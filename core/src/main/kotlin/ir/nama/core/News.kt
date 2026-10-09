package ir.nama.core

import kotlinx.serialization.Serializable

@Serializable
data class NewsItem(
    val id: String,
    val title: String,
    val link: String,
    val sourceId: String,
    val sourceName: String,
    val category: NewsCategory,
    val publishedAt: Long,
    val summary: String = "",
    val imageUrl: String? = null
)

data class NewsFilter(
    val categories: Set<NewsCategory>,
    val mutedWords: List<String>,
    /** Hide politics and alarming headlines. */
    val calm: Boolean
)

object NewsLogic {
    private val ALARMING = listOf(
        "انفجار", "کشته", "حمله", "جنگ", "زلزله", "اعدام", "قتل", "تیراندازی", "سقوط", "آتش‌سوزی", "حادثه",
        "explosion", "killed", "attack", "war", "earthquake"
    ).map { PersianText.normalize(it) }

    private val BREAKING = listOf("فوری", "خبر فوری", "breaking", "urgent").map { PersianText.normalize(it) }

    fun isBreaking(item: NewsItem): Boolean {
        val t = PersianText.normalize(item.title)
        return BREAKING.any { t.startsWith(it) || t.contains("$it:") || t.contains("#$it") }
    }

    fun filter(items: List<NewsItem>, f: NewsFilter): List<NewsItem> {
        val muted = f.mutedWords.map { PersianText.normalize(it) }.filter { it.isNotBlank() }
        return items.filter { item ->
            if (f.categories.isNotEmpty() && item.category !in f.categories) return@filter false
            val text = PersianText.normalize(item.title + " " + item.summary)
            if (muted.any { text.contains(it) }) return@filter false
            if (f.calm) {
                if (item.category == NewsCategory.POLITICS) return@filter false
                if (ALARMING.any { text.contains(it) }) return@filter false
            }
            true
        }
    }

    /** Deduplicates by link and near-identical titles, newest first. */
    fun dedupe(items: List<NewsItem>): List<NewsItem> {
        val seenLinks = HashSet<String>()
        val seenTitles = HashSet<String>()
        return items.sortedByDescending { it.publishedAt }.filter {
            val key = PersianText.normalize(it.title).take(40)
            seenLinks.add(it.link) && seenTitles.add(key)
        }
    }

    /** A short digest: newest items, at most [perSource] from each source. */
    fun digest(items: List<NewsItem>, size: Int = 10, perSource: Int = 3): List<NewsItem> {
        val counts = HashMap<String, Int>()
        val out = mutableListOf<NewsItem>()
        for (it in items.sortedByDescending { n -> n.publishedAt }) {
            val c = counts.getOrDefault(it.sourceId, 0)
            if (c >= perSource) continue
            counts[it.sourceId] = c + 1
            out += it
            if (out.size >= size) break
        }
        return out
    }

    /** Removes HTML tags and entities from feed text. */
    fun stripHtml(s: String): String = s
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&zwnj;", "‌")
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: "" }
        .replace(Regex("[ \\t]+"), " ")
        .trim()
}
