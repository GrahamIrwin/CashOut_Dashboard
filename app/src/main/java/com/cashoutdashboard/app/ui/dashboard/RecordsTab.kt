package com.cashoutdashboard.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WorkHistory
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cashoutdashboard.app.stats.Bucket
import com.cashoutdashboard.app.stats.DateRange
import com.cashoutdashboard.app.stats.Slice
import com.cashoutdashboard.app.stats.Stats
import com.cashoutdashboard.app.ui.ProportionBar
import com.cashoutdashboard.app.ui.hrs
import com.cashoutdashboard.app.ui.money
import com.cashoutdashboard.app.ui.moneyOrDash
import com.cashoutdashboard.app.ui.moneyShort
import com.cashoutdashboard.app.ui.pct
import com.cashoutdashboard.app.ui.short
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal fun LazyListScope.recordsTab(d: DashData, today: LocalDate, onOpenShift: (String) -> Unit) {
    item(key = "ytd") { YearToDateCard(d, today) }
    if (d.shifts.isEmpty()) {
        item(key = "empty") { EmptyNote(Icons.Filled.EmojiEvents, "No shifts in this period", "Your personal bests for the period will show here.") }
        return
    }
    item(key = "bests") { BestsCard(d, onOpenShift) }
    val mix = Stats.paymentMix(d.shifts)
    if (mix.isNotEmpty()) item(key = "mix") { PaymentMixCard(mix) }
    if (d.shifts.map { it.localDate.withDayOfMonth(1) }.distinct().size > 1) item(key = "months") { MonthlyCard(d) }
}

@Composable
private fun YearToDateCard(d: DashData, today: LocalDate) {
    val ytd = remember(d.all, d.takeHome, today) {
        Stats.summary(Stats.filter(d.all, DateRange(today.withDayOfYear(1), today)), d.takeHome)
    }
    DashCard("${today.year} so far", "Year to date — handy at tax time") {
        if (ytd.shifts == 0) {
            Text("No shifts logged this year yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@DashCard
        }
        Text(ytd.tips.money(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "tips over ${plural(ytd.shifts, "shift")}" + (ytd.hours.takeIf { it > 0 }?.let { " · ${it.hrs()}" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("Sales", ytd.sales.moneyShort(), Modifier.weight(1f))
            MiniStat("Tip %", ytd.tipPct.pct(), Modifier.weight(1f))
            MiniStat("Per hour", ytd.tipsPerHour.moneyOrDash(), Modifier.weight(1f))
        }
        ytd.takeHome?.let {
            Text(
                "Cash take-home recorded: ${it.money()} (${ytd.takeHomeShifts} shifts)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Text(
            "Export every shift as a spreadsheet from Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

private fun weekLabel(b: Bucket): String {
    val end = b.start.plusDays(6)
    val f = DateTimeFormatter.ofPattern("MMM d")
    return "Week of ${b.start.format(f)} – ${end.format(if (end.month == b.start.month) DateTimeFormatter.ofPattern("d") else f)}"
}

@Composable
private fun BestsCard(d: DashData, onOpenShift: (String) -> Unit) {
    val r = remember(d.shifts, d.takeHome) { Stats.records(d.shifts, d.takeHome) }
    val th = d.takeHome
    DashCard("Personal bests", "In this period — tap one to see the shift") {
        r.bestShift?.let { InfoRow(Icons.Filled.EmojiEvents, "Biggest tip night", it.effectiveTips(th).money(), it.localDate.short()) { onOpenShift(it.id) } }
        r.bestPct?.let { InfoRow(Icons.Filled.Percent, "Best tip %", it.tipPercent(th).pct(), "${it.localDate.short()} · ${it.sales.moneyShort()} sales") { onOpenShift(it.id) } }
        r.bestPerHour?.let {
            InfoRow(Icons.Filled.Timer, "Best hourly rate", "${(it.effectiveTips(th) / it.hours!!).money()}/hr", "${it.localDate.short()} · ${it.hours.hrs()}") { onOpenShift(it.id) }
        }
        r.biggestSales?.let { InfoRow(Icons.Filled.PointOfSale, "Biggest sales night", it.sales.money(), it.localDate.short()) { onOpenShift(it.id) } }
        r.mostCovers?.let { InfoRow(Icons.Filled.Groups, "Most guests", "${it.covers} guests", it.localDate.short()) { onOpenShift(it.id) } }
        r.bestWeek?.takeIf { d.shifts.size > 1 }?.let { InfoRow(Icons.Filled.DateRange, "Best week", it.tips.money(), "${weekLabel(it)} · ${plural(it.shifts, "shift")}") }
        r.bestMonth?.let {
            InfoRow(Icons.Filled.CalendarMonth, "Best month", it.tips.money(), "${it.start.format(DateTimeFormatter.ofPattern("MMMM yyyy"))} · ${plural(it.shifts, "shift")}")
        }
        r.mostShiftsWeek?.takeIf { it.shifts > 1 }?.let { InfoRow(Icons.Filled.WorkHistory, "Busiest week", plural(it.shifts, "shift"), weekLabel(it)) }
        if (r.longestStreak > 1) {
            InfoRow(Icons.Filled.LocalFireDepartment, "Longest streak", "${r.longestStreak} days in a row")
        }
    }
}

@Composable
private fun PaymentMixCard(mix: List<Slice>) {
    val total = mix.sumOf { it.amount }
    DashCard("How guests paid", "Share of payments by type") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            mix.forEach { s ->
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        Text(s.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            "${s.amount.moneyShort()} · ${(s.amount / total * 100).pct(0)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    ProportionBar((s.amount / total).toFloat())
                }
            }
        }
    }
}

@Composable
private fun MonthlyCard(d: DashData) {
    val months = remember(d.shifts, d.takeHome) { Stats.byMonth(d.shifts, d.takeHome) }
    DashCard("Month by month") {
        val weights = listOf(1.2f, 0.8f, 1.3f, 1.3f, 0.9f)
        TableRow(listOf("Month", "Shifts", "Tips", "Per shift", "Tip %"), weights, header = true)
        HorizontalDivider()
        val best = months.maxByOrNull { it.tips }
        months.forEach { m ->
            TableRow(
                listOf(m.label, m.shifts.toString(), wholeDollars(m.tips), (m.tips / m.shifts).money(), m.tipPct.pct()),
                weights,
                emphasize = m == best && months.size > 1,
            )
        }
    }
}
