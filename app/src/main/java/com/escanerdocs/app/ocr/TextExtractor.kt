package com.escanerdocs.app.ocr

import android.graphics.BitmapFactory
import com.escanerdocs.app.data.OcrLine
import com.escanerdocs.app.data.OcrPage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/** On-device OCR with ML Kit (Latin script: Spanish, English, etc.). No internet needed. */
class TextExtractor {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Recognizes the lines of one page, with boxes in the original image's pixel coordinates. */
    suspend fun recognize(page: File): OcrPage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(page.path, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        require(width > 0 && height > 0) { "No se pudo leer la imagen de la página" }

        // Downsample very large scans: faster OCR with the same accuracy on document text.
        var sample = 1
        while (max(width, height) / (sample * 2) >= MAX_OCR_EDGE) sample *= 2
        val bitmap = BitmapFactory.decodeFile(page.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("No se pudo leer la imagen de la página")
        val scaleX = width.toFloat() / bitmap.width
        val scaleY = height.toFloat() / bitmap.height

        val result = try {
            suspendCancellableCoroutine { cont ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        } finally {
            bitmap.recycle()
        }

        val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            OcrLine(
                text = line.text,
                left = (box.left * scaleX).toInt(),
                top = (box.top * scaleY).toInt(),
                right = (box.right * scaleX).toInt(),
                bottom = (box.bottom * scaleY).toInt(),
            )
        }
        return OcrPage(width, height, lines)
    }

    fun close() = recognizer.close()

    private companion object {
        const val MAX_OCR_EDGE = 2560
    }
}
