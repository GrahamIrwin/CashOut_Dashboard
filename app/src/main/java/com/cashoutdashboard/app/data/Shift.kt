package com.cashoutdashboard.app.data

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Serializable
data class Payment(
    val type: String,
    val amount: Double,
    val count: Int? = null,
)

@Serializable
data class Transfer(
    val name: String,
    val amount: Double,
)

/** One shift's cashout. Dates are ISO yyyy-MM-dd, times are 24h HH:mm. */
@Serializable
data class Shift(
    val id: String = UUID.randomUUID().toString(),
    val serverName: String? = null,
    val date: String,
    val openTime: String? = null,
    val closeTime: String? = null,
    val sales: Double = 0.0,
    val tips: Double = 0.0,
    /** Cash the server actually walked out with (often handwritten on the slip); entered by the user. */
    val takeHome: Double? = null,
    val foodSales: Double? = null,
    val foodVolume: Int? = null,
    val lwbSales: Double? = null,
    val lwbVolume: Int? = null,
    val covers: Int? = null,
    val avgCheck: Double? = null,
    val checks: Int? = null,
    val staffChecks: Int? = null,
    val paymentTotal: Double? = null,
    val net: Double? = null,
    val payments: List<Payment> = emptyList(),
    val transfersOut: List<Transfer> = emptyList(),
    val transfersIn: List<Transfer> = emptyList(),
    val reference: Int? = null,
    val notes: String? = null,
    val photoPath: String? = null,
    val source: String = "manual",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val localDate: LocalDate get() = LocalDate.parse(date)
    val dayOfWeek: DayOfWeek get() = localDate.dayOfWeek

    val open: LocalTime? get() = openTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    val close: LocalTime? get() = closeTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    /** Shift length in hours; handles shifts that close after midnight. */
    val hours: Double?
        get() {
            val o = open ?: return null
            val c = close ?: return null
            var mins = Duration.between(o, c).toMinutes()
            if (mins < 0) mins += 24 * 60
            return if (mins in 1..(20 * 60)) mins / 60.0 else null
        }

    fun effectiveTips(useTakeHome: Boolean): Double =
        if (useTakeHome) takeHome ?: tips else tips

    fun tipPercent(useTakeHome: Boolean = false): Double? =
        if (sales > 0) effectiveTips(useTakeHome) / sales * 100.0 else null
}
