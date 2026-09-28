package com.cashoutdashboard.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cashoutdashboard.app.data.Payment
import com.cashoutdashboard.app.data.Shift
import com.cashoutdashboard.app.data.Transfer
import com.cashoutdashboard.app.ocr.Field
import com.cashoutdashboard.app.ocr.Issue
import com.cashoutdashboard.app.ocr.isBlank
import java.time.LocalTime

/**
 * Top-of-form summary of what needs fixing. Each arithmetic problem the slip can solve itself
 * gets a one-tap fix button.
 */
@Composable
fun IssuesCard(issues: List<Issue>, notes: List<String>, unread: List<Field>, onFix: (Issue) -> Unit) {
    if (issues.isEmpty() && notes.isEmpty() && unread.isEmpty()) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(androidx.compose.material.icons.Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "Everything adds up. Give it a quick look and save.",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        return
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    "Needs your attention",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            if (unread.isNotEmpty()) {
                val names = if (unread.size > 4) "${unread.size} fields" else unread.joinToString(", ") { it.label }
                Text(
                    "Couldn't read: $names. ${if (unread.size == 1) "It's" else "They're"} highlighted in red below — type ${if (unread.size == 1) "it" else "them"} in from the slip.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            notes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer) }
            issues.forEach { issue ->
                Column {
                    Text("• ${issue.message}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    issue.fix?.let { fix ->
                        AssistChip(
                            onClick = { onFix(issue) },
                            label = { Text(fix.label) },
                            leadingIcon = { Icon(Icons.Default.AutoFixHigh, null, Modifier.size(AssistChipDefaults.IconSize)) },
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Editable form for every field a cashout slip carries. Fields in [missing] that are still empty, and
 * fields named by an [issues] entry, are outlined in red so it's obvious what to type in.
 */
@Composable
fun ShiftEditor(
    shift: Shift,
    onChange: (Shift) -> Unit,
    modifier: Modifier = Modifier,
    missing: Set<Field> = emptySet(),
    issues: List<Issue> = emptyList(),
) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<String?>(null) }
    var more by remember { mutableStateOf(false) }
    val gap = Arrangement.spacedBy(12.dp)

    fun unread(f: Field) = f in missing && shift.isBlank(f)
    fun flagged(f: Field) = unread(f) || issues.any { f in it.fields }
    fun hint(f: Field) = if (unread(f)) "Couldn't read — enter from slip" else null

    Column(modifier, verticalArrangement = gap) {
        SectionCard("Shift") {
            Column(verticalArrangement = gap) {
                PickerField(
                    label = "Business day",
                    value = shift.localDate.pretty(),
                    icon = Icons.Default.CalendarMonth,
                    isError = Field.DATE in missing,
                    supportingText = if (Field.DATE in missing) "Couldn't read — check the date" else null,
                    modifier = Modifier.fillMaxWidth(),
                ) { pickDate = true }
                Row(horizontalArrangement = gap) {
                    PickerField("Clock in", shift.openTime?.clock() ?: "", Icons.Default.Schedule, flagged(Field.OPEN), hint(Field.OPEN), Modifier.weight(1f)) { pickTime = "open" }
                    PickerField("Clock out", shift.closeTime?.clock() ?: "", Icons.Default.Schedule, flagged(Field.CLOSE), hint(Field.CLOSE), Modifier.weight(1f)) { pickTime = "close" }
                }
                shift.hours?.let { Text("${it.hrs()} shift", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        SectionCard("Earnings") {
            Column(verticalArrangement = gap) {
                Row(horizontalArrangement = gap) {
                    NumberField(
                        "Sales", shift.sales, { onChange(shift.copy(sales = it ?: 0.0)) }, Modifier.weight(1f),
                        isError = flagged(Field.SALES), supportingText = hint(Field.SALES), blankWhenZero = true,
                    )
                    NumberField(
                        "Tips", shift.tips, { onChange(shift.copy(tips = it ?: 0.0)) }, Modifier.weight(1f),
                        isError = flagged(Field.TIPS), supportingText = hint(Field.TIPS), blankWhenZero = true,
                    )
                }
                Row(horizontalArrangement = gap, verticalAlignment = Alignment.CenterVertically) {
                    NumberField(
                        "Cash take-home", shift.takeHome, { onChange(shift.copy(takeHome = it)) }, Modifier.weight(1f),
                        supportingText = "Optional",
                    )
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        LabeledValue("Tip %", shift.tipPercent().pct())
                        shift.hours?.takeIf { shift.tips > 0 }?.let {
                            Text("${(shift.tips / it).money()}/hr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(
                    "Cash take-home is the cash you walked out with — usually the number written on the slip.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard("Sales breakdown") {
            Column(verticalArrangement = gap) {
                Row(horizontalArrangement = gap) {
                    NumberField("Food", shift.foodSales, { onChange(shift.copy(foodSales = it)) }, Modifier.weight(1.6f), isError = flagged(Field.FOOD), supportingText = hint(Field.FOOD))
                    NumberField("Items", shift.foodVolume?.toDouble(), { onChange(shift.copy(foodVolume = it?.toInt())) }, Modifier.weight(1f), integer = true)
                }
                Row(horizontalArrangement = gap) {
                    NumberField("Liquor/wine/beer", shift.lwbSales, { onChange(shift.copy(lwbSales = it)) }, Modifier.weight(1.6f), isError = flagged(Field.LWB), supportingText = hint(Field.LWB))
                    NumberField("Items", shift.lwbVolume?.toDouble(), { onChange(shift.copy(lwbVolume = it?.toInt())) }, Modifier.weight(1f), integer = true)
                }
            }
        }

        SectionCard("Guests") {
            Row(horizontalArrangement = gap) {
                NumberField("Covers", shift.covers?.toDouble(), { onChange(shift.copy(covers = it?.toInt())) }, Modifier.weight(1f), integer = true, isError = flagged(Field.COVERS), supportingText = hint(Field.COVERS)?.let { "Enter from slip" })
                NumberField("Checks", shift.checks?.toDouble(), { onChange(shift.copy(checks = it?.toInt())) }, Modifier.weight(1f), integer = true, isError = flagged(Field.CHECKS), supportingText = hint(Field.CHECKS)?.let { "Enter from slip" })
                NumberField("Avg check", shift.avgCheck, { onChange(shift.copy(avgCheck = it)) }, Modifier.weight(1.3f), isError = flagged(Field.AVG_CHECK), supportingText = hint(Field.AVG_CHECK)?.let { "Enter from slip" })
            }
        }

        SectionCard("Payments") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (unread(Field.PAYMENTS)) {
                    Text("No payment lines were read. Add them from the slip (optional).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                shift.payments.forEachIndexed { i, p ->
                    PaymentRow(
                        p,
                        onChange = { np -> onChange(shift.copy(payments = shift.payments.toMutableList().also { it[i] = np })) },
                        onRemove = { onChange(shift.copy(payments = shift.payments.toMutableList().also { it.removeAt(i) })) },
                        isError = issues.any { Field.PAYMENTS in it.fields },
                    )
                }
                TextButton(onClick = { onChange(shift.copy(payments = shift.payments + Payment("", 0.0, null))) }) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Text("Add payment line", Modifier.padding(start = 6.dp))
                }
                HorizontalDivider()
                Row(horizontalArrangement = gap) {
                    NumberField("Payment total", shift.paymentTotal, { onChange(shift.copy(paymentTotal = it)) }, Modifier.weight(1f), isError = flagged(Field.PAYMENT_TOTAL), supportingText = hint(Field.PAYMENT_TOTAL)?.let { "Enter from slip" })
                    NumberField("Net", shift.net, { onChange(shift.copy(net = it)) }, Modifier.weight(1f), isError = flagged(Field.NET), supportingText = hint(Field.NET)?.let { "Enter from slip" })
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable { more = !more }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("More details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Server, staff checks, reference #, transfers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (more) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (more) "Collapse" else "Expand")
            }
            AnimatedVisibility(more) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = gap) {
                    OutlinedTextField(
                        value = shift.serverName ?: "",
                        onValueChange = { onChange(shift.copy(serverName = it.ifBlank { null })) },
                        label = { Text("Server") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = gap) {
                        NumberField("Staff checks", shift.staffChecks?.toDouble(), { onChange(shift.copy(staffChecks = it?.toInt())) }, Modifier.weight(1f), integer = true)
                        NumberField("Reference #", shift.reference?.toDouble(), { onChange(shift.copy(reference = it?.toInt())) }, Modifier.weight(1f), integer = true)
                    }
                    TransferList("Transfers out", shift.transfersOut) { onChange(shift.copy(transfersOut = it)) }
                    TransferList("Transfers in", shift.transfersIn) { onChange(shift.copy(transfersIn = it)) }
                }
            }
        }

        OutlinedTextField(
            value = shift.notes ?: "",
            onValueChange = { onChange(shift.copy(notes = it.ifBlank { null })) },
            label = { Text("Notes") },
            placeholder = { Text("e.g. big party, patio closed, trained a new server") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
    }

    if (pickDate) DatePickDialog(shift.localDate, { pickDate = false }) { onChange(shift.copy(date = it.toString())) }
    pickTime?.let { which ->
        TimePickDialog(
            title = if (which == "open") "Clock in" else "Clock out",
            initial = if (which == "open") shift.openTime else shift.closeTime,
            fallback = if (which == "open") LocalTime.of(16, 0) else LocalTime.of(21, 0),
            onDismiss = { pickTime = null },
            onPick = { t -> onChange(if (which == "open") shift.copy(openTime = t) else shift.copy(closeTime = t)) },
        )
    }
}

@Composable
private fun PaymentRow(p: Payment, onChange: (Payment) -> Unit, onRemove: () -> Unit, isError: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = p.type,
            onValueChange = { onChange(p.copy(type = it.uppercase())) },
            label = { Text("Type") },
            placeholder = { Text("VISA") },
            singleLine = true,
            modifier = Modifier.weight(1.2f),
        )
        NumberField("Amount", p.amount, { onChange(p.copy(amount = it ?: 0.0)) }, Modifier.weight(1.3f), isError = isError)
        NumberField("#", p.count?.toDouble(), { onChange(p.copy(count = it?.toInt())) }, Modifier.width(64.dp), integer = true)
        IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Remove payment line") }
    }
}

@Composable
private fun TransferList(title: String, items: List<Transfer>, onChange: (List<Transfer>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        items.forEachIndexed { i, t ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = t.name,
                    onValueChange = { v -> onChange(items.toMutableList().also { it[i] = t.copy(name = v) }) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f),
                )
                NumberField("Amount", t.amount, { v -> onChange(items.toMutableList().also { it[i] = t.copy(amount = v ?: 0.0) }) }, Modifier.weight(1.2f), blankWhenZero = true)
                IconButton(onClick = { onChange(items.toMutableList().also { it.removeAt(i) }) }) { Icon(Icons.Default.Close, "Remove transfer") }
            }
        }
        TextButton(onClick = { onChange(items + Transfer("", 0.0)) }) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Text("Add", Modifier.padding(start = 6.dp))
        }
    }
}

/** Read-only field that opens a picker when tapped anywhere on it. */
@Composable
private fun PickerField(
    label: String,
    value: String,
    icon: ImageVector,
    isError: Boolean,
    supportingText: String?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Tap to set") },
            trailingIcon = { Icon(icon, null) },
            isError = isError,
            supportingText = supportingText?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Cover the field so a tap anywhere opens the picker instead of focusing a read-only box.
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(title: String, initial: String?, fallback: LocalTime, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val t = initial?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: fallback
    val state = rememberTimePickerState(initialHour = t.hour, initialMinute = t.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            TextButton(onClick = {
                onPick("%02d:%02d".format(state.hour, state.minute))
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state) },
    )
}
