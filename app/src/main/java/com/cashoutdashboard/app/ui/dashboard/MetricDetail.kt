package com.cashoutdashboard.app.ui.dashboard

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cashoutdashboard.app.data.Shift
import com.cashoutdashboard.app.stats.Kpi
import com.cashoutdashboard.app.stats.Stats
import com.cashoutdashboard.app.ui.ColumnChart
import com.cashoutdashboard.app.ui.LegendMark
import com.cashoutdashboard.app.ui.LegendRow
import com.cashoutdashboard.app.ui.LineChart
import com.cashoutdashboard.app.ui.SoftChip
import com.cashoutdashboard.app.ui.StripPlot
import com.cashoutdashboard.app.ui.pretty
import com.cashoutdashboard.app.ui.short
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Measures worth offering for this period: those with data, plus take-home only when it isn't already the headline. */
internal fun availableKpis(d: DashData): List<Kpi> = Kpi.entries.filter { k ->
    (k != Kpi.TAKE_HOME || !d.takeHome) && (d.shifts.isEmpty() || k.of(d.totals) != null)
}

/**
 * Full-screen look at one measure for the selected period: headline and change, how it moved over
 * time, a running total against last period (for sums), shift-by-shift spread and weekday pattern.
 * The period picker above stays live, so changing the range re-scopes everything here.
 */
@Composable
internal fun MetricDetail(
    d: DashData,
    kpi: Kpi,
    today: LocalDate,
    onKpi: (Kpi) -> Unit,
    onBack: () -> Unit,
    onOpenShift: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = remember(d) { availableKpis(d).let { if (kpi in it) it else it + kpi } }
    val chips = rememberLazyListState()
    LaunchedEffect(kpi) { options.indexOf(kpi).takeIf { it >= 0 }?.let { chips.animateScrollToItem((it - 1).coerceAtLeast(0)) } }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to overview") }
            Text(kpi.title(d.takeHome), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        LazyRow(
            state = chips,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(options, key = { _, k -> k.name }) { _, k ->
                SoftChip(selected = k == kpi, onClick = { onKpi(k) }, label = { Text(k.title(d.takeHome)) })
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (d.shifts.isEmpty()) {
                item(key = "empty") {
                    EmptyNote(Icons.AutoMirrored.Filled.ShowChart, "No shifts in this period", "Pick another period above to see ${kpi.title(d.takeHome).lowercase()} over time.")
                }
                return@LazyColumn
            }
            item(key = "headline-${kpi.name}") { HeadlineCard(d, kpi) }
            item(key = "time-${kpi.name}") { OverTimeCard(d, kpi, onOpenShift) }
            if (kpi.additive) item(key = "running-${kpi.name}") { RunningTotalCard(d, kpi, today) }
            item(key = "spread-${kpi.name}") { ShiftSpreadCard(d, kpi, onOpenShift) }
            item(key = "weekday-${kpi.name}") { WeekdayPatternCard(d, kpi) }
        }
    }
}

@Composable
private fun HeadlineCard(d: DashData, kpi: Kpi) {
    val value = kpi.of(d.totals)
    val before = d.baseline(kpi)?.let { kpi.of(it) }
    val counted = d.shifts.count { kpi.counts(it) }
    DashCard {
        Text(kpi.blurb(d.takeHome), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value?.let { kpi.format(it) } ?: "—",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp),
            maxLines = 1,
        )
        val basis = buildString {
            append(plural(d.shifts.size, "shift"))
            if (kpi.additive && value != null) kpi.perShift(d.totals)?.let { append(" · ${kpi.format(it)} per shift") }
        }
        Text(basis, style = MaterialTheme.typography.bodyMedium)
        val need = kpi.requirement
        if (need != null && counted < d.shifts.size) {
            Text(
                "Based on the $counted of ${d.shifts.size} shifts with $need.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (value != null && before != null) {
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            val same = kpi.change(value, before).startsWith("Same")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        same -> Icons.AutoMirrored.Filled.TrendingFlat
                        value > before -> Icons.AutoMirrored.Filled.TrendingUp
                        else -> Icons.AutoMirrored.Filled.TrendingDown
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(kpi.change(value, before), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        d.baselineLabel(kpi),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

internal enum class GrainChoice(val label: String) { AUTO("Auto"), SHIFT("Shift"), WEEK("Week"), MONTH("Month") }

internal fun GrainChoice.resolve(shifts: List<Shift>) = when (this) {
    GrainChoice.AUTO -> Stats.grainFor(shifts)
    GrainChoice.SHIFT -> Stats.Grain.SHIFT
    GrainChoice.WEEK -> Stats.Grain.WEEK
    GrainChoice.MONTH -> Stats.Grain.MONTH
}

private fun Stats.Grain.unit() = when (this) {
    Stats.Grain.SHIFT -> "shift"
    Stats.Grain.WEEK -> "week"
    Stats.Grain.MONTH -> "month"
}

/**
 * The measure per shift/week/month. Sums are columns (with a moving average once there's enough
 * history); rates are a line against a dashed period-average level.
 */
@Composable
private fun OverTimeCard(d: DashData, kpi: Kpi, onOpenShift: (String) -> Unit) {
    var grainName by rememberSaveable { mutableStateOf(GrainChoice.AUTO.name) }
    val choice = GrainChoice.valueOf(grainName)
    val grain = choice.resolve(d.shifts)
    val buckets = remember(d.shifts, d.takeHome, grain) { Stats.buckets(d.shifts, grain, d.takeHome) }
    DashCard("Over time", "${kpi.title(d.takeHome)} by ${grain.unit()}") {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GrainChoice.entries.forEach { g ->
                SoftChip(selected = g == choice, onClick = { grainName = g.name }, label = { Text(g.label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Crossfade(targetState = grain to buckets, label = "detail-trend") { (gr, bs) ->
            var selected by remember(bs, kpi) { mutableStateOf<Int?>(null) }
            val raw = remember(bs, kpi) { bs.map { kpi.of(it) } }
            val label: (Int) -> String = { i -> if (gr == Stats.Grain.SHIFT) bs[i].start.short() else bs[i].label }
            val detail: (Int) -> String = { i ->
                val b = bs[i]
                val main = raw[i]?.let { kpi.format(it) } ?: "no ${kpi.requirement ?: "data"}"
                "${label(i)}: $main" + if (gr == Stats.Grain.SHIFT) "" else " · ${plural(b.shifts, "shift")}"
            }
            Column {
                if (kpi.additive) {
                    val window = when (gr) { Stats.Grain.SHIFT -> 5; Stats.Grain.WEEK -> 4; Stats.Grain.MONTH -> 3 }
                    val avg = if (bs.size >= window + 1) Stats.movingAverage(raw, window) else null
                    ColumnChart(
                        values = raw.map { it ?: 0.0 },
                        labels = bs.map { it.label },
                        format = { kpi.format(it) },
                        axisFormat = { kpi.axis(it) },
                        detail = { i -> detail(i) + (avg?.getOrNull(i)?.let { " · avg ${kpi.format(it)}" } ?: "") },
                        selected = selected,
                        onSelect = { selected = it },
                        overlay = avg,
                        height = 200.dp,
                    )
                    if (avg != null) {
                        LegendRow(
                            listOf(LegendMark.BAR to "Per ${gr.unit()}", LegendMark.INK_LINE to "$window-${gr.unit()} average"),
                            Modifier.padding(top = 8.dp),
                        )
                    }
                } else {
                    val ref = kpi.of(d.totals)
                    LineChart(
                        values = raw,
                        labels = bs.map { it.label },
                        format = { kpi.format(it) },
                        axisFormat = { kpi.axis(it) },
                        detail = detail,
                        selected = selected,
                        onSelect = { selected = it },
                        reference = ref,
                        height = 200.dp,
                    )
                    LegendRow(
                        listOf(LegendMark.LINE to "Per ${gr.unit()}", LegendMark.DASHED to "Period: ${ref?.let { kpi.format(it) } ?: "—"}"),
                        Modifier.padding(top = 8.dp),
                    )
                }
                val id = selected?.let { bs.getOrNull(it)?.shiftId }
                if (id != null) {
                    TextButton(onClick = { onOpenShift(id) }, modifier = Modifier.align(Alignment.End)) { Text("Open this shift") }
                }
            }
        }
    }
}

/**
 * Day-by-day running total through today, with the previous period's total on the same days as a
 * muted line — shows at a glance whether you're ahead of or behind last period's pace.
 */
@Composable
private fun RunningTotalCard(d: DashData, kpi: Kpi, today: LocalDate) {
    val start = d.range.start ?: d.shifts.minOf { it.localDate }
    val end = d.range.end ?: maxOf(today, d.shifts.maxOf { it.localDate })
    val through = if (end.isAfter(today)) today else end
    val cur = remember(d.shifts, kpi, start, end, through, d.takeHome) { Stats.runningTotal(d.shifts, kpi, start, end, through, d.takeHome) }
    val prevRange = Stats.previous(d.range)
    val prev = remember(d.previous, kpi, prevRange, d.takeHome) {
        prevRange?.takeIf { d.previous.isNotEmpty() }?.let { Stats.runningTotal(d.previous, kpi, it.start!!, it.end!!, it.end, d.takeHome) }
    }
    if (cur.size < 2) return
    val fmt = DateTimeFormatter.ofPattern("M/d")
    val dates = remember(start, cur.size) { List(cur.size) { start.plusDays(it.toLong()) } }
    var selected by remember(cur, prev) { mutableStateOf<Int?>(null) }
    DashCard("Running total", if (prev != null) "This period vs the same days last period" else "Adds up day by day") {
        LineChart(
            values = cur,
            labels = dates.map { it.format(fmt) },
            format = { kpi.format(it) },
            axisFormat = { kpi.axis(it) },
            detail = { i ->
                "${dates[i].short()}: ${cur[i]?.let { kpi.format(it) } ?: "—"}" +
                    (prev?.getOrNull(i)?.let { " · last period ${kpi.format(it)}" } ?: "")
            },
            ghost = prev,
            selected = selected,
            onSelect = { selected = it },
            height = 180.dp,
        )
        if (prev != null) {
            LegendRow(listOf(LegendMark.LINE to "This period", LegendMark.MUTED_LINE to "Last period"), Modifier.padding(top = 8.dp))
            val idx = ChronoUnit.DAYS.between(start, through).toInt()
            val now = cur.getOrNull(idx)
            val then = prev.getOrNull(idx)
            if (now != null && then != null) {
                val gap = now - then
                val text = when {
                    kotlin.math.abs(gap) < 0.005 * maxOf(then, 1.0) -> "Right on last period's pace."
                    gap > 0 -> "${kpi.format(gap)} ahead of last period at this point."
                    else -> "${kpi.format(-gap)} behind last period at this point."
                }
                Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/** Every shift as a dot: where most land, the best and the lowest. */
@Composable
private fun ShiftSpreadCard(d: DashData, kpi: Kpi, onOpenShift: (String) -> Unit) {
    val rows = remember(d.shifts, kpi, d.takeHome) {
        d.shifts.filter { kpi.counts(it) }
            .mapNotNull { s -> kpi.perShift(Stats.aggregate(listOf(s), d.takeHome))?.let { s to it } }
    }
    if (rows.size < 2) return
    val values = rows.map { it.second }
    var selected by remember(rows) { mutableStateOf<Int?>(null) }
    DashCard("Shift by shift", "Each dot is one shift") {
        StripPlot(values, axisFormat = { kpi.axis(it) }, selected = selected, onSelect = { selected = it })
        LegendRow(
            listOf(LegendMark.DOT to "Shift", LegendMark.BAND to "Middle half", LegendMark.INK_LINE to "Median"),
            Modifier.padding(top = 6.dp),
        )
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
                TextButton(onClick = { onOpenShift(sel.first.id) }) { Text("Open") }
            }
        } else {
            val median = Stats.percentile(values, 0.5)!!
            Text(
                "Typical shift ${kpi.format(median)} · middle half ${kpi.range(Stats.percentile(values, 0.25)!!, Stats.percentile(values, 0.75)!!)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(4.dp))
        val best = rows.maxBy { it.second }
        val low = rows.minBy { it.second }
        InfoRow(Icons.AutoMirrored.Filled.TrendingUp, "Best shift", kpi.format(best.second), best.first.localDate.pretty()) { onOpenShift(best.first.id) }
        InfoRow(Icons.AutoMirrored.Filled.TrendingDown, "Lowest shift", kpi.format(low.second), low.first.localDate.pretty()) { onOpenShift(low.first.id) }
        val latest = rows.maxWith(compareBy({ it.first.date }, { it.first.openTime ?: "" }))
        InfoRow(Icons.Filled.History, "Most recent", kpi.format(latest.second), latest.first.localDate.pretty()) { onOpenShift(latest.first.id) }
    }
}

/** The measure by weekday; sums are shown per shift so busy weekdays don't win just by volume. */
@Composable
private fun WeekdayPatternCard(d: DashData, kpi: Kpi) {
    val days = remember(d.shifts, d.takeHome) { Stats.weekdayBuckets(d.shifts, d.takeHome) }
    val values = days.map { kpi.perShift(it) }
    if (values.count { it != null } < 2) return
    var selected by remember(days, kpi) { mutableStateOf<Int?>(null) }
    val best = values.withIndex().filter { it.value != null }.maxByOrNull { it.value!! }?.index
    fun dayName(i: Int) = java.time.DayOfWeek.of(i + 1).getDisplayName(TextStyle.FULL, Locale.getDefault())
    DashCard("By day of week", if (kpi.additive) "Average per shift" else kpi.title(d.takeHome)) {
        ColumnChart(
            values = values.map { it ?: 0.0 },
            labels = days.map { it.label },
            format = { kpi.format(it) },
            axisFormat = { kpi.axis(it) },
            detail = { i ->
                val v = values[i]
                if (days[i].shifts == 0) "${dayName(i)}: no shifts"
                else "${dayName(i)}: ${v?.let { kpi.format(it) } ?: "—"} · ${plural(days[i].shifts, "shift")}"
            },
            selected = selected,
            onSelect = { selected = it },
            highlight = best,
            height = 160.dp,
            placeholder = best?.let { "Best: ${dayName(it)} · tap a bar for details" } ?: "Tap a bar for details",
        )
    }
}
