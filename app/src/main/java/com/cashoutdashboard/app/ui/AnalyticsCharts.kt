package com.cashoutdashboard.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Three or more clean ticks spanning [minV]..[maxV]. Starts at zero unless the data sits in a
 * narrow band far above it (e.g. tip % between 16 and 20), where a zero baseline would flatten it.
 */
internal fun niceTicks(minV: Double, maxV: Double): List<Double> {
    if (maxV <= 0) return listOf(0.0, 0.5, 1.0)
    if (minV < maxV * 0.5) {
        val top = niceCeil(maxV)
        return listOf(0.0, top / 2, top)
    }
    val step = niceCeil(((maxV - minV) / 2).coerceAtLeast(maxV * 0.01))
    val lo = floor(minV / step) * step
    var n = 2
    while (lo + n * step < maxV - 1e-9) n++
    return (0..n).map { lo + it * step }
}

/** What a legend entry's key looks like; the text beside it always stays in the text colors. */
enum class LegendMark { BAR, LINE, MUTED_LINE, INK_LINE, DASHED, BAND, DOT, RING }

@Composable
fun LegendRow(items: List<Pair<LegendMark, String>>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (mark, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(width = 16.dp, height = 12.dp)) {
                    val cy = size.height / 2
                    when (mark) {
                        LegendMark.BAR -> drawRoundRect(colors.primary, Offset(3.dp.toPx(), 1.dp.toPx()), Size(10.dp.toPx(), 10.dp.toPx()), CornerRadius(2.dp.toPx()))
                        LegendMark.BAND -> drawRoundRect(colors.primary.copy(alpha = 0.16f), Offset.Zero, size, CornerRadius(2.dp.toPx()))
                        LegendMark.LINE -> drawLine(colors.primary, Offset(0f, cy), Offset(size.width, cy), 2.dp.toPx(), StrokeCap.Round)
                        LegendMark.MUTED_LINE -> drawLine(colors.outline, Offset(0f, cy), Offset(size.width, cy), 2.dp.toPx(), StrokeCap.Round)
                        LegendMark.INK_LINE -> drawLine(colors.onSurface, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2.dp.toPx(), StrokeCap.Round)
                        LegendMark.DASHED -> drawLine(
                            colors.onSurfaceVariant, Offset(0f, cy), Offset(size.width, cy), 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                        )
                        LegendMark.DOT -> drawCircle(colors.primary, 4.dp.toPx(), center)
                        LegendMark.RING -> drawCircle(colors.outline, 3.5.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

/**
 * Line chart for rates and running totals. One primary series (2dp line over a soft area), an
 * optional [ghost] series for context (muted line, same unit and axis — never a second scale) and a
 * dashed [reference] level such as the period average. Tap or drag across the plot to scrub; a
 * crosshair marks the selected point and the readout row above describes it. Null values are gaps.
 */
@Composable
fun LineChart(
    values: List<Double?>,
    labels: List<String>,
    format: (Double) -> String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    axisFormat: (Double) -> String = format,
    detail: (Int) -> String = { i -> "${labels[i]} · ${values[i]?.let(format) ?: "no data"}" },
    ghost: List<Double?>? = null,
    reference: Double? = null,
    height: Dp = 200.dp,
    placeholder: String = "Tap or drag across the chart for details",
) {
    val colors = MaterialTheme.colorScheme
    val line = colors.primary
    val ghostColor = colors.outline
    val grid = colors.outlineVariant
    val surface = colors.surfaceContainerLow
    val cross = colors.onSurfaceVariant
    val axisText = TextStyle(color = colors.onSurfaceVariant, fontSize = 10.sp)
    val measurer = rememberTextMeasurer()
    val ticks = remember(values, ghost, reference) {
        val all = values.filterNotNull() + ghost.orEmpty().filterNotNull() + listOfNotNull(reference)
        niceTicks(all.minOrNull() ?: 0.0, all.maxOrNull() ?: 1.0)
    }
    val lo = ticks.first()
    val span = (ticks.last() - lo).takeIf { it > 1e-9 } ?: 1.0
    val grow = rememberGrowth(values)
    val tickLabels = ticks.map(axisFormat)
    val tickLayouts = remember(tickLabels, axisText) { tickLabels.map { measurer.measure(it, axisText) } }
    val labelLayouts = remember(labels, axisText) { labels.map { measurer.measure(it, axisText) } }
    val labelWidth = labelLayouts.maxOfOrNull { it.size.width } ?: 0
    val haptics = LocalHapticFeedback.current
    val n = values.size
    val pad = 10.dp

    // Nearest index with data to an x position, so scrubbing skips gaps.
    fun nearest(x: Float, width: Float, padPx: Float): Int? {
        if (n == 0) return null
        val raw = if (n > 1) ((x - padPx) / ((width - padPx * 2) / (n - 1))).roundToInt() else 0
        val i = raw.coerceIn(0, n - 1)
        return (0 until n).filter { values[it] != null }.minByOrNull { abs(it - i) }
    }

    Column(modifier) {
        Text(
            text = selected?.takeIf { it in values.indices }?.let(detail) ?: placeholder,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected != null) colors.onSurface else colors.onSurfaceVariant,
            fontWeight = if (selected != null) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(Modifier.fillMaxWidth().height(height)) {
            Canvas(Modifier.width(44.dp).fillMaxHeight()) {
                val plotBottom = size.height - 18.dp.toPx()
                val plotTop = 6.dp.toPx()
                ticks.forEachIndexed { i, t ->
                    val y = plotBottom - ((t - lo) / span * (plotBottom - plotTop)).toFloat()
                    val layout = tickLayouts[i]
                    drawText(layout, topLeft = Offset(size.width - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
                }
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(values, selected) {
                        detectTapGestures { pos ->
                            val i = nearest(pos.x, size.width.toFloat(), pad.toPx())
                            if (i != null && i != selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(if (i == selected) null else i)
                        }
                    }
                    .pointerInput(values) {
                        var last: Int? = null
                        detectHorizontalDragGestures(
                            onDragStart = { pos -> last = nearest(pos.x, size.width.toFloat(), pad.toPx()); onSelect(last) },
                        ) { change, _ ->
                            val i = nearest(change.position.x, size.width.toFloat(), pad.toPx())
                            if (i != last) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                last = i
                                onSelect(i)
                            }
                        }
                    }
            ) {
                val plotBottom = size.height - 18.dp.toPx()
                val plotTop = 6.dp.toPx()
                val left = pad.toPx()
                val right = size.width - pad.toPx()
                val step = if (n > 1) (right - left) / (n - 1) else 0f
                fun x(i: Int) = if (n > 1) left + step * i else size.width / 2
                fun y(v: Double) = plotBottom - ((v - lo) / span * (plotBottom - plotTop)).toFloat()
                ticks.forEach { t -> drawLine(grid, Offset(0f, y(t)), Offset(size.width, y(t)), 1f) }

                // Thin the x labels, anchored on the most recent point.
                val every = if (n > 1) max(1, ceil((labelWidth + 8.dp.toPx()) / step).toInt()) else 1
                labelLayouts.forEachIndexed { i, layout ->
                    if ((n - 1 - i) % every == 0) {
                        val lx = (x(i) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                        drawText(layout, topLeft = Offset(lx, plotBottom + 4.dp.toPx()))
                    }
                }

                fun runs(series: List<Double?>): List<IntRange> {
                    val out = mutableListOf<IntRange>()
                    var start = -1
                    for (i in 0..series.size) {
                        val has = i < series.size && series[i] != null
                        if (has && start < 0) start = i
                        if (!has && start >= 0) { out += start until i; start = -1 }
                    }
                    return out
                }
                fun pathOf(series: List<Double?>, r: IntRange) = Path().apply {
                    moveTo(x(r.first), y(series[r.first]!!))
                    for (j in r.first + 1..r.last) lineTo(x(j), y(series[j]!!))
                }
                val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

                clipRect(right = size.width * grow) {
                    ghost?.let { g -> runs(g).forEach { drawPath(pathOf(g, it), ghostColor, style = stroke) } }
                    val mainRuns = runs(values)
                    mainRuns.filter { it.last > it.first }.forEach { r ->
                        val area = pathOf(values, r).apply {
                            lineTo(x(r.last), plotBottom)
                            lineTo(x(r.first), plotBottom)
                            close()
                        }
                        drawPath(area, Brush.verticalGradient(listOf(line.copy(alpha = 0.22f), line.copy(alpha = 0f)), startY = plotTop, endY = plotBottom))
                    }
                    mainRuns.forEach { drawPath(pathOf(values, it), line, style = stroke) }
                    // Markers only while they have room; isolated points and the latest always get one.
                    val roomy = step >= 12.dp.toPx() || n == 1
                    mainRuns.forEach { r ->
                        for (i in r) {
                            if (roomy || r.first == r.last || i == n - 1) {
                                drawCircle(surface, 5.dp.toPx(), Offset(x(i), y(values[i]!!)))
                                drawCircle(line, 3.dp.toPx(), Offset(x(i), y(values[i]!!)))
                            }
                        }
                    }
                }
                if (reference != null && grow > 0.99f) {
                    drawLine(
                        cross, Offset(0f, y(reference)), Offset(size.width, y(reference)), 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                    )
                }
                val sel = selected?.takeIf { it in values.indices }
                val sv = sel?.let { values[it] }
                if (sel != null && sv != null) {
                    drawLine(cross, Offset(x(sel), plotTop), Offset(x(sel), plotBottom), 1f)
                    ghost?.getOrNull(sel)?.let { g ->
                        drawCircle(surface, 5.5.dp.toPx(), Offset(x(sel), y(g)))
                        drawCircle(ghostColor, 3.5.dp.toPx(), Offset(x(sel), y(g)))
                    }
                    drawCircle(surface, 8.dp.toPx(), Offset(x(sel), y(sv)))
                    drawCircle(line, 5.5.dp.toPx(), Offset(x(sel), y(sv)))
                }
            }
        }
    }
}

/**
 * One dot per shift along a single value axis, showing how spread out results are. The middle half
 * (25th–75th percentile) is a tinted band and the median a solid line. Dots are jittered vertically
 * (deterministically) so ties stay visible; tap one to select it.
 */
@Composable
fun StripPlot(
    values: List<Double>,
    axisFormat: (Double) -> String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 112.dp,
) {
    if (values.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val dot = colors.primary
    val muted = colors.primary.copy(alpha = 0.4f)
    val band = colors.primary.copy(alpha = 0.16f)
    val ring = colors.surfaceContainerLow
    val medianColor = colors.onSurface
    val grid = colors.outlineVariant
    val axisText = TextStyle(color = colors.onSurfaceVariant, fontSize = 10.sp)
    val measurer = rememberTextMeasurer()
    val ticks = remember(values) { niceTicks(values.min(), values.max()) }
    val lo = ticks.first()
    val span = (ticks.last() - lo).takeIf { it > 1e-9 } ?: 1.0
    val quartiles = remember(values) {
        val sorted = values.sorted()
        listOf(0.25, 0.5, 0.75).map { p ->
            val pos = p * (sorted.size - 1)
            val a = sorted[pos.toInt()]
            val b = sorted[min(pos.toInt() + 1, sorted.lastIndex)]
            a + (b - a) * (pos - pos.toInt())
        }
    }
    val (q1, med, q3) = quartiles
    // Golden-ratio jitter spreads dots evenly without randomness, so the layout is stable.
    val jitter = remember(values.size) { List(values.size) { ((it * 0.618034) % 1.0).toFloat() } }
    val tickLayouts = remember(ticks, axisText) { ticks.map { measurer.measure(axisFormat(it), axisText) } }
    val grow = rememberGrowth(values)
    val haptics = LocalHapticFeedback.current

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(values, selected) {
                val left = 8.dp.toPx()
                val right = size.width - 8.dp.toPx()
                val top = 8.dp.toPx()
                val bottom = size.height - 22.dp.toPx()
                fun center(i: Int) = Offset(
                    left + ((values[i] - lo) / span * (right - left)).toFloat(),
                    top + 6.dp.toPx() + jitter[i] * (bottom - top - 12.dp.toPx()),
                )
                detectTapGestures { pos ->
                    val hit = values.indices
                        .minByOrNull { (center(it) - pos).getDistance() }
                        ?.takeIf { (center(it) - pos).getDistance() < 28.dp.toPx() }
                    if (hit != null && hit != selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(if (hit == selected) null else hit)
                }
            }
    ) {
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        fun px(v: Double) = left + ((v - lo) / span * (right - left)).toFloat()
        fun center(i: Int) = Offset(px(values[i]), top + 6.dp.toPx() + jitter[i] * (bottom - top - 12.dp.toPx()))
        ticks.forEachIndexed { i, t ->
            drawLine(grid, Offset(px(t), top), Offset(px(t), bottom), 1f)
            val l = tickLayouts[i]
            drawText(l, topLeft = Offset((px(t) - l.size.width / 2f).coerceIn(0f, size.width - l.size.width), bottom + 6.dp.toPx()))
        }
        drawRoundRect(band, Offset(px(q1), top), Size(max(px(q3) - px(q1), 2.dp.toPx()), bottom - top), CornerRadius(4.dp.toPx()))
        drawLine(medianColor, Offset(px(med), top), Offset(px(med), bottom), 2.dp.toPx(), StrokeCap.Round)
        val r = 4.dp.toPx() * grow
        values.indices.forEach { i ->
            if (i == selected) return@forEach
            drawCircle(ring, r + 2.dp.toPx() * grow, center(i))
            drawCircle(if (selected == null) dot else muted, r, center(i))
        }
        selected?.takeIf { it in values.indices }?.let { i ->
            drawCircle(ring, r * 1.6f + 2.dp.toPx(), center(i))
            drawCircle(dot, r * 1.6f, center(i))
        }
    }
}

/**
 * Before → after on one row: a hollow ring at [before], a filled dot at [after], joined by a bar,
 * on a zero-based track that ends at [scaleMax]. The dot slides out from the ring.
 */
@Composable
fun Dumbbell(before: Double, after: Double, scaleMax: Double, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val track = colors.surfaceContainerHighest
    val link = colors.primary.copy(alpha = 0.35f)
    val ring = colors.outline
    val dot = colors.primary
    val surface = colors.surfaceContainerLow
    val grow = rememberGrowth(before to after)
    Canvas(modifier.fillMaxWidth().height(20.dp)) {
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val cy = size.height / 2
        fun px(v: Double) = left + (v / scaleMax).coerceIn(0.0, 1.0).toFloat() * (right - left)
        drawLine(track, Offset(left, cy), Offset(right, cy), 4.dp.toPx(), StrokeCap.Round)
        val from = px(before)
        val to = from + (px(after) - from) * grow
        drawLine(link, Offset(from, cy), Offset(to, cy), 6.dp.toPx(), StrokeCap.Round)
        drawCircle(surface, 7.dp.toPx(), Offset(from, cy))
        drawCircle(ring, 5.dp.toPx(), Offset(from, cy), style = Stroke(2.dp.toPx()))
        drawCircle(surface, 8.dp.toPx(), Offset(to, cy))
        drawCircle(dot, 6.dp.toPx(), Offset(to, cy))
    }
}
