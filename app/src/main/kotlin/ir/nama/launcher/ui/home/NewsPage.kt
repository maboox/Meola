package ir.nama.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.nama.core.NewsFilter
import ir.nama.core.NewsItem
import ir.nama.core.NewsLogic
import ir.nama.launcher.Nama
import ir.nama.launcher.num
import ir.nama.launcher.system.Actions
import ir.nama.launcher.tr
import ir.nama.launcher.ui.theme.LocalNamaStyle
import java.time.LocalTime

/** Is "calm news" on right now (always, at night, or because the active space asks for it)? */
fun calmNow(env: HomeEnv): Boolean {
    val s = env.settings
    if (s.newsCalmAlways || env.space?.overrides?.calmNews == true) return true
    if (!s.newsCalmAtNight) return false
    val now = LocalTime.now().let { it.hour * 60 + it.minute }
    val a = s.newsCalmStartMinute
    val b = s.newsCalmEndMinute
    return if (a <= b) now in a until b else now >= a || now < b
}

fun visibleNews(items: List<NewsItem>, env: HomeEnv): List<NewsItem> =
    NewsLogic.filter(items, NewsFilter(env.settings.newsCategories, env.settings.newsMutedWords, calmNow(env)))

@Composable
fun NewsPage(ctrl: HomeController, env: HomeEnv) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val items by Nama.news.items.collectAsState()
    val loading by Nama.news.loading.collectAsState()
    val personal by Nama.store.personal.flow.collectAsState()
    LaunchedEffect(Unit) { Nama.news.refreshIfStale() }
    var tab by remember { mutableStateOf("all") }
    val calm = calmNow(env)
    val filtered = remember(items, env.settings, calm) { visibleNews(items, env) }
    val shown = when (tab) {
        "digest" -> NewsLogic.digest(filtered)
        "saved" -> personal.readLater
        "all" -> filtered
        else -> filtered.filter { it.category.name == tab }
    }
    val cats = remember(filtered) { filtered.map { it.category }.distinct() }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("اخبار", "News"), color = s.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (calm) Text(tr("🕊 حالت آرامش", "🕊 Calm"), color = s.accent, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
            Text(
                if (loading) "…" else "⟳", color = s.subText, fontSize = 20.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { Nama.news.refreshIfStale(0) }.padding(8.dp)
            )
        }
        val tabs = listOf("digest" to tr("خلاصه", "Digest"), "all" to tr("همه", "All")) +
            cats.map { it.name to tr(it.fa, it.en) } + ("saved" to tr("ذخیره‌شده", "Saved"))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(tabs) { (id, label) ->
                val sel = id == tab
                Text(
                    label, color = if (sel) s.accent else s.text, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp))
                        .background(if (sel) s.accent.copy(alpha = 0.16f) else s.card)
                        .border(1.dp, if (sel) s.accent else s.cardBorder, RoundedCornerShape(16.dp))
                        .clickable { tab = id }.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        if (Nama.news.enabledSources().isEmpty()) {
            Text(tr("هیچ منبع خبری فعال نیست. از تنظیمات نما › اخبار منبع انتخاب کنید.", "No news sources enabled. Pick some in Nama settings › News."), color = s.subText, modifier = Modifier.padding(16.dp))
        } else if (shown.isEmpty()) {
            Text(
                if (loading) tr("در حال دریافت خبرها…", "Fetching news…")
                else tr("خبری نیست. اگر اینترنت وصل است، منبع‌ها را در تنظیمات بررسی کنید.", "Nothing here. Check your sources in settings."),
                color = s.subText, modifier = Modifier.padding(16.dp)
            )
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { n ->
                val saved = personal.readLater.any { it.id == n.id }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(s.cardRadius)).background(s.card)
                        .border(1.dp, s.cardBorder, RoundedCornerShape(s.cardRadius))
                        .combinedClickable(
                            onClick = { Actions.openUrl(ctx, n.link) },
                            onLongClick = { Actions.share(ctx, n.title + "\n" + n.link) }
                        ).padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n.sourceName + " · " + ago(n.publishedAt), color = s.subText, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text(
                            if (saved) "★" else "☆", color = if (saved) s.accent2 else s.subText, fontSize = 18.sp,
                            modifier = Modifier.clickable {
                                Nama.store.personal.update { p ->
                                    p.copy(readLater = if (saved) p.readLater.filterNot { it.id == n.id } else (listOf(n) + p.readLater).take(100))
                                }
                            }.padding(4.dp)
                        )
                    }
                    Text(n.title, color = s.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (n.summary.isNotBlank() && tab != "digest") {
                        Spacer(Modifier.width(4.dp))
                        Text(n.summary, color = s.subText, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (shown.isNotEmpty()) item {
                Text(tr("${num(shown.size)} خبر · لمس طولانی برای اشتراک", "${shown.size} stories · long press to share"), color = s.subText, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
            }
        }
    }
}
