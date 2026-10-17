package com.escanerdocs.app.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device OCR with ML Kit (Latin script: Spanish, English, etc.). No internet needed. */
class TextExtractor(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extract(page: File): String {
        val image = InputImage.fromFilePath(context, Uri.fromFile(page))
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    /** Extracts every page and joins them with a page header, reporting progress as (done, total). */
    suspend fun extractAll(pages: List<File>, onProgress: (Int, Int) -> Unit): String =
        buildString {
            pages.forEachIndexed { index, page ->
                onProgress(index, pages.size)
                if (pages.size > 1) append("--- Página ${index + 1} ---\n")
                append(extract(page).trim())
                append("\n\n")
            }
            onProgress(pages.size, pages.size)
        }.trim()

    fun close() = recognizer.close()
}
