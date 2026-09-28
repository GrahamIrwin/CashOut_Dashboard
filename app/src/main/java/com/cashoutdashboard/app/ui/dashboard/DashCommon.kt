package com.cashoutdashboard.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cashoutdashboard.app.data.Shift
import com.cashoutdashboard.app.stats.Bucket
import com.cashoutdashboard.app.stats.DateRange
import com.cashoutdashboard.app.stats.Kpi
import com.cashoutdashboard.app.stats.Metric
import com.cashoutdashboard.app.stats.Summary
import com.cashoutdashboard.app.ui.IconBadge
import com.cashoutdashboard.app.ui.money
import com.cashoutdashboard.app.ui.pct

/** Everything the tabs need for the selected period, computed once per change. */
class DashData(
    val all: List<Shift>,
    val range: DateRange,
    val shifts: List<Shift>,
    val previous: List<Shift>,
    val summary: Summary,
    val prevSummary: Summary?,
    val takeHome: Boolean,
    /** Period totals every [Kpi] reads from; [prevTotals] is null without a previous period. */
    val totals: Bucket,
    val prevTotals: Bucket?,
    /** Days elapsed when the period is still in progress (today falls inside it), else null. */
    val elapsedDays: Int? = null,
    /** Last period's totals over its first [elapsedDays] days, so running sums compare like for like. */
    val prevToDate: Bucket? = prevTotals,
)

/** Length of the selected period in days, or null when it's open-ended. */
internal val DashData.periodDays: Int?
    get() {
        val s = range.start ?: return null
        val e = range.end ?: return null
        return (java.time.temporal.ChronoUnit.DAYS.between(s, e) + 1).toInt()
    }

/**
 * What [kpi] is compared against. Rates use the whole previous period; totals use the same number
 * of elapsed days, so 25 days into a month isn't measured against a full 30.
 */
internal fun DashData.baseline(kpi: Kpi): Bucket? = if (kpi.additive) prevToDate else prevTotals

internal fun DashData.baselineLabel(kpi: Kpi): String {
    val e = elapsedDays
    return when {
        kpi.additive && e != null -> "vs the first ${plural(e, "day")} of last period"
        else -> periodDays?.let { "vs the previous ${plural(it, "day")}" } ?: "vs the previous period"
    }
}

/** Rounded surface card with an optional title row and trailing action. */
@Composable
internal fun DashCard(
    title: String? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    action: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    action?.invoke(this)
                }
                Spacer(Modifier.height(14.dp))
            }
            content()
        }
    }
}

/** One number in a stat grid: small label, the value, and an optional quiet footnote. */
@Composable
internal fun StatCell(label: String, value: String, sub: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 2.dp))
        if (sub != null) {
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A list row with an icon badge; tappable (chevron shown) when [onClick] is set. Min 56dp tall. */
@Composable
internal fun InfoRow(icon: ImageVector, title: String, value: String, sub: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Open", tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
internal fun EmptyNote(
    icon: ImageVector,
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(icon, 72.dp)
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

/** Header-less table: [weights] per column; first column start-aligned, the rest end-aligned. */
@Composable
internal fun TableRow(cells: List<String>, weights: List<Float>, header: Boolean = false, emphasize: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (emphasize) it.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), RoundedCornerShape(10.dp)) else it }
            .padding(horizontal = 8.dp, vertical = if (header) 4.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        cells.forEachIndexed { i, c ->
            Text(
                c,
                style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                maxLines = 1,
                modifier = Modifier.weight(weights[i]),
            )
        }
    }
}

internal fun axisMoney(v: Double): String = when {
    v >= 10_000 -> "$" + "%.0fk".format(v / 1000)
    v >= 1000 -> "$" + "%.1fk".format(v / 1000).replace(".0k", "k")
    else -> "$" + "%.0f".format(v)
}

internal fun Metric.format(v: Double): String = when (this) {
    Metric.TIPS, Metric.SALES -> v.money()
    Metric.TIP_PCT -> v.pct()
    Metric.PER_HOUR -> v.money() + "/hr"
}

internal fun Metric.axis(v: Double): String = when (this) {
    Metric.TIP_PCT -> "%.0f%%".format(v)
    else -> axisMoney(v)
}

internal fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

internal fun wholeDollars(v: Double) = "$" + "%,.0f".format(v)

// ---- Kpi presentation ------------------------------------------------------------------------

internal fun Kpi.title(takeHome: Boolean) = if (this == Kpi.TIPS && takeHome) "Cash take-home" else label

internal val Kpi.isPercent get() = this == Kpi.TIP_PCT || this == Kpi.LIQUOR_SHARE

/** Full value with its unit, for readouts: "$31.20/hr", "6.5 h", "42 guests". */
internal fun Kpi.format(v: Double): String = when (this) {
    Kpi.TIP_PCT, Kpi.LIQUOR_SHARE -> v.pct()
    Kpi.TIPS_PER_HOUR, Kpi.SALES_PER_HOUR -> v.money() + "/hr"
    Kpi.HOURS -> "%.1f h".format(v)
    Kpi.GUESTS -> (if (v % 1.0 == 0.0) "%,.0f" else "%.1f").format(v) + " guests"
    else -> v.money()
}

/** Value where the label already names the unit (stat tiles): "$31.20", "42". */
internal fun Kpi.formatBare(v: Double): String = when (this) {
    Kpi.TIPS_PER_HOUR, Kpi.SALES_PER_HOUR -> v.money()
    Kpi.GUESTS -> "%,.0f".format(v)
    else -> format(v)
}

/** Compact value for tight spots like heatmap cells. */
internal fun Kpi.formatCompact(v: Double): String = when {
    isPercent -> "%.0f%%".format(v)
    this == Kpi.HOURS -> "%.1fh".format(v)
    this == Kpi.GUESTS -> "%.0f".format(v)
    else -> axisMoney(v)
}

internal fun Kpi.axis(v: Double): String {
    val whole = v % 1.0 == 0.0
    return when {
        isPercent -> (if (whole) "%.0f%%" else "%.1f%%").format(v)
        this == Kpi.HOURS -> (if (whole) "%.0fh" else "%.1fh").format(v)
        this == Kpi.GUESTS -> if (whole) "%.0f".format(v) else ""
        !whole && v < 100 -> "$" + "%.1f".format(v)
        else -> axisMoney(v)
    }
}

/** One-line definition shown under a measure's name. */
internal fun Kpi.blurb(takeHome: Boolean): String = when (this) {
    Kpi.TIPS -> if (takeHome) "Cash you walked out with (tips where you didn't record it)." else "Everything you made in tips."
    Kpi.TIP_PCT -> "Tips as a share of your sales."
    Kpi.TIPS_PER_HOUR -> "Tips divided by hours on the clock."
    Kpi.TIPS_PER_SHIFT -> "What a typical shift brings in."
    Kpi.SALES -> "Everything you rang in."
    Kpi.SALES_PER_SHIFT -> "What you ring in on a typical shift."
    Kpi.SALES_PER_HOUR -> "Sales divided by hours on the clock."
    Kpi.HOURS -> "Time on the clock, from your open and close times."
    Kpi.GUESTS -> "Guests (covers) you served."
    Kpi.TIPS_PER_GUEST -> "Tips divided by guests served."
    Kpi.AVG_CHECK -> "What each guest spends on average."
    Kpi.LIQUOR_SHARE -> "Liquor, wine and beer as a share of your sales."
    Kpi.TAKE_HOME -> "Cash you walked out with, where you recorded it."
}

/** What a shift needs to count toward this measure, or null when every shift counts. */
internal val Kpi.requirement: String?
    get() = when (this) {
        Kpi.TIP_PCT -> "sales"
        Kpi.TIPS_PER_HOUR, Kpi.SALES_PER_HOUR, Kpi.HOURS -> "clock times"
        Kpi.GUESTS, Kpi.TIPS_PER_GUEST, Kpi.AVG_CHECK -> "a guest count"
        Kpi.LIQUOR_SHARE -> "liquor sales"
        Kpi.TAKE_HOME -> "take-home recorded"
        else -> null
    }

/** A low–high span with the unit written once: "$20.48–$42.02/hr", "$101–$276", "15.2%–17.9%". */
internal fun Kpi.range(lo: Double, hi: Double): String {
    fun m(v: Double) = if (maxOf(lo, hi) >= 100) wholeDollars(v) else v.money()
    return when (this) {
        Kpi.TIP_PCT, Kpi.LIQUOR_SHARE -> "${lo.pct()}–${hi.pct()}"
        Kpi.TIPS_PER_HOUR, Kpi.SALES_PER_HOUR -> "${m(lo)}–${m(hi)}/hr"
        Kpi.HOURS -> "%.1f–%.1f h".format(lo, hi)
        Kpi.GUESTS -> "%.0f–%.0f guests".format(lo, hi)
        else -> "${m(lo)}–${m(hi)}"
    }
}

/**
 * Plain comparison with an earlier value. Percent measures name the old rate ("Up from 17.8%")
 * so there's no percent-of-a-percent to decode; everything else gives the relative change.
 */
internal fun Kpi.change(now: Double, before: Double): String {
    val same = if (isPercent) kotlin.math.abs(now - before) < 0.05 else before != 0.0 && kotlin.math.abs(now - before) / before < 0.005
    return when {
        same -> "Same as before (${format(before)})"
        isPercent || before == 0.0 -> "${if (now > before) "Up" else "Down"} from ${format(before)}"
        else -> "${if (now > before) "Up" else "Down"} ${"%.0f".format(kotlin.math.abs(now - before) / before * 100)}% from ${format(before)}"
    }
}
