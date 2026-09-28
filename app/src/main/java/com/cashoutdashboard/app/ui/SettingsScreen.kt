package com.cashoutdashboard.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashoutdashboard.app.AppViewModel
import com.cashoutdashboard.app.BuildConfig
import java.time.LocalDate

@Composable
fun SettingsScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val shifts by vm.shifts.collectAsStateWithLifecycle()
    var editGoal by remember { mutableStateOf(false) }
    var pickAnchor by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(vm::exportCsv) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportBackup) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importBackup) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ListGroup("Dashboard") {
            ListRow(
                Icons.Filled.Dashboard,
                "Simple dashboard",
                "Just your tips, your goal and how to earn more",
                onClick = { vm.updateSettings { it.copy(simpleDashboard = !it.simpleDashboard) } },
                trailing = { Switch(settings.simpleDashboard, { v -> vm.updateSettings { it.copy(simpleDashboard = v) } }) },
            )
            ListDivider()
            ListRow(
                Icons.Filled.Wallet,
                "Use cash take-home",
                "When a shift has one, count it instead of the POS tips",
                onClick = { vm.updateSettings { it.copy(useTakeHome = !it.useTakeHome) } },
                trailing = { Switch(settings.useTakeHome, { v -> vm.updateSettings { it.copy(useTakeHome = v) } }) },
            )
            ListDivider()
            ListRow(
                Icons.Filled.Flag,
                "Tip goal",
                settings.goalAmount?.let { "${settings.goalPeriod.label} · ${it.money()}" } ?: "Not set",
                onClick = { editGoal = true },
            )
            ListDivider()
            ListRow(
                Icons.Filled.EventRepeat,
                "Pay period start",
                settings.payPeriodAnchorDate?.let { "Every 2 weeks from ${it.pretty()}" } ?: "Not set",
                onClick = { pickAnchor = true },
            )
        }

        ListGroup("Your data · ${shifts.size} shifts") {
            ListRow(Icons.Filled.TableChart, "Export spreadsheet", "CSV for Excel or Google Sheets", onClick = { csv.launch("cashouts-$today.csv") })
            ListDivider()
            ListRow(Icons.Filled.Backup, "Back up", "Save a copy of every shift", onClick = { backup.launch("cashout-backup-$today.json") })
            ListDivider()
            ListRow(Icons.Filled.Restore, "Restore from backup", "Bring shifts back from a backup file", onClick = { restore.launch(arrayOf("application/json", "*/*")) })
        }

        ListGroup {
            ListRow(
                Icons.Filled.DeleteForever,
                "Delete all shifts",
                tint = MaterialTheme.colorScheme.error,
                titleColor = MaterialTheme.colorScheme.error,
                onClick = { confirmWipe = true },
                trailing = null,
            )
        }

        Text(
            "Cashout Dashboard ${BuildConfig.VERSION_NAME}\nNo internet access. Everything is read and stored on your phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
        )
    }

    if (editGoal) {
        GoalDialog(settings.goalAmount, settings.goalPeriod, onDismiss = { editGoal = false }) { amount, period ->
            vm.updateSettings { it.copy(goalAmount = amount, goalPeriod = period) }
        }
    }
    if (pickAnchor) {
        DatePickDialog(settings.payPeriodAnchorDate, { pickAnchor = false }) { d ->
            vm.updateSettings { it.copy(payPeriodAnchor = d.toString()) }
        }
    }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Delete all ${shifts.size} shifts?") },
            text = { Text("This can't be undone. Consider backing up first.") },
            confirmButton = { TextButton(onClick = { confirmWipe = false; vm.deleteAll() }) { Text("Delete all", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
        )
    }
}
