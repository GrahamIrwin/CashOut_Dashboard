package com.cashoutdashboard.app

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cashoutdashboard.app.ocr.OcrLine
import com.cashoutdashboard.app.ocr.ReceiptParser
import com.cashoutdashboard.app.ocr.ReceiptScanner
import com.cashoutdashboard.app.ocr.RowBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs the real on-device OCR over the sample photos in testdata/photos and dumps the raw
 * OCR lines to the app's external files dir (ocr_dump.json) so the parser can be tuned in JVM tests.
 */
@RunWith(AndroidJUnit4::class)
class OcrAccuracyTest {
    @Serializable
    data class Dump(val file: String, val rotation: Int, val lines: List<OcrLine>)

    @Test
    fun dumpOcrForSamples(): Unit = runBlocking {
        val testCtx = InstrumentationRegistry.getInstrumentation().context
        val appCtx = InstrumentationRegistry.getInstrumentation().targetContext
        val scanner = ReceiptScanner(appCtx)
        val files = testCtx.assets.list("")!!.filter { it.endsWith(".jpg") }.sorted()
        val dumps = mutableListOf<Dump>()
        for (name in files) {
            val src = File(appCtx.cacheDir, name)
            testCtx.assets.open(name).use { input -> src.outputStream().use { input.copyTo(it) } }
            val photo = scanner.importPhoto(Uri.fromFile(src), File(appCtx.cacheDir, "up_$name"))
            val bmp = BitmapFactory.decodeFile(photo.absolutePath)
            val (rotation, _) = scanner.bestRotation(bmp)
            val best = Dump(name, rotation, scanner.ocrLines(bmp, rotation))
            dumps += best
            val r = ReceiptParser.parse(RowBuilder.rows(best.lines))
            Log.i("OcrAccuracy", "$name rot=${best.rotation} score=${r.score} date=${r.shift.date} sales=${r.shift.sales} tips=${r.shift.tips} missing=${r.missing} notes=${r.notes}")
        }
        val out = File(appCtx.getExternalFilesDir(null), "ocr_dump.json")
        out.writeText(Json.encodeToString(dumps))
        Log.i("OcrAccuracy", "wrote ${out.absolutePath}")
    }
}
