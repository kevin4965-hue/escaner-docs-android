package com.escanerdocs.app.sign

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.io.File

/**
 * Where to stamp the signature on a page, as fractions of the page image:
 * [left] and [top] of the signature's top-left corner, and its [width].
 * The height follows from the signature image's aspect ratio.
 */
data class SignaturePlacement(val left: Float, val top: Float, val width: Float)

object Signer {

    /** Returns a new full-resolution page image with the signature drawn on it. */
    fun stamp(page: File, signature: File, placement: SignaturePlacement): Bitmap {
        val options = BitmapFactory.Options().apply { inMutable = true }
        val pageBitmap = BitmapFactory.decodeFile(page.path, options)
            ?: error("No se pudo leer la página")
        val sig = BitmapFactory.decodeFile(signature.path) ?: error("No se pudo leer la firma")
        try {
            val w = placement.width * pageBitmap.width
            val h = w * sig.height / sig.width
            val left = placement.left * pageBitmap.width
            val top = placement.top * pageBitmap.height
            Canvas(pageBitmap).drawBitmap(
                sig, null, RectF(left, top, left + w, top + h),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
            )
        } finally {
            sig.recycle()
        }
        return pageBitmap
    }
}
