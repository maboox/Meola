package ir.nama.launcher.ui.widgets

import android.Manifest
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.nama.core.BankCards
import ir.nama.core.Jalali
import ir.nama.core.JalaliDate
import ir.nama.core.PersianText
import ir.nama.launcher.Nama
import ir.nama.launcher.data.Habit
import ir.nama.launcher.data.Pomodoro
import ir.nama.launcher.data.Todo
import ir.nama.launcher.num
import ir.nama.launcher.system.Actions
import ir.nama.launcher.system.Schedules
import ir.nama.launcher.tr
import ir.nama.launcher.ui.common.TextInputDialog
import ir.nama.launcher.ui.theme.LocalNamaStyle
import ir.nama.launcher.ui.theme.Vazir
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun BillsWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val personal by Nama.store.personal.flow.collectAsState()
    var adding by remember { mutableStateOf(false) }
    val today = rememberNow().toLocalDate().toEpochDay()
    val unpaid = personal.bills.filter { it.paidEpochDay == null || it.paidEpochDay < it.dueEpochDay }.sortedBy { it.dueEpochDay }
    WidgetCard {
        if (personal.bills.isEmpty()) {
            WEmpty(Icons.Outlined.ReceiptLong, tr("قسط‌ها و قبض‌ها را ثبت کن تا سر موعد یادت بیاید", "Add bills to be reminded"), tr("افزودن", "Add")) { adding = true }
        } else Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { WHeader(Icons.Outlined.ReceiptLong, tr("قسط و قبض", "Bills")) }
                Icon(Icons.Outlined.Add, tr("افزودن", "Add"), tint = s.accent, modifier = Modifier.clip(CircleShape).clickable { adding = true }.padding(2.dp).size(20.dp))
            }
            if (unpaid.isEmpty()) WText(tr("همه پرداخت شده", "All paid"), 14.sp, color = s.success)
            unpaid.take(if (sc.size.h >= 2 && sc.wide) 3 else 2).forEach { b ->
                val days = b.dueEpochDay - today
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.RadioButtonUnchecked, tr("پرداخت شد", "Paid"), tint = s.accent,
                        modifier = Modifier.clip(CircleShape).clickable {
                            Nama.store.personal.update { p ->
                                p.copy(bills = p.bills.map {
                                    if (it.id != b.id) it
                                    else if (it.monthly) it.copy(paidEpochDay = it.dueEpochDay, dueEpochDay = Jalali.addMonths(JalaliDate.from(LocalDate.ofEpochDay(it.dueEpochDay)), 1).toLocalDate().toEpochDay())
                                    else it.copy(paidEpochDay = it.dueEpochDay)
                                })
                            }
                        }.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        WText(b.title, 13.sp, FontWeight.Medium)
                        if (b.amountToman > 0 && sc.wide) WText(PersianText.shortToman(b.amountToman, Nama.faDigits) + tr(" تومان", ""), 11.sp, secondary = true)
                    }
                    WText(when { days < 0 -> tr("گذشته", "late"); days == 0L -> tr("امروز", "today"); else -> num(days) + tr(" روز", "d") }, 12.sp, FontWeight.Medium, color = if (days <= 1) s.danger else s.onSurfaceVariant)
                }
            }
        }
    }
    if (adding) BillDialog { adding = false }
}

@Composable
fun BankCardsWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val cards by Nama.cards.cards.collectAsState()
    var revealedUntil by remember { mutableLongStateOf(0L) }
    var adding by remember { mutableStateOf(false) }
    val revealed = revealedUntil > System.currentTimeMillis()
    LaunchedEffect(revealedUntil) { if (revealedUntil > 0) { delay(30_000); revealedUntil = 0 } }
    WidgetCard {
        if (cards.isEmpty()) {
            WEmpty(Icons.Outlined.CreditCard, tr("کارت‌ها رمزنگاری‌شده و فقط روی همین گوشی ذخیره می‌شوند", "Stored encrypted, only on this phone"), tr("افزودن کارت", "Add card")) { adding = true }
        } else Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { WHeader(Icons.Outlined.CreditCard, tr("کارت‌های بانکی", "Bank cards")) }
                Icon(Icons.Outlined.Visibility, tr("نمایش", "Show"), tint = s.onSurfaceVariant, modifier = Modifier.clip(CircleShape).clickable {
                    sc.ctrl.authenticate(tr("نمایش کارت‌ها", "Show cards")) { revealedUntil = System.currentTimeMillis() + 30_000 }
                }.padding(2.dp).size(20.dp))
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.Add, tr("افزودن", "Add"), tint = s.accent, modifier = Modifier.clip(CircleShape).clickable { adding = true }.padding(2.dp).size(20.dp))
            }
            cards.take(if (sc.size.h >= 3) 3 else 2).forEach { c ->
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(s.accentContainer)
                        .combinedClickable(
                            onClick = { sc.ctrl.authenticate(tr("کپی شماره کارت", "Copy card number")) { Actions.copy(ctx, BankCards.digitsOnly(c.number), sensitive = true) } },
                            onLongClick = { if (c.sheba.isNotBlank()) sc.ctrl.authenticate(tr("کپی شبا", "Copy IBAN")) { Actions.copy(ctx, c.sheba, sensitive = true) } }
                        ).padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text((BankCards.bankName(c.number) ?: tr("کارت", "Card")) + (if (c.owner.isNotBlank()) " · ${c.owner}" else ""), color = s.onAccentContainer.copy(alpha = 0.75f), fontSize = 11.sp)
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                        Text(if (revealed) BankCards.format(c.number, Nama.faDigits) else BankCards.mask(c.number, Nama.faDigits), color = s.onAccentContainer, fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
    if (adding) CardDialog(onDismiss = { adding = false }) { c -> Nama.cards.set(cards + c) }
}

@Composable
fun ExpensesWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val personal by Nama.store.personal.flow.collectAsState()
    var adding by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val tj = JalaliDate.from(today)
    val monthStart = JalaliDate(tj.year, tj.month, 1).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    fun sum(from: Long) = personal.expenses.filter { it.at >= from }.sumOf { if (it.income) -it.amountToman else it.amountToman }.coerceAtLeast(0)
    WidgetCard(onClick = { adding = true }) {
        if (sc.size.h == 1) Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Payments, null, tint = s.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Column { WText(PersianText.shortToman(sum(dayStart), Nama.faDigits), 16.sp, FontWeight.Medium); WText(tr("خرج امروز", "Today"), 11.sp, secondary = true) }
        } else Column(Modifier.fillMaxSize()) {
            WHeader(Icons.Outlined.Payments, tr("دخل‌وخرج", "Spending"))
            WSpacer()
            WText(PersianText.shortToman(sum(dayStart), Nama.faDigits), 20.sp, FontWeight.Medium, maxLines = 2)
            WText(tr("تومان خرج امروز", "toman today"), 11.sp, secondary = true)
            WText(tr("این ماه: ", "Month: ") + PersianText.shortToman(sum(monthStart), Nama.faDigits), 11.sp, secondary = true)
        }
    }
    if (adding) ExpenseDialog { adding = false }
}

@Composable
fun TodoWidget(sc: WScope, shopping: Boolean) {
    val s = LocalNamaStyle.current
    val personal by Nama.store.personal.flow.collectAsState()
    var text by remember { mutableStateOf("") }
    val list = if (shopping) personal.shopping else personal.todos
    fun save(f: (List<Todo>) -> List<Todo>) = Nama.store.personal.update { if (shopping) it.copy(shopping = f(it.shopping)) else it.copy(todos = f(it.todos)) }
    val open = list.filter { !it.done }
    val done = list.filter { it.done }
    WidgetCard(padding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp)) {
        Column(Modifier.fillMaxSize()) {
            WHeader(
                if (shopping) Icons.Outlined.ShoppingCart else Icons.Outlined.TaskAlt,
                if (shopping) tr("لیست خرید", "Shopping") else tr("کارها", "To-do"),
                if (list.isNotEmpty()) num(open.size) else null
            )
            LazyColumn(Modifier.weight(1f)) {
                items(open + done.take(2), key = { it.id }) { t ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (t.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                            tint = if (t.done) s.onSurfaceVariant else s.accent,
                            modifier = Modifier.clip(CircleShape).clickable { save { l -> l.map { if (it.id == t.id) it.copy(done = !it.done) else it } } }.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            t.text, color = if (t.done) s.onSurfaceVariant else s.onSurface, fontSize = 14.sp, maxLines = 1,
                            textDecoration = if (t.done) TextDecoration.LineThrough else null, modifier = Modifier.weight(1f)
                        )
                        if (t.done) Icon(Icons.Outlined.Close, tr("حذف", "Delete"), tint = s.onSurfaceVariant, modifier = Modifier.clip(CircleShape).clickable { save { l -> l.filterNot { it.id == t.id } } }.size(18.dp))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(s.accentContainer.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Add, null, tint = s.onAccentContainer, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) Text(if (shopping) tr("نان، شیر…", "Bread, milk…") else tr("کار تازه", "New task"), color = s.onAccentContainer.copy(alpha = 0.7f), fontSize = 13.sp)
                    BasicTextField(
                        text, { text = it }, singleLine = true,
                        textStyle = TextStyle(color = s.onAccentContainer, fontSize = 13.sp, fontFamily = Vazir),
                        cursorBrush = SolidColor(s.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            val v = text.trim()
                            if (v.isNotEmpty()) {
                                val parts = if (shopping) v.split(',', '،').map { it.trim() }.filter { it.isNotEmpty() } else listOf(v)
                                save { l -> l + parts.map { Todo(uid(), it, createdAt = System.currentTimeMillis()) } }
                                text = ""
                            }
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun NotesWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    var text by remember { mutableStateOf(Nama.store.personal.value.note) }
    WidgetCard {
        Column(Modifier.fillMaxSize()) {
            WHeader(Icons.Outlined.StickyNote2, tr("یادداشت", "Note"))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (text.isEmpty()) Text(tr("هر چیزی که باید یادت بماند…", "Anything to remember…"), color = s.onSurfaceVariant, fontSize = 14.sp)
                BasicTextField(
                    text, { v -> text = v; Nama.store.personal.update { it.copy(note = v) } },
                    textStyle = TextStyle(color = s.onSurface, fontSize = 14.sp, fontFamily = Vazir, lineHeight = 22.sp),
                    cursorBrush = SolidColor(s.accent), modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun HabitsWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val personal by Nama.store.personal.flow.collectAsState()
    var adding by remember { mutableStateOf(false) }
    val today = rememberNow().toLocalDate().toEpochDay()
    fun streak(h: Habit): Int {
        var d = if (today in h.doneDays) today else today - 1
        var n = 0
        while (d in h.doneDays) { n++; d-- }
        return n
    }
    WidgetCard {
        if (personal.habits.isEmpty()) {
            WEmpty(Icons.Outlined.DonutLarge, tr("یک عادت کوچک شروع کن: ورزش، کتاب، آب", "Start a small habit"), tr("عادت تازه", "New habit")) { adding = true }
        } else Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { WHeader(Icons.Outlined.DonutLarge, tr("عادت‌ها", "Habits")) }
                Icon(Icons.Outlined.Add, tr("افزودن", "Add"), tint = s.accent, modifier = Modifier.clip(CircleShape).clickable { adding = true }.padding(2.dp).size(20.dp))
            }
            personal.habits.take(if (sc.wide) 3 else 2).forEach { h ->
                val doneToday = today in h.doneDays
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(if (doneToday) s.accent else s.accentContainer)
                            .clickable {
                                Nama.store.personal.update { p ->
                                    p.copy(habits = p.habits.map { if (it.id == h.id) it.copy(doneDays = if (doneToday) it.doneDays - today else (it.doneDays + today).filter { d -> d > today - 400 }.toSet()) else it })
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) { if (doneToday) Icon(Icons.Outlined.CheckCircle, null, tint = if (s.minimal) Color.Black else Color.White, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(8.dp))
                    Text(h.name, color = s.onSurface, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f).combinedClickable(onClick = {}, onLongClick = {
                        Nama.store.personal.update { p -> p.copy(habits = p.habits.filterNot { it.id == h.id }) }
                    }))
                    if (sc.wide) Row(Modifier.padding(horizontal = 6.dp)) {
                        for (i in 6 downTo 0) Box(Modifier.padding(1.5.dp).size(6.dp).clip(CircleShape).background(if ((today - i) in h.doneDays) s.accent else s.outline))
                    }
                    val st = streak(h)
                    if (st > 0) WText(num(st), 12.sp, FontWeight.Bold, color = s.warning)
                }
            }
        }
    }
    if (adding) TextInputDialog(tr("نام عادت", "Habit name"), onDismiss = { adding = false }) { n ->
        if (n.isNotBlank()) Nama.store.personal.update { it.copy(habits = it.habits + Habit(uid(), n)) }
    }
}

@Composable
fun PomodoroWidget(sc: WScope) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val personal by Nama.store.personal.flow.collectAsState()
    val p = personal.pomodoro
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(p.running, p.endsAt) { while (p.running) { now = System.currentTimeMillis(); delay(1000) } }
    val totalMs = (if (p.onBreak) p.breakMinutes else p.workMinutes) * 60_000L
    val leftMs = if (p.running) (p.endsAt - now).coerceAtLeast(0) else totalMs
    val progress = if (p.running) 1f - leftMs.toFloat() / totalMs else 0f
    val left = leftMs / 1000
    val toggle = {
        if (p.running) {
            Schedules.cancelPomodoro(ctx)
            Nama.store.personal.update { it.copy(pomodoro = it.pomodoro.copy(running = false, onBreak = false, endsAt = 0L)) }
        } else {
            if (Build.VERSION.SDK_INT >= 33) sc.ctrl.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            val ends = System.currentTimeMillis() + p.workMinutes * 60_000L
            val today = LocalDate.now().toEpochDay()
            Nama.store.personal.update { it.copy(pomodoro = Pomodoro(true, false, ends, p.workMinutes, p.breakMinutes, if (p.day == today) p.doneToday else 0, today)) }
            Nama.store.personal.flushNow()
            Schedules.schedulePomodoro(ctx, ends)
        }
    }
    val time = num("${left / 60}:${(left % 60).toString().padStart(2, '0')}")
    val ring = if (p.onBreak) s.success else s.accent
    WidgetCard(onClick = toggle) {
        if (sc.size.h == 1) Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Timer, null, tint = ring, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Column { WText(time, 20.sp, FontWeight.Light); WText(if (!p.running) tr("لمس برای شروع", "Tap to start") else if (p.onBreak) tr("استراحت", "Break") else tr("تمرکز", "Focus"), 11.sp, secondary = true) }
        } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val track = s.outline
            Canvas(Modifier.fillMaxSize(0.92f).aspectRatio(1f)) {
                val stroke = size.minDimension * 0.06f
                val arcSize = Size(size.minDimension - stroke, size.minDimension - stroke)
                val tl = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
                drawArc(track, 0f, 360f, false, tl, arcSize, style = Stroke(stroke))
                drawArc(ring, -90f, 360f * progress, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                WText(time, 24.sp, FontWeight.Light)
                WText(if (!p.running) tr("شروع", "Start") else if (p.onBreak) tr("استراحت", "Break") else tr("تمرکز", "Focus"), 11.sp, secondary = true)
            }
        }
    }
}
