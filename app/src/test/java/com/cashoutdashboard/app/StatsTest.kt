package com.cashoutdashboard.app

import com.cashoutdashboard.app.data.GoalPeriod
import com.cashoutdashboard.app.data.Shift
import com.cashoutdashboard.app.stats.DateRange
import com.cashoutdashboard.app.stats.Insight
import com.cashoutdashboard.app.stats.InsightKind
import com.cashoutdashboard.app.stats.Kpi
import com.cashoutdashboard.app.stats.Period
import com.cashoutdashboard.app.stats.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StatsTest {
    private val today = LocalDate.of(2026, 9, 25) // a Friday

    private fun shift(date: String, sales: Double, tips: Double, open: String? = "17:00", close: String? = "21:00", takeHome: Double? = null) =
        Shift(date = date, sales = sales, tips = tips, openTime = open, closeTime = close, takeHome = takeHome)

    @Test fun weekStartsMonday() {
        assertEquals(DateRange(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 27)), Stats.range(Period.WEEK, today, null, null))
    }

    @Test fun twoWeeksAndThirtyDaysEndToday() {
        assertEquals(DateRange(LocalDate.of(2026, 9, 12), today), Stats.range(Period.TWO_WEEKS, today, null, null))
        assertEquals(DateRange(LocalDate.of(2026, 8, 27), today), Stats.range(Period.LAST_30, today, null, null))
    }

    @Test fun monthYearAll() {
        assertEquals(DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), Stats.range(Period.MONTH, today, null, null))
        assertEquals(DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), Stats.range(Period.YEAR, today, null, null))
        assertEquals(DateRange(null, null), Stats.range(Period.ALL, today, null, null))
    }

    @Test fun customFallsBackToAll() {
        val c = DateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))
        assertEquals(c, Stats.range(Period.CUSTOM, today, null, c))
        assertEquals(DateRange(null, null), Stats.range(Period.CUSTOM, today, null, null))
    }

    @Test fun payPeriodFromPastAnchor() {
        // Anchor Aug 31; periods: Aug 31–Sep 13, Sep 14–Sep 27.
        val r = Stats.range(Period.PAY_PERIOD, today, LocalDate.of(2026, 8, 31), null)
        assertEquals(DateRange(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 27)), r)
    }

    @Test fun payPeriodOnAnchorBoundaryAndFutureAnchor() {
        assertEquals(
            DateRange(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 8)),
            Stats.range(Period.PAY_PERIOD, today, LocalDate.of(2026, 9, 25), null),
        )
        // Anchor in the future still resolves to the period containing today.
        assertEquals(
            DateRange(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 10, 2)),
            Stats.range(Period.PAY_PERIOD, today, LocalDate.of(2026, 10, 3), null),
        )
    }

    @Test fun payPeriodWithoutAnchorStartsThisMonday() {
        assertEquals(
            DateRange(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 10, 4)),
            Stats.range(Period.PAY_PERIOD, today, null, null),
        )
    }

    @Test fun previousRangeIsSameLength() {
        val r = DateRange(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 27))
        assertEquals(DateRange(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 13)), Stats.previous(r))
        assertNull(Stats.previous(DateRange(null, null)))
    }

    @Test fun tipPercentIsWeightedBySales() {
        // 10% on $100 and 20% on $1000 -> (10+200)/1100 = 19.09%, not the 15% simple mean.
        val s = Stats.summary(listOf(shift("2026-09-01", 100.0, 10.0), shift("2026-09-02", 1000.0, 200.0)), false)
        assertEquals(210.0 / 1100.0 * 100, s.tipPct!!, 1e-9)
        assertEquals(105.0, s.avgTipsPerShift!!, 1e-9)
    }

    @Test fun tipsPerHourIgnoresShiftsWithoutTimes() {
        val s = Stats.summary(
            listOf(
                shift("2026-09-01", 500.0, 80.0), // 4h
                shift("2026-09-02", 500.0, 999.0, open = null, close = null),
            ),
            false,
        )
        assertEquals(4.0, s.hours, 1e-9)
        assertEquals(20.0, s.tipsPerHour!!, 1e-9)
    }

    @Test fun overnightShiftHours() {
        assertEquals(4.5, shift("2026-09-01", 1.0, 1.0, open = "21:30", close = "02:00").hours!!, 1e-9)
    }

    @Test fun takeHomeModeFallsBackToPosTips() {
        val list = listOf(shift("2026-09-01", 100.0, 20.0, takeHome = 15.0), shift("2026-09-02", 100.0, 30.0))
        assertEquals(45.0, Stats.summary(list, true).tips, 1e-9)
        assertEquals(50.0, Stats.summary(list, false).tips, 1e-9)
        assertEquals(15.0, Stats.summary(list, false).takeHome!!, 1e-9)
    }

    @Test fun byDayOfWeekIsMondayFirstAndComplete() {
        val days = Stats.byDayOfWeek(
            listOf(shift("2026-09-25", 100.0, 20.0), shift("2026-09-18", 100.0, 10.0), shift("2026-09-20", 50.0, 5.0)),
            false,
        )
        assertEquals(DayOfWeek.entries.toList(), days.map { it.day })
        val fri = days.first { it.day == DayOfWeek.FRIDAY }
        assertEquals(2, fri.shifts)
        assertEquals(15.0, fri.avgTips!!, 1e-9)
        assertEquals(15.0, fri.tipPct!!, 1e-9)
        assertNull(days.first { it.day == DayOfWeek.MONDAY }.avgTips)
    }

    @Test fun grainSelection() {
        val few = (1..20).map { shift(LocalDate.of(2026, 1, 1).plusDays(it.toLong()).toString(), 100.0, 10.0) }
        assertEquals(Stats.Grain.SHIFT, Stats.grainFor(few))
        val months = (0 until 60).map { shift(LocalDate.of(2026, 1, 1).plusDays(it * 2L).toString(), 100.0, 10.0) }
        assertEquals(Stats.Grain.WEEK, Stats.grainFor(months))
        val year = (0 until 100).map { shift(LocalDate.of(2025, 1, 1).plusDays(it * 5L).toString(), 100.0, 10.0) }
        assertEquals(Stats.Grain.MONTH, Stats.grainFor(year))
        assertEquals(Stats.Grain.SHIFT, Stats.grainFor(emptyList()))
    }

    @Test fun weeklyBucketsGroupByMonday() {
        val list = listOf(
            shift("2026-09-21", 100.0, 10.0), shift("2026-09-27", 100.0, 20.0), shift("2026-09-28", 100.0, 5.0),
        )
        val b = Stats.buckets(list, Stats.Grain.WEEK, false)
        assertEquals(listOf(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 28)), b.map { it.start })
        assertEquals(30.0, b[0].tips, 1e-9)
        assertEquals(2, b[0].shifts)
    }

    @Test fun shiftBucketsAreChronological() {
        val list = listOf(shift("2026-09-03", 1.0, 3.0), shift("2026-09-01", 1.0, 1.0), shift("2026-09-02", 1.0, 2.0))
        assertEquals(listOf(1.0, 2.0, 3.0), Stats.buckets(list, Stats.Grain.SHIFT, false).map { it.tips })
    }

    @Test fun longestStreak() {
        val list = listOf("2026-09-01", "2026-09-02", "2026-09-03", "2026-09-05", "2026-09-06").map { shift(it, 1.0, 1.0) }
        assertEquals(3, Stats.longestStreak(list))
        assertEquals(0, Stats.longestStreak(emptyList()))
    }

    // ---- Goals & pace ----

    @Test fun goalRangesFollowTheirPeriod() {
        val anchor = LocalDate.of(2026, 9, 14)
        assertEquals(Stats.range(Period.WEEK, today, null, null), Stats.goalRange(GoalPeriod.WEEK, today, anchor))
        assertEquals(DateRange(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 27)), Stats.goalRange(GoalPeriod.PAY_PERIOD, today, anchor))
        assertEquals(DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), Stats.goalRange(GoalPeriod.MONTH, today, anchor))
        assertEquals(DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), Stats.goalRange(GoalPeriod.YEAR, today, anchor))
    }

    @Test fun paceProjectsFromRecentRate() {
        // Worked every other day for 8 weeks at $100 a shift -> 0.5 shifts/day.
        val list = (0 until 56 step 2).map { shift(today.minusDays(it.toLong() + 1).toString(), 1000.0, 100.0) }
        val month = DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
        val p = Stats.pace(list, month, today, false)!!
        val earnedSept = list.filter { it.localDate.monthValue == 9 }.sumOf { it.tips }
        assertEquals(earnedSept, p.earned, 1e-9)
        assertEquals(6, p.daysLeft) // 25th (not worked yet) through 30th
        assertEquals(earnedSept + 6 * 0.5 * 100.0, p.projected, 1e-6)
    }

    @Test fun paceSkipsTodayWhenAlreadyWorked() {
        val list = listOf(shift(today.toString(), 1000.0, 150.0), shift(today.minusDays(10).toString(), 1000.0, 50.0))
        val p = Stats.pace(list, DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), today, false)!!
        assertEquals(5, p.daysLeft)
        assertEquals(200.0, p.earned, 1e-9)
    }

    @Test fun paceNeedsHistoryAndBoundedRange() {
        assertNull(Stats.pace(emptyList(), DateRange(today, today), today, false))
        assertNull(Stats.pace(listOf(shift("2026-09-20", 1.0, 1.0)), DateRange(null, null), today, false))
    }

    // ---- Trends ----

    @Test fun movingAverageIsTrailingAndSkipsNulls() {
        val out = Stats.movingAverage(listOf(null, 2.0, 4.0, null, 6.0, 8.0), 2)
        assertEquals(listOf(null, 2.0, 3.0, 3.0, 5.0, 7.0), out)
    }

    @Test fun histogramBucketsByTipPercent() {
        val list = listOf(9.0, 10.0, 11.9, 12.0, 14.99, 15.0, 18.0, 25.0).map { shift("2026-09-01", 100.0, it) }
        val bins = Stats.tipPctHistogram(list, false)
        assertEquals(listOf("<10%", "10–12%", "12–15%", "15–18%", "18%+"), bins.map { it.label })
        assertEquals(listOf(1, 2, 2, 1, 2), bins.map { it.count })
    }

    @Test fun linearFitRecoversALine() {
        val f = Stats.linearFit(listOf(100.0 to 20.0, 200.0 to 32.0, 300.0 to 44.0, 400.0 to 56.0))!!
        assertEquals(0.12, f.slope, 1e-9)
        assertEquals(8.0, f.intercept, 1e-9)
        assertEquals(1.0, f.r2, 1e-9)
        assertNull(Stats.linearFit(listOf(1.0 to 1.0, 2.0 to 2.0)))
        assertNull(Stats.linearFit(listOf(5.0 to 1.0, 5.0 to 2.0, 5.0 to 3.0)))
    }

    @Test fun percentileInterpolates() {
        assertEquals(2.5, Stats.percentile(listOf(1.0, 2.0, 3.0, 4.0), 0.5)!!, 1e-9)
        assertEquals(1.0, Stats.percentile(listOf(4.0, 1.0), 0.0)!!, 1e-9)
        assertNull(Stats.percentile(emptyList(), 0.5))
    }

    @Test fun perHourBucketIgnoresUntimedShifts() {
        val list = listOf(shift("2026-09-21", 1000.0, 100.0), shift("2026-09-22", 1000.0, 300.0, open = null, close = null))
        val week = Stats.buckets(list, Stats.Grain.WEEK, false).single()
        assertEquals(400.0, week.tips, 1e-9)
        assertEquals(25.0, week.perHour!!, 1e-9)
    }

    // ---- Records ----

    @Test fun recordsFindWeeklyAndMonthlyBests() {
        val list = listOf(
            shift("2026-08-03", 1000.0, 100.0), shift("2026-08-04", 1000.0, 90.0), shift("2026-08-05", 900.0, 95.0),
            shift("2026-09-07", 2000.0, 300.0, open = "16:00", close = "20:00"),
            shift("2026-09-14", 500.0, 120.0, open = "17:00", close = "19:00"),
        ).map { it.copy(covers = (it.sales / 50).toInt()) }
        val r = Stats.records(list, false)
        assertEquals("2026-09-07", r.bestShift!!.date)
        assertEquals("2026-09-14", r.bestPct!!.date)
        assertEquals("2026-09-07", r.bestPerHour!!.date) // 75/hr beats 60/hr
        assertEquals("2026-09-07", r.biggestSales!!.date)
        assertEquals(40, r.mostCovers!!.covers)
        assertEquals(LocalDate.of(2026, 9, 7), r.bestWeek!!.start)
        assertEquals(LocalDate.of(2026, 9, 1), r.bestMonth!!.start)
        assertEquals(3, r.mostShiftsWeek!!.shifts)
        assertEquals(3, r.longestStreak)
    }

    @Test fun bestMonthNeedsTwoMonths() {
        assertNull(Stats.records(listOf(shift("2026-09-01", 1.0, 1.0)), false).bestMonth)
    }

    // ---- Insights ----

    @Test fun noInsightsFromTooLittleData() {
        assertEquals(emptyList<Insight>(), Stats.insights(listOf(shift("2026-09-01", 1.0, 1.0), shift("2026-09-02", 1.0, 1.0)), emptyList(), false))
    }

    @Test fun bestDayInsightNeedsTwoShiftsPerDay() {
        // Sundays 6, 13 at $200; Mondays 7, 14 at $100.
        val list = listOf(
            shift("2026-09-06", 1000.0, 200.0), shift("2026-09-13", 1000.0, 200.0),
            shift("2026-09-07", 1000.0, 100.0), shift("2026-09-14", 1000.0, 100.0),
        )
        val out = Stats.insights(list, emptyList(), false)
        assertTrue(out.any { it.kind == InsightKind.BEST_DAY && it.headline == "Sundays pay best" })
        assertEquals("Pick up more Sundays", out.single { it.kind == InsightKind.BEST_DAY }.action)
        // With a single Sunday there's no qualifying comparison.
        val thin = Stats.insights(list.drop(1) + shift("2026-09-08", 1000.0, 100.0), emptyList(), false)
        assertTrue(thin.none { it.kind == InsightKind.BEST_DAY })
    }

    @Test fun tipPctChangeInsightNeedsPreviousData() {
        val cur = List(3) { shift("2026-09-1$it", 1000.0, 150.0) }
        val prev = List(2) { shift("2026-08-1$it", 1000.0, 120.0) }
        val rate = Stats.insights(cur, prev, false).single { it.kind == InsightKind.TIP_RATE_CHANGE }
        assertEquals("Tip rate up to 15.0%", rate.headline)
        assertEquals("From 12.0% last period.", rate.detail)
        assertTrue(Stats.insights(cur, prev.take(1), false).none { it.kind == InsightKind.TIP_RATE_CHANGE })
    }

    @Test fun insightsAreCapped() {
        val list = (1..20).map { i ->
            shift("2026-0${if (i <= 10) 8 else 9}-${"%02d".format(i % 10 + 1)}", 800.0 + i * 20, 80.0 + i * 5, open = "17:00", close = if (i % 2 == 0) "23:00" else "20:00")
                .copy(covers = 20)
        }
        assertTrue(Stats.insights(list, emptyList(), false, max = 3).size <= 3)
    }

    // ---- Kpis & breakdowns ----

    @Test fun kpiRatiosIgnoreShiftsMissingTheirInputs() {
        val list = listOf(
            shift("2026-09-01", 1000.0, 200.0).copy(covers = 10),
            shift("2026-09-02", 1000.0, 100.0, open = null, close = null),
        )
        val b = Stats.aggregate(list, false)
        assertEquals(300.0, Kpi.TIPS.of(b)!!, 1e-9)
        assertEquals(50.0, Kpi.TIPS_PER_HOUR.of(b)!!, 1e-9) // 200 over the one timed 4h shift
        assertEquals(250.0, Kpi.SALES_PER_HOUR.of(b)!!, 1e-9)
        assertEquals(20.0, Kpi.TIPS_PER_GUEST.of(b)!!, 1e-9)
        assertEquals(4.0, Kpi.HOURS.perShift(b)!!, 1e-9)
        assertNull(Kpi.LIQUOR_SHARE.of(b))
        assertNull(Kpi.TAKE_HOME.of(b))
    }

    @Test fun salesBandsSplitEvenlySlowToBusy() {
        val list = (1..12).map { shift("2026-09-%02d".format(it), it * 100.0, it * 10.0) }
        val bands = Stats.salesBands(list, false)
        assertEquals(4, bands.size)
        assertTrue(bands.all { it.shifts == 3 })
        assertEquals("$100–$300", bands.first().label)
        assertEquals("$1k–$1.2k", bands.last().label)
    }

    @Test fun runningTotalStopsAtToday() {
        val list = listOf(shift("2026-09-01", 100.0, 10.0), shift("2026-09-03", 100.0, 5.0))
        val run = Stats.runningTotal(list, Kpi.TIPS, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 3), false)
        assertEquals(listOf(10.0, 10.0, 15.0, null, null), run)
    }

    @Test fun busyNightInsightComparesHalvesBySales() {
        val list = listOf(
            shift("2026-09-01", 500.0, 50.0), shift("2026-09-02", 600.0, 60.0),
            shift("2026-09-03", 1500.0, 300.0), shift("2026-09-04", 1600.0, 320.0),
        )
        val busy = Stats.insights(list, emptyList(), false).single { it.kind == InsightKind.BUSY_NIGHTS }
        assertEquals("Busy nights tip better", busy.headline)
        assertEquals(Kpi.TIP_PCT, busy.kpi)
        assertEquals("Go after the busy shifts", busy.action)
    }
}
