package com.cashoutdashboard.app.stats

import com.cashoutdashboard.app.data.GoalPeriod
import com.cashoutdashboard.app.data.Shift
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs

enum class Period(val label: String) {
    PAY_PERIOD("Pay period"),
    WEEK("This week"),
    TWO_WEEKS("2 weeks"),
    MONTH("This month"),
    LAST_30("30 days"),
    YEAR("This year"),
    ALL("All time"),
    CUSTOM("Custom"),
}

/** Inclusive date range; null bounds are open-ended. */
data class DateRange(val start: LocalDate?, val end: LocalDate?) {
    operator fun contains(d: LocalDate) = (start == null || !d.isBefore(start)) && (end == null || !d.isAfter(end))

    fun label(): String {
        val f = DateTimeFormatter.ofPattern("MMM d, yyyy")
        return when {
            start == null && end == null -> "All time"
            start == null -> "Through ${end!!.format(f)}"
            end == null -> "Since ${start.format(f)}"
            else -> "${start.format(DateTimeFormatter.ofPattern("MMM d"))} – ${end.format(f)}"
        }
    }
}

data class Summary(
    val shifts: Int,
    val tips: Double,
    val sales: Double,
    val tipPct: Double?,
    val hours: Double,
    val tipsPerHour: Double?,
    val avgTipsPerShift: Double?,
    val avgSalesPerShift: Double?,
    val covers: Int,
    val tipsPerCover: Double?,
    val avgCheck: Double?,
    /** Sum of recorded take-home amounts and how many shifts had one. */
    val takeHome: Double?,
    val takeHomeShifts: Int,
    val liquorShare: Double?,
    val best: Shift?,
    val worst: Shift?,
    val bestPct: Shift?,
)

data class DayStat(
    val day: DayOfWeek,
    val shifts: Int,
    val totalTips: Double,
    val avgTips: Double?,
    val tipPct: Double?,
    val tipsPerHour: Double?,
    val avgSales: Double?,
) {
    val shortName: String get() = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
}

data class Bucket(
    val label: String,
    val start: LocalDate,
    val shifts: Int,
    val tips: Double,
    val sales: Double,
    val hours: Double,
    /** Tips from shifts that have open/close times, so $/hr isn't inflated by untimed shifts. */
    val timedTips: Double = 0.0,
    val timedSales: Double = 0.0,
    val timedShifts: Int = 0,
    /** Guests and the tips/sales of the shifts that recorded them. */
    val covers: Int = 0,
    val coveredShifts: Int = 0,
    val coveredTips: Double = 0.0,
    val coveredSales: Double = 0.0,
    /** Liquor/wine/beer sales and the total sales of the shifts that broke them out. */
    val liquorSales: Double = 0.0,
    val liquorBase: Double = 0.0,
    val takeHome: Double = 0.0,
    val takeHomeShifts: Int = 0,
    /** Set when the bucket is a single shift, so charts can open it. */
    val shiftId: String? = null,
) {
    val tipPct: Double? get() = if (sales > 0) tips / sales * 100 else null
    val perHour: Double? get() = if (hours > 0) timedTips / hours else null
}

/**
 * Every headline number on the dashboard, measurable for any group of shifts. [additive] measures
 * sum across shifts (tips, hours); the rest are ratios that must be recomputed from totals.
 */
enum class Kpi(val label: String, val additive: Boolean) {
    TIPS("Tips earned", true),
    TIP_PCT("Tip rate", false),
    TIPS_PER_HOUR("Tips per hour", false),
    TIPS_PER_SHIFT("Avg per shift", false),
    SALES("Sales", true),
    SALES_PER_SHIFT("Sales per shift", false),
    SALES_PER_HOUR("Sales per hour", false),
    HOURS("Hours worked", true),
    GUESTS("Guests served", true),
    TIPS_PER_GUEST("Tips per guest", false),
    AVG_CHECK("Avg check", false),
    LIQUOR_SHARE("Liquor share", false),
    TAKE_HOME("Cash take-home", true);

    fun of(b: Bucket): Double? = when (this) {
        TIPS -> b.tips.takeIf { b.shifts > 0 }
        TIP_PCT -> b.tipPct
        TIPS_PER_HOUR -> b.perHour
        TIPS_PER_SHIFT -> if (b.shifts > 0) b.tips / b.shifts else null
        SALES -> b.sales.takeIf { b.shifts > 0 }
        SALES_PER_SHIFT -> if (b.shifts > 0) b.sales / b.shifts else null
        SALES_PER_HOUR -> if (b.hours > 0) b.timedSales / b.hours else null
        HOURS -> b.hours.takeIf { b.timedShifts > 0 }
        GUESTS -> b.covers.toDouble().takeIf { b.coveredShifts > 0 }
        TIPS_PER_GUEST -> if (b.covers > 0) b.coveredTips / b.covers else null
        AVG_CHECK -> if (b.covers > 0) b.coveredSales / b.covers else null
        LIQUOR_SHARE -> if (b.liquorBase > 0) b.liquorSales / b.liquorBase * 100 else null
        TAKE_HOME -> b.takeHome.takeIf { b.takeHomeShifts > 0 }
    }

    /** Typical single-shift value: additive measures averaged over the shifts that recorded them. */
    fun perShift(b: Bucket): Double? {
        if (!additive) return of(b)
        val n = when (this) {
            HOURS -> b.timedShifts
            GUESTS -> b.coveredShifts
            TAKE_HOME -> b.takeHomeShifts
            else -> b.shifts
        }
        return if (n > 0) of(b)?.let { it / n } else null
    }

    /** Whether [s] has the fields this measure needs (e.g. $/hr needs clock times). */
    fun counts(s: Shift): Boolean = when (this) {
        TIP_PCT -> s.sales > 0
        TIPS_PER_HOUR, SALES_PER_HOUR, HOURS -> s.hours != null
        GUESTS, TIPS_PER_GUEST, AVG_CHECK -> (s.covers ?: 0) > 0
        LIQUOR_SHARE -> s.lwbSales != null && s.sales > 0
        TAKE_HOME -> s.takeHome != null
        else -> true
    }
}

/** When a shift started, for time-of-day comparisons. */
enum class DayPart(val label: String, val hint: String) {
    LUNCH("Lunch", "starts before 2pm"),
    DINNER("Dinner", "starts 2–7pm"),
    LATE("Late", "starts 7pm or later");

    companion object {
        fun of(s: Shift): DayPart? = s.open?.let {
            when {
                it.hour < 14 -> LUNCH
                it.hour < 19 -> DINNER
                else -> LATE
            }
        }
    }
}

enum class InsightKind { BEST_DAY, PER_SHIFT_CHANGE, TIP_RATE_CHANGE, MOMENTUM, TIME_OF_DAY, SHIFT_LENGTH, BUSY_NIGHTS, BEST_MONTH, GUEST_VALUE, TYPICAL_RANGE }

/** One observation: a short [headline], the numbers behind it, and the measure to open for more. */
data class Insight(val kind: InsightKind, val headline: String, val detail: String, val kpi: Kpi? = null)

/** The measure a trend chart plots, one at a time (never a dual axis). */
enum class Metric(val label: String) {
    TIPS("Tips"),
    TIP_PCT("Tip %"),
    PER_HOUR("$/hr"),
    SALES("Sales");

    fun of(b: Bucket): Double? = when (this) {
        TIPS -> b.tips
        TIP_PCT -> b.tipPct
        PER_HOUR -> b.perHour
        SALES -> b.sales
    }
}

/** Weekday chart measures. */
enum class DayMetric(val label: String) {
    AVG_TIPS("Avg tips"),
    TIP_PCT("Tip %"),
    PER_HOUR("$/hr"),
    AVG_SALES("Avg sales"),
    SHIFTS("Shifts");

    fun of(d: DayStat): Double? = when (this) {
        AVG_TIPS -> d.avgTips
        TIP_PCT -> d.tipPct
        PER_HOUR -> d.tipsPerHour
        AVG_SALES -> d.avgSales
        SHIFTS -> d.shifts.toDouble().takeIf { it > 0 }
    }
}

data class HistBin(val label: String, val lo: Double, val hi: Double, val count: Int)

/** Least-squares line y = slope * x + intercept, with r^2 for how well it fits. */
data class Fit(val slope: Double, val intercept: Double, val r2: Double) {
    fun at(x: Double) = slope * x + intercept
}

/** Earnings so far in a window and where the current pace lands by its end. */
data class Pace(
    val range: DateRange,
    val earned: Double,
    val shifts: Int,
    val projected: Double,
    val expectedMoreShifts: Double,
    val daysLeft: Int,
)

data class Records(
    val bestShift: Shift?,
    val bestPct: Shift?,
    val bestPerHour: Shift?,
    val biggestSales: Shift?,
    val mostCovers: Shift?,
    val bestWeek: Bucket?,
    val bestMonth: Bucket?,
    val mostShiftsWeek: Bucket?,
    val longestStreak: Int,
)

data class Slice(val label: String, val amount: Double)

object Stats {
    fun range(period: Period, today: LocalDate, payAnchor: LocalDate?, custom: DateRange?): DateRange = when (period) {
        Period.PAY_PERIOD -> {
            val anchor = payAnchor ?: today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val days = ChronoUnit.DAYS.between(anchor, today)
            val start = anchor.plusDays(Math.floorDiv(days, 14L) * 14)
            DateRange(start, start.plusDays(13))
        }
        Period.WEEK -> {
            val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            DateRange(start, start.plusDays(6))
        }
        Period.TWO_WEEKS -> DateRange(today.minusDays(13), today)
        Period.MONTH -> DateRange(today.withDayOfMonth(1), today.with(TemporalAdjusters.lastDayOfMonth()))
        Period.LAST_30 -> DateRange(today.minusDays(29), today)
        Period.YEAR -> DateRange(today.withDayOfYear(1), today.with(TemporalAdjusters.lastDayOfYear()))
        Period.ALL -> DateRange(null, null)
        Period.CUSTOM -> custom ?: DateRange(null, null)
    }

    /** The equally long range immediately before [r], for period-over-period comparison. */
    fun previous(r: DateRange): DateRange? {
        val s = r.start ?: return null
        val e = r.end ?: return null
        val len = ChronoUnit.DAYS.between(s, e) + 1
        return DateRange(s.minusDays(len), s.minusDays(1))
    }

    fun filter(shifts: List<Shift>, r: DateRange) = shifts.filter { it.localDate in r }

    fun summary(shifts: List<Shift>, takeHome: Boolean): Summary {
        val tips = shifts.sumOf { it.effectiveTips(takeHome) }
        val sales = shifts.sumOf { it.sales }
        val timed = shifts.filter { it.hours != null }
        val hours = timed.sumOf { it.hours!! }
        val covered = shifts.filter { (it.covers ?: 0) > 0 }
        val covers = covered.sumOf { it.covers!! }
        val coveredSales = covered.sumOf { it.sales }
        val withLiquor = shifts.filter { it.lwbSales != null && it.sales > 0 }
        val takeHomes = shifts.mapNotNull { it.takeHome }
        return Summary(
            shifts = shifts.size,
            tips = tips,
            sales = sales,
            tipPct = if (sales > 0) tips / sales * 100 else null,
            hours = hours,
            tipsPerHour = if (hours > 0) timed.sumOf { it.effectiveTips(takeHome) } / hours else null,
            avgTipsPerShift = if (shifts.isNotEmpty()) tips / shifts.size else null,
            avgSalesPerShift = if (shifts.isNotEmpty()) sales / shifts.size else null,
            covers = covers,
            tipsPerCover = if (covers > 0) covered.sumOf { it.effectiveTips(takeHome) } / covers else null,
            avgCheck = if (covers > 0) coveredSales / covers else null,
            takeHome = if (takeHomes.isNotEmpty()) takeHomes.sum() else null,
            takeHomeShifts = takeHomes.size,
            liquorShare = withLiquor.sumOf { it.sales }.takeIf { it > 0 }
                ?.let { withLiquor.sumOf { s -> s.lwbSales!! } / it * 100 },
            best = shifts.maxByOrNull { it.effectiveTips(takeHome) },
            worst = shifts.minByOrNull { it.effectiveTips(takeHome) },
            bestPct = shifts.filter { it.sales > 0 }.maxByOrNull { it.tipPercent(takeHome)!! },
        )
    }

    fun byDayOfWeek(shifts: List<Shift>, takeHome: Boolean): List<DayStat> {
        val order = listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
        )
        val grouped = shifts.groupBy { it.dayOfWeek }
        return order.map { day ->
            val list = grouped[day].orEmpty()
            val tips = list.sumOf { it.effectiveTips(takeHome) }
            val sales = list.sumOf { it.sales }
            val timed = list.filter { it.hours != null }
            val hours = timed.sumOf { it.hours!! }
            DayStat(
                day = day,
                shifts = list.size,
                totalTips = tips,
                avgTips = if (list.isNotEmpty()) tips / list.size else null,
                tipPct = if (sales > 0) tips / sales * 100 else null,
                tipsPerHour = if (hours > 0) timed.sumOf { it.effectiveTips(takeHome) } / hours else null,
                avgSales = if (list.isNotEmpty()) sales / list.size else null,
            )
        }
    }

    enum class Grain { SHIFT, WEEK, MONTH }

    /** Picks a readable granularity for a trend chart given how much data there is. */
    fun grainFor(shifts: List<Shift>): Grain {
        if (shifts.size <= 31) return Grain.SHIFT
        val first = shifts.minOf { it.localDate }
        val last = shifts.maxOf { it.localDate }
        return if (ChronoUnit.DAYS.between(first, last) <= 7 * 26) Grain.WEEK else Grain.MONTH
    }

    /** Totals for any group of shifts; [Kpi.of] turns them into a headline number. */
    fun aggregate(list: List<Shift>, takeHome: Boolean, label: String = "", start: LocalDate = LocalDate.MIN, shiftId: String? = null): Bucket {
        val timed = list.filter { it.hours != null }
        val covered = list.filter { (it.covers ?: 0) > 0 }
        val liquor = list.filter { it.lwbSales != null && it.sales > 0 }
        val th = list.filter { it.takeHome != null }
        return Bucket(
            label = label, start = start, shifts = list.size,
            tips = list.sumOf { it.effectiveTips(takeHome) }, sales = list.sumOf { it.sales },
            hours = timed.sumOf { it.hours!! },
            timedTips = timed.sumOf { it.effectiveTips(takeHome) },
            timedSales = timed.sumOf { it.sales },
            timedShifts = timed.size,
            covers = covered.sumOf { it.covers!! },
            coveredShifts = covered.size,
            coveredTips = covered.sumOf { it.effectiveTips(takeHome) },
            coveredSales = covered.sumOf { it.sales },
            liquorSales = liquor.sumOf { it.lwbSales!! },
            liquorBase = liquor.sumOf { it.sales },
            takeHome = th.sumOf { it.takeHome!! },
            takeHomeShifts = th.size,
            shiftId = shiftId,
        )
    }

    fun buckets(shifts: List<Shift>, grain: Grain, takeHome: Boolean): List<Bucket> {
        val keyed: Map<LocalDate, List<Shift>> = when (grain) {
            Grain.SHIFT -> return shifts.sortedWith(compareBy({ it.date }, { it.openTime ?: "" })).map {
                aggregate(listOf(it), takeHome, it.localDate.format(DateTimeFormatter.ofPattern("M/d")), it.localDate, it.id)
            }
            Grain.WEEK -> shifts.groupBy { it.localDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            Grain.MONTH -> shifts.groupBy { it.localDate.withDayOfMonth(1) }
        }
        val fmt = DateTimeFormatter.ofPattern(if (grain == Grain.WEEK) "M/d" else "MMM ''yy")
        return keyed.toSortedMap().map { (start, list) -> aggregate(list, takeHome, start.format(fmt), start) }
    }

    /** Monday-first weekday groups; days without shifts come back as empty buckets. */
    fun weekdayBuckets(shifts: List<Shift>, takeHome: Boolean): List<Bucket> {
        val grouped = shifts.groupBy { it.dayOfWeek }
        return DayOfWeek.entries.map { day ->
            aggregate(grouped[day].orEmpty(), takeHome, day.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
        }
    }

    /** Weekday × time-of-day groups, for shifts with a start time. */
    fun weekdayByDayPart(shifts: List<Shift>, takeHome: Boolean): Map<Pair<DayOfWeek, DayPart>, Bucket> =
        shifts.mapNotNull { s -> DayPart.of(s)?.let { (s.dayOfWeek to it) to s } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, l) -> aggregate(l, takeHome) }

    val LENGTH_BANDS = listOf(0.0 to 4.0, 4.0 to 6.0, 6.0 to 8.0, 8.0 to Double.MAX_VALUE)

    /** Timed shifts grouped by length (non-empty bands only), to compare $/hr. */
    fun lengthBands(shifts: List<Shift>, takeHome: Boolean): List<Bucket> =
        LENGTH_BANDS.mapNotNull { (lo, hi) ->
            val list = shifts.filter { val h = it.hours; h != null && h >= lo && h < hi }
            if (list.isEmpty()) return@mapNotNull null
            val label = when {
                lo == 0.0 -> "<${hi.toInt()}h"
                hi == Double.MAX_VALUE -> "${lo.toInt()}h+"
                else -> "${lo.toInt()}–${hi.toInt()}h"
            }
            aggregate(list, takeHome, label)
        }

    /**
     * Shifts split into equal-size groups from slowest to busiest by sales (4 groups with 12+
     * shifts, 3 with 6+, 2 with 4+), labelled by their sales range.
     */
    fun salesBands(shifts: List<Shift>, takeHome: Boolean): List<Bucket> {
        val list = shifts.filter { it.sales > 0 }.sortedBy { it.sales }
        val k = when {
            list.size >= 12 -> 4
            list.size >= 6 -> 3
            list.size >= 4 -> 2
            else -> return emptyList()
        }
        return (0 until k).map { g ->
            val part = list.subList(g * list.size / k, (g + 1) * list.size / k)
            aggregate(part, takeHome, "${compactMoney(part.first().sales)}–${compactMoney(part.last().sales)}")
        }
    }

    private fun compactMoney(v: Double): String = when {
        v >= 1000 -> "$" + "%.1fk".format(v / 1000).replace(".0k", "k")
        else -> "$" + "%.0f".format(v)
    }

    /**
     * Day-by-day running total of an additive [kpi] across [start]..[end]. Days after [through]
     * are null so an in-progress period stops at today.
     */
    fun runningTotal(shifts: List<Shift>, kpi: Kpi, start: LocalDate, end: LocalDate, through: LocalDate, takeHome: Boolean): List<Double?> {
        val byDay = shifts.groupBy { it.localDate }
        val days = ChronoUnit.DAYS.between(start, end).toInt() + 1
        var total = 0.0
        return (0 until days).map { i ->
            val d = start.plusDays(i.toLong())
            if (d.isAfter(through)) return@map null
            total += byDay[d]?.let { kpi.of(aggregate(it, takeHome)) } ?: 0.0
            total
        }
    }

    fun byMonth(shifts: List<Shift>, takeHome: Boolean) = buckets(shifts, Grain.MONTH, takeHome).reversed()

    /** Sales by tender type, merged across shifts (cash vs. each card type). */
    fun paymentMix(shifts: List<Shift>): List<Slice> =
        shifts.flatMap { it.payments }
            .groupBy { normalizeTender(it.type) }
            .map { (k, v) -> Slice(k, v.sumOf { it.amount }) }
            .filter { it.amount > 0.005 }
            .sortedByDescending { it.amount }

    private fun normalizeTender(t: String): String {
        val u = t.uppercase().replace(Regex("[^A-Z ]"), "").trim()
        return when {
            u.startsWith("CASH") -> "Cash"
            u.startsWith("DEBIT") -> "Debit"
            u.startsWith("VISA") -> "Visa"
            u.startsWith("MAST") || u == "MC" -> "Mastercard"
            u.startsWith("AMEX") || u.startsWith("AMER") -> "Amex"
            u.startsWith("RDM") -> "Redeemed card"
            u.startsWith("OLD") -> "Old card"
            u.startsWith("QSA") -> "QSA"
            u.startsWith("BIRTH") -> "Birthday"
            u.startsWith("MANAGE") -> "Manager"
            u.startsWith("CERT") -> "Certificate"
            else -> u.lowercase().replaceFirstChar { it.uppercase() }.ifBlank { "Other" }
        }
    }

    /** Longest run of consecutive calendar days worked in the given shifts. */
    fun longestStreak(shifts: List<Shift>): Int {
        val days = shifts.map { it.localDate }.distinct().sorted()
        var best = 0
        var cur = 0
        var prev: LocalDate? = null
        for (d in days) {
            cur = if (prev != null && prev.plusDays(1) == d) cur + 1 else 1
            best = maxOf(best, cur)
            prev = d
        }
        return best
    }

    // ---- Goals & pace ----------------------------------------------------------------------

    /** The current window a goal of [period] covers (this week, this pay period, ...). */
    fun goalRange(period: GoalPeriod, today: LocalDate, payAnchor: LocalDate?): DateRange = when (period) {
        GoalPeriod.WEEK -> range(Period.WEEK, today, payAnchor, null)
        GoalPeriod.PAY_PERIOD -> range(Period.PAY_PERIOD, today, payAnchor, null)
        GoalPeriod.MONTH -> range(Period.MONTH, today, payAnchor, null)
        GoalPeriod.YEAR -> range(Period.YEAR, today, payAnchor, null)
    }

    /**
     * Projects earnings to the end of [r] (a bounded window containing [today]) from the recent
     * rate of work: days worked per calendar day and tips per shift over the last [lookbackDays]
     * (or all history if shorter). Returns null when there's no history to learn a rate from.
     */
    fun pace(all: List<Shift>, r: DateRange, today: LocalDate, takeHome: Boolean, lookbackDays: Long = 56): Pace? {
        val start = r.start ?: return null
        val end = r.end ?: return null
        val inWindow = all.filter { it.localDate in DateRange(start, minOf(end, today)) }
        val earned = inWindow.sumOf { it.effectiveTips(takeHome) }
        val history = all.filter { !it.localDate.isAfter(today) }
        if (history.isEmpty()) return null
        val first = history.minOf { it.localDate }
        val lookStart = maxOf(first, today.minusDays(lookbackDays - 1))
        val recent = history.filter { !it.localDate.isBefore(lookStart) }
        val spanDays = ChronoUnit.DAYS.between(lookStart, today) + 1
        // With under a week of history, assume a typical 4 shifts a week rather than extrapolating.
        val perDay = if (spanDays < 7 || recent.isEmpty()) 4.0 / 7 else recent.map { it.localDate }.distinct().size.toDouble() / spanDays
        val basis = recent.ifEmpty { history }
        val avgTips = basis.sumOf { it.effectiveTips(takeHome) } / basis.size
        val workedToday = all.any { it.localDate == today }
        val remainingFrom = if (workedToday) today.plusDays(1) else today
        val daysLeft = if (remainingFrom.isAfter(end)) 0 else (ChronoUnit.DAYS.between(remainingFrom, end) + 1).toInt()
        val more = perDay * daysLeft
        return Pace(r, earned, inWindow.size, earned + more * avgTips, more, daysLeft)
    }

    // ---- Trends -----------------------------------------------------------------------------

    /** Trailing moving average over the last [window] non-null values; null until data exists. */
    fun movingAverage(values: List<Double?>, window: Int): List<Double?> {
        val out = ArrayList<Double?>(values.size)
        val buf = ArrayDeque<Double>()
        for (v in values) {
            if (v != null) {
                buf.addLast(v)
                if (buf.size > window) buf.removeFirst()
            }
            out += if (buf.isEmpty()) null else buf.sum() / buf.size
        }
        return out
    }

    val TIP_PCT_BINS = listOf(0.0 to 10.0, 10.0 to 12.0, 12.0 to 15.0, 15.0 to 18.0, 18.0 to Double.MAX_VALUE)

    /** How many shifts fall into each tip-% band. */
    fun tipPctHistogram(shifts: List<Shift>, takeHome: Boolean): List<HistBin> {
        val pcts = shifts.mapNotNull { it.tipPercent(takeHome) }
        return TIP_PCT_BINS.map { (lo, hi) ->
            val label = when {
                lo == 0.0 -> "<${hi.toInt()}%"
                hi == Double.MAX_VALUE -> "${lo.toInt()}%+"
                else -> "${lo.toInt()}–${hi.toInt()}%"
            }
            HistBin(label, lo, hi, pcts.count { it >= lo && it < hi })
        }
    }

    fun linearFit(points: List<Pair<Double, Double>>): Fit? {
        if (points.size < 3) return null
        val n = points.size.toDouble()
        val mx = points.sumOf { it.first } / n
        val my = points.sumOf { it.second } / n
        val sxx = points.sumOf { (it.first - mx) * (it.first - mx) }
        if (sxx <= 1e-9) return null
        val sxy = points.sumOf { (it.first - mx) * (it.second - my) }
        val slope = sxy / sxx
        val intercept = my - slope * mx
        val ssTot = points.sumOf { (it.second - my) * (it.second - my) }
        val ssRes = points.sumOf { val e = it.second - (slope * it.first + intercept); e * e }
        val r2 = if (ssTot <= 1e-9) 1.0 else 1 - ssRes / ssTot
        return Fit(slope, intercept, r2)
    }

    /** Linear-interpolated percentile (p in 0..1) of [values]. */
    fun percentile(values: List<Double>, p: Double): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val pos = p * (sorted.size - 1)
        val lo = sorted[pos.toInt()]
        val hi = sorted[minOf(pos.toInt() + 1, sorted.lastIndex)]
        return lo + (hi - lo) * (pos - pos.toInt())
    }

    // ---- Records ----------------------------------------------------------------------------

    fun records(shifts: List<Shift>, takeHome: Boolean): Records {
        val weeks = buckets(shifts, Grain.WEEK, takeHome)
        val months = buckets(shifts, Grain.MONTH, takeHome)
        return Records(
            bestShift = shifts.maxByOrNull { it.effectiveTips(takeHome) },
            bestPct = shifts.filter { it.sales > 0 }.maxByOrNull { it.tipPercent(takeHome)!! },
            bestPerHour = shifts.filter { it.hours != null }.maxByOrNull { it.effectiveTips(takeHome) / it.hours!! },
            biggestSales = shifts.maxByOrNull { it.sales },
            mostCovers = shifts.filter { (it.covers ?: 0) > 0 }.maxByOrNull { it.covers!! },
            bestWeek = weeks.maxByOrNull { it.tips },
            bestMonth = months.takeIf { it.size > 1 }?.maxByOrNull { it.tips },
            mostShiftsWeek = weeks.maxWithOrNull(compareBy<Bucket> { it.shifts }.thenBy { it.tips }),
            longestStreak = longestStreak(shifts),
        )
    }

    // ---- Insights ---------------------------------------------------------------------------

    private fun plural(d: DayOfWeek) = d.getDisplayName(TextStyle.FULL, Locale.getDefault()) + "s"
    private fun m0(v: Double) = "$" + "%,.0f".format(v)
    private fun m2(v: Double) = "$" + "%,.2f".format(v)
    private fun p1(v: Double) = "%.1f%%".format(v)

    /**
     * Plain-English observations, most useful first, each only when enough data backs it
     * (at least two shifts on each side of any comparison).
     */
    fun insights(shifts: List<Shift>, previous: List<Shift>, takeHome: Boolean, max: Int = 10): List<Insight> {
        if (shifts.size < 3) return emptyList()
        val out = mutableListOf<Insight>()
        val all = aggregate(shifts, takeHome)
        val overall = all.tips / all.shifts

        // Best weekday vs. overall.
        val days = byDayOfWeek(shifts, takeHome).filter { it.shifts >= 2 }
        if (days.size >= 2) {
            val best = days.maxBy { it.avgTips!! }
            if (best.avgTips!! > overall * 1.03) {
                out += Insight(
                    InsightKind.BEST_DAY, "${plural(best.day)} pay best",
                    "${m0(best.avgTips)} a shift, vs ${m0(overall)} across all your shifts.", Kpi.TIPS_PER_SHIFT,
                )
            }
        }

        // Versus the previous period: per-shift earnings and tip rate.
        if (previous.size >= 2) {
            val prev = aggregate(previous, takeHome)
            val prevPerShift = prev.tips / prev.shifts
            if (prevPerShift > 0) {
                val change = (overall - prevPerShift) / prevPerShift * 100
                if (abs(change) >= 5) {
                    out += Insight(
                        InsightKind.PER_SHIFT_CHANGE,
                        if (change > 0) "Earning more per shift" else "Earning less per shift",
                        "${m0(overall)} a shift, ${if (change > 0) "up" else "down"} ${"%.0f".format(abs(change))}% from ${m0(prevPerShift)} last period.",
                        Kpi.TIPS_PER_SHIFT,
                    )
                }
            }
            val cur = all.tipPct
            val was = prev.tipPct
            if (cur != null && was != null && abs(cur - was) >= 0.3) {
                out += Insight(
                    InsightKind.TIP_RATE_CHANGE,
                    "Tip rate ${if (cur > was) "up" else "down"} to ${p1(cur)}",
                    "From ${p1(was)} last period.",
                    Kpi.TIP_PCT,
                )
            }
        }

        // Momentum: the latest few shifts against the rest of the period.
        if (shifts.size >= 8) {
            val sorted = shifts.sortedWith(compareBy({ it.date }, { it.openTime ?: "" }))
            val recent = sorted.takeLast(5)
            val recentAvg = recent.sumOf { it.effectiveTips(takeHome) } / recent.size
            val change = (recentAvg - overall) / overall * 100
            if (overall > 0 && abs(change) >= 10) {
                out += Insight(
                    InsightKind.MOMENTUM,
                    if (change > 0) "You're on a roll" else "A slower stretch lately",
                    "Your last 5 shifts averaged ${m0(recentAvg)}, ${"%.0f".format(abs(change))}% ${if (change > 0) "above" else "below"} your ${m0(overall)} average.",
                    Kpi.TIPS_PER_SHIFT,
                )
            }
        }

        // Time of day.
        val parts = shifts.groupBy { DayPart.of(it) }.filterKeys { it != null }.filterValues { it.size >= 2 }
        if (parts.size >= 2) {
            val avgs = parts.mapValues { (_, l) -> l.sumOf { it.effectiveTips(takeHome) } / l.size }
            val (bestPart, bestAvg) = avgs.maxBy { it.value }
            val (worstPart, worstAvg) = avgs.minBy { it.value }
            if (bestAvg > worstAvg * 1.05) {
                out += Insight(
                    InsightKind.TIME_OF_DAY, "${bestPart!!.label} shifts earn the most",
                    "${m0(bestAvg)} a shift, vs ${m0(worstAvg)} for ${worstPart!!.label.lowercase()}.",
                )
            }
        }

        // Longer vs. shorter shifts ($/hr), split at the median length.
        val timed = shifts.filter { it.hours != null }
        if (timed.size >= 4) {
            val cut = kotlin.math.round(percentile(timed.map { it.hours!! }, 0.5)!! * 2) / 2
            val (long, short) = timed.partition { it.hours!! >= cut }
            if (long.size >= 2 && short.size >= 2) {
                val lr = long.sumOf { it.effectiveTips(takeHome) } / long.sumOf { it.hours!! }
                val sr = short.sumOf { it.effectiveTips(takeHome) } / short.sumOf { it.hours!! }
                if (abs(lr - sr) >= 1.0) {
                    val h = if (cut % 1.0 == 0.0) "%.0f".format(cut) else "%.1f".format(cut)
                    out += Insight(
                        InsightKind.SHIFT_LENGTH,
                        if (lr > sr) "Longer shifts pay better per hour" else "Shorter shifts pay better per hour",
                        "${h}h+ shifts: ${m2(lr)}/hr. Shorter: ${m2(sr)}/hr.",
                        Kpi.TIPS_PER_HOUR,
                    )
                }
            }
        }

        // Busy vs. slow nights: tip rate on the busier half of shifts by sales.
        val bySales = shifts.filter { it.sales > 0 }.sortedBy { it.sales }
        if (bySales.size >= 4) {
            val slow = aggregate(bySales.subList(0, bySales.size / 2), takeHome).tipPct
            val busy = aggregate(bySales.subList(bySales.size / 2, bySales.size), takeHome).tipPct
            if (slow != null && busy != null && abs(busy - slow) >= 1.0) {
                out += Insight(
                    InsightKind.BUSY_NIGHTS,
                    if (busy > slow) "Busy nights tip better" else "Your tip rate dips on busy nights",
                    "${p1(busy)} on your busier half of shifts, ${p1(slow)} on slower ones.",
                    Kpi.TIP_PCT,
                )
            }
        }

        // Best month.
        val months = buckets(shifts, Grain.MONTH, takeHome).filter { it.shifts >= 2 }
        if (months.size >= 2) {
            val best = months.maxBy { it.tips }
            val name = best.start.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
            out += Insight(InsightKind.BEST_MONTH, "Best month: $name", "${m0(best.tips)} over ${best.shifts} shifts.", Kpi.TIPS)
        }

        // What a table is worth.
        if (all.coveredShifts >= 3 && all.covers > 0) {
            val perCover = all.coveredTips / all.covers
            out += Insight(
                InsightKind.GUEST_VALUE, "Each guest is worth ${m2(perCover)}",
                "A table of four brings in about ${m0(perCover * 4)} in tips.", Kpi.TIPS_PER_GUEST,
            )
        }

        // Typical range (middle half).
        if (shifts.size >= 6) {
            val tips = shifts.map { it.effectiveTips(takeHome) }
            out += Insight(
                InsightKind.TYPICAL_RANGE,
                "A typical shift: ${m0(percentile(tips, 0.25)!!)}–${m0(percentile(tips, 0.75)!!)}",
                "Half your shifts land in this range. The middle shift made ${m0(percentile(tips, 0.5)!!)}.",
                Kpi.TIPS_PER_SHIFT,
            )
        }
        return out.take(max)
    }
}
