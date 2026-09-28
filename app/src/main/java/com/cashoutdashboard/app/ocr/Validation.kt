package com.cashoutdashboard.app.ocr

import com.cashoutdashboard.app.data.Shift
import kotlin.math.abs
import kotlin.math.round

/** Editable fields of a shift, used to point the user at what needs attention. */
enum class Field(val label: String) {
    DATE("Date"), OPEN("Clock in"), CLOSE("Clock out"), SALES("Sales"), TIPS("Tips"), FOOD("Food"), LWB("Liquor/wine/beer"),
    COVERS("Covers"), AVG_CHECK("Avg check"), CHECKS("Checks"), PAYMENT_TOTAL("Payment total"), NET("Net"), PAYMENTS("Payment lines"),
}

/** True while [f] has no value (the date always has one). */
fun Shift.isBlank(f: Field): Boolean = when (f) {
    Field.DATE -> false
    Field.OPEN -> openTime == null
    Field.CLOSE -> closeTime == null
    Field.SALES -> sales <= 0
    Field.TIPS -> tips <= 0
    Field.FOOD -> foodSales == null
    Field.LWB -> lwbSales == null
    Field.COVERS -> covers == null
    Field.AVG_CHECK -> avgCheck == null
    Field.CHECKS -> checks == null
    Field.PAYMENT_TOTAL -> paymentTotal == null
    Field.NET -> net == null
    Field.PAYMENTS -> payments.isEmpty()
}

/** A one-tap correction the slip's own arithmetic can supply. */
class Fix(val label: String, val apply: (Shift) -> Shift)

data class Issue(val message: String, val fields: Set<Field>, val fix: Fix? = null)

/** Arithmetic cross-checks a cashout slip must satisfy. */
object Validation {
    private fun money(v: Double) = "$" + "%,.2f".format(v)
    private fun r2(v: Double) = round(v * 100) / 100
    private fun off(a: Double, b: Double) = abs(a - b) > 0.015

    fun check(s: Shift): List<Issue> {
        val out = mutableListOf<Issue>()
        val itemSum = s.foodSales?.let { r2(it + (s.lwbSales ?: 0.0)) }
        val payTotal = s.paymentTotal
        val net = s.net
        val paySum = r2(s.payments.sumOf { it.amount })

        if (s.sales <= 0) {
            out += Issue(
                "Sales is missing", setOf(Field.SALES),
                itemSum?.takeIf { it > 0 }?.let { v -> Fix("Use food + L/W/B (${money(v)})") { it.copy(sales = v) } },
            )
        } else if (itemSum != null && off(itemSum, s.sales)) {
            out += Issue(
                "Food + L/W/B (${money(itemSum)}) doesn't match sales", setOf(Field.SALES, Field.FOOD, Field.LWB),
                Fix("Set sales to ${money(itemSum)}") { it.copy(sales = itemSum) },
            )
        }

        if (s.tips <= 0) {
            val derived = if (payTotal != null && net != null) r2(payTotal - net) else null
            out += Issue(
                "Tips is missing", setOf(Field.TIPS),
                derived?.takeIf { it > 0 }?.let { v -> Fix("Use total − net (${money(v)})") { it.copy(tips = v) } },
            )
        } else if (payTotal != null && net != null && off(payTotal - s.tips, net)) {
            val v = r2(payTotal - s.tips)
            out += Issue(
                "Payment total − tips should equal net", setOf(Field.TIPS, Field.PAYMENT_TOTAL, Field.NET),
                Fix("Set net to ${money(v)}") { it.copy(net = v) },
            )
        }

        if (payTotal != null && s.payments.isNotEmpty() && off(paySum, payTotal)) {
            out += Issue(
                "Payment lines add up to ${money(paySum)}, not the ${money(payTotal)} total", setOf(Field.PAYMENTS, Field.PAYMENT_TOTAL),
                Fix("Set total to ${money(paySum)}") { it.copy(paymentTotal = paySum) },
            )
        }

        val covers = s.covers
        if (covers != null && covers > 0 && s.avgCheck != null && s.sales > 0 && abs(s.sales / covers - s.avgCheck) > 0.02) {
            val v = r2(s.sales / covers)
            out += Issue(
                "Sales ÷ covers doesn't match the avg check", setOf(Field.COVERS, Field.AVG_CHECK),
                Fix("Set avg check to ${money(v)}") { it.copy(avgCheck = v) },
            )
        }

        if (s.openTime == null || s.closeTime == null) {
            out += Issue("Enter when the shift started and ended", setOfNotNull(Field.OPEN.takeIf { s.openTime == null }, Field.CLOSE.takeIf { s.closeTime == null }))
        } else if (s.hours == null) {
            out += Issue("Open and close times look wrong", setOf(Field.OPEN, Field.CLOSE))
        }

        if (s.tips > 0 && s.sales > 0 && s.tips / s.sales > 0.6) {
            out += Issue("Tips are over 60% of sales — double-check both", setOf(Field.TIPS, Field.SALES))
        }
        return out
    }
}
