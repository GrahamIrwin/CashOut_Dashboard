package com.cashoutdashboard.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ReceiptScanner(private val context: Context) {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** Copies a picked/captured photo into [dest], upright (EXIF applied) and downscaled. */
    suspend fun importPhoto(uri: Uri, dest: File): File = withContext(Dispatchers.IO) {
        // Read the source exactly once into a local file; picker/provider streams can fail when
        // reopened repeatedly or read concurrently, so retry the copy a couple of times.
        val src = File(dest.parentFile, dest.name + ".src")
        var lastError: Exception? = null
        for (attempt in 1..3) {
            try {
                context.contentResolver.openInputStream(uri)!!.use { input -> src.outputStream().use { input.copyTo(it) } }
                lastError = null
                break
            } catch (e: Exception) {
                lastError = e
                delay(300L * attempt)
            }
        }
        lastError?.let { throw IllegalStateException("Couldn't open that photo (${it.message})") }
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(src.absolutePath, bounds)
            require(bounds.outWidth > 0) { "That file isn't an image we can read" }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
            val raw = BitmapFactory.decodeFile(src.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: error("Couldn't decode that image")
            val orientation = ExifInterface(src.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val upright = scaleDown(applyExif(raw, orientation), MAX_EDGE)
            FileOutputStream(dest).use { upright.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            dest
        } finally {
            src.delete()
        }
    }

    suspend fun scan(photo: File): ScanResult {
        val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(photo.absolutePath) } ?: error("Couldn't read photo")
        val (rotation, result) = bestRotation(bitmap)
        if (rotation != 0) {
            // Keep the saved photo upright so it's easy to read later.
            withContext(Dispatchers.IO) { FileOutputStream(photo).use { rotate(bitmap, rotation).compress(Bitmap.CompressFormat.JPEG, 90, it) } }
        }
        return result
    }

    /** Runs ML Kit, trying each orientation until the receipt's landmarks are found. */
    suspend fun scanOnDevice(bitmap: Bitmap): ScanResult = bestRotation(bitmap).second

    /**
     * ML Kit can read sideways text but is noticeably more accurate upright, so the first pass is
     * only used to find which way the text runs; the photo is then turned upright and read again.
     */
    suspend fun bestRotation(bitmap: Bitmap): Pair<Int, ScanResult> {
        val first = ocrLines(bitmap, 0)
        val angle = RowBuilder.dominantAngle(first) ?: 0.0
        val upright = when {
            angle < -135 || angle > 135 -> 180
            angle < -45 -> 90
            angle > 45 -> 270
            else -> 0
        }
        var best: Pair<Int, ScanResult>? = null
        for (rotation in listOf(upright) + listOf(0, 90, 270, 180).filter { it != upright }) {
            val lines = if (rotation == 0) first else ocrLines(bitmap, rotation)
            val result = ReceiptParser.parse(RowBuilder.rows(lines))
            if (best == null || result.score > best.second.score) best = rotation to result
            if (result.score >= 8) break
        }
        if (best == null || best.second.score < 3) error("Couldn't find a cashout slip in this photo — try a closer, flatter shot")
        return best
    }

    suspend fun rows(bitmap: Bitmap, rotation: Int): List<String> = RowBuilder.rows(ocrLines(bitmap, rotation))

    suspend fun ocrLines(bitmap: Bitmap, rotation: Int): List<OcrLine> {
        val rotated = if (rotation == 0) bitmap else rotate(bitmap, rotation)
        val text: Text = recognizer.process(InputImage.fromBitmap(rotated, 0)).await()
        return text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val pts = line.cornerPoints
            val corners = if (pts != null && pts.size == 4) {
                pts.flatMap { listOf(it.x.toFloat(), it.y.toFloat()) }
            } else {
                val b = line.boundingBox ?: return@mapNotNull null
                listOf(b.left, b.top, b.right, b.top, b.right, b.bottom, b.left, b.bottom).map { it.toFloat() }
            }
            OcrLine(line.text, corners)
        }
    }

    companion object {
        const val MAX_EDGE = 2400

        fun rotate(b: Bitmap, degrees: Int): Bitmap =
            Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)

        fun scaleDown(b: Bitmap, maxEdge: Int): Bitmap {
            val edge = maxOf(b.width, b.height)
            if (edge <= maxEdge) return b
            val f = maxEdge.toFloat() / edge
            return Bitmap.createScaledBitmap(b, (b.width * f).toInt(), (b.height * f).toInt(), true)
        }

        private fun applyExif(b: Bitmap, orientation: Int): Bitmap {
            val m = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
                else -> return b
            }
            return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
        }
    }
}
