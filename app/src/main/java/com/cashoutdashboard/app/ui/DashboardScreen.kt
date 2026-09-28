@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.cashoutdashboard.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cashoutdashboard.app.AppViewModel
import com.cashoutdashboard.app.stats.DateRange
import com.cashoutdashboard.app.stats.Kpi
import com.cashoutdashboard.app.stats.Period
import com.cashoutdashboard.app.stats.Stats
import com.cashoutdashboard.app.ui.dashboard.DashData
import com.cashoutdashboard.app.ui.dashboard.EmptyNote
import com.cashoutdashboard.app.ui.dashboard.MetricDetail
import com.cashoutdashboard.app.ui.dashboard.insightsTab
import com.cashoutdashboard.app.ui.dashboard.daysTab
import com.cashoutdashboard.app.ui.dashboard.overviewTab
import com.cashoutdashboard.app.ui.dashboard.recordsTab
import com.cashoutdashboard.app.ui.dashboard.simpleTab
import com.cashoutdashboard.app.data.GoalPeriod
import com.cashoutdashboard.app.ui.dashboard.trendsTab
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private val TABS = listOf("Overview", "Insights", "Trends", "Days", "Records")
private const val INSIGHTS_TAB = 1

@Composable
fun DashboardScreen(vm: AppViewModel, onOpenShift: (String) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val allShifts by vm.shifts.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var periodName by rememberSaveable { mutableStateOf(Period.MONTH.name) }
    var customStart by rememberSaveable { mutableStateOf(-1L) }
    var customEnd by rememberSaveable { mutableStateOf(-1L) }
    var showPicker by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(0) }
    // The measure being drilled into from a tile, if any; the period picker stays above it.
    var detailName by rememberSaveable { mutableStateOf<String?>(null) }
    val detail = detailName?.let { runCatching { Kpi.valueOf(it) }.getOrNull() }
    BackHandler(enabled = detail != null) { detailName = null }
    val period = Period.valueOf(periodName)

    val today = LocalDate.now()
    val custom = if (customStart >= 0 && customEnd >= 0) DateRange(LocalDate.ofEpochDay(customStart), LocalDate.ofEpochDay(customEnd)) else null
    val range = Stats.range(period, today, settings.payPeriodAnchorDate, custom)
    val takeHome = settings.useTakeHome
    val data = remember(allShifts, range, takeHome, today) {
        val shifts = Stats.filter(allShifts, range)
        val prevRange = Stats.previous(range)
        val prev = prevRange?.let { Stats.filter(allShifts, it) }.orEmpty()
        val elapsed = range.start?.takeIf { today in range && range.end != today }
            ?.let { (ChronoUnit.DAYS.between(it, today) + 1).toInt() }
        val prevTotals = prev.takeIf { it.isNotEmpty() }?.let { Stats.aggregate(it, takeHome) }
        DashData(
            all = allShifts, range = range, shifts = shifts, previous = prev,
            summary = Stats.summary(shifts, takeHome),
            prevSummary = prev.takeIf { it.isNotEmpty() }?.let { Stats.summary(it, takeHome) },
            takeHome = takeHome,
            totals = Stats.aggregate(shifts, takeHome),
            prevTotals = prevTotals,
            elapsedDays = elapsed,
            prevToDate = if (elapsed == null || prevRange == null) prevTotals else {
                val cutoff = prevRange.start!!.plusDays(elapsed.toLong())
                prev.filter { it.localDate.isBefore(cutoff) }.takeIf { it.isNotEmpty() }?.let { Stats.aggregate(it, takeHome) }
            },
        )
    }

    if (showPicker) {
        CustomRangeDialog(
            initial = custom,
            onDismiss = { showPicker = false },
            onConfirm = { s, e ->
                customStart = s.toEpochDay(); customEnd = e.toEpochDay()
                periodName = Period.CUSTOM.name
                showPicker = false
            },
        )
    }

    if (allShifts.isEmpty()) {
        Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
            EmptyNote(
                Icons.AutoMirrored.Filled.ReceiptLong,
                "Let's add your first cashout",
                "Snap a photo of tonight's slip, or pick a stack of old ones from your gallery. " +
                    "Your tips, best days and trends will show up here.",
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

    val pager = rememberPagerState(initialPage = tab) { TABS.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage) { tab = pager.currentPage }

    Column(modifier.fillMaxSize()) {
        PeriodHeader(
            period = period,
            range = range,
            hint = if (period == Period.PAY_PERIOD && settings.payPeriodAnchorDate == null) "Set your pay period start in Settings" else null,
            onSelect = { p -> if (p == Period.CUSTOM) showPicker = true else periodName = p.name },
        )
        val openMetric: (Kpi) -> Unit = { detailName = it.name }
        val saveGoal: (Double?, GoalPeriod) -> Unit = { amount, gp -> vm.updateSettings { it.copy(goalAmount = amount, goalPeriod = gp) } }
        val listPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)
        AnimatedContent(
            targetState = detail,
            contentKey = { it != null },
            transitionSpec = {
                val forward = targetState != null
                (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { w -> if (forward) w / 8 else -w / 8 }) togetherWith
                    fadeOut(tween(160))
            },
            modifier = Modifier.weight(1f),
            label = "dashboard-detail",
        ) { kpi ->
            if (kpi != null) {
                MetricDetail(
                    d = data,
                    kpi = kpi,
                    today = today,
                    onKpi = openMetric,
                    onBack = { detailName = null },
                    onOpenShift = onOpenShift,
                )
            } else if (settings.simpleDashboard) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    simpleTab(data, settings, today, onOpenMetric = openMetric, onSaveGoal = saveGoal)
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    PillTabs(
                        TABS,
                        pager,
                        onSelect = { i -> scope.launch { pager.animateScrollToPage(i, animationSpec = tween(280, easing = FastOutSlowInEasing)) } },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    HorizontalPager(pager, Modifier.weight(1f), beyondBoundsPageCount = 1) { page ->
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = listPadding,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            when (page) {
                                0 -> overviewTab(
                                    data, settings, today,
                                    onOpenMetric = openMetric,
                                    onOpenInsights = { scope.launch { pager.animateScrollToPage(INSIGHTS_TAB, animationSpec = tween(280, easing = FastOutSlowInEasing)) } },
                                    onSaveGoal = saveGoal,
                                )
                                1 -> insightsTab(data, openMetric, onOpenShift)
                                2 -> trendsTab(data, onOpenShift)
                                3 -> daysTab(data, onOpenShift)
                                else -> recordsTab(data, today, onOpenShift)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "This month ▾" title over the exact dates; tapping it opens the list of periods. */
@Composable
private fun PeriodHeader(period: Period, range: DateRange, hint: String?, onSelect: (Period) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(start = 8.dp, end = 16.dp, top = 12.dp)) {
        Column(
            Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable { open = true }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (period == Period.CUSTOM) "Custom range" else period.label, style = MaterialTheme.typography.headlineSmall)
                Icon(Icons.Filled.KeyboardArrowDown, "Change period", Modifier.padding(start = 2.dp).size(28.dp))
            }
            Text(range.label(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Period.entries.forEach { p ->
                DropdownMenuItem(
                    text = { Text(if (p == Period.CUSTOM) "Custom range…" else p.label) },
                    onClick = { open = false; onSelect(p) },
                    leadingIcon = if (p == Period.CUSTOM) {
                        { Icon(Icons.Filled.DateRange, null) }
                    } else null,
                    trailingIcon = if (p == period) {
                        { Icon(Icons.Filled.Check, "Selected", tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                )
            }
        }
    }
}

/**
 * Segmented pill tabs. The indicator follows the pager's scroll position directly (read in the
 * layout phase), so it tracks a swipe frame-by-frame without recomposing.
 */
@Composable
private fun PillTabs(titles: List<String>, pager: PagerState, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(colors.surfaceContainerHigh, CircleShape)
            .padding(3.dp)
    ) {
        val slot = maxWidth / titles.size
        Box(
            Modifier
                .offset { IntOffset((slot.toPx() * (pager.currentPage + pager.currentPageOffsetFraction)).roundToInt(), 0) }
                .width(slot)
                .fillMaxHeight()
                .shadow(1.dp, CircleShape)
                .background(colors.surfaceBright, CircleShape)
        )
        Row(Modifier.fillMaxSize()) {
            titles.forEachIndexed { i, title ->
                val selected = pager.targetPage == i
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        title,
                        style = if (titles.size > 4) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomRangeDialog(initial: DateRange?, onDismiss: () -> Unit, onConfirm: (LocalDate, LocalDate) -> Unit) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.start?.toUtcMillis(),
        initialSelectedEndDateMillis = initial?.end?.toUtcMillis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val s = state.selectedStartDateMillis!!.toUtcDate()
                    val e = state.selectedEndDateMillis?.toUtcDate() ?: s
                    onConfirm(s, e)
                },
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f), title = {
            Text("Custom range", Modifier.padding(start = 24.dp, top = 16.dp))
        })
    }
}

private fun LocalDate.toUtcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
