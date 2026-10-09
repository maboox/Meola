package ir.nama.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.nama.core.NewsFilter
import ir.nama.core.NewsItem
import ir.nama.core.NewsLogic
import ir.nama.launcher.Nama
import ir.nama.launcher.num
import ir.nama.launcher.system.Actions
import ir.nama.launcher.tr
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
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val items by Nama.news.items.collectAsStateWithLifecycle()
    val loading by Nama.news.loading.collectAsStateWithLifecycle()
    val personal by Nama.store.personal.flow.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { Nama.news.refreshIfStale() }
    var tab by remember { mutableStateOf("digest") }
    val calm = calmNow(env)
    val filtered = remember(items, env.settings, calm) { visibleNews(items, env) }
    val shown = when (tab) {
        "digest" -> NewsLogic.digest(filtered)
        "saved" -> personal.readLater
        "all" -> filtered
        else -> filtered.filter { it.category.name == tab }
    }
    val cats = remember(filtered) { filtered.map { it.category }.distinct() }

    Column(
        Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerLow)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("اخبار", "News"), style = MaterialTheme.typography.headlineSmall, color = cs.onSurface, modifier = Modifier.weight(1f))
            if (calm) AssistChip({}, label = { Text(tr("آرام", "Calm")) }, leadingIcon = { Icon(Icons.Outlined.Spa, null, Modifier.size(16.dp)) })
            IconButton({ Nama.news.refreshIfStale(0) }, enabled = !loading) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Refresh, tr("تازه کردن", "Refresh"), tint = cs.onSurfaceVariant)
            }
        }
        val tabs = listOf("digest" to tr("خلاصه", "Digest"), "all" to tr("همه", "All")) +
            cats.map { it.name to tr(it.fa, it.en) } + ("saved" to tr("ذخیره‌شده", "Saved"))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tabs, key = { it.first }) { (id, label) -> FilterChip(id == tab, { tab = id }, label = { Text(label) }) }
        }
        if (Nama.news.enabledSources().isEmpty()) {
            Text(tr("هیچ منبع خبری فعال نیست. از تنظیمات نما › اخبار منبع انتخاب کنید.", "No news sources enabled. Pick some in Nama settings › News."), color = cs.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        } else if (shown.isEmpty()) {
            Text(
                if (loading) tr("در حال دریافت خبرها…", "Fetching news…")
                else tr("خبری نیست. اگر اینترنت وصل است، منبع‌ها را در تنظیمات بررسی کنید.", "Nothing here. Check your sources in settings."),
                color = cs.onSurfaceVariant, modifier = Modifier.padding(20.dp)
            )
        }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { n ->
                val saved = personal.readLater.any { it.id == n.id }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh)
                        .combinedClickable(
                            onClick = { Actions.openUrl(ctx, n.link) },
                            onLongClick = { Actions.share(ctx, n.title + "\n" + n.link) }
                        ).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n.sourceName + " · " + ago(n.publishedAt), style = MaterialTheme.typography.labelMedium, color = cs.primary, modifier = Modifier.weight(1f))
                        IconButton({
                            Nama.store.personal.update { p ->
                                p.copy(readLater = if (saved) p.readLater.filterNot { it.id == n.id } else (listOf(n) + p.readLater).take(100))
                            }
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(if (saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, tr("ذخیره", "Save"), tint = if (saved) cs.primary else cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                    Text(n.title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 10.dp))
                    if (n.summary.isNotBlank() && tab != "digest") {
                        Text(n.summary, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, end = 10.dp))
                    }
                }
            }
            if (shown.isNotEmpty()) item {
                Text(tr("${num(shown.size)} خبر · لمس طولانی برای اشتراک", "${shown.size} stories · long press to share"), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            }
        }
    }
}
