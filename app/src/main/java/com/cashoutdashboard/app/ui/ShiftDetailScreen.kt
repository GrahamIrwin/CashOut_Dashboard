package com.cashoutdashboard.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashoutdashboard.app.AppViewModel
import com.cashoutdashboard.app.data.Shift
import com.cashoutdashboard.app.ocr.Validation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftDetailScreen(vm: AppViewModel, id: String, onBack: () -> Unit) {
    val shifts by vm.shifts.collectAsStateWithLifecycle()
    val shift = shifts.firstOrNull { it.id == id }
    if (shift == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var editing by remember { mutableStateOf<Shift?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var viewPhoto by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(if (editing != null) "Edit shift" else shift.localDate.short()) },
                navigationIcon = {
                    IconButton(onClick = { if (editing != null) editing = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (editing != null) {
                        TextButton(onClick = { vm.saveShift(editing!!); editing = null }) { Text("Save") }
                    } else {
                        IconButton(onClick = { editing = shift }) { Icon(Icons.Default.Edit, "Edit") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val e = editing
            if (e != null) {
                if (shift.photoPath != null) {
                    PhotoThumb(shift.photoPath, Modifier.fillMaxWidth().height(160.dp)) { viewPhoto = true }
                }
                val issues = Validation.check(e)
                if (issues.isNotEmpty()) IssuesCard(issues, emptyList(), emptyList()) { issue -> issue.fix?.let { editing = it.apply(e) } }
                ShiftEditor(e, { editing = it }, issues = issues)
            } else {
                Summary(shift, onEdit = { editing = shift }, onPhoto = { viewPhoto = true })
            }
        }
    }

    if (viewPhoto && shift.photoPath != null) PhotoViewer(shift.photoPath) { viewPhoto = false }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this shift?") },
            text = { Text("${shift.localDate.pretty()} will be removed along with its photo.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteShift(shift.id); onBack() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Summary(s: Shift, onEdit: () -> Unit, onPhoto: () -> Unit) {
    val onHero = HeroColors.content
    Card(
        colors = CardDefaults.cardColors(containerColor = HeroColors.container),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(s.localDate.pretty(), style = MaterialTheme.typography.titleMedium, color = onHero)
            Text(
                "${s.openTime.clock()} – ${s.closeTime.clock()}" + (s.hours?.let { " · ${it.hrs()}" } ?: "") + (s.serverName?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = onHero.copy(alpha = 0.8f),
            )
            Row(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Tips", style = MaterialTheme.typography.labelLarge, color = onHero.copy(alpha = 0.8f))
                    Text(s.tips.money(), style = MaterialTheme.typography.headlineLarge, color = onHero)
                }
                Column(Modifier.weight(1f)) {
                    Text("Tip %", style = MaterialTheme.typography.labelLarge, color = onHero.copy(alpha = 0.8f))
                    Text(s.tipPercent().pct(), style = MaterialTheme.typography.headlineLarge, color = onHero)
                }
            }
            val chips = listOfNotNull(
                s.hours?.takeIf { s.tips > 0 }?.let { "${(s.tips / it).money()}/hr" },
                s.takeHome?.let { "${it.money()} take-home" },
                s.covers?.takeIf { it > 0 }?.let { "$it guests" },
            )
            if (chips.isNotEmpty()) {
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chips.forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = onHero,
                            modifier = Modifier
                                .background(onHero.copy(alpha = 0.14f), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
    if (s.takeHome == null) {
        FilledTonalButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add cash take-home")
        }
    }
    if (s.photoPath != null) {
        PhotoThumb(s.photoPath, Modifier.fillMaxWidth().height(160.dp), onClick = onPhoto)
    }
    SectionCard("Details") {
        val rows = listOfNotNull(
            "Sales" to s.sales.money(),
            s.takeHome?.let { "Cash take-home" to it.money() },
            s.hours?.let { h -> "Tips per hour" to (s.tips / h).money() },
            s.foodSales?.let { "Food" to it.money() + (s.foodVolume?.let { v -> "  ($v items)" } ?: "") },
            s.lwbSales?.let { "Liquor / wine / beer" to it.money() + (s.lwbVolume?.let { v -> "  ($v items)" } ?: "") },
            s.covers?.let { "Covers" to it.toString() },
            s.avgCheck?.let { "Avg check" to it.money() },
            s.covers?.takeIf { it > 0 }?.let { "Tips per cover" to (s.tips / it).money() },
            s.checks?.let { "Checks" to it.toString() },
            s.paymentTotal?.let { "Payment total" to it.money() },
            s.net?.let { "Net" to it.money() },
            s.reference?.let { "Reference #" to it.toString() },
        )
        rows.forEach { (k, v) -> DetailRow(k, v) }
    }
    if (s.payments.isNotEmpty()) {
        SectionCard("Payments") {
            s.payments.forEach { p -> DetailRow(p.type + (p.count?.let { " ×$it" } ?: ""), p.amount.money()) }
        }
    }
    if (s.transfersOut.isNotEmpty() || s.transfersIn.isNotEmpty()) {
        SectionCard("Transfers") {
            s.transfersOut.forEach { DetailRow("Out → ${it.name}", it.amount.money()) }
            s.transfersIn.forEach { DetailRow("In ← ${it.name}", it.amount.money()) }
        }
    }
    s.notes?.let { SectionCard("Notes") { Text(it) } }
}

@Composable
private fun DetailRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(k, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
