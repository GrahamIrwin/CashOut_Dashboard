package com.cashoutdashboard.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Rounds [v] up to a clean axis maximum (1, 1.5, 2, 2.5, 3, 4, 5, 6, 8 x 10^n) whose half is also clean. */
internal fun niceCeil(v: Double): Double {
    if (v <= 0) return 1.0
    val exp = floor(log10(v))
    val base = 10.0.pow(exp)
    val f = v / base
    val nice = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0).first { it >= f - 1e-9 }
    return nice * base
}

/** Grows marks in from the baseline whenever [key] changes. */
@Composable
internal fun rememberGrowth(key: Any?): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(key) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }
    return anim.value
}

/**
 * Single-measure column chart. Columns are capped at 24dp, rounded 4dp at the data end and square at
 * the baseline; hairline gridlines at clean ticks on a fixed y-axis. An optional [overlay] draws a
 * 2dp line in the same unit (e.g. a moving average) — the caller supplies a legend for it.
 * Tapping a column's slot selects it (hit target larger than the mark); the readout row shows it.
 * Scrolls horizontally when there are more columns than fit, starting at the most recent.
 */
@Composable
fun ColumnChart(
    values: List<Double>,
    labels: List<String>,
    format: (Double) -> String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    axisFormat: (Double) -> String = format,
    detail: (Int) -> String = { "${labels[it]} · ${format(values[it])}" },
    highlight: Int? = null,
    height: Dp = 180.dp,
    minSlot: Dp = 32.dp,
    overlay: List<Double?>? = null,
    placeholder: String = "Tap a bar for details",
) {
    val colors = MaterialTheme.colorScheme
    val barColor = colors.primary
    val mutedBar = colors.primary.copy(alpha = 0.35f)
    val lineColor = colors.onSurface
    val surface = colors.surfaceContainerLow
    val gridColor = colors.outlineVariant
    val axisText = TextStyle(color = colors.onSurfaceVariant, fontSize = 10.sp)
    val measurer = rememberTextMeasurer()
    val maxV = niceCeil(max(values.maxOrNull() ?: 0.0, overlay?.filterNotNull()?.maxOrNull() ?: 0.0))
    val ticks = listOf(0.0, maxV / 2, maxV)
    val grow = rememberGrowth(values)
    // Measure text once per data change, not on every frame of the grow animation.
    val tickLabels = ticks.map(axisFormat)
    val tickLayouts = remember(tickLabels, axisText) { tickLabels.map { measurer.measure(it, axisText) } }
    val labelLayouts = remember(labels, axisText) { labels.map { measurer.measure(it, axisText) } }
    val labelWidth = labelLayouts.maxOfOrNull { it.size.width } ?: 0
    val haptics = LocalHapticFeedback.current
    val select: (Int?) -> Unit = { i ->
        if (i != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onSelect(i)
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
            // Fixed y-axis so tick labels stay visible while columns scroll.
            Canvas(Modifier.width(44.dp).fillMaxHeight()) {
                val plotBottom = size.height - 18.dp.toPx()
                val plotTop = 6.dp.toPx()
                ticks.forEachIndexed { i, t ->
                    val y = plotBottom - ((t / maxV) * (plotBottom - plotTop)).toFloat()
                    val layout = tickLayouts[i]
                    drawText(layout, topLeft = Offset(size.width - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val n = max(values.size, 1)
                val slot = max(minSlot.value, maxWidth.value / n).dp
                val contentWidth = slot * n
                val scroll = rememberScrollState()
                LaunchedEffect(values.size, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
                Box(Modifier.fillMaxHeight().let { if (contentWidth > maxWidth) it.horizontalScroll(scroll) else it }) {
                    Canvas(
                        Modifier
                            .width(contentWidth)
                            .fillMaxHeight()
                            .pointerInput(values, selected) {
                                detectTapGestures { pos ->
                                    val idx = (pos.x / slot.toPx()).toInt()
                                    if (idx in values.indices) select(if (idx == selected) null else idx)
                                }
                            }
                    ) {
                        val plotBottom = size.height - 18.dp.toPx()
                        val plotTop = 6.dp.toPx()
                        val plotH = plotBottom - plotTop
                        ticks.forEach { t ->
                            val y = plotBottom - ((t / maxV) * plotH).toFloat()
                            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        }
                        val slotPx = slot.toPx()
                        val barW = min(24.dp.toPx(), slotPx - 4.dp.toPx()).coerceAtLeast(2f)
                        val r = min(4.dp.toPx(), barW / 2)
                        val every = max(1, ceil((labelWidth + 6.dp.toPx()) / slotPx).toInt())
                        values.forEachIndexed { i, v ->
                            val cx = slotPx * i + slotPx / 2
                            val h = ((v / maxV) * plotH * grow).toFloat().coerceAtLeast(if (v > 0) 2f else 0f)
                            val color = when {
                                selected != null -> if (i == selected) barColor else mutedBar
                                highlight != null -> if (i == highlight) barColor else mutedBar
                                else -> barColor
                            }
                            if (h > 0) {
                                val path = Path().apply {
                                    addRoundRect(
                                        RoundRect(
                                            left = cx - barW / 2, top = plotBottom - h, right = cx + barW / 2, bottom = plotBottom,
                                            topLeftCornerRadius = CornerRadius(r, r), topRightCornerRadius = CornerRadius(r, r),
                                        )
                                    )
                                }
                                drawPath(path, color)
                            }
                            val mustShow = i == selected || i == values.lastIndex
                            val layout = labelLayouts.getOrNull(i)
                            if (layout != null && (i % every == 0 || mustShow)) {
                                val x = (cx - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                                drawText(layout, topLeft = Offset(x, plotBottom + 4.dp.toPx()))
                            }
                        }
                        drawLine(gridColor, Offset(0f, plotBottom), Offset(size.width, plotBottom), strokeWidth = 1f)
                        if (overlay != null && grow > 0.99f) {
                            val path = Path()
                            var started = false
                            overlay.forEachIndexed { i, v ->
                                if (v == null) return@forEachIndexed
                                val x = slotPx * i + slotPx / 2
                                val y = plotBottom - ((v / maxV) * plotH).toFloat()
                                if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                            }
                            // Surface halo keeps the line legible where it crosses columns.
                            drawPath(path, surface, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                            drawPath(path, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
            }
        }
    }
}

/** Legend row: a swatch or line-key beside plain text (text never wears the data color). */
@Composable
fun ChartLegend(items: List<Pair<String, Boolean>>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (label, isLine) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLine) {
                    Box(Modifier.width(16.dp).height(2.dp).background(colors.onSurface, RoundedCornerShape(1.dp)))
                } else {
                    Box(Modifier.size(10.dp).background(colors.primary, RoundedCornerShape(2.dp)))
                }
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** A horizontal proportion bar: tinted track with a rounded fill for [fraction] (0..1). */
@Composable
fun ProportionBar(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, height: Dp = 8.dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "bar")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(color.copy(alpha = 0.15f))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width * animated
            if (w > 0f) drawRoundRect(color, size = Size(w, size.height), cornerRadius = CornerRadius(size.height / 2))
        }
    }
}

/** Tiny trend line for stat tiles / hero: 2dp line with an end dot, no axes; [fill] adds a soft area underneath. */
@Composable
fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier, fill: Boolean = false) {
    if (values.size < 2) return
    val grow = rememberGrowth(values)
    Canvas(modifier) {
        val lo = values.min()
        val hi = values.max()
        val span = (hi - lo).takeIf { it > 1e-9 } ?: 1.0
        val pad = 4.dp.toPx()
        val step = (size.width - pad * 2) / (values.size - 1)
        fun pt(i: Int) = Offset(pad + step * i, pad + ((1 - (values[i] - lo) / span) * (size.height - pad * 2)).toFloat())
        val shown = max(2, (values.size * grow).toInt())
        val path = Path().apply {
            moveTo(pt(0).x, pt(0).y)
            for (i in 1 until shown) lineTo(pt(i).x, pt(i).y)
        }
        if (fill) {
            val area = Path().apply {
                addPath(path)
                lineTo(pt(shown - 1).x, size.height)
                lineTo(pt(0).x, size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))))
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, 4.dp.toPx(), pt(shown - 1))
    }
}

/** Circular progress meter; the unfilled track is a lighter step of the same hue. */
@Composable
fun ProgressRing(progress: Float, color: Color, modifier: Modifier = Modifier, stroke: Dp = 10.dp, content: @Composable () -> Unit = {}) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(800, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = Size(size.width - w, size.height - w)
            drawArc(color.copy(alpha = 0.18f), -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            if (animated > 0f) {
                drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/**
 * Scatter plot of (x, y) points with an optional fitted line. Dots are 8dp with a 2dp surface ring;
 * tapping selects the nearest dot within 28dp.
 */
@Composable
fun ScatterChart(
    points: List<Pair<Double, Double>>,
    fit: ((Double) -> Double)?,
    xFormat: (Double) -> String,
    yFormat: (Double) -> String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
) {
    val colors = MaterialTheme.colorScheme
    val axisText = TextStyle(color = colors.onSurfaceVariant, fontSize = 10.sp)
    val measurer = rememberTextMeasurer()
    val maxX = niceCeil(points.maxOfOrNull { it.first } ?: 1.0)
    val maxY = niceCeil(points.maxOfOrNull { it.second } ?: 1.0)
    val dot = colors.primary
    val muted = colors.primary.copy(alpha = 0.45f)
    val ring = colors.surfaceContainerLow
    val grid = colors.outlineVariant
    val lineColor = colors.onSurface
    val grow = rememberGrowth(points)
    val haptics = LocalHapticFeedback.current
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(points, selected) {
                detectTapGestures { pos ->
                    val left = 44.dp.toPx()
                    val bottom = size.height - 20.dp.toPx()
                    val top = 8.dp.toPx()
                    val right = size.width - 8.dp.toPx()
                    val nearest = points.indices.minByOrNull { i ->
                        val px = left + (points[i].first / maxX * (right - left)).toFloat()
                        val py = bottom - (points[i].second / maxY * (bottom - top)).toFloat()
                        hypot(px - pos.x, py - pos.y)
                    }
                    val hit = nearest?.takeIf { i ->
                        val px = left + (points[i].first / maxX * (right - left)).toFloat()
                        val py = bottom - (points[i].second / maxY * (bottom - top)).toFloat()
                        hypot(px - pos.x, py - pos.y) < 28.dp.toPx()
                    }
                    if (hit != null && hit != selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(if (hit == selected) null else hit)
                }
            }
    ) {
        val left = 44.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val top = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        fun px(x: Double) = left + (x / maxX * (right - left)).toFloat()
        fun py(y: Double) = bottom - (y / maxY * (bottom - top)).toFloat()
        listOf(0.0, maxY / 2, maxY).forEach { t ->
            drawLine(grid, Offset(left, py(t)), Offset(right, py(t)), 1f)
            val l = measurer.measure(yFormat(t), axisText)
            drawText(l, topLeft = Offset(left - l.size.width - 6.dp.toPx(), py(t) - l.size.height / 2f))
        }
        listOf(0.0, maxX / 2, maxX).forEach { t ->
            val l = measurer.measure(xFormat(t), axisText)
            drawText(l, topLeft = Offset((px(t) - l.size.width / 2f).coerceIn(left - 10f, size.width - l.size.width), bottom + 4.dp.toPx()))
        }
        if (fit != null && grow > 0.99f) {
            // Only draw the fit across the data we have; don't extrapolate to zero.
            val x0 = points.minOf { it.first }
            val x1 = points.maxOf { it.first }
            val y0 = fit(x0).coerceIn(0.0, maxY)
            val y1 = fit(x1).coerceIn(0.0, maxY)
            drawLine(lineColor, Offset(px(x0), py(y0)), Offset(px(x1), py(y1)), 2.dp.toPx(), cap = StrokeCap.Round)
        }
        val r = 4.dp.toPx() * grow
        points.forEachIndexed { i, (x, y) ->
            val c = Offset(px(x), py(y))
            val isSel = i == selected
            drawCircle(ring, r + 2.dp.toPx() * grow, c)
            drawCircle(if (selected == null || isSel) dot else muted, if (isSel) r * 1.5f else r, c)
        }
    }
}

/**
 * One month as a calendar heatmap: cells shaded on a single-hue ramp by [values] (4 steps, light to
 * dark), days without data left neutral. Days with data are tappable.
 */
@Composable
fun CalendarMonth(
    month: YearMonth,
    values: Map<LocalDate, Double>,
    maxValue: Double,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val today = LocalDate.now()
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value - 1 // Monday-first
    val cells = lead + month.lengthOfMonth()
    val rows = ceil(cells / 7.0).toInt()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DayOfWeek.entries.forEach { d ->
                Text(
                    d.getDisplayName(JTextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c - lead
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (idx in 0 until month.lengthOfMonth()) {
                            val date = month.atDay(idx + 1)
                            val v = values[date]
                            val fill = if (v == null) colors.surfaceContainerHighest.copy(alpha = 0.5f) else heatColor(v / maxValue, colors.primary, colors.surfaceContainerHighest)
                            // Pick ink by the cell's own luminance so numbers stay legible on every step, in both themes.
                            val ink = if (fill.luminance() > 0.4f) Color(0xFF1A1B20) else Color.White
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(fill)
                                    .let { if (date == today) it.border(2.dp, colors.onSurface, RoundedCornerShape(6.dp)) else it }
                                    .let { if (v != null) it.clickable { onDayClick(date) } else it },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${idx + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (v == null) colors.onSurfaceVariant else ink,
                                    fontWeight = if (v != null) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 4-step sequential ramp from a light tint to the full hue. */
fun heatColor(t: Double, hue: Color, base: Color): Color {
    val step = when {
        t <= 0.25 -> 0.3f
        t <= 0.5 -> 0.52f
        t <= 0.75 -> 0.76f
        else -> 1f
    }
    return lerp(base, hue, step)
}

@Composable
fun HeatLegend(modifier: Modifier = Modifier, high: String = "More tips") {
    val colors = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Less", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        listOf(0.2, 0.4, 0.7, 1.0).forEach { t ->
            Box(Modifier.size(14.dp).background(heatColor(t, colors.primary, colors.surfaceContainerHighest), RoundedCornerShape(3.dp)))
        }
        Text(high, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
}
