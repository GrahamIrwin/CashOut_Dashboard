package com.cashoutdashboard.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import com.cashoutdashboard.app.ui.HeroColors
import com.cashoutdashboard.app.ui.IconBadge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashoutdashboard.app.data.AppSettings
import com.cashoutdashboard.app.data.GoalPeriod
import com.cashoutdashboard.app.stats.Kpi
import com.cashoutdashboard.app.stats.Pace
import com.cashoutdashboard.app.stats.Period
import com.cashoutdashboard.app.stats.Stats
import com.cashoutdashboard.app.ui.GoalDialog
import com.cashoutdashboard.app.ui.ProgressRing
import com.cashoutdashboard.app.ui.ProportionBar
import com.cashoutdashboard.app.ui.Sparkline
import com.cashoutdashboard.app.ui.hrs
import com.cashoutdashboard.app.ui.money
import com.cashoutdashboard.app.ui.moneyShort
import com.cashoutdashboard.app.ui.pct
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal fun LazyListScope.overviewTab(
    d: DashData,
    settings: AppSettings,
    today: LocalDate,
    onOpenMetric: (Kpi) -> Unit,
    onOpenInsights: () -> Unit,
    onSaveGoal: (Double?, GoalPeriod) -> Unit,
) {
    // Goal and projections are about "now", so they show even when the selected period is empty.
    // An active goal sits right under the headline; the "set a goal" prompt waits at the bottom.
    val hasGoal = settings.goalAmount != null
    if (d.shifts.isEmpty()) {
        if (hasGoal) item(key = "goal") { GoalCard(d, settings, today, onSaveGoal) }
        item(key = "empty") { EmptyNote(Icons.AutoMirrored.Filled.ReceiptLong, "No shifts in this period", "Pick another period above to see your stats.") }
        item(key = "pace") { PaceCard(d, settings, today) }
        if (!hasGoal) item(key = "goal") { GoalCard(d, settings, today, onSaveGoal) }
        return
    }
    item(key = "hero") { HeroCard(d) { onOpenMetric(Kpi.TIPS) } }
    if (hasGoal) item(key = "goal") { GoalCard(d, settings, today, onSaveGoal) }
    item(key = "kpis") { KpiGrid(d, onOpenMetric) }
    item(key = "insights") { InsightsPreview(d, onOpenMetric, onOpenInsights) }
    item(key = "pace") { PaceCard(d, settings, today) }
    if (!hasGoal) item(key = "goal") { GoalCard(d, settings, today, onSaveGoal) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroCard(d: DashData, onClick: () -> Unit) {
    val s = d.summary
    val prev = d.prevSummary
    val onHero = HeroColors.content
    val spark = remember(d.shifts, d.takeHome) {
        d.shifts.sortedWith(compareBy({ it.date }, { it.openTime ?: "" })).map { it.effectiveTips(d.takeHome) }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = HeroColors.container),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(Modifier.padding(top = 20.dp, start = 20.dp, end = 20.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (d.takeHome) "Cash take-home" else "Tips earned",
                    style = MaterialTheme.typography.labelLarge,
                    color = onHero.copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f),
                )
                Text("See trend", style = MaterialTheme.typography.labelMedium, color = onHero.copy(alpha = 0.8f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = onHero.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
            }
            Text(
                s.tips.money(),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp, lineHeight = 52.sp),
                color = onHero,
                maxLines = 1,
            )
            Text(
                "${s.tipPct.pct()} of ${s.sales.moneyShort()} sales · ${plural(s.shifts, "shift")}",
                style = MaterialTheme.typography.bodyMedium,
                color = onHero.copy(alpha = 0.8f),
            )
            if (spark.size >= 3) {
                Sparkline(spark.takeLast(30), onHero, Modifier.padding(top = 14.dp).fillMaxWidth().height(52.dp), fill = true)
            }
            if (prev != null && prev.avgTipsPerShift != null && s.avgTipsPerShift != null && prev.avgTipsPerShift > 0) {
                Spacer(Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // "Old → new" reads plainly; the arrow carries direction, so no signed percents or "pts".
                    DeltaBadge(
                        s.avgTipsPerShift >= prev.avgTipsPerShift,
                        "Per shift ${wholeDollars(prev.avgTipsPerShift)} → ${wholeDollars(s.avgTipsPerShift)}",
                        onHero,
                    )
                    if (s.tipPct != null && prev.tipPct != null) {
                        DeltaBadge(s.tipPct >= prev.tipPct, "Tip rate ${prev.tipPct.pct()} → ${s.tipPct.pct()}", onHero)
                    }
                }
                Text(
                    "vs the previous ${plural((java.time.temporal.ChronoUnit.DAYS.between(d.range.start!!, d.range.end!!) + 1).toInt(), "day")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = onHero.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** Change chip on the hero: direction is carried by the arrow, so it stays readable on the brand color. */
@Composable
private fun DeltaBadge(up: Boolean, text: String, ink: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier
            .background(ink.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (up) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
            contentDescription = if (up) "Up" else "Down",
            tint = ink,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = ink)
    }
}

@Composable
private fun GoalCard(d: DashData, settings: AppSettings, today: LocalDate, onSave: (Double?, GoalPeriod) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    if (editing) GoalDialog(settings.goalAmount, settings.goalPeriod, onDismiss = { editing = false }, onSave = onSave)
    val goal = settings.goalAmount
    if (goal == null) {
        DashCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.Flag, 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Set a tip goal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Track your progress each week, pay period, month or year.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("Set goal") }
        }
        return
    }
    val window = Stats.goalRange(settings.goalPeriod, today, settings.payPeriodAnchorDate)
    val pace = remember(d.all, window, d.takeHome, today) { Stats.pace(d.all, window, today, d.takeHome) }
    val earned = pace?.earned ?: 0.0
    val progress = (earned / goal).toFloat()
    val reached = earned >= goal
    val onTrack = (pace?.projected ?: 0.0) >= goal
    val accent = if (reached || onTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    DashCard(
        title = "${settings.goalPeriod.label} goal",
        subtitle = window.label(),
        action = { IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, "Edit goal") } },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(progress, accent, Modifier.size(104.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.0f%%".format(progress * 100), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("of goal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${earned.moneyShort()} of ${goal.moneyShort()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                when {
                    reached -> StatusLine(Icons.Filled.EmojiEvents, "Goal reached — nice work!", MaterialTheme.colorScheme.primary)
                    pace != null -> {
                        Text(
                            "${(goal - earned).moneyShort()} to go · ${plural(pace.daysLeft, "day")} left",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        StatusLine(
                            if (onTrack) Icons.Filled.CheckCircle else Icons.Filled.Speed,
                            if (onTrack) "On pace for ${pace.projected.moneyShort()}" else "Pace: ${pace.projected.moneyShort()} — pick up ~${extraShifts(goal, pace)}",
                            if (onTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }
    }
}

private fun extraShifts(goal: Double, pace: Pace): String {
    val perShift = if (pace.expectedMoreShifts > 0) (pace.projected - pace.earned) / pace.expectedMoreShifts else 0.0
    if (perShift <= 0) return "more shifts"
    val n = kotlin.math.ceil((goal - pace.projected) / perShift).toInt().coerceAtLeast(1)
    return plural(n, "more shift")
}

@Composable
private fun StatusLine(icon: ImageVector, text: String, tint: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = tint)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PaceCard(d: DashData, settings: AppSettings, today: LocalDate) {
    val month = remember(d.all, d.takeHome, today) {
        Stats.pace(d.all, Stats.range(Period.MONTH, today, null, null), today, d.takeHome)
    }
    val year = remember(d.all, d.takeHome, today) {
        Stats.pace(d.all, Stats.range(Period.YEAR, today, null, null), today, d.takeHome)
    }
    if (month == null || year == null) return
    DashCard("Where you're headed", "Projected from your last 8 weeks of shifts") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PaceColumn(today.format(DateTimeFormatter.ofPattern("MMMM")), month, Modifier.weight(1f))
            PaceColumn(today.year.toString(), year, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PaceColumn(label: String, p: Pace, modifier: Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(p.projected.moneyShort(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("projected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        ProportionBar((if (p.projected > 0) p.earned / p.projected else 0.0).toFloat())
        Spacer(Modifier.height(6.dp))
        Text("${p.earned.moneyShort()} so far", style = MaterialTheme.typography.bodySmall)
    }
}

/** One tappable stat tile: label, value, a quiet footnote and a per-shift sparkline. */
@Composable
private fun KpiTile(label: String, value: String, sub: String?, spark: List<Double>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
        }
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp))
        if (sub != null) {
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (spark.size >= 3) {
            Sparkline(spark, MaterialTheme.colorScheme.primary, Modifier.padding(top = 6.dp).fillMaxWidth().height(22.dp))
        } else {
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun KpiGrid(d: DashData, onOpenMetric: (Kpi) -> Unit) {
    val s = d.summary
    data class Tile(val kpi: Kpi, val sub: String?)
    val tiles = buildList {
        add(Tile(Kpi.TIPS_PER_HOUR, if (s.hours > 0) "over ${s.hours.hrs()}" else "add shift times"))
        add(Tile(Kpi.TIP_PCT, "of ${s.sales.moneyShort()} sales"))
        add(Tile(Kpi.TIPS_PER_SHIFT, "in tips"))
        add(Tile(Kpi.SALES_PER_SHIFT, "${s.sales.moneyShort()} total"))
        add(Tile(Kpi.HOURS, s.shifts.takeIf { s.hours > 0 }?.let { "%.1f h avg shift".format(s.hours / it) }))
        add(Tile(Kpi.GUESTS, s.tipsPerCover?.let { "${it.money()} tips each" }))
        add(Tile(Kpi.AVG_CHECK, "per guest"))
        add(Tile(Kpi.SALES_PER_HOUR, "rung in per hour"))
        add(Tile(Kpi.LIQUOR_SHARE, "of your sales"))
        if (s.takeHome != null && !d.takeHome) add(Tile(Kpi.TAKE_HOME, "${s.takeHomeShifts} of ${s.shifts} shifts"))
    }
    // Each measure shift by shift (oldest first) for the tile sparklines.
    val perShift = remember(d.shifts, d.takeHome) { Stats.buckets(d.shifts, Stats.Grain.SHIFT, d.takeHome) }
    DashCard("At a glance", "Tap any number to see it over time") {
        tiles.chunked(2).forEachIndexed { r, row ->
            if (r > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    val spark = remember(perShift, t.kpi) { perShift.mapNotNull { t.kpi.of(it) }.takeLast(20) }
                    KpiTile(
                        label = t.kpi.label,
                        value = t.kpi.of(d.totals)?.let { t.kpi.formatBare(it) } ?: "—",
                        sub = t.sub,
                        spark = spark,
                        onClick = { onOpenMetric(t.kpi) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** The two strongest takeaways, with a way into the full Insights tab. */
@Composable
private fun InsightsPreview(d: DashData, onOpenMetric: (Kpi) -> Unit, onOpenInsights: () -> Unit) {
    val insights = remember(d.shifts, d.previous, d.takeHome) { Stats.insights(d.shifts, d.previous, d.takeHome, max = 2) }
    DashCard("Insights", action = { TextButton(onClick = onOpenInsights) { Text("See all") } }) {
        if (insights.isEmpty()) {
            Text(
                "Log a few more shifts in this period and personalized insights will show up here.",
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
