package com.cashoutdashboard.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashoutdashboard.app.AppViewModel
import com.cashoutdashboard.app.ScanItem
import com.cashoutdashboard.app.ScanStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(vm: AppViewModel, onDone: () -> Unit) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = queue.firstOrNull { it.id == selectedId } ?: queue.firstOrNull()

    LaunchedEffect(queue.isEmpty()) { if (queue.isEmpty()) onDone() }
    if (selected == null) return

    val clean = queue.count { it.isClean }
    val processing = queue.count { it.status == ScanStatus.PROCESSING }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(if (queue.size == 1) "Review cashout" else "Review ${queue.size} cashouts") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (queue.size > 1) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    queue.forEachIndexed { i, item ->
                        SoftChip(
                            selected = item.id == selected.id,
                            onClick = { selectedId = item.id },
                            label = { Text(item.draft?.takeIf { item.status == ScanStatus.READY }?.localDate?.short() ?: "#${i + 1}") },
                            leadingIcon = { StatusIcon(item) },
                        )
                    }
                }
                if (clean > 1 || processing > 0) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (processing > 0) "Reading $processing more…" else "$clean scans passed every check",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (clean > 0) TextButton(onClick = { vm.toast("Saved ${vm.saveAllClean()} shifts") }) { Text("Save all $clean") }
                    }
                }
                val dups = queue.count { it.duplicate }
                if (dups > 0) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("$dups already saved before", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.toast("Removed ${vm.discardDuplicates()} duplicates") }) { Text("Discard duplicates") }
                    }
                }
            }
            key(selected.id, selected.status) { ItemEditor(vm, selected, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun StatusIcon(item: ScanItem) = when {
    item.status == ScanStatus.PROCESSING -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
    item.status == ScanStatus.FAILED -> Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
    !item.isClean -> Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
    else -> Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
}

@Composable
private fun ItemEditor(vm: AppViewModel, item: ScanItem, modifier: Modifier) {
    if (item.status == ScanStatus.PROCESSING) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Reading your cashout…")
            }
        }
        return
    }
    val draft = item.draft!!
    var viewPhoto by remember { mutableStateOf(false) }
    val issues = item.issues
    val dup = vm.repo.findDuplicate(draft)

    Column(modifier) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (item.status == ScanStatus.FAILED) {
                Text("Couldn't read this one: ${item.error}", color = MaterialTheme.colorScheme.error)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Try again, or enter the numbers yourself below.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { vm.retry(item.id) }) { Text("Retry") }
                }
            }
            if (item.photoPath != null) {
                PhotoThumb(item.photoPath, Modifier.fillMaxWidth().height(180.dp)) { viewPhoto = true }
                Text("Tap the photo to zoom and compare", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (dup != null) {
                Text(
                    "You already saved a shift like this on ${dup.localDate.short()}. Discard this one unless it's different.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            IssuesCard(issues, item.notes, item.unread) { issue -> issue.fix?.let { vm.updateDraft(item.id, it.apply(draft)) } }
            ShiftEditor(draft, { vm.updateDraft(item.id, it) }, missing = item.missing, issues = issues)
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { vm.discard(item.id) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Discard") }
                    Button(onClick = { vm.saveDraft(item.id, draft) }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save shift")
                    }
                }
            }
        }
    }
    if (viewPhoto && item.photoPath != null) PhotoViewer(item.photoPath) { viewPhoto = false }
}

