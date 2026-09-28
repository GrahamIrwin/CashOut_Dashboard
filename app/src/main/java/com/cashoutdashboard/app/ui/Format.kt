package com.cashoutdashboard.app.ui

import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val money = NumberFormat.getCurrencyInstance(Locale.US)
private val moneyWhole = NumberFormat.getCurrencyInstance(Locale.US).apply { maximumFractionDigits = 0 }

fun Double.money(): String = money.format(this)
fun Double.moneyShort(): String = if (kotlin.math.abs(this) >= 1000) moneyWhole.format(this) else money.format(this)
fun Double?.moneyOrDash(): String = this?.money() ?: "—"
fun Double?.pct(digits: Int = 1): String = this?.let { "%.${digits}f%%".format(it) } ?: "—"
fun Double?.hrs(): String = this?.let { "%.1f h".format(it) } ?: "—"

fun LocalDate.pretty(): String = format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))
fun LocalDate.short(): String = format(DateTimeFormatter.ofPattern("EEE MMM d"))

/** "17:05" -> "5:05 PM" */
fun String?.clock(): String = this?.let {
    runCatching { LocalTime.parse(it).format(DateTimeFormatter.ofPattern("h:mm a")) }.getOrDefault(it)
} ?: "—"
