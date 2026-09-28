package com.cashoutdashboard.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashoutdashboard.app.AppViewModel
import com.cashoutdashboard.app.data.Shift
import com.cashoutdashboard.app.ui.dashboard.EmptyNote
import java.time.format.DateTimeFormatter

private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy")
private val dowFormat = DateTimeFormatter.ofPattern("EEE")
private val dayNameFormat = DateTimeFormatter.ofPattern("EEEE")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShiftsScreen(vm: AppViewModel, onOpen: (String) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val shifts by vm.shifts.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    if (shifts.isEmpty()) {
        Box(modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            EmptyNote(
                Icons.AutoMirrored.Filled.ReceiptLong,
                "No shifts yet",
                "Snap a photo of your cashout, or import a stack of old ones from your gallery.",
                action = {
                    Button(onClick = onAdd) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add cashout")
                    }
                },
            )
        }
        return
    }
    val months = remember(shifts) { shifts.groupBy { it.localDate.withDayOfMonth(1) } }
    val background = MaterialTheme.colorScheme.background
    val card = MaterialTheme.colorScheme.surfaceContainerLow
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
        months.forEach { (month, list) ->
            stickyHeader(key = "h$month") {
                val tips = list.sumOf { it.effectiveTips(settings.useTakeHome) }
                val sales = list.sumOf { it.sales }
                Row(
                    Modifier.fillMaxWidth().background(background).padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(month.format(monthFormat), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${list.size} shifts · ${tips.moneyShort()} · ${(if (sales > 0) tips / sales * 100 else null).pct()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                // Each month reads as one rounded card: only its first and last rows get corners.
                val top = if (i == 0) 20.dp else 0.dp
                val bottom = if (i == list.lastIndex) 20.dp else 0.dp
                Column(Modifier.clip(RoundedCornerShape(top, top, bottom, bottom)).background(card)) {
                    ShiftRow(s, settings.useTakeHome) { onOpen(s.id) }
                    if (i < list.lastIndex) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ShiftRow(s: Shift, useTakeHome: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            Modifier.size(44.dp).background(colors.secondaryContainer, RoundedCornerShape(12.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(s.localDate.format(dowFormat).uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.primary)
            Text(s.localDate.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (s.openTime == null && s.closeTime == null) s.localDate.format(dayNameFormat) else "${s.openTime.clock()} – ${s.closeTime.clock()}",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                "${s.sales.moneyShort()} sales" + (s.hours?.let { " · ${it.hrs()}" } ?: "") + (s.covers?.let { " · $it guests" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(s.effectiveTips(useTakeHome).money(), style = MaterialTheme.typography.titleMedium)
            s.tipPercent(useTakeHome)?.let {
                Text(
                    it.pct(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .background(colors.primaryContainer, RoundedCornerShape(50))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
    }
}
