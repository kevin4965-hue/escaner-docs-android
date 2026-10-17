package com.escanerdocs.app.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

object FileSharing {

    /** Directory for files handed to other apps; it is the only path exposed by the FileProvider. */
    fun exportDir(context: Context): File = File(context.cacheDir, "exports").apply { mkdirs() }

    /** Turns a document title into a safe file name. */
    fun safeName(title: String): String =
        title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "documento" }

    fun share(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "Compartir").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun shareText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            Intent.createChooser(send, "Compartir texto").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Copies a file into the public Downloads folder. Returns false on Android 9 and older,
     * where this needs a storage permission the app does not request; callers fall back to sharing.
     */
    fun saveToDownloads(context: Context, file: File, mimeType: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri).use { out ->
            requireNotNull(out)
            file.inputStream().use { it.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return true
    }
}
