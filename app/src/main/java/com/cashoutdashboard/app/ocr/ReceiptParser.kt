package com.cashoutdashboard.app.ocr

import com.cashoutdashboard.app.data.Payment
import com.cashoutdashboard.app.data.Shift
import com.cashoutdashboard.app.data.Transfer
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.round

data class ScanResult(
    val shift: Shift,
    /** Fields the scanner couldn't find on the slip; the review screen asks the user to fill them in. */
    val missing: Set<Field> = emptySet(),
    /** Things the scanner noticed that the arithmetic checks can't catch (e.g. date vs weekday). */
    val notes: List<String> = emptyList(),
    /** How many receipt landmarks were recognised; used to pick the best rotation. */
    val score: Int = 0,
)

/**
 * Turns the text rows of a POS "CASHOUT FOR <name>" slip into a [Shift].
 * Works on rows already reconstructed left-to-right, top-to-bottom, and is tolerant of
 * the usual OCR confusions (O/0, S/$, comma decimals, stray spaces).
 */
object ReceiptParser {
    // A minus only counts as a sign when attached ("= -141.91"); "0.00 - 141.91=" is a subtraction.
    private val MONEY = Regex("""(?<![\d.])(-?\d{1,3}(?:,\d{3})+|-?\d+)\s?[.,]\s?(\d{2})(?!\d)""")
    private val TIME = Regex("""(\d{1,2})\s?[:.;]\s?(\d{2})\s?([AP])""")
    private val DATE = Regex("""(\d{1,2})\s?/\s?(\d{1,2})\s?/\s?(\d{2,4})""")
    private val TRAILING_INT = Regex("""(?:^|\s)(\d{1,3})\s*$""")
    private val KNOWN_TENDERS = listOf("CASH", "RDM CA", "DEBITC", "VISA", "MASTCH", "AMEX", "OLD CA", "CERT P", "QSA", "BIRTHD", "MANAGE")
    private val DAYS = DayOfWeek.entries.associateBy { it.name.take(3) }

    fun parse(rawLines: List<String>): ScanResult {
        val lines = rawLines.map { clean(it) }.filter { it.isNotBlank() }
        val up = lines.map { it.uppercase() }
        val notes = mutableListOf<String>()
        var score = 0

        fun idx(pred: (String) -> Boolean, from: Int = 0) = (from until up.size).firstOrNull { pred(up[it]) } ?: -1

        // Header -----------------------------------------------------------------------------
        val cashoutIdx = idx({ it.contains("CASHOUT") && it.contains("FOR") })
        val serverName = if (cashoutIdx >= 0) {
            score++
            Regex("""FOR\s+([A-Za-z][A-Za-z'.\- ]*?)\s*(?:\*|$)""", RegexOption.IGNORE_CASE)
                .find(lines[cashoutIdx])?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        } else null

        val bizIdx = idx({ it.contains("BUSINESS") || it.contains("DAY:") })
        var printedDay: DayOfWeek? = null
        var date: LocalDate? = null
        if (bizIdx >= 0) {
            score++
            printedDay = Regex("""DAY\W*([A-Z]{3,9})""").find(up[bizIdx])?.groupValues?.get(1)?.let { DAYS[it.take(3)] }
            date = DATE.find(up[bizIdx])?.let { toDate(it) }
        }
        if (date == null) {
            // Fall back to the first date printed in the header (the print timestamp).
            val headerEnd = idx({ it.contains("PAYMENT") }).let { if (it < 0) minOf(up.size, 10) else it }
            date = (0 until headerEnd).firstNotNullOfOrNull { i -> DATE.find(up[i])?.let { toDate(it) } }
        }
        if (printedDay == null) {
            printedDay = up.firstNotNullOfOrNull { l ->
                Regex("""(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)""").find(l)?.groupValues?.get(1)?.let { DayOfWeek.valueOf(it) }
            }
        }
        if (date != null && printedDay != null && date.dayOfWeek != printedDay) {
            notes += "The slip says ${printedDay.name.lowercase().replaceFirstChar { it.uppercase() }}, but $date isn't one — check the date"
        }

        var openTime: String? = null
        var closeTime: String? = null
        val openIdx = idx({ it.contains("OPEN") })
        if (openIdx >= 0) {
            val times = TIME.findAll(up[openIdx]).map { toTime(it) }.toList()
            openTime = times.getOrNull(0)
            closeTime = times.getOrNull(1)
        }
        if (closeTime == null) {
            val closeIdx = idx({ it.contains("CLOSE") })
            if (closeIdx >= 0) closeTime = TIME.findAll(up[closeIdx].substringAfter("CLOSE")).map { toTime(it) }.firstOrNull()
        }
        if (openTime != null) score++

        val reference = up.firstNotNullOfOrNull { Regex("""REFERENCE\s*#?\s*(\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull() }

        // Handwritten amounts are deliberately not read here: on-device OCR misreads handwriting
        // too often, and a wrong take-home is worse than a blank one. The Claude scanner reads them.

        // Payments ---------------------------------------------------------------------------
        val payStart = idx({ it.contains("PAYMENT") })
        val itemsIdx = idx({ it.startsWith("ITEMS") || it.contains("DOLLARS") || it.contains("VOLUME") }, maxOf(payStart, 0))
        val foodIdx = idx({ Regex("""^F[O0U]{2}D""").containsMatchIn(it) }, maxOf(payStart, 0))
        val payEnd = listOf(itemsIdx, foodIdx).filter { it >= 0 }.minOrNull() ?: up.size
        val payments = mutableListOf<Payment>()
        var total: List<Double>? = null
        var totalCount: Int? = null
        var cashTips: Double? = null
        if (payStart >= 0) {
            score++
            for (i in payStart + 1 until payEnd) {
                val l = up[i]
                if (isRule(l)) continue
                val nums = moneys(l)
                if (nums.isEmpty()) continue
                if (l.startsWith("TOTAL") || l.startsWith("TOTA") || l.startsWith("T0TAL")) {
                    total = nums
                    totalCount = trailingCount(l)
                    continue
                }
                val type = tenderType(l) ?: continue
                if (type == "CASH" && nums.size >= 2) cashTips = abs(nums[1])
                payments += Payment(type = type, amount = abs(nums[0]), count = trailingCount(l))
            }
        }
        var paymentTotal = total?.getOrNull(0)?.let { abs(it) }
        var tips = (total?.getOrNull(1) ?: cashTips)?.let { abs(it) }
        var net = total?.getOrNull(2)
        if (paymentTotal != null && tips != null && net != null && abs(paymentTotal - tips + net) <= 0.015) net = -net
        if (total != null) score++
        if (tips == null && paymentTotal != null && net != null) tips = r2(paymentTotal - net)
        if (paymentTotal != null && net != null && tips != null && abs(paymentTotal - tips - net) > 0.015) {
            // One of the three is misread; trust the pair that agrees with the payment lines.
            val sumPay = r2(payments.sumOf { it.amount })
            if (abs(sumPay - paymentTotal) > 0.015 && payments.isNotEmpty()) paymentTotal = sumPay
            if (abs(paymentTotal - tips - net) > 0.015) {
                if (cashTips != null && abs(paymentTotal - cashTips - net) <= 0.015) tips = cashTips
            }
        }
        if (paymentTotal == null && payments.isNotEmpty()) paymentTotal = r2(payments.sumOf { it.amount })

        // Items & server stats ---------------------------------------------------------------
        var foodSales: Double? = null
        var foodVolume: Int? = null
        var lwbSales: Double? = null
        var lwbVolume: Int? = null
        for (i in maxOf(payEnd, 0) until up.size) {
            val l = up[i]
            if (foodSales == null && Regex("""^F[O0U]{2}D""").containsMatchIn(l)) {
                foodSales = moneys(l).firstOrNull()
                foodVolume = trailingCount(l)
            } else if (lwbSales == null && Regex("""^L\W{0,2}[WNH]\W{0,2}[B8]""").containsMatchIn(l)) {
                lwbSales = moneys(l).firstOrNull()
                lwbVolume = trailingCount(l)
            }
        }
        var sales = up.firstNotNullOfOrNull { l -> if (l.contains("SALES")) moneys(l.substringAfter("SALES")).firstOrNull() else null }
        var covers = up.firstNotNullOfOrNull { l -> if (l.contains("COVER")) Regex("""COVERS?\D*(\d{1,3})""").find(l)?.groupValues?.get(1)?.toIntOrNull() else null }
        val avgCheck = up.firstNotNullOfOrNull { l -> if (l.contains("AVG") || l.contains("CHECK$")) moneys(l).firstOrNull() else null }
        // The count for "TOTAL # OF CHECKS:" sometimes lands on its own row just above or below.
        fun labeled(match: (String) -> Boolean, pattern: Regex): Int? {
            val i = up.indexOfFirst(match)
            if (i < 0) return null
            pattern.find(up[i])?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
            return listOf(i + 1, i - 1).filter { it in up.indices }
                .firstNotNullOfOrNull { j -> Regex("""^(\d{1,3})$""").find(up[j].trim())?.groupValues?.get(1)?.toIntOrNull() }
        }
        val checks = labeled({ it.contains("CHECKS") && !it.contains("STAFF") }, Regex("""CHECKS\W*(\d{1,3})"""))
        val staffChecks = labeled({ it.contains("STAFF") }, Regex("""CHECKS\W*(\d{1,3})"""))
        if (covers == null) covers = labeled({ it.contains("COVER") }, Regex("""COVERS?\D*(\d{1,3})"""))
        if (sales != null) score++
        if (foodSales != null) score++
        if (covers != null) score++

        val itemSum = if (foodSales != null) r2(foodSales + (lwbSales ?: 0.0)) else null
        if (sales == null && itemSum != null) sales = itemSum
        if (sales != null && itemSum != null && abs(sales - itemSum) > 0.015) {
            // Prefer whichever agrees with covers x average check.
            val byAvg = if (covers != null && avgCheck != null && covers > 0) covers * avgCheck else null
            if (byAvg != null && abs(itemSum - byAvg) < covers!! * 0.01 && abs(sales - byAvg) >= covers * 0.01) sales = itemSum
        }
        // Transfers --------------------------------------------------------------------------
        val transfersOut = transfers(lines, up, "TRANSFERS OUT")
        val transfersIn = transfers(lines, up, "TRANSFERS IN")

        val missing = buildSet {
            if (date == null) add(Field.DATE)
            if (openTime == null) add(Field.OPEN)
            if (closeTime == null) add(Field.CLOSE)
            if (sales == null) add(Field.SALES)
            if (tips == null) add(Field.TIPS)
            if (foodSales == null) add(Field.FOOD)
            if (lwbSales == null) add(Field.LWB)
            if (covers == null) add(Field.COVERS)
            if (avgCheck == null) add(Field.AVG_CHECK)
            if (checks == null) add(Field.CHECKS)
            if (paymentTotal == null) add(Field.PAYMENT_TOTAL)
            if (net == null) add(Field.NET)
            if (payments.isEmpty()) add(Field.PAYMENTS)
        }

        val shift = Shift(
            serverName = serverName,
            date = (date ?: LocalDate.now()).toString(),
            openTime = openTime,
            closeTime = closeTime,
            sales = sales ?: 0.0,
            tips = tips ?: 0.0,
            foodSales = foodSales,
            foodVolume = foodVolume,
            lwbSales = lwbSales,
            lwbVolume = lwbVolume,
            covers = covers,
            avgCheck = avgCheck,
            checks = checks,
            staffChecks = staffChecks,
            paymentTotal = paymentTotal,
            net = net,
            payments = payments,
            transfersOut = transfersOut,
            transfersIn = transfersIn,
            reference = reference,
            source = "ocr",
        )
        return ScanResult(shift, missing, notes, score)
    }

    private fun transfers(lines: List<String>, up: List<String>, header: String): List<Transfer> {
        val wantIn = header.endsWith("IN")
        // "TRANSFERS OUT" is often read as "TRANSFERS QUT"/"0UT"; tell the two apart by IN vs not.
        val start = up.indexOfFirst {
            it.startsWith("TRANSFER") && it.substringAfter("TRANSFER").trimStart('S', ' ').startsWith("IN") == wantIn
        }
        if (start < 0) return emptyList()
        val out = mutableListOf<Transfer>()
        for (i in start + 1 until up.size) {
            val l = up[i]
            if (l.startsWith("TRANSFERS") || l.startsWith("TOTAL")) break
            if (isRule(l)) continue
            val m = moneys(l).firstOrNull() ?: continue
            val name = lines[i].substringBefore("$").replace(Regex("""[\d.,\-]+"""), "").trim()
            if (name.isNotEmpty()) out += Transfer(name, m)
        }
        return out
    }

    private fun tenderType(l: String): String? {
        val head = l.substringBefore("$").takeWhile { !it.isDigit() }.replace(Regex("""[^A-Z ]"""), "").trim()
        if (head.length < 2) return null
        val compact = head.replace(" ", "")
        KNOWN_TENDERS.firstOrNull { compact.startsWith(it.replace(" ", "")) }?.let { return it }
        // Tolerate one misread letter ("ROM CA", "CAS", "MASTGH").
        KNOWN_TENDERS.map { it to editDistance(compact.take(it.replace(" ", "").length), it.replace(" ", "")) }
            .filter { (k, d) -> d <= (if (k.length >= 5) 2 else 1) && compact.length >= k.replace(" ", "").length - 1 }
            .minByOrNull { it.second }?.let { return it.first }
        // "DEBITCS" etc: a trailing S is usually a misread "$".
        return (if (compact.length > 4 && compact.endsWith("S")) head.dropLast(1) else head).trim()
    }

    private fun editDistance(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }

    private fun trailingCount(l: String): Int? {
        // Count is the last integer token when it follows the last money value.
        val lastMoney = MONEY.findAll(l).lastOrNull() ?: return null
        val rest = l.substring(lastMoney.range.last + 1)
        return TRAILING_INT.find(rest)?.groupValues?.get(1)?.toIntOrNull()
    }

    fun moneys(l: String): List<Double> = MONEY.findAll(l).mapNotNull { m ->
        val whole = m.groupValues[1].replace(" ", "").replace(",", "")
        "$whole.${m.groupValues[2]}".toDoubleOrNull()
    }.toList()

    private fun isRule(l: String) = l.count { it == '=' || it == '-' || it == '_' } > l.length / 2

    private fun toDate(m: MatchResult): LocalDate? {
        val (mo, d, y) = m.destructured
        val year = y.toInt().let { if (it < 100) 2000 + it else it }
        return runCatching { LocalDate.of(year, mo.toInt(), d.toInt()) }.getOrNull()
    }

    private fun toTime(m: MatchResult): String {
        var h = m.groupValues[1].toInt() % 12
        if (m.groupValues[3] == "P") h += 12
        return "%02d:%s".format(h, m.groupValues[2])
    }

    /** Fix characters OCR commonly swaps inside numbers. */
    private fun clean(s: String): String {
        var t = s.replace('—', '-').replace('–', '-').replace('§', '$')
        // O/o/D between or next to digits -> 0; l/I/| between digits -> 1
        t = t.replace(Regex("""(?<=\d)[Oo](?=[\d.,])|(?<=[\d.,])[Oo](?=\d|\b)"""), "0")
        t = t.replace(Regex("""(?<=\d)[Il|](?=\d)"""), "1")
        return t.trim()
    }

    private fun r2(v: Double) = round(v * 100) / 100
}
