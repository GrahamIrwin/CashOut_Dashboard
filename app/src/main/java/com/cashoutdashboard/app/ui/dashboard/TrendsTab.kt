package com.cashoutdashboard.app.ui.dashboard

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.ExperimentalMaterial3Api
import com.cashoutdashboard.app.ui.SoftChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cashoutdashboard.app.stats.Metric
import com.cashoutdashboard.app.stats.Stats
import com.cashoutdashboard.app.ui.ChartLegend
import com.cashoutdashboard.app.ui.ColumnChart
import com.cashoutdashboard.app.ui.ScatterChart
import com.cashoutdashboard.app.ui.money
import com.cashoutdashboard.app.ui.pct
import com.cashoutdashboard.app.ui.short

internal fun LazyListScope.trendsTab(d: DashData, onOpenShift: (String) -> Unit) {
    if (d.shifts.isEmpty()) {
        item(key = "empty") { EmptyNote(Icons.AutoMirrored.Filled.ShowChart, "No shifts in this period", "Trends appear once you've logged a few shifts.") }
        return
    }
    item(key = "over-time") { OverTimeCard(d) }
    item(key = "histogram") { HistogramCard(d) }
    item(key = "scatter") { ScatterCard(d, onOpenShift) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverTimeCard(d: DashData) {
    var metricName by rememberSaveable { mutableStateOf(Metric.TIPS.name) }
    var grainName by rememberSaveable { mutableStateOf(GrainChoice.AUTO.name) }
    val metric = Metric.valueOf(metricName)
    val choice = GrainChoice.valueOf(grainName)
    val grain = choice.resolve(d.shifts)
    val buckets = remember(d.shifts, d.takeHome, grain) { Stats.buckets(d.shifts, grain, d.takeHome) }
    val unit = when (grain) {
        Stats.Grain.SHIFT -> "shift"
        Stats.Grain.WEEK -> "week"
        Stats.Grain.MONTH -> "month"
    }
    val window = when (grain) {
        Stats.Grain.SHIFT -> 5
        Stats.Grain.WEEK -> 4
        Stats.Grain.MONTH -> 3
    }
    DashCard("Over time", "${metric.label} per $unit") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Metric.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = m == metric,
                    onClick = { metricName = m.name },
                    shape = SegmentedButtonDefaults.itemShape(i, Metric.entries.size),
                    icon = {},
                ) { Text(m.label, maxLines = 1) }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Group by", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GrainChoice.entries.forEach { g ->
                SoftChip(selected = g == choice, onClick = { grainName = g.name }, label = { Text(g.label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Crossfade(targetState = Triple(metric, grain, buckets), label = "trend") { (m, gr, bs) ->
            var selected by remember(bs, m) { mutableStateOf<Int?>(null) }
            val raw = bs.map { m.of(it) }
            val avg = if (bs.size >= window + 1) Stats.movingAverage(raw, window) else null
            Column {
                ColumnChart(
                    values = raw.map { it ?: 0.0 },
                    labels = bs.map { it.label },
                    format = { m.format(it) },
                    axisFormat = { m.axis(it) },
                    detail = { i ->
                        val b = bs[i]
                        val extra = if (gr == Stats.Grain.SHIFT) "" else " · ${plural(b.shifts, "shift")}"
                        val main = raw[i]?.let { m.format(it) } ?: "no data"
                        "${b.label}: $main$extra" + (avg?.getOrNull(i)?.let { " · avg ${m.format(it)}" } ?: "")
                    },
                    selected = selected,
                    onSelect = { selected = it },
                    overlay = avg,
                    height = 200.dp,
                )
                if (avg != null) {
                    ChartLegend(
                        listOf("${m.label} per ${unitOf(gr)}" to false, "$window-${unitOf(gr)} average" to true),
                        Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

private fun unitOf(g: Stats.Grain) = when (g) {
    Stats.Grain.SHIFT -> "shift"
    Stats.Grain.WEEK -> "week"
    Stats.Grain.MONTH -> "month"
}

@Composable
private fun HistogramCard(d: DashData) {
    val bins = remember(d.shifts, d.takeHome) { Stats.tipPctHistogram(d.shifts, d.takeHome) }
    val pcts = remember(d.shifts, d.takeHome) { d.shifts.mapNotNull { it.tipPercent(d.takeHome) } }
    if (pcts.size < 2) return
    var selected by remember(bins) { mutableStateOf<Int?>(null) }
    val top = bins.withIndex().maxByOrNull { it.value.count }?.index
    DashCard("Tip % spread", "How many shifts landed in each tip range") {
        ColumnChart(
            values = bins.map { it.count.toDouble() },
            labels = bins.map { it.label },
            format = { plural(it.toInt(), "shift") },
            axisFormat = { if (it % 1.0 == 0.0) "%.0f".format(it) else "" },
            detail = { i -> "${bins[i].label}: ${plural(bins[i].count, "shift")} (${(bins[i].count * 100.0 / pcts.size).pct(0)})" },
            selected = selected,
            onSelect = { selected = it },
            highlight = top,
            height = 160.dp,
        )
        val median = Stats.percentile(pcts, 0.5)
        val q1 = Stats.percentile(pcts, 0.25)
        val q3 = Stats.percentile(pcts, 0.75)
        Text(
            "Typical tip rate ${median.pct()} · middle half ${q1.pct()}–${q3.pct()}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun ScatterCard(d: DashData, onOpenShift: (String) -> Unit) {
    val list = remember(d.shifts) { d.shifts.filter { it.sales > 0 } }
    if (list.size < 3) return
    val points = remember(list, d.takeHome) { list.map { it.sales to it.effectiveTips(d.takeHome) } }
    val fit = remember(points) { Stats.linearFit(points) }
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    DashCard("Sales vs. tips", "Each dot is a shift") {
        ScatterChart(
            points = points,
            fit = fit?.let { f -> { x: Double -> f.at(x) } },
            xFormat = { axisMoney(it) },
            yFormat = { axisMoney(it) },
            selected = selected,
            onSelect = { selected = it },
        )
        Text(
            "Sales →",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.End),
        )
        val sel = selected?.let { list.getOrNull(it) }
        if (sel != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${sel.localDate.short()} · ${sel.sales.money()} sales · ${sel.effectiveTips(d.takeHome).money()} tips",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onOpenShift(sel.id) }) { Text("Open") }
            }
        } else if (fit != null) {
            val strength = when {
                fit.r2 >= 0.6 -> "Tips track your sales closely."
                fit.r2 >= 0.3 -> "Sales explain part of your tips."
                else -> "Your tips vary a lot beyond sales alone."
            }
            if (fit.slope > 0) {
                ChartLegend(listOf("Shift" to false, "Trend line" to true), Modifier.padding(vertical = 6.dp))
                Text(
                    "Each extra \$100 in sales ≈ ${(fit.slope * 100).money()} more in tips. $strength",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(strength, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
