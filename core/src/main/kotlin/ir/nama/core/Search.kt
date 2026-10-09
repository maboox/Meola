package ir.nama.core

/**
 * Search matching that understands Persian, English and Finglish (Persian typed with Latin letters),
 * plus text typed on the wrong keyboard layout.
 */
object Finglish {
    private val FA_TO_LATIN: Map<Char, String> = mapOf(
        'ا' to "a", 'آ' to "a", 'ب' to "b", 'پ' to "p", 'ت' to "t", 'ث' to "s", 'ج' to "j", 'چ' to "ch",
        'ح' to "h", 'خ' to "kh", 'د' to "d", 'ذ' to "z", 'ر' to "r", 'ز' to "z", 'ژ' to "zh", 'س' to "s",
        'ش' to "sh", 'ص' to "s", 'ض' to "z", 'ط' to "t", 'ظ' to "z", 'ع' to "a", 'غ' to "gh", 'ف' to "f",
        'ق' to "gh", 'ک' to "k", 'گ' to "g", 'ل' to "l", 'م' to "m", 'ن' to "n", 'و' to "v", 'ه' to "h",
        'ی' to "y", 'ء' to "", ' ' to " "
    )

    /** Rough Latin transliteration of Persian text. */
    fun transliterate(text: String): String {
        val n = PersianText.normalize(text)
        val sb = StringBuilder()
        for ((i, c) in n.withIndex()) {
            val atWordEnd = i == n.length - 1 || n[i + 1] == ' '
            if (c == 'ه' && atWordEnd && i > 0) { sb.append('e'); continue }
            sb.append(FA_TO_LATIN[c] ?: c.toString())
        }
        return sb.toString()
    }

    /**
     * Consonant skeleton used to match Persian and Latin spellings of the same word,
     * e.g. "تلگرام" and "telegram" both become "tlgrm".
     */
    fun skeleton(text: String): String {
        val latin = if (PersianText.containsPersian(text)) transliterate(text) else PersianText.normalize(text)
        var s = latin.lowercase()
            .replace("wh", "w").replace("ph", "f").replace("ck", "k").replace("chr", "kr")
            .replace("gh", "g").replace("kh", "k").replace("sh", "s").replace("zh", "z").replace("ch", "c")
            .replace("q", "g").replace("x", "ks").replace("j", "g").replace("c", "k")
        val sb = StringBuilder()
        for (ch in s) {
            if (ch in "aeiouyvwh'` -_.") continue
            if (!ch.isLetterOrDigit()) continue
            if (sb.isNotEmpty() && sb.last() == ch) continue
            sb.append(ch)
        }
        s = sb.toString()
        return s
    }

    // Persian standard keyboard <-> QWERTY positions.
    private const val QWERTY = "qwertyuiop[]asdfghjkl;'zxcvbnm,"
    private const val FA_KEYS = "ضصثقفغعهخحجچشسیبلاتنمکگظطزرذدپو"

    /** Converts text typed with the Persian layout into what the same keys produce on QWERTY, and vice versa. */
    fun swapLayout(text: String): String {
        val sb = StringBuilder()
        for (c in text.lowercase()) {
            val fa = FA_KEYS.indexOf(c)
            val en = QWERTY.indexOf(c)
            sb.append(
                when {
                    fa >= 0 -> QWERTY[fa]
                    en >= 0 -> FA_KEYS[en]
                    else -> c
                }
            )
        }
        return sb.toString()
    }
}

/** Precomputed searchable form of an item (e.g. an app label plus aliases). */
class SearchKey(label: String, aliases: List<String> = emptyList(), extra: List<String> = emptyList()) {
    val names: List<String> = (listOf(label) + aliases + extra).map { PersianText.normalize(it) }.filter { it.isNotBlank() }
    val skeletons: List<String> = names.map { Finglish.skeleton(it) }.filter { it.isNotEmpty() }
    val words: List<String> = names.flatMap { it.split(' ') }.filter { it.isNotBlank() }
}

object SearchScorer {
    /** Returns a score > 0 when the query matches, higher is better. */
    fun score(query: String, key: SearchKey): Int {
        val q = PersianText.normalize(query)
        if (q.isEmpty()) return 0
        var best = scoreDirect(q, key)
        if (best >= 900) return best
        val qs = Finglish.skeleton(q)
        if (qs.length >= 2) {
            for (s in key.skeletons) {
                val v = when {
                    s == qs -> 700
                    s.startsWith(qs) -> 600 + (qs.length * 10).coerceAtMost(90)
                    qs.length >= 3 && s.contains(qs) -> 450
                    qs.length >= 3 && isSubsequence(qs, s) -> 250
                    else -> 0
                }
                if (v > best) best = v
            }
        }
        if (best < 500) {
            // Typed on the wrong keyboard layout?
            val swapped = PersianText.normalize(Finglish.swapLayout(query))
            if (swapped != q) {
                val v = scoreDirect(swapped, key)
                if (v > 0) best = maxOf(best, v - 150)
            }
        }
        return best
    }

    private fun scoreDirect(q: String, key: SearchKey): Int {
        var best = 0
        for (n in key.names) {
            val v = when {
                n == q -> 1000
                n.startsWith(q) -> 900
                key.words.any { it.startsWith(q) } -> 800
                q.length >= 2 && n.contains(q) -> 650
                q.length >= 3 && isSubsequence(q.replace(" ", ""), n.replace(" ", "")) -> 300
                else -> 0
            }
            if (v > best) best = v
        }
        return best
    }

    private fun isSubsequence(needle: String, hay: String): Boolean {
        var i = 0
        for (c in hay) {
            if (i < needle.length && needle[i] == c) i++
        }
        return i == needle.length
    }
}
