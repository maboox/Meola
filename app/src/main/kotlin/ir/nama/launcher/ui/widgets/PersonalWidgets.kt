package ir.nama.launcher.ui.widgets

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.nama.core.BankCards
import ir.nama.core.Jalali
import ir.nama.core.JalaliDate
import ir.nama.core.PersianText
import ir.nama.launcher.Nama
import ir.nama.launcher.data.BankCard
import ir.nama.launcher.data.Bill
import ir.nama.launcher.data.Expense
import ir.nama.launcher.data.Habit
import ir.nama.launcher.data.Pomodoro
import ir.nama.launcher.data.Todo
import ir.nama.launcher.data.WidgetInstance
import ir.nama.launcher.num
import ir.nama.launcher.system.Actions
import ir.nama.launcher.system.Schedules
import ir.nama.launcher.tr
import ir.nama.launcher.ui.common.JalaliDateDialog
import ir.nama.launcher.ui.common.TextInputDialog
import ir.nama.launcher.ui.home.HomeController
import ir.nama.launcher.ui.home.HomeEnv
import ir.nama.launcher.ui.theme.LocalNamaStyle
import ir.nama.launcher.ui.theme.Vazir
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private fun uid() = UUID.randomUUID().toString().take(12)

@Composable
fun BillsWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val personal by Nama.store.personal.flow.collectAsState()
    var adding by remember { mutableStateOf(false) }
    val today = rememberNow().toLocalDate().toEpochDay()
    val unpaid = personal.bills.filter { it.paidEpochDay == null || it.paidEpochDay < it.dueEpochDay }.sortedBy { it.dueEpochDay }
    WidgetFrame(ctrl, pageIndex, w, title = tr("💳 قسط و قبض", "💳 Bills")) {
        if (unpaid.isEmpty()) Text(tr("همه پرداخت شده ✓", "All paid ✓"), color = s.success, fontSize = 13.sp)
        unpaid.take(4).forEach { b ->
            val days = b.dueEpochDay - today
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "○", color = s.accent, fontSize = 16.sp,
                    modifier = Modifier.clip(CircleShape).clickable {
                        Nama.store.personal.update { p ->
                            p.copy(bills = p.bills.map {
                                if (it.id != b.id) it
                                else if (it.monthly) {
                                    val next = Jalali.addMonths(JalaliDate.from(LocalDate.ofEpochDay(it.dueEpochDay)), 1).toLocalDate().toEpochDay()
                                    it.copy(paidEpochDay = it.dueEpochDay, dueEpochDay = next)
                                } else it.copy(paidEpochDay = it.dueEpochDay)
                            })
                        }
                    }.padding(horizontal = 4.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(b.title, color = s.text, fontSize = 13.sp, maxLines = 1)
                    if (b.amountToman > 0) Text(num(PersianText.group(b.amountToman, false)) + tr(" تومان", " toman"), color = s.subText, fontSize = 11.sp)
                }
                Text(
                    when { days < 0 -> tr("${num(-days)} روز گذشته", "${-days}d late"); days == 0L -> tr("امروز", "today"); else -> num(days) + tr(" روز", "d") },
                    color = if (days <= 1) s.danger else s.subText, fontSize = 12.sp
                )
            }
        }
        Text(tr("＋ افزودن قسط یا قبض", "＋ Add bill"), color = s.accent, fontSize = 12.sp, modifier = Modifier.clickable { adding = true }.padding(top = 4.dp))
    }
    if (adding) BillDialog { adding = false }
}

@Composable
private fun BillDialog(onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var due by remember { mutableStateOf(LocalDate.now().plusDays(7)) }
    var monthly by remember { mutableStateOf(true) }
    var pick by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("قسط / قبض", "Bill")) },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text(tr("عنوان (مثلاً قسط وام)", "Title")) }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text(tr("مبلغ (تومان)", "Amount (toman)")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                TextButton({ pick = true }) { Text(tr("سررسید: ", "Due: ") + jalaliLong(due)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("ماهانه تکرار شود", "Repeats monthly"), Modifier.weight(1f))
                    Switch(monthly, { monthly = it })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                val b = Bill(uid(), title.trim(), PersianText.parseNumber(amount)?.toLong() ?: 0L, due.toEpochDay(), monthly)
                Nama.store.personal.update { it.copy(bills = it.bills + b) }
                onDismiss()
            }) { Text(tr("ذخیره", "Save")) }
        },
        dismissButton = { TextButton(onDismiss) { Text(tr("انصراف", "Cancel")) } }
    )
    if (pick) JalaliDateDialog(due, tr("سررسید", "Due date"), { pick = false }) { due = it }
}

@Composable
fun BankCardsWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val cards by Nama.cards.cards.collectAsState()
    var revealedUntil by remember { mutableLongStateOf(0L) }
    var adding by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val revealed = revealedUntil > now
    LaunchedEffect(revealedUntil) { if (revealedUntil > 0) { delay(30_000); revealedUntil = 0 } }
    WidgetFrame(ctrl, pageIndex, w, title = tr("🏦 کارت‌های بانکی", "🏦 Bank cards"), trailing = if (revealed) tr("نمایش کامل", "shown") else null) {
        if (cards.isEmpty()) Text(tr("کارت‌ها فقط روی همین گوشی و رمزنگاری‌شده ذخیره می‌شوند.", "Cards are stored encrypted on this phone only."), color = s.subText, fontSize = 12.sp)
        cards.forEach { c ->
            Column(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(s.accent.copy(alpha = 0.10f))
                    .combinedClickable(
                        onClick = {
                            ctrl.authenticate(tr("کپی شماره کارت", "Copy card number")) {
                                Actions.copy(ctx, BankCards.digitsOnly(c.number), sensitive = true)
                            }
                        },
                        onLongClick = {
                            ctrl.authenticate(tr("حذف کارت", "Delete card")) { Nama.cards.set(cards.filterNot { it.id == c.id }) }
                        }
                    ).padding(10.dp)
            ) {
                Row {
                    Text((BankCards.bankName(c.number) ?: tr("کارت", "Card")) + (if (c.owner.isNotBlank()) " · ${c.owner}" else ""), color = s.subText, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    if (c.sheba.isNotBlank()) Text(
                        tr("کپی شبا", "Copy IBAN"), color = s.accent, fontSize = 11.sp,
                        modifier = Modifier.clickable { ctrl.authenticate(tr("کپی شبا", "Copy IBAN")) { Actions.copy(ctx, BankCards.normalizeSheba(c.sheba), sensitive = true) } }
                    )
                }
                Text(
                    if (revealed) BankCards.format(c.number, Nama.faDigits) else BankCards.mask(c.number, Nama.faDigits),
                    color = s.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp
                )
            }
        }
        Row {
            Text(tr("＋ افزودن کارت", "＋ Add card"), color = s.accent, fontSize = 12.sp, modifier = Modifier.clickable { adding = true }.padding(top = 4.dp, end = 16.dp))
            if (cards.isNotEmpty() && !revealed) Text(
                tr("نمایش کامل", "Show numbers"), color = s.subText, fontSize = 12.sp,
                modifier = Modifier.clickable { ctrl.authenticate(tr("نمایش کارت‌ها", "Show cards")) { revealedUntil = System.currentTimeMillis() + 30_000 } }.padding(top = 4.dp)
            )
        }
        if (cards.isNotEmpty()) Text(tr("لمس: کپی شماره · لمس طولانی: حذف", "Tap: copy · Long press: delete"), color = s.subText, fontSize = 10.sp)
    }
    if (adding) CardDialog(onDismiss = { adding = false }) { c -> Nama.cards.set(cards + c) }
}

@Composable
private fun CardDialog(onDismiss: () -> Unit, onSave: (BankCard) -> Unit) {
    var number by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var sheba by remember { mutableStateOf("") }
    val digits = BankCards.digitsOnly(number)
    val valid = BankCards.isValidLuhn(digits)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("کارت بانکی", "Bank card")) },
        text = {
            Column {
                OutlinedTextField(
                    number, { number = it }, label = { Text(tr("شماره کارت ۱۶ رقمی", "16-digit card number")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text(
                            when {
                                digits.length < 16 -> num(digits.length) + "/۱۶"
                                valid -> "✓ " + (BankCards.bankName(digits) ?: tr("معتبر", "valid"))
                                else -> tr("شماره کارت درست نیست", "Invalid card number")
                            }
                        )
                    }
                )
                OutlinedTextField(owner, { owner = it }, label = { Text(tr("نام صاحب کارت (اختیاری)", "Owner (optional)")) }, singleLine = true)
                OutlinedTextField(
                    sheba, { sheba = it }, label = { Text(tr("شبا (اختیاری، بدون IR)", "IBAN (optional)")) }, singleLine = true,
                    supportingText = { if (sheba.isNotBlank()) Text(if (BankCards.isValidSheba(sheba)) "✓" else tr("شبا درست نیست", "Invalid IBAN")) }
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(BankCard(uid(), digits, owner.trim(), if (sheba.isNotBlank()) BankCards.normalizeSheba(sheba) else ""))
                onDismiss()
            }) { Text(tr("ذخیره", "Save")) }
        },
        dismissButton = { TextButton(onDismiss) { Text(tr("انصراف", "Cancel")) } }
    )
}

@Composable
fun ExpensesWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val personal by Nama.store.personal.flow.collectAsState()
    var adding by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val startDay = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val tj = JalaliDate.from(today)
    val startMonth = JalaliDate(tj.year, tj.month, 1).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    fun sum(from: Long) = personal.expenses.filter { it.at >= from }.sumOf { if (it.income) -it.amountToman else it.amountToman }
    WidgetFrame(ctrl, pageIndex, w, title = tr("💰 دخل‌وخرج", "💰 Spending"), onClick = { adding = true }) {
        Text(PersianText.shortToman(sum(startDay).coerceAtLeast(0), Nama.faDigits), color = s.text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text(tr("خرج امروز (تومان)", "Spent today (toman)"), color = s.subText, fontSize = 11.sp)
        Text(tr("این ماه: ", "This month: ") + PersianText.shortToman(sum(startMonth).coerceAtLeast(0), Nama.faDigits), color = s.subText, fontSize = 12.sp)
        Text(tr("＋ ثبت", "＋ Add"), color = s.accent, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
    if (adding) {
        var amount by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var income by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(if (income) tr("درآمد", "Income") else tr("خرج", "Expense")) },
            text = {
                Column {
                    OutlinedTextField(amount, { amount = it }, label = { Text(tr("مبلغ (تومان)", "Amount (toman)")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(note, { note = it }, label = { Text(tr("بابت (اختیاری)", "Note (optional)")) }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("درآمد است", "This is income"), Modifier.weight(1f))
                        Switch(income, { income = it })
                    }
                    personal.expenses.takeLast(3).reversed().forEach { e ->
                        Text((if (e.income) "+ " else "- ") + num(PersianText.group(e.amountToman, false)) + " " + e.note, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton({
                    val v = PersianText.parseNumber(amount)?.toLong() ?: 0L
                    if (v > 0) Nama.store.personal.update { it.copy(expenses = (it.expenses + Expense(uid(), v, note.trim(), System.currentTimeMillis(), income)).takeLast(2000)) }
                    adding = false
                }) { Text(tr("ثبت", "Save")) }
            },
            dismissButton = { TextButton({ adding = false }) { Text(tr("انصراف", "Cancel")) } }
        )
    }
}

@Composable
fun TodoWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int, shopping: Boolean) {
    val s = LocalNamaStyle.current
    val personal by Nama.store.personal.flow.collectAsState()
    var text by remember { mutableStateOf("") }
    val list = if (shopping) personal.shopping else personal.todos
    fun save(f: (List<Todo>) -> List<Todo>) = Nama.store.personal.update { if (shopping) it.copy(shopping = f(it.shopping)) else it.copy(todos = f(it.todos)) }
    val open = list.filter { !it.done }
    val done = list.filter { it.done }
    WidgetFrame(
        ctrl, pageIndex, w,
        title = if (shopping) tr("🛒 لیست خرید", "🛒 Shopping list") else tr("✓ کارهای روز", "✓ To-do"),
        trailing = if (list.isNotEmpty()) num(done.size) + "/" + num(list.size) else null
    ) {
        (open + done.take(3)).forEach { t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (t.done) "●" else "○", color = s.accent, fontSize = 16.sp,
                    modifier = Modifier.clip(CircleShape).clickable { save { l -> l.map { if (it.id == t.id) it.copy(done = !it.done) else it } } }.padding(horizontal = 4.dp)
                )
                Text(
                    t.text + (t.dueEpochDay?.let { d -> if (d != LocalDate.now().toEpochDay()) " · " + jalaliLong(LocalDate.ofEpochDay(d)).substringBeforeLast(' ') else "" } ?: ""),
                    color = if (t.done) s.subText else s.text, fontSize = 14.sp,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f)
                )
                Text("✕", color = s.subText, fontSize = 12.sp, modifier = Modifier.clickable { save { l -> l.filterNot { it.id == t.id } } }.padding(4.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("＋ ", color = s.accent)
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) Text(if (shopping) tr("مثلاً نان، شیر…", "e.g. bread, milk…") else tr("کار تازه…", "New task…"), color = s.subText, fontSize = 14.sp)
                BasicTextField(
                    text, { text = it }, singleLine = true,
                    textStyle = TextStyle(color = s.text, fontSize = 14.sp, fontFamily = Vazir),
                    cursorBrush = SolidColor(s.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val v = text.trim()
                        if (v.isNotEmpty()) {
                            // Shopping lists accept several items separated by commas.
                            val parts = if (shopping) v.split(',', '،').map { it.trim() }.filter { it.isNotEmpty() } else listOf(v)
                            save { l -> l + parts.map { Todo(uid(), it, createdAt = System.currentTimeMillis()) } }
                            text = ""
                        }
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (done.isNotEmpty()) Text(tr("پاک کردن انجام‌شده‌ها", "Clear done"), color = s.subText, fontSize = 11.sp, modifier = Modifier.clickable { save { l -> l.filterNot { it.done } } }.padding(top = 4.dp))
    }
}

@Composable
fun NotesWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val initial = remember { Nama.store.personal.value.note }
    var text by remember { mutableStateOf(initial) }
    WidgetFrame(ctrl, pageIndex, w, title = tr("📝 یادداشت", "📝 Note")) {
        Box(Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
            if (text.isEmpty()) Text(tr("هر چیزی که باید یادت بماند…", "Anything to remember…"), color = s.subText, fontSize = 14.sp)
            BasicTextField(
                text, { v -> text = v; Nama.store.personal.update { it.copy(note = v) } },
                textStyle = TextStyle(color = s.text, fontSize = 14.sp, fontFamily = Vazir, lineHeight = 22.sp),
                cursorBrush = SolidColor(s.accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun HabitsWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
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
    WidgetFrame(ctrl, pageIndex, w, title = tr("🔥 عادت‌ها", "🔥 Habits")) {
        if (personal.habits.isEmpty()) Text(tr("مثلاً ورزش، کتاب خواندن، آب کافی", "e.g. exercise, reading, water"), color = s.subText, fontSize = 12.sp)
        personal.habits.forEach { h ->
            val doneToday = today in h.doneDays
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(if (doneToday) s.accent else s.cardBorder)
                        .clickable {
                            Nama.store.personal.update { p ->
                                p.copy(habits = p.habits.map { if (it.id == h.id) it.copy(doneDays = if (doneToday) it.doneDays - today else (it.doneDays + today).filter { d -> d > today - 400 }.toSet()) else it })
                            }
                        },
                    contentAlignment = Alignment.Center
                ) { if (doneToday) Text("✓", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp) }
                Spacer(Modifier.width(8.dp))
                Text(h.name, color = s.text, fontSize = 14.sp, modifier = Modifier.weight(1f).combinedClickable(onClick = {}, onLongClick = {
                    Nama.store.personal.update { p -> p.copy(habits = p.habits.filterNot { it.id == h.id }) }
                }))
                val st = streak(h)
                if (st > 0) Text(num(st) + tr(" روز 🔥", "d 🔥"), color = s.accent2, fontSize = 12.sp)
                // Last 7 days as dots.
                Row(Modifier.padding(start = 8.dp)) {
                    for (i in 6 downTo 0) {
                        Box(Modifier.padding(1.dp).size(5.dp).clip(CircleShape).background(if ((today - i) in h.doneDays) s.accent else s.cardBorder))
                    }
                }
            }
        }
        Text(tr("＋ عادت تازه", "＋ New habit"), color = s.accent, fontSize = 12.sp, modifier = Modifier.clickable { adding = true }.padding(top = 4.dp))
    }
    if (adding) TextInputDialog(tr("نام عادت", "Habit name"), onDismiss = { adding = false }) { n ->
        if (n.isNotBlank()) Nama.store.personal.update { it.copy(habits = it.habits + Habit(uid(), n)) }
    }
}

@Composable
fun PomodoroWidget(ctrl: HomeController, env: HomeEnv, w: WidgetInstance, pageIndex: Int) {
    val s = LocalNamaStyle.current
    val ctx = LocalContext.current
    val personal by Nama.store.personal.flow.collectAsState()
    val p = personal.pomodoro
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(p.running, p.endsAt) {
        while (p.running) { now = System.currentTimeMillis(); delay(1000) }
    }
    val left = ((p.endsAt - now) / 1000).coerceAtLeast(0)
    val todayDone = if (p.day == LocalDate.now().toEpochDay()) p.doneToday else 0
    WidgetFrame(ctrl, pageIndex, w, title = "🍅 " + tr("پومودورو", "Pomodoro"), trailing = num(todayDone) + tr(" امروز", " today")) {
        Text(
            if (p.running) num("${left / 60}:${(left % 60).toString().padStart(2, '0')}") else num("${p.workMinutes}:00"),
            color = if (p.onBreak) s.success else s.text, fontSize = 30.sp, fontWeight = FontWeight.Light
        )
        Text(if (!p.running) tr("آماده", "Ready") else if (p.onBreak) tr("استراحت", "Break") else tr("تمرکز", "Focus"), color = s.subText, fontSize = 12.sp)
        Text(
            if (p.running) tr("توقف", "Stop") else tr("شروع", "Start"), color = s.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                if (p.running) {
                    Schedules.cancelPomodoro(ctx)
                    Nama.store.personal.update { it.copy(pomodoro = it.pomodoro.copy(running = false, onBreak = false, endsAt = 0L)) }
                } else {
                    if (Build.VERSION.SDK_INT >= 33) ctrl.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    val ends = System.currentTimeMillis() + p.workMinutes * 60_000L
                    val today = LocalDate.now().toEpochDay()
                    Nama.store.personal.update {
                        it.copy(pomodoro = Pomodoro(true, false, ends, p.workMinutes, p.breakMinutes, if (p.day == today) p.doneToday else 0, today))
                    }
                    Nama.store.personal.flushNow()
                    Schedules.schedulePomodoro(ctx, ends)
                }
            }.padding(vertical = 6.dp, horizontal = 4.dp)
        )
    }
}
