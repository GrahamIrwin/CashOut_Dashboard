package com.cashoutdashboard.app.ocr

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/** One OCR text line with its four corners (TL, TR, BR, BL) as x0,y0,x1,y1,... */
@Serializable
data class OcrLine(val text: String, val corners: List<Float>)

/**
 * OCR engines return a receipt's columns as separate text lines, so "CASH $ 0.00 - 141.91= ..."
 * comes back in pieces. This stitches pieces into printed rows by following each piece's own
 * baseline to the right, which copes with tilted photos and paper that curls.
 */
object RowBuilder {
    private class Piece(val text: String, c: List<Float>, private val skew: Double) {
        // Deskewed geometry: rotate everything by -skew so rows are roughly horizontal.
        private fun rx(x: Float, y: Float) = x * cos(-skew) - y * sin(-skew)
        private fun ry(x: Float, y: Float) = x * sin(-skew) + y * cos(-skew)
        val lx = ((rx(c[0], c[1]) + rx(c[6], c[7])) / 2)
        val ly = ((ry(c[0], c[1]) + ry(c[6], c[7])) / 2)
        val rX = ((rx(c[2], c[3]) + rx(c[4], c[5])) / 2)
        val rY = ((ry(c[2], c[3]) + ry(c[4], c[5])) / 2)
        val h = (hypot((c[6] - c[0]).toDouble(), (c[7] - c[1]).toDouble()) + hypot((c[4] - c[2]).toDouble(), (c[5] - c[3]).toDouble())) / 2
        val width = rX - lx
        val angle = atan2(rY - ly, rX - lx)
        val cy = (ly + rY) / 2
    }

    private class Row(first: Piece) {
        val pieces = mutableListOf(first)
        val last get() = pieces.last()
        val angle: Double
            get() {
                val long = pieces.filter { it.width > 3 * it.h }
                if (long.isEmpty()) return 0.0
                return long.sumOf { it.angle * it.width } / long.sumOf { it.width }
            }
    }

    /** Median reading direction of the text in degrees (0 = upright, -90 = text runs bottom-to-top). */
    fun dominantAngle(lines: List<OcrLine>): Double? = median(lines.mapNotNull { l ->
        val c = l.corners
        if (c.size != 8 || l.text.length < 4) return@mapNotNull null
        Math.toDegrees(atan2((c[3] - c[1]).toDouble(), (c[2] - c[0]).toDouble()))
    })

    fun rows(lines: List<OcrLine>): List<String> {
        val valid = lines.filter { it.corners.size == 8 && it.text.isNotBlank() }
        if (valid.isEmpty()) return emptyList()
        val skew = median(valid.mapNotNull { l ->
            val c = l.corners
            val w = hypot((c[2] - c[0]).toDouble(), (c[3] - c[1]).toDouble())
            val h = hypot((c[6] - c[0]).toDouble(), (c[7] - c[1]).toDouble())
            if (w > 3 * h) atan2((c[3] - c[1]).toDouble(), (c[2] - c[0]).toDouble()) else null
        }) ?: 0.0
        val pieces = valid.map { Piece(it.text.trim(), it.corners, skew) }.sortedBy { it.lx }
        val rows = mutableListOf<Row>()
        for (p in pieces) {
            var best: Row? = null
            var bestDist = Double.MAX_VALUE
            for (row in rows) {
                val last = row.last
                if (last.rX > p.lx + 0.8 * p.h) continue // overlaps horizontally: not the same row
                val gap = p.lx - last.rX
                val yPred = last.rY + tan(row.angle) * gap
                val dist = abs(p.ly - yPred)
                val tol = 0.55 * minOf(last.h, p.h)
                if (dist < tol && dist < bestDist) {
                    best = row
                    bestDist = dist
                }
            }
            if (best != null) best.pieces += p else rows += Row(p)
        }
        // Second pass: short right-hand values ("6", "11") often sit a little off the extended
        // baseline when the paper curls. Re-home orphans with a looser tolerance.
        for (orphan in rows.filter { it.pieces.size == 1 && it.pieces[0].text.length <= 3 }.toList()) {
            val p = orphan.pieces[0]
            val target = rows.filter { it !== orphan && it.last.rX < p.lx && it.pieces.size > 0 }
                .map { row -> row to abs(p.ly - (row.last.rY + tan(row.angle) * (p.lx - row.last.rX))) }
                .filter { (row, d) -> d < 0.9 * minOf(row.last.h, p.h) && !row.pieces.last().text.trim().let { it.isNotEmpty() && it.last().isDigit() && row.pieces.size > 1 && it.length <= 3 } }
                .minByOrNull { it.second }?.first
            if (target != null) {
                target.pieces += p
                rows.remove(orphan)
            }
        }
        return rows.sortedBy { it.pieces.first().cy }.map { r -> r.pieces.joinToString(" ") { it.text } }
    }

    private fun median(v: List<Double>): Double? = if (v.isEmpty()) null else v.sorted()[v.size / 2]
}
