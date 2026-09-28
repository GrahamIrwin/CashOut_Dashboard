package com.cashoutdashboard.app

import com.cashoutdashboard.app.data.Payment
import com.cashoutdashboard.app.data.Shift
import com.cashoutdashboard.app.ocr.Field
import com.cashoutdashboard.app.ocr.Validation
import com.cashoutdashboard.app.ocr.isBlank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {
    // Values from the 9/04/26 sample slip.
    private val good = Shift(
        date = "2026-09-04", openTime = "17:05", closeTime = "21:40",
        sales = 1054.30, tips = 141.91, foodSales = 1024.80, lwbSales = 29.50,
        covers = 21, avgCheck = 50.20, checks = 6, paymentTotal = 1322.87, net = 1180.96,
        payments = listOf(Payment("CASH", 0.0, 0), Payment("DEBITC", 640.21, 3), Payment("VISA", 682.66, 3)),
    )

    @Test
    fun cleanSlipHasNoIssues() = assertEquals(emptyList<Any>(), Validation.check(good))

    @Test
    fun missingSalesIsFixedFromItems() {
        val issue = Validation.check(good.copy(sales = 0.0)).single { Field.SALES in it.fields }
        assertEquals(1054.30, issue.fix!!.apply(good.copy(sales = 0.0)).sales, 0.001)
    }

    @Test
    fun missingTipsIsFixedFromTotalMinusNet() {
        val broken = good.copy(tips = 0.0)
        val fixed = Validation.check(broken).single { Field.TIPS in it.fields }.fix!!.apply(broken)
        assertEquals(141.91, fixed.tips, 0.001)
        assertTrue(Validation.check(fixed).isEmpty())
    }

    @Test
    fun paymentLinesMismatchOffersNewTotal() {
        val broken = good.copy(payments = good.payments.dropLast(1))
        val issue = Validation.check(broken).first { Field.PAYMENTS in it.fields }
        assertEquals(640.21, issue.fix!!.apply(broken).paymentTotal!!, 0.001)
    }

    @Test
    fun missingTimesAreFlaggedWithoutAFix() {
        val issue = Validation.check(good.copy(openTime = null)).single()
        assertEquals(setOf(Field.OPEN), issue.fields)
        assertEquals(null, issue.fix)
    }

    @Test
    fun blankFieldsAreDetected() {
        val empty = Shift(date = "2026-09-04")
        assertTrue(Field.entries.filter { it != Field.DATE }.all { empty.isBlank(it) })
        assertTrue(Field.entries.none { good.isBlank(it) })
    }
}
