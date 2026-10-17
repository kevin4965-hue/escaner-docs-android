package com.escanerdocs.app.export

import android.content.Context
import android.graphics.BitmapFactory
import com.escanerdocs.app.data.OcrPage
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import kotlin.math.min

/**
 * Builds an A4 PDF with one scanned image per page, scaled to fit with a small margin.
 * When OCR is available for a page, its lines are written as invisible text exactly over
 * the image, so the PDF can be searched, and its text selected and copied, in any PDF reader.
 */
object PdfExporter {

    private const val MARGIN = 18f

    fun init(context: Context) = PDFBoxResourceLoader.init(context)

    fun export(pages: List<File>, ocr: List<OcrPage?>, output: File) {
        require(pages.isNotEmpty()) { "El documento no tiene páginas" }
        output.parentFile?.mkdirs()
        PDDocument().use { pdf ->
            pages.forEachIndexed { index, file ->
                val (imgW, imgH) = imageSize(file) ?: return@forEachIndexed
                val page = PDPage(PDRectangle.A4)
                pdf.addPage(page)

                // Fit the image inside the page margins, centered. PDF origin is bottom-left.
                val box = page.mediaBox
                val scale = min((box.width - 2 * MARGIN) / imgW, (box.height - 2 * MARGIN) / imgH)
                val w = imgW * scale
                val h = imgH * scale
                val x = (box.width - w) / 2
                val y = (box.height - h) / 2

                // The original JPEG bytes are embedded as-is: no recompression, no quality loss.
                val image = file.inputStream().use { JPEGFactory.createFromStream(pdf, it) }
                PDPageContentStream(pdf, page).use { content ->
                    content.drawImage(image, x, y, w, h)
                    ocr.getOrNull(index)?.let { writeTextLayer(content, it, x, y, h, w / it.width) }
                }
            }
            pdf.save(output)
        }
    }

    private fun writeTextLayer(
        content: PDPageContentStream,
        ocr: OcrPage,
        imageX: Float,
        imageY: Float,
        imageH: Float,
        scale: Float,
    ) {
        val font = PDType1Font.HELVETICA
        content.beginText()
        content.setRenderingMode(RenderingMode.NEITHER)
        for (line in ocr.lines) {
            val text = encodable(line.text)
            if (text.isBlank()) continue
            val boxW = (line.right - line.left) * scale
            val boxH = (line.bottom - line.top) * scale
            if (boxW <= 0f || boxH <= 0f) continue
            // Font size from the line height; stretch horizontally so the text spans the box.
            val fontSize = boxH * 0.85f
            val naturalW = font.getStringWidth(text) / 1000f * fontSize
            content.setFont(font, fontSize)
            content.setHorizontalScaling(if (naturalW > 0f) boxW / naturalW * 100f else 100f)
            // Baseline slightly above the bottom of the box, converting from top-left image coords.
            val baseline = imageY + imageH - line.bottom * scale + boxH * 0.2f
            content.setTextMatrix(Matrix.getTranslateInstance(imageX + line.left * scale, baseline))
            content.showText(text)
        }
        content.endText()
    }

    /**
     * Helvetica uses WinAnsi encoding, which covers Spanish (á, é, ñ, ü, ¿, ¡…) but not every
     * character OCR can return. Characters it cannot encode become spaces, keeping word positions.
     */
    private fun encodable(text: String): String = buildString {
        val font = PDType1Font.HELVETICA
        for (c in text) {
            append(if (runCatching { font.encode(c.toString()) }.isSuccess) c else ' ')
        }
    }

    private fun imageSize(file: File): Pair<Float, Float>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return bounds.outWidth.toFloat() to bounds.outHeight.toFloat()
    }
}
