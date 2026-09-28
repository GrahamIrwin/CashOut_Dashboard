package com.cashoutdashboard.app

import com.cashoutdashboard.app.ocr.OcrLine
import com.cashoutdashboard.app.ocr.ReceiptParser
import com.cashoutdashboard.app.ocr.RowBuilder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Replays real ML Kit output captured from the sample photos (testdata/ocr_dump.json, produced by
 * the OcrAccuracyTest instrumented test) through the parser and compares against the hand-made
 * answer key (testdata/ground_truth.json).
 */
class ParserAccuracyTest {
    @Serializable
    data class Dump(val file: String, val rotation: Int, val lines: List<OcrLine>)

    private val json = Json { ignoreUnknownKeys = true }

    private fun testdata(name: String): File =
        listOf(File("../testdata/$name"), File("testdata/$name")).first { it.exists() }

    @Test
    fun parsesAllSamples() {
        val dumps = json.decodeFromString<List<Dump>>(testdata("ocr_dump.json").readText()).associateBy { it.file }
        val truth = json.parseToJsonElement(testdata("ground_truth.json").readText()).jsonArray.map { it.jsonObject }
        var fields = 0
        var wrong = 0
        val criticalMisses = mutableListOf<String>()
        val report = StringBuilder()
        for (t in truth) {
            val file = t.str("file")!!
            val dump = dumps[file] ?: continue
            val rows = RowBuilder.rows(dump.lines)
            File("build/rows/$file.txt").apply { parentFile?.mkdirs() }.writeText(rows.joinToString("\n"))
            val r = ReceiptParser.parse(rows)
            val s = r.shift
            val checks = listOf(
                Triple("date", t.str("businessDate"), s.date),
                Triple("open", t.str("openTime"), s.openTime),
                Triple("close", t.str("closeTime"), s.closeTime),
                Triple("sales", t.num("sales"), s.sales),
                Triple("tips", t.num("tips"), s.tips),
                Triple("paymentTotal", t.num("paymentTotal"), s.paymentTotal),
                Triple("net", t.num("net"), s.net),
                Triple("food", t.num("foodSales"), s.foodSales),
                Triple("foodVol", t.int("foodVolume"), s.foodVolume),
                Triple("lwb", t.num("lwbSales"), s.lwbSales),
                Triple("lwbVol", t.int("lwbVolume"), s.lwbVolume),
                Triple("covers", t.int("covers"), s.covers),
                Triple("avgCheck", t.num("avgCheck"), s.avgCheck),
                Triple("checks", t.int("totalChecks"), s.checks),
                Triple("staffChecks", t.int("staffChecks"), s.staffChecks),
                Triple("server", t.str("serverName"), s.serverName),
                Triple("reference", t.int("reference"), s.reference),
                Triple("payments", payments(t), s.payments.joinToString("; ") { "${it.type} ${fmt(it.amount)} x${it.count}" }),
                Triple("transfersOut", transfers(t, "transfersOut"), s.transfersOut.joinToString("; ") { "${it.name} ${fmt(it.amount)}" }),
            )
            val misses = checks.filter { (_, want, got) -> norm(want) != norm(got) }
            fields += checks.size
            wrong += misses.size
            misses.filter { it.first in setOf("date", "sales", "tips", "open", "close") }.forEach { criticalMisses += "$file ${it.first}" }
            if (misses.isNotEmpty() || r.notes.isNotEmpty()) {
                report.appendLine("== $file (rot ${dump.rotation}) notes=${r.notes} missing=${r.missing}")
                misses.forEach { (k, want, got) -> report.appendLine("   $k: want=${norm(want)} got=${norm(got)}") }
            }
        }
        report.appendLine("Field accuracy: ${fields - wrong}/$fields")
        println(report)
        File("build/parser_report.txt").apply { parentFile?.mkdirs() }.writeText(report.toString())
        assertEquals("Critical fields misread: $criticalMisses", 0, criticalMisses.size)
    }

    private fun fmt(d: Double) = "%.2f".format(d)
    private fun norm(v: Any?): String = when (v) {
        null -> "null"
        is Double -> fmt(v)
        else -> v.toString()
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.intOrNull

    private fun payments(t: JsonObject) = (t["payments"] as JsonArray).joinToString("; ") {
        val o = it.jsonObject
        "${o.str("type")} ${fmt(o.num("amount")!!)} x${o.int("count")}"
    }

    private fun transfers(t: JsonObject, key: String) = (t[key] as? JsonArray).orEmpty().joinToString("; ") {
        val o = it.jsonObject
        "${o.str("name")} ${fmt(o.num("amount")!!)}"
    }
}
