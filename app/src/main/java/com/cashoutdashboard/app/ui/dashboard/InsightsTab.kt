package com.cashoutdashboard.app.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cashoutdashboard.app.stats.DayPart
import com.cashoutdashboard.app.stats.Insight
import com.cashoutdashboard.app.stats.InsightKind
import com.cashoutdashboard.app.stats.Kpi
import com.cashoutdashboard.app.stats.Stats
import com.cashoutdashboard.app.ui.ColumnChart
import com.cashoutdashboard.app.ui.Dumbbell
import com.cashoutdashboard.app.ui.HeatLegend
import com.cashoutdashboard.app.ui.IconBadge
import com.cashoutdashboard.app.ui.LegendMark
import com.cashoutdashboard.app.ui.LegendRow
import com.cashoutdashboard.app.ui.SoftChip
import com.cashoutdashboard.app.ui.StripPlot
import com.cashoutdashboard.app.ui.heatColor
import com.cashoutdashboard.app.ui.niceCeil
import com.cashoutdashboard.app.ui.short
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

internal fun LazyListScope.insightsTab(d: DashData, onOpenMetric: (Kpi) -> Unit, onOpenShift: (String) -> Unit) {
    if (d.shifts.isEmpty()) {
        item(key = "empty") { EmptyNote(Icons.Filled.Lightbulb, "No shifts in this period", "Insights about your best days, times and habits will show here.") }
        return
    }
    item(key = "takeaways") { TakeawaysCard(d, onOpenMetric) }
    if (d.prevTotals != null && d.previous.isNotEmpty()) item(key = "compare") { CompareCard(d, onOpenMetric) }
    item(key = "spread") { SpreadCard(d, onOpenShift) }
    item(key = "when") { WhenCard(d) }
    item(key = "length") { LengthCard(d, onOpenMetric) }
    item(key = "busy") { BusyCard(d, onOpenMetric) }
}

internal fun InsightKind.icon(): ImageVector = when (this) {
    InsightKind.BEST_DAY -> Icons.Filled.CalendarMonth
    InsightKind.PER_SHIFT_CHANGE -> Icons.Filled.Payments
    InsightKind.TIP_RATE_CHANGE -> Icons.Filled.Percent
    InsightKind.MOMENTUM -> Icons.Filled.LocalFireDepartment
    InsightKind.TIME_OF_DAY -> Icons.Filled.Schedule
    InsightKind.SHIFT_LENGTH -> Icons.Filled.Timer
    InsightKind.BUSY_NIGHTS -> Icons.Filled.PointOfSale
    InsightKind.BEST_MONTH -> Icons.Filled.EmojiEvents
    InsightKind.GUEST_VALUE -> Icons.Filled.Groups
    InsightKind.TYPICAL_RANGE -> Icons.Filled.BarChart
}

/** One takeaway: icon, headline and the numbers behind it; opens its measure when it has one. */
@Composable
internal fun InsightRow(insight: Insight, onOpenMetric: (Kpi) -> Unit) {
    val kpi = insight.kpi
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .let { if (kpi != null) it.clickable { onOpenMetric(kpi) } else it }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(insight.kind.icon())
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(insight.headline, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(insight.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (kpi != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Open ${kpi.label}", tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun TakeawaysCard(d: DashData, onOpenMetric: (Kpi) -> Unit) {
    val insights = remember(d.shifts, d.previous, d.takeHome) { Stats.insights(d.shifts, d.previous, d.takeHome) }
    DashCard("Takeaways", "What stands out this period") {
        if (insights.isEmpty()) {
            Text(
                "Log a few more shifts in this period and personalized takeaways will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        insights.forEachIndexed { i, insight ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.outlineVariant)
            InsightRow(insight, onOpenMetric)
        }
    }
}

/** Last period → this period for the core measures, as dumbbells on zero-based tracks. */
@Composable
private fun CompareCard(d: DashData, onOpenMetric: (Kpi) -> Unit) {
    val rows = listOf(Kpi.TIPS, Kpi.TIPS_PER_SHIFT, Kpi.TIP_PCT, Kpi.TIPS_PER_HOUR, Kpi.SALES_PER_SHIFT, Kpi.HOURS)
        .mapNotNull { k ->
            val a = d.baseline(k)?.let { k.of(it) }
            val b = k.of(d.totals)
            if (a != null && b != null) Triple(k, a, b) else null
        }
    if (rows.isEmpty()) return
    val elapsed = d.elapsedDays
    val subtitle = when {
        elapsed != null -> "Totals use the first ${plural(elapsed, "day")} of each period"
        else -> d.periodDays?.let { "Compared with the ${plural(it, "day")} before" }
    }
    DashCard("This period vs last", subtitle) {
        LegendRow(listOf(LegendMark.RING to "Last period", LegendMark.DOT to "This period"))
        Spacer(Modifier.height(6.dp))
        rows.forEach { (k, before, now) ->
            val same = k.change(now, before).startsWith("Same")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenMetric(k) }
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(k.title(d.takeHome), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Icon(
                        when {
                            same -> Icons.AutoMirrored.Filled.TrendingFlat
                            now > before -> Icons.AutoMirrored.Filled.TrendingUp
                            else -> Icons.AutoMirrored.Filled.TrendingDown
                        },
                        contentDescription = if (same) "No change" else if (now > before) "Up" else "Down",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("${k.format(before)} → ${k.format(now)}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                Dumbbell(before, now, niceCeil(maxOf(before, now)), Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Shift results as a dot strip, switchable between tips, tip rate and hourly. */
@Composable
private fun SpreadCard(d: DashData, onOpenShift: (String) -> Unit) {
    val choices = listOf(Kpi.TIPS to "Tips", Kpi.TIP_PCT to "Tip rate", Kpi.TIPS_PER_HOUR to "Per hour")
    var kpiName by rememberSaveable { mutableStateOf(Kpi.TIPS.name) }
    val kpi = Kpi.valueOf(kpiName)
    val rows = remember(d.shifts, kpi, d.takeHome) {
        d.shifts.filter { kpi.counts(it) }.mapNotNull { s -> kpi.of(Stats.aggregate(listOf(s), d.takeHome))?.let { s to it } }
    }
    if (d.shifts.size < 3) return
    DashCard("How your shifts vary", "Each dot is one shift") {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { (k, label) -> SoftChip(selected = k == kpi, onClick = { kpiName = k.name }, label = { Text(label) }) }
        }
        Spacer(Modifier.height(8.dp))
        if (rows.size < 3) {
            Text(
                "Not enough shifts with ${kpi.requirement ?: "data"} for this view yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@DashCard
        }
        val values = rows.map { it.second }
        var selected by remember(rows) { mutableStateOf<Int?>(null) }
        StripPlot(values, axisFormat = { kpi.axis(it) }, selected = selected, onSelect = { selected = it })
        LegendRow(listOf(LegendMark.DOT to "Shift", LegendMark.BAND to "Middle half", LegendMark.INK_LINE to "Median"), Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(10.dp))
        val sel = selected?.let { rows.getOrNull(it) }
        if (sel != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${sel.first.localDate.short()} · ${kpi.format(sel.second)}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.TextButton(onClick = { onOpenShift(sel.first.id) }) { Text("Open") }
            }
        } else {
            val q1 = Stats.percentile(values, 0.25)!!
            val q3 = Stats.percentile(values, 0.75)!!
            val mean = values.average()
            val sd = kotlin.math.sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
            val cv = if (mean > 0) sd / mean else 0.0
            val steadiness = when {
                cv < 0.2 -> "Your shifts are pretty consistent."
                cv < 0.4 -> "Your shifts vary a fair bit."
                else -> "Your shifts swing widely."
            }
            Text(
                "Half your shifts land in ${kpi.range(q1, q3)}. $steadiness",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Weekday × time-of-day heatmap. One sequential hue, shaded from the lowest to the highest cell,
 * with the value printed in each cell (ink picked by the cell's luminance for contrast).
 */
@Composable
private fun WhenCard(d: DashData) {
    val choices = listOf(Kpi.TIPS_PER_SHIFT to "Avg tips", Kpi.TIP_PCT to "Tip rate", Kpi.TIPS_PER_HOUR to "Per hour")
    var kpiName by rememberSaveable { mutableStateOf(Kpi.TIPS_PER_SHIFT.name) }
    val kpi = Kpi.valueOf(kpiName)
    val grid = remember(d.shifts, d.takeHome) { Stats.weekdayByDayPart(d.shifts, d.takeHome) }
    if (grid.values.sumOf { it.shifts } < 3) return
    val parts = DayPart.entries.filter { p -> grid.keys.any { it.second == p } }
    val days = DayOfWeek.entries.filter { day -> grid.keys.any { it.first == day } }
    val colors = MaterialTheme.colorScheme
    var selected by remember(grid) { mutableStateOf<Pair<DayOfWeek, DayPart>?>(null) }
    DashCard("When you earn most", "By weekday and when your shift starts") {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { (k, label) -> SoftChip(selected = k == kpi, onClick = { kpiName = k.name }, label = { Text(label) }) }
        }
        Spacer(Modifier.height(8.dp))
        AnimatedContent(kpi, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "when") { k ->
            val cells = grid.mapValues { (_, b) -> k.of(b) }.filterValues { it != null }.mapValues { it.value!! }
            val lo = cells.values.minOrNull() ?: 0.0
            val hi = cells.values.maxOrNull() ?: 1.0
            val best = cells.maxByOrNull { it.value }?.key
            fun name(key: Pair<DayOfWeek, DayPart>) =
                "${key.first.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${key.second.label.lowercase()}"
            Column {
                val sel = selected
                val readout = when {
                    sel != null && grid[sel] != null -> {
                        val b = grid[sel]!!
                        "${name(sel)}: ${cells[sel]?.let { k.format(it) } ?: "no ${k.requirement ?: "data"}"} · ${plural(b.shifts, "shift")}"
                    }
                    best != null -> "Best: ${name(best)} · ${k.format(cells[best]!!)}"
                    else -> "Tap a square for details"
                }
                Text(
                    readout,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (sel != null) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (sel != null) colors.onSurface else colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Spacer(Modifier.width(44.dp))
                    parts.forEach { p ->
                        Text(p.label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(4.dp))
                days.forEach { day ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.width(44.dp),
                        )
                        parts.forEach { p ->
                            val key = day to p
                            val v = cells[key]
                            val fill = if (v == null) colors.surfaceContainerHighest.copy(alpha = 0.5f)
                            else heatColor(if (hi > lo) (v - lo) / (hi - lo) else 1.0, colors.primary, colors.surfaceContainerHighest)
                            val ink = if (v == null) colors.onSurfaceVariant else if (fill.luminance() > 0.4f) Color(0xFF1A1B20) else Color.White
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(fill)
                                    .let { if (key == sel) it.border(2.dp, colors.onSurface, RoundedCornerShape(8.dp)) else it }
                                    .let { m -> if (grid[key] != null) m.clickable { selected = if (sel == key) null else key } else m },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    v?.let { k.formatCompact(it) } ?: "–",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (v != null) FontWeight.SemiBold else FontWeight.Normal,
                                    color = ink,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                parts.joinToString(" · ") { "${it.label} ${it.hint.removePrefix("starts ")}" },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            HeatLegend(high = "More")
        }
    }
}

/** Hourly tips by shift length: do long doubles pay off, or do short shifts earn more per hour? */
@Composable
private fun LengthCard(d: DashData, onOpenMetric: (Kpi) -> Unit) {
    val bands = remember(d.shifts, d.takeHome) { Stats.lengthBands(d.shifts, d.takeHome) }
    if (bands.size < 2 || bands.sumOf { it.shifts } < 4) return
    var selected by remember(bands) { mutableStateOf<Int?>(null) }
    val values = bands.map { Kpi.TIPS_PER_HOUR.of(it) ?: 0.0 }
    val best = values.indices.maxByOrNull { values[it] }
    DashCard("Shift length vs. hourly", "Tips per hour by how long you worked", action = {
        androidx.compose.material3.IconButton(onClick = { onOpenMetric(Kpi.TIPS_PER_HOUR) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Open tips per hour")
        }
    }) {
        ColumnChart(
            values = values,
            labels = bands.map { it.label },
            format = { Kpi.TIPS_PER_HOUR.format(it) },
            axisFormat = { Kpi.TIPS_PER_HOUR.axis(it) },
            detail = { i -> "${bands[i].label} shifts: ${Kpi.TIPS_PER_HOUR.format(values[i])} · ${plural(bands[i].shifts, "shift")}" },
            selected = selected,
            onSelect = { selected = it },
            highlight = best,
            height = 150.dp,
            placeholder = best?.let { "Best: ${bands[it].label} shifts · tap a bar for details" } ?: "Tap a bar for details",
        )
    }
}

/** Tip rate from slowest to busiest shifts by sales: does the rate hold up when it's slammed? */
@Composable
private fun BusyCard(d: DashData, onOpenMetric: (Kpi) -> Unit) {
    val bands = remember(d.shifts, d.takeHome) { Stats.salesBands(d.shifts, d.takeHome) }
    if (bands.size < 2) return
    var selected by remember(bands) { mutableStateOf<Int?>(null) }
    val values = bands.map { Kpi.TIP_PCT.of(it) ?: 0.0 }
    DashCard("Busy vs. slow shifts", "Tip rate from your slowest to busiest shifts, by sales", action = {
        androidx.compose.material3.IconButton(onClick = { onOpenMetric(Kpi.TIP_PCT) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Open tip rate")
        }
    }) {
        ColumnChart(
            values = values,
            labels = bands.map { it.label },
            format = { Kpi.TIP_PCT.format(it) },
            axisFormat = { Kpi.TIP_PCT.axis(it) },
            detail = { i -> "${bands[i].label} in sales: ${Kpi.TIP_PCT.format(values[i])} · ${plural(bands[i].shifts, "shift")}" },
            selected = selected,
            onSelect = { selected = it },
            height = 150.dp,
        )
        val slow = values.first()
        val busy = values.last()
        Text(
            when {
                kotlin.math.abs(busy - slow) < 0.5 -> "Your tip rate holds steady no matter how busy it gets."
                busy > slow -> "Your busiest shifts tip at ${Kpi.TIP_PCT.format(busy)}, vs ${Kpi.TIP_PCT.format(slow)} on your slowest."
                else -> "Your tip rate slips to ${Kpi.TIP_PCT.format(busy)} on your busiest shifts, from ${Kpi.TIP_PCT.format(slow)} on your slowest."
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
