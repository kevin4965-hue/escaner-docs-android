package com.escanerdocs.app.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Builds an A4 PDF with one scanned image per page, scaled to fit with a small margin. */
object PdfExporter {

    // A4 in PDF points (1/72 inch).
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 18f

    // Long edge in pixels for the embedded image: ~200 dpi on A4, enough to read and print.
    private const val MAX_IMAGE_EDGE = 2339

    fun export(pages: List<File>, output: File) {
        require(pages.isNotEmpty()) { "El documento no tiene páginas" }
        output.parentFile?.mkdirs()
        val pdf = PdfDocument()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        try {
            pages.forEachIndexed { index, file ->
                val bitmap = decodeScaled(file) ?: return@forEachIndexed
                val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
                val page = pdf.startPage(info)
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(bitmap, null, fitRect(bitmap), paint)
                pdf.finishPage(page)
                bitmap.recycle()
            }
            output.outputStream().use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
    }

    private fun fitRect(bitmap: Bitmap): RectF {
        val availW = PAGE_WIDTH - 2 * MARGIN
        val availH = PAGE_HEIGHT - 2 * MARGIN
        val scale = min(availW / bitmap.width, availH / bitmap.height)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val left = (PAGE_WIDTH - w) / 2
        val top = (PAGE_HEIGHT - h) / 2
        return RectF(left, top, left + w, top + h)
    }

    private fun decodeScaled(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return null
        var sample = 1
        while (longEdge / (sample * 2) >= MAX_IMAGE_EDGE) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
