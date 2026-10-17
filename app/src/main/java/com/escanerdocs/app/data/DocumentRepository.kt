package com.escanerdocs.app.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A scanned document: an ordered list of page images plus metadata. */
data class ScannedDocument(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pages: List<File>,
)

/**
 * Stores each document as a folder under `filesDir/documents/<id>/`:
 * `meta.json` with the title, dates and page order, and one JPEG per page.
 * All functions do disk I/O and must be called off the main thread.
 */
class DocumentRepository(private val context: Context) {

    private val root = File(context.filesDir, "documents").apply { mkdirs() }

    fun list(): List<ScannedDocument> =
        root.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { read(it.name) }
            ?.sortedByDescending { it.updatedAt }
            .orEmpty()

    fun read(id: String): ScannedDocument? {
        val dir = File(root, id)
        val meta = File(dir, META)
        if (!meta.exists()) return null
        return runCatching {
            val json = JSONObject(meta.readText())
            val names = json.getJSONArray("pages")
            ScannedDocument(
                id = id,
                title = json.getString("title"),
                createdAt = json.getLong("createdAt"),
                updatedAt = json.getLong("updatedAt"),
                pages = (0 until names.length()).map { File(dir, names.getString(it)) },
            )
        }.getOrNull()
    }

    /** Creates a document by copying the scanner's page images into app storage. */
    fun create(title: String, pageUris: List<Uri>): ScannedDocument {
        val id = UUID.randomUUID().toString()
        val dir = File(root, id).apply { mkdirs() }
        val now = System.currentTimeMillis()
        val doc = ScannedDocument(id, title, now, now, copyPages(dir, pageUris))
        write(doc)
        return doc
    }

    fun addPages(id: String, pageUris: List<Uri>): ScannedDocument? {
        val doc = read(id) ?: return null
        val added = copyPages(File(root, id), pageUris)
        return doc.copy(pages = doc.pages + added, updatedAt = System.currentTimeMillis())
            .also(::write)
    }

    fun deletePage(id: String, index: Int): ScannedDocument? {
        val doc = read(id) ?: return null
        if (index !in doc.pages.indices) return doc
        doc.pages[index].delete()
        return doc.copy(
            pages = doc.pages.filterIndexed { i, _ -> i != index },
            updatedAt = System.currentTimeMillis(),
        ).also(::write)
    }

    fun movePage(id: String, from: Int, to: Int): ScannedDocument? {
        val doc = read(id) ?: return null
        if (from !in doc.pages.indices || to !in doc.pages.indices) return doc
        val pages = doc.pages.toMutableList().apply { add(to, removeAt(from)) }
        return doc.copy(pages = pages, updatedAt = System.currentTimeMillis()).also(::write)
    }

    fun rename(id: String, title: String): ScannedDocument? {
        val doc = read(id) ?: return null
        return doc.copy(title = title, updatedAt = System.currentTimeMillis()).also(::write)
    }

    fun delete(id: String) {
        File(root, id).deleteRecursively()
    }

    private fun copyPages(dir: File, uris: List<Uri>): List<File> =
        uris.map { uri ->
            val file = File(dir, "page_${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "No se pudo leer la página $uri" }
                file.outputStream().use { input.copyTo(it) }
            }
            file
        }

    private fun write(doc: ScannedDocument) {
        val json = JSONObject()
            .put("title", doc.title)
            .put("createdAt", doc.createdAt)
            .put("updatedAt", doc.updatedAt)
            .put("pages", JSONArray(doc.pages.map { it.name }))
        val dir = File(root, doc.id)
        // Write to a temp file first so a crash never leaves a half-written meta.json.
        val tmp = File(dir, "$META.tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(File(dir, META))
    }

    private companion object {
        const val META = "meta.json"
    }
}
