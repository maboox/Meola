package ir.nama.launcher.ui.widgets

import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.nama.core.BankCards
import ir.nama.core.Jalali
import ir.nama.core.PersianText
import ir.nama.core.UssdCode
import ir.nama.launcher.Nama
import ir.nama.launcher.data.BankCard
import ir.nama.launcher.data.Bill
import ir.nama.launcher.data.BlackoutSlot
import ir.nama.launcher.data.DataPackage
import ir.nama.launcher.data.Expense
import ir.nama.launcher.data.FavContact
import ir.nama.launcher.num
import ir.nama.launcher.tr
import ir.nama.launcher.ui.common.ChipsSelector
import ir.nama.launcher.ui.common.JalaliDateDialog
import ir.nama.launcher.ui.common.TimeDialog
import ir.nama.launcher.ui.common.minuteText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

fun uid() = UUID.randomUUID().toString().take(12)

@Composable
fun BlackoutDialog(onDismiss: () -> Unit) {
    var days by remember { mutableStateOf(setOf<Int>()) }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var start by remember { mutableStateOf(14 * 60) }
    var end by remember { mutableStateOf(16 * 60) }
    var pick by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("زمان خاموشی", "Power cut time")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("جدول خاموشی منطقه را از اپ یا سایت شرکت برق ببینید و اینجا ثبت کنید.", "Copy the schedule from your power company's app or site."), fontSize = 12.sp)
                Text(tr("روزهای هفته (تکرار هفتگی):", "Weekdays (weekly):"), fontSize = 13.sp)
                ChipsSelector((0..6).toList(), days, { Jalali.WEEKDAYS[it] }) { d -> days = if (d in days) days - d else days + d; date = null }
                TextButton({ pick = 3 }) { Text(date?.let { tr("یا فقط تاریخ: ", "Or only on: ") + jalaliLong(it) } ?: tr("یا فقط یک تاریخ مشخص…", "Or a single date…")) }
                Row {
                    TextButton({ pick = 1 }) { Text(tr("از ", "From ") + minuteText(start)) }
                    TextButton({ pick = 2 }) { Text(tr("تا ", "To ") + minuteText(end)) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = days.isNotEmpty() || date != null, onClick = {
                val slot = BlackoutSlot(UUID.randomUUID().toString(), start, end, if (date == null) days else emptySet(), date?.toEpochDay())
                Nama.store.personal.update { it.copy(blackouts = it.blackouts + slot) }
                onDismiss()
            }) { Text(tr("ثبت", "Save")) }
        },
        dismissButton = { TextButton(onDismiss) { Text(tr("انصراف", "Cancel")) } }
    )
    when (pick) {
        1 -> TimeDialog(start, tr("شروع", "Start"), { pick = 0 }) { start = it }
        2 -> TimeDialog(end, tr("پایان", "End"), { pick = 0 }) { end = it }
        3 -> JalaliDateDialog(LocalDate.now(), tr("تاریخ", "Date"), { pick = 0 }) { date = it; days = emptySet() }
    }
}


@Composable
fun BillDialog(onDismiss: () -> Unit) {
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
fun CardDialog(onDismiss: () -> Unit, onSave: (BankCard) -> Unit) {
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
fun ManualRateDialog(onDismiss: () -> Unit) {
    val s = Nama.settings
    var usd by remember { mutableStateOf(if (s.manualUsdRate > 0) s.manualUsdRate.toString() else "") }
    var gold by remember { mutableStateOf(if (s.manualGoldRate > 0) s.manualGoldRate.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("نرخ دستی (تومان)", "Manual rates (toman)")) },
        text = {
            Column {
                Text(tr("اگر قیمت آنلاین در دسترس نبود، این‌ها نمایش داده می‌شوند.", "Shown when online prices are unavailable."), fontSize = 13.sp)
                OutlinedTextField(usd, { usd = it }, label = { Text(tr("دلار", "USD")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(gold, { gold = it }, label = { Text(tr("هر گرم طلای ۱۸", "18k gold per gram")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            TextButton({
                Nama.store.settings.update {
                    it.copy(manualUsdRate = PersianText.parseNumber(usd)?.toLong() ?: 0L, manualGoldRate = PersianText.parseNumber(gold)?.toLong() ?: 0L)
                }
                onDismiss()
            }) { Text(tr("ذخیره", "Save")) }
        },
        dismissButton = { TextButton(onDismiss) { Text(tr("انصراف", "Cancel")) } }
    )
}

@Composable
fun DataPackageDialog(current: DataPackage?, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(current?.title ?: "") }
    var size by remember { mutableStateOf(current?.sizeMb?.let { (it / 1024).toString() } ?: "") }
    var days by remember { mutableStateOf("30") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("بسته اینترنت", "Data package")) },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text(tr("نام (اختیاری)", "Name (optional)")) }, singleLine = true)
                OutlinedTextField(size, { size = it }, label = { Text(tr("حجم (گیگ)", "Size (GB)")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(days, { days = it }, label = { Text(tr("مدت (روز، از امروز)", "Days from today")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            TextButton({
                val gb = PersianText.parseNumber(size) ?: 0.0
                val d = PersianText.parseNumber(days)?.toLong() ?: 30
                val start = LocalDate.now().toEpochDay()
                Nama.store.personal.update { it.copy(dataPackage = DataPackage(title, (gb * 1024).toLong(), start, start + d)) }
                onDismiss()
            }) { Text(tr("ذخیره", "Save")) }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton({ Nama.store.personal.update { it.copy(dataPackage = null) }; onDismiss() }) { Text(tr("حذف", "Remove")) }
                TextButton(onDismiss) { Text(tr("انصراف", "Cancel")) }
            }
        }
    )
}

@Composable
fun CustomUssdDialog(onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var op by remember { mutableStateOf(tr("دلخواه", "Custom")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("کد دلخواه", "Custom code")) },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text(tr("عنوان", "Title")) }, singleLine = true)
                OutlinedTextField(code, { code = it }, label = { Text(tr("کد (مثلاً *۱۴۰#)", "Code (e.g. *140#)")) }, singleLine = true)
                OutlinedTextField(op, { op = it }, label = { Text(tr("اپراتور", "Operator")) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton({
                if (title.isNotBlank() && code.isNotBlank()) Nama.store.personal.update { it.copy(customUssd = it.customUssd + UssdCode(op, title, PersianText.toEnDigits(code))) }
                onDismiss()
            }) { Text(tr("ذخیره", "Save")) }
        },
        dismissButton = { TextButton(onDismiss) { Text(tr("انصراف", "Cancel")) } }
    )
}

@Composable
fun ExpenseDialog(onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var income by remember { mutableStateOf(false) }
    val personal = Nama.store.personal.value
    AlertDialog(
        onDismissRequest = onDismiss,
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
                    Text((if (e.income) "+ " else "− ") + num(PersianText.group(e.amountToman, false)) + " " + e.note, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton({
                val v = PersianText.parseNumber(amount)?.toLong() ?: 0L
                if (v > 0) Nama.store.personal.update { it.copy(expenses = (it.expenses + Expense(uid(), v, note.trim(), System.currentTimeMillis(), income)).takeLast(2000)) }
                onDismiss()
            }) { Text(tr("ثبت", "Save")) }
        },
        dismissButton = { TextButton(onDismiss) { Text(tr("انصراف", "Cancel")) } }
    )
}

@Composable
fun ContactPickDialog(onDismiss: () -> Unit, onPick: (FavContact) -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val results by produceState(emptyList<FavContact>(), q) {
        value = withContext(Dispatchers.IO) { queryContacts(ctx, q) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("انتخاب مخاطب", "Pick a contact")) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text(tr("نام", "Name")) }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(results) { c ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(c); onDismiss() }.padding(vertical = 8.dp)) {
                            Text(c.name)
                            Text(num(c.phone), fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text(tr("بستن", "Close")) } }
    )
}

private fun queryContacts(ctx: android.content.Context, q: String): List<FavContact> = try {
    val uri = if (q.isBlank()) ContactsContract.CommonDataKinds.Phone.CONTENT_URI
    else Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(q))
    val out = mutableListOf<FavContact>()
    ctx.contentResolver.query(
        uri,
        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY),
        null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
    )?.use { c ->
        while (c.moveToNext() && out.size < 60) {
            val n = c.getString(0) ?: continue
            val p = c.getString(1) ?: continue
            if (out.none { it.phone == p }) out += FavContact(n, p, c.getString(2) ?: "")
        }
    }
    out
} catch (e: Exception) {
    emptyList()
}
