package com.cashoutdashboard.app.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import com.cashoutdashboard.app.ui.SoftChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cashoutdashboard.app.stats.DayMetric
import com.cashoutdashboard.app.stats.Stats
import com.cashoutdashboard.app.ui.CalendarMonth
import com.cashoutdashboard.app.ui.ColumnChart
import com.cashoutdashboard.app.ui.HeatLegend
import com.cashoutdashboard.app.ui.money
import com.cashoutdashboard.app.ui.moneyOrDash
import com.cashoutdashboard.app.ui.moneyShort
import com.cashoutdashboard.app.ui.pct
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal fun LazyListScope.daysTab(d: DashData, onOpenShift: (String) -> Unit) {
    if (d.shifts.isEmpty()) {
        item(key = "empty") { EmptyNote(Icons.Filled.CalendarMonth, "No shifts in this period", "Your best days and a calendar of earnings will show here.") }
        return
    }
    item(key = "weekday") { WeekdayCard(d) }
    item(key = "calendar") { CalendarCard(d, onOpenShift) }
}

private fun DayMetric.format(v: Double?): String = when (this) {
    DayMetric.AVG_TIPS, DayMetric.AVG_SALES -> v.moneyOrDash()
    DayMetric.TIP_PCT -> v.pct()
    DayMetric.PER_HOUR -> v?.let { it.money() + "/hr" } ?: "—"
    DayMetric.SHIFTS -> v?.let { plural(it.toInt(), "shift") } ?: "0 shifts"
}

private fun DayMetric.axis(v: Double): String = when (this) {
    DayMetric.TIP_PCT -> "%.0f%%".format(v)
    DayMetric.SHIFTS -> if (v % 1.0 == 0.0) "%.0f".format(v) else ""
    else -> axisMoney(v)
}

@Composable
private fun WeekdayCard(d: DashData) {
    var metricName by rememberSaveable { mutableStateOf(DayMetric.AVG_TIPS.name) }
    val metric = DayMetric.valueOf(metricName)
    val days = remember(d.shifts, d.takeHome) { Stats.byDayOfWeek(d.shifts, d.takeHome) }
    DashCard("By day of week", "Which days pay best") {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DayMetric.entries.forEach { m ->
                SoftChip(selected = m == metric, onClick = { metricName = m.name }, label = { Text(m.label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        AnimatedContent(metric, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "weekday") { m ->
            var selected by remember(days, m) { mutableStateOf<Int?>(null) }
            val values = days.map { m.of(it) ?: 0.0 }
            val best = days.withIndex().filter { it.value.shifts > 0 && m.of(it.value) != null }.maxByOrNull { m.of(it.value)!! }?.index
            ColumnChart(
                values = values,
                labels = days.map { it.shortName },
                format = { m.format(it) },
                axisFormat = { m.axis(it) },
                detail = { i ->
                    val day = days[i]
                    val name = day.day.getDisplayName(TextStyle.FULL, Locale.getDefault())
                    if (day.shifts == 0) "$name: no shifts" else "$name: ${m.format(m.of(day))} · ${plural(day.shifts, "shift")}"
                },
                selected = selected,
                onSelect = { selected = it },
                highlight = best,
                height = 170.dp,
                placeholder = best?.let { "Best: ${days[it].day.getDisplayName(TextStyle.FULL, Locale.getDefault())} · tap a bar for details" } ?: "Tap a bar for details",
            )
        }
        Spacer(Modifier.height(12.dp))
        val weights = listOf(1.1f, 0.9f, 1.4f, 1f, 1.1f)
        TableRow(listOf("Day", "Shifts", "Avg tips", "Tip %", "$/hr"), weights, header = true)
        HorizontalDivider()
        val bestTips = days.withIndex().filter { it.value.shifts > 0 }.maxByOrNull { it.value.avgTips ?: 0.0 }?.index
        days.forEachIndexed { i, day ->
            TableRow(
                listOf(
                    day.shortName + if (i == bestTips) " ★" else "",
                    day.shifts.toString(),
                    day.avgTips.moneyOrDash(),
                    day.tipPct.pct(),
                    day.tipsPerHour?.let { "$" + "%.0f".format(it) } ?: "—",
                ),
                weights,
                emphasize = i == bestTips,
            )
        }
        if (bestTips != null) {
            Text(
                "★ Highest average tips",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun CalendarCard(d: DashData, onOpenShift: (String) -> Unit) {
    val daily = remember(d.shifts, d.takeHome) {
        d.shifts.groupBy { it.localDate }.mapValues { (_, l) -> l.sumOf { it.effectiveTips(d.takeHome) } }
    }
    val months = remember(d.shifts) { d.shifts.map { YearMonth.from(it.localDate) }.distinct().sorted() }
    if (months.isEmpty()) return
    var index by rememberSaveable(months.size, months.last().toString()) { mutableStateOf(months.lastIndex) }
    val month = months[index.coerceIn(months.indices)]
    val maxDay = daily.values.maxOrNull() ?: 1.0
    val inMonth = d.shifts.filter { YearMonth.from(it.localDate) == month }
    DashCard("Calendar", "Stronger color means more tips") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { index-- }, enabled = index > 0) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { index++ }, enabled = index < months.lastIndex) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
        }
        AnimatedContent(month, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "month") { m ->
            CalendarMonth(
                month = m,
                values = daily.filterKeys { YearMonth.from(it) == m },
                maxValue = maxDay,
                onDayClick = { date -> d.shifts.firstOrNull { it.localDate == date }?.let { onOpenShift(it.id) } },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${plural(inMonth.size, "shift")} · ${inMonth.sumOf { it.effectiveTips(d.takeHome) }.moneyShort()}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            HeatLegend()
        }
        Text(
            "Tap a shaded day to open that shift.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
