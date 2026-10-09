package ir.nama.launcher.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.nama.core.Jalali
import ir.nama.core.JalaliDate
import ir.nama.core.SearchScorer
import ir.nama.launcher.Nama
import ir.nama.launcher.data.AppEntry
import ir.nama.launcher.data.IconMode
import ir.nama.launcher.data.IconShape
import ir.nama.launcher.num
import ir.nama.launcher.tr
import ir.nama.launcher.ui.theme.LocalNamaStyle
import java.time.LocalDate

/** Icon rendering mode for the current screen (normal, themed or monochrome). */
val LocalIconMode = androidx.compose.runtime.staticCompositionLocalOf { IconMode.NORMAL }

private val grayscaleFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** App icon with the user's shape and icon pack; loads off the main thread and caches. */
@Composable
fun AppIconImage(
    entry: AppEntry,
    size: Dp,
    shape: IconShape,
    modifier: Modifier = Modifier,
    grayscale: Boolean = false,
    dim: Boolean = false,
    dot: Boolean = false,
    mode: IconMode = LocalIconMode.current
) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val pack = Nama.settings.iconPack
    val version by Nama.apps.packageEvents.collectAsState()
    val bmp by produceState(Nama.icons.peek(entry.key, shape, px, pack, mode), entry.key, shape, px, pack, version, mode) {
        value = Nama.icons.load(entry.key, shape, px, pack, mode)
    }
    val s = LocalNamaStyle.current
    Box(modifier.size(size)) {
        val b = bmp
        if (b != null) {
            Image(
                b, contentDescription = entry.label, modifier = Modifier.size(size),
                colorFilter = if (grayscale) grayscaleFilter else null,
                alpha = if (dim) 0.38f else 1f
            )
        } else {
            Box(
                Modifier.size(size).clip(CircleShape).background(s.accentContainer),
                contentAlignment = Alignment.Center
            ) { Text(entry.label.take(1), color = s.onAccentContainer, fontSize = (size.value / 2.6f).sp) }
        }
        if (dot) {
            Box(
                Modifier.align(Alignment.TopEnd).size(size / 4.5f).clip(CircleShape).background(s.accent)
                    .border(2.dp, Color.Black.copy(alpha = 0.18f), CircleShape)
            )
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("انصراف", "Cancel")) } }
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String = "",
    hint: String = "",
    numeric: Boolean = false,
    singleLine: Boolean = true,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = singleLine,
                placeholder = { if (hint.isNotEmpty()) Text(hint) },
                keyboardOptions = if (numeric) androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                else androidx.compose.foundation.text.KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onDone(text.trim()); onDismiss() }) { Text(tr("تأیید", "OK")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("انصراف", "Cancel")) } }
    )
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, format: (Int) -> String = { num(it) }, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { onChange(if (value + 1 > range.last) range.first else value + 1) }) { Text("▲") }
        Text(format(value), fontSize = 18.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.width(84.dp))
        TextButton(onClick = { onChange(if (value - 1 < range.first) range.last else value - 1) }) { Text("▼") }
    }
}

/** Jalali date picker made of three steppers. */
@Composable
fun JalaliDateDialog(initial: LocalDate, title: String, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val j0 = JalaliDate.from(initial)
    var y by remember { mutableStateOf(j0.year) }
    var m by remember { mutableStateOf(j0.month) }
    var d by remember { mutableStateOf(j0.day) }
    val maxDay = Jalali.monthLength(y, m)
    if (d > maxDay) d = maxDay
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stepper(tr("روز", "Day"), d, 1..maxDay) { d = it }
                Stepper(tr("ماه", "Month"), m, 1..12, { Jalali.MONTHS[it - 1] }) { m = it }
                Stepper(tr("سال", "Year"), y, (j0.year - 80)..(j0.year + 20)) { y = it }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(JalaliDate(y, m, d).toLocalDate()); onDismiss() }) { Text(tr("تأیید", "OK")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("انصراف", "Cancel")) } }
    )
}

/** Hour/minute picker. */
@Composable
fun TimeDialog(initialMinute: Int, title: String, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var h by remember { mutableStateOf(initialMinute / 60) }
    var mi by remember { mutableStateOf(initialMinute % 60) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stepper(tr("دقیقه", "Minute"), mi, 0..59, { num(it.toString().padStart(2, '0')) }) { mi = it }
                Stepper(tr("ساعت", "Hour"), h, 0..23, { num(it.toString().padStart(2, '0')) }) { h = it }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(h * 60 + mi); onDismiss() }) { Text(tr("تأیید", "OK")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("انصراف", "Cancel")) } }
    )
}

fun minuteText(m: Int): String = num("${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}")

/** Searchable multi-select list of apps. */
@Composable
fun AppPickerDialog(
    title: String,
    selected: Set<String>,
    single: Boolean = false,
    onDismiss: () -> Unit,
    onDone: (Set<String>) -> Unit
) {
    val apps by Nama.apps.apps.collectAsState()
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(selected) }
    val list = remember(apps, query) {
        if (query.isBlank()) apps else apps.map { it to SearchScorer.score(query, it.search) }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(tr("جستجو", "Search")) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.size(8.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(list, key = { it.key }) { e ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                chosen = if (single) setOf(e.key) else if (e.key in chosen) chosen - e.key else chosen + e.key
                                if (single) { onDone(chosen); onDismiss() }
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconImage(e, 36.dp, IconShape.SQUIRCLE, mode = IconMode.NORMAL)
                            Spacer(Modifier.width(10.dp))
                            Text(e.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!single) Checkbox(checked = e.key in chosen, onCheckedChange = null)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!single) TextButton(onClick = { onDone(chosen); onDismiss() }) { Text(tr("تأیید", "OK") + " (" + num(chosen.size) + ")") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("انصراف", "Cancel")) } }
    )
}

@Composable
fun <T> ChipsSelector(options: List<T>, selected: Set<T>, label: (T) -> String, onToggle: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { o -> FilterChip(selected = o in selected, onClick = { onToggle(o) }, label = { Text(label(o)) }) }
    }
}

@Composable
fun ChoiceDialog(title: String, options: List<String>, selectedIndex: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                items(options.size) { i ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(i); onDismiss() }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(selected = i == selectedIndex, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Text(options[i])
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("بستن", "Close")) } }
    )
}
