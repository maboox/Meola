package ir.nama.launcher.ui.widgets

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.nama.core.NewsLogic
import ir.nama.core.Poem
import ir.nama.core.Poems
import ir.nama.launcher.Nama
import ir.nama.launcher.data.NotificationRepo
import ir.nama.launcher.num
import ir.nama.launcher.system.Actions
import ir.nama.launcher.tr
import ir.nama.launcher.ui.common.AppPickerDialog
import ir.nama.launcher.ui.home.ago
import ir.nama.launcher.ui.home.visibleNews
import ir.nama.launcher.ui.theme.LocalNamaStyle
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
private fun PoemCard(p: Poem, title: String, onClick: (() -> Unit)? = null, translation: Boolean = false) {
    val s = LocalNamaStyle.current
    WidgetCard(onClick = onClick) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(p.line1, color = s.onSurface, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
            Text(p.line2, color = if (translation) s.onSurfaceVariant else s.onSurface, fontSize = if (translation) 12.sp else 15.sp, textAlign = TextAlign.Center, lineHeight = 24.sp, fontWeight = if (translation) FontWeight.Normal else FontWeight.Medium)
            Spacer(Modifier.size(6.dp))
            Text(title + " · " + p.poet, color = s.accent, fontSize = 11.sp)
        }
    }
}

@Composable
fun HafezWidget(sc: WScope) {
    val all = Poems.HAFEZ + Nama.remote.config.value.extraHafez
    val day = rememberNow().toLocalDate().toEpochDay()
    var pick by remember(day) { mutableIntStateOf(-1) }
    val poem = if (pick >= 0) all[pick % all.size] else Poems.ofDay(all, day, 7)
    AnimatedContent(poem, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "fal") {
        PoemCard(it, tr("فال حافظ · لمس برای فال تازه", "Hafez · tap for another"), onClick = { pick = Random.nextInt(all.size) })
    }
}

@Composable
fun PoemWidget(sc: WScope) {
    val all = Poems.OTHERS + Poems.HAFEZ + Nama.remote.config.value.extraPoems
    PoemCard(Poems.ofDay(all, rememberNow().toLocalDate().toEpochDay(), 3), tr("شعر روز", "Poem of the day"))
}

@Composable
fun VerseWidget(sc: WScope) {
    PoemCard(Poems.ofDay(Poems.VERSES, rememberNow().toLocalDate().toEpochDay(), 5), tr("آیه روز", "Verse"), translation = true)
}

@Composable
fun QuoteWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val q = Poems.ofDay(Poems.QUOTES, rememberNow().toLocalDate().toEpochDay(), 11)
    WidgetCard {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.FormatQuote, null, tint = s.accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            WText(q, if (sc.tall) 17.sp else 15.sp, FontWeight.Medium, maxLines = 3)
        }
    }
}

@Composable
fun NewsWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val items by Nama.news.items.collectAsState()
    LaunchedEffect(Unit) { Nama.news.refreshIfStale() }
    val list = remember(items, sc.env.settings) { NewsLogic.digest(visibleNews(items, sc.env), 12, 3) }
    if (sc.size.h == 1) {
        var i by remember { mutableIntStateOf(0) }
        LaunchedEffect(list.size, sc.env.reduceMotion) { while (list.isNotEmpty() && !sc.env.reduceMotion) { delay(6000); i = (i + 1) % list.size } }
        val n = list.getOrNull(i % list.size.coerceAtLeast(1))
        WidgetCard(onClick = { n?.let { Actions.openUrl(ctx, it.link) } }, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Article, null, tint = s.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                AnimatedContent(n, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "ticker") { item ->
                    Column {
                        WText(item?.title ?: tr("خبری نیست", "No news"), 13.sp, FontWeight.Medium, maxLines = 1)
                        if (item != null) WText(item.sourceName + " · " + ago(item.publishedAt), 11.sp, secondary = true)
                    }
                }
            }
        }
        return
    }
    WidgetCard {
        Column(Modifier.fillMaxSize()) {
            WHeader(Icons.Outlined.Article, tr("اخبار", "News"))
            if (list.isEmpty()) WText(if (Nama.news.enabledSources().isEmpty()) tr("منبع خبری فعال نیست", "No sources enabled") else tr("در حال دریافت…", "Loading…"), 13.sp, secondary = true)
            list.take(if (sc.size.h >= 3) 4 else 2).forEach { n ->
                Column(Modifier.fillMaxWidth().clickable { Actions.openUrl(ctx, n.link) }.padding(vertical = 4.dp)) {
                    Text(n.title, color = s.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                    WText(n.sourceName + " · " + ago(n.publishedAt), 11.sp, secondary = true)
                }
            }
        }
    }
}

@Composable
fun NotifDigestWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val recent by NotificationRepo.recent.collectAsState()
    val access = remember { NotificationRepo.hasAccess(ctx) }
    WidgetCard {
        if (!access) {
            WEmpty(Icons.Outlined.Notifications, tr("برای خلاصه اعلان‌ها، دسترسی اعلان لازم است", "Needs notification access"), tr("دسترسی", "Grant")) {
                Actions.start(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        } else Column(Modifier.fillMaxSize()) {
            val important = recent.filter { it.important }
            WHeader(Icons.Outlined.Notifications, tr("اعلان‌های مهم", "Important"), num(important.size) + " / " + num(recent.size))
            if (important.isEmpty()) WText(tr("چیز مهمی نیست", "Nothing important"), 13.sp, secondary = true)
            important.take(3).forEach { n ->
                val app = Nama.apps.byPackage(n.pkg)?.label ?: ""
                Column(Modifier.padding(vertical = 2.dp)) {
                    WText(listOf(app, n.title).filter { it.isNotBlank() }.joinToString(" · "), 12.sp, FontWeight.Medium)
                    if (n.text.isNotBlank()) WText(n.text, 12.sp, secondary = true)
                }
            }
        }
    }
}

private fun minutesText(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) num(m / 60) + tr(" ساعت و ", "h ") + num(m % 60) + tr(" دقیقه", "m") else num(m) + tr(" دقیقه", " min")
}

@Composable
fun UsageWidget(sc: WScope) {
    val ctx = LocalContext.current
    val today by Nama.usage.today.collectAsState()
    LaunchedEffect(Unit) { Nama.usage.refreshToday(true) }
    WidgetCard {
        if (!Nama.usage.hasPermission()) {
            WEmpty(Icons.Outlined.HourglassEmpty, tr("برای زمان استفاده، دسترسی «آمار استفاده» لازم است", "Needs usage access"), tr("دسترسی", "Grant")) {
                Actions.start(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        } else Column(Modifier.fillMaxSize()) {
            WHeader(Icons.Outlined.HourglassEmpty, tr("امروز با گوشی", "Screen time"))
            WText(minutesText(today.values.sum()), 22.sp, FontWeight.Light)
            WSpacer()
            today.entries.sortedByDescending { it.value }.take(if (sc.wide) 3 else 2).forEach { (pkg, ms) ->
                val name = Nama.apps.byPackage(pkg)?.label ?: return@forEach
                Row(Modifier.fillMaxWidth()) {
                    WText(name, 12.sp, modifier = Modifier.weight(1f))
                    WText(minutesText(ms), 12.sp, secondary = true)
                }
            }
        }
    }
}

@Composable
fun FocusWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    rememberNow()
    var picking by remember { mutableStateOf(false) }
    val until = sc.env.settings.focusUntil
    val active = until > System.currentTimeMillis()
    WidgetCard(onClick = {
        if (active) Nama.store.settings.update { it.copy(focusUntil = 0L) }
        else Nama.store.settings.update { it.copy(focusUntil = System.currentTimeMillis() + 25 * 60_000L) }
    }) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CenterFocusStrong, null, tint = if (active) s.accent else s.onSurfaceVariant, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                WText(if (active) num((until - System.currentTimeMillis()) / 60_000) + tr(" دقیقه تمرکز", " min focus") else tr("تمرکز", "Focus"), 15.sp, FontWeight.Medium)
                WText(if (active) tr("لمس برای پایان", "Tap to stop") else tr("لمس: ۲۵ دقیقه", "Tap: 25 min"), 11.sp, secondary = true)
                if (sc.tall) WText(tr("برنامه‌های مجاز: ", "Allowed: ") + num(sc.env.settings.focusApps.size), 11.sp, color = s.accent, modifier = Modifier.clickable { picking = true }.padding(top = 4.dp))
            }
        }
    }
    if (picking) AppPickerDialog(tr("برنامه‌های مجاز در تمرکز", "Apps allowed in focus"), sc.env.settings.focusApps, onDismiss = { picking = false }) { set ->
        Nama.store.settings.update { it.copy(focusApps = set) }
    }
}

@Composable
fun SpaceWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    WidgetCard(onClick = { sc.ctrl.spaceSwitcher = true }) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Dashboard, null, tint = s.accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                WText(sc.env.space?.name ?: tr("بدون فضا", "No space"), 15.sp, FontWeight.Medium)
                WText(if (sc.env.settings.manualSpaceId == null) tr("خودکار", "Automatic") else tr("دستی", "Manual"), 11.sp, secondary = true)
            }
        }
    }
}
