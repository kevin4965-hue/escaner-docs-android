package com.escanerdocs.app.data

import android.content.Context
import android.graphics.Bitmap
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
    val folderId: String? = null,
    /** Text recognized so far, for searching; empty until OCR has run on the pages. */
    val text: String = "",
)

data class Folder(val id: String, val name: String, val createdAt: Long)

/** One recognized line of text and its bounding box, in pixels of the original page image. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

/** OCR result for one page image; [width] and [height] are the image's pixel size. */
data class OcrPage(val width: Int, val height: Int, val lines: List<OcrLine>) {
    val text: String get() = lines.joinToString("\n") { it.text }
}

/**
 * Stores each document as a folder under `filesDir/documents/<id>/`:
 * `meta.json` with the title, dates, folder and page order, one JPEG per page, and an
 * optional `<page>.ocr.json` cache with the page's recognized text.
 * Folders are listed in `documents/folders.json`; the signature is `filesDir/signature.png`.
 * All functions do disk I/O and must be called off the main thread.
 */
class DocumentRepository(private val context: Context) {

    private val root = File(context.filesDir, "documents").apply { mkdirs() }
    private val foldersFile = File(root, "folders.json")

    val signatureFile = File(context.filesDir, "signature.png")

    // ---- Documents ----

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
            val pages = (0 until names.length()).map { File(dir, names.getString(it)) }
            ScannedDocument(
                id = id,
                title = json.getString("title"),
                createdAt = json.getLong("createdAt"),
                updatedAt = json.getLong("updatedAt"),
                pages = pages,
                folderId = json.optString("folderId").ifEmpty { null },
                text = pages.mapNotNull { readOcr(it)?.text }.joinToString("\n"),
            )
        }.getOrNull()
    }

    /** Creates a document by copying the scanner's page images into app storage. */
    fun create(title: String, pageUris: List<Uri>, folderId: String?): ScannedDocument {
        val id = UUID.randomUUID().toString()
        val dir = File(root, id).apply { mkdirs() }
        val now = System.currentTimeMillis()
        val doc = ScannedDocument(id, title, now, now, copyPages(dir, pageUris), folderId)
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
        ocrFile(doc.pages[index]).delete()
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

    /**
     * Replaces page [index] with [image]. The new image gets a new file name so image caches
     * refresh; the OCR cache moves with it because stamping a signature does not change the text.
     */
    fun replacePage(id: String, index: Int, image: Bitmap): ScannedDocument? {
        val doc = read(id) ?: return null
        if (index !in doc.pages.indices) return doc
        val old = doc.pages[index]
        val file = newPageFile(File(root, id))
        file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        ocrFile(old).takeIf { it.exists() }?.renameTo(ocrFile(file))
        old.delete()
        val pages = doc.pages.toMutableList().apply { set(index, file) }
        return doc.copy(pages = pages, updatedAt = System.currentTimeMillis()).also(::write)
    }

    fun rename(id: String, title: String): ScannedDocument? {
        val doc = read(id) ?: return null
        return doc.copy(title = title, updatedAt = System.currentTimeMillis()).also(::write)
    }

    fun moveToFolder(id: String, folderId: String?): ScannedDocument? {
        val doc = read(id) ?: return null
        return doc.copy(folderId = folderId).also(::write)
    }

    fun delete(id: String) {
        File(root, id).deleteRecursively()
    }

    // ---- OCR cache ----

    fun readOcr(page: File): OcrPage? {
        val file = ocrFile(page)
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            val lines = json.getJSONArray("lines")
            OcrPage(
                width = json.getInt("width"),
                height = json.getInt("height"),
                lines = (0 until lines.length()).map {
                    val l = lines.getJSONObject(it)
                    OcrLine(l.getString("t"), l.getInt("l"), l.getInt("tp"), l.getInt("r"), l.getInt("b"))
                },
            )
        }.getOrNull()
    }

    fun writeOcr(page: File, ocr: OcrPage) {
        val lines = JSONArray()
        ocr.lines.forEach {
            lines.put(
                JSONObject().put("t", it.text).put("l", it.left).put("tp", it.top)
                    .put("r", it.right).put("b", it.bottom)
            )
        }
        val json = JSONObject().put("width", ocr.width).put("height", ocr.height).put("lines", lines)
        atomicWrite(ocrFile(page), json.toString())
    }

    private fun ocrFile(page: File) = File(page.parentFile, page.nameWithoutExtension + ".ocr.json")

    // ---- Folders ----

    fun folders(): List<Folder> {
        if (!foldersFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(foldersFile.readText())
            (0 until array.length()).map {
                val f = array.getJSONObject(it)
                Folder(f.getString("id"), f.getString("name"), f.getLong("createdAt"))
            }.sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
    }

    fun createFolder(name: String): Folder {
        val folder = Folder(UUID.randomUUID().toString(), name, System.currentTimeMillis())
        writeFolders(folders() + folder)
        return folder
    }

    fun renameFolder(id: String, name: String) {
        writeFolders(folders().map { if (it.id == id) it.copy(name = name) else it })
    }

    /** Deletes a folder; its documents are kept and moved to the top level. */
    fun deleteFolder(id: String) {
        list().filter { it.folderId == id }.forEach { moveToFolder(it.id, null) }
        writeFolders(folders().filter { it.id != id })
    }

    private fun writeFolders(folders: List<Folder>) {
        val array = JSONArray()
        folders.forEach {
            array.put(JSONObject().put("id", it.id).put("name", it.name).put("createdAt", it.createdAt))
        }
        atomicWrite(foldersFile, array.toString())
    }

    // ---- Signature ----

    fun saveSignature(bitmap: Bitmap) {
        val tmp = File(signatureFile.path + ".tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        tmp.renameTo(signatureFile)
    }

    fun deleteSignature() {
        signatureFile.delete()
    }

    // ---- Helpers ----

    private fun copyPages(dir: File, uris: List<Uri>): List<File> =
        uris.map { uri ->
            val file = newPageFile(dir)
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "No se pudo leer la página $uri" }
                file.outputStream().use { input.copyTo(it) }
            }
            file
        }

    private fun newPageFile(dir: File) = File(dir, "page_${UUID.randomUUID()}.jpg")

    private fun write(doc: ScannedDocument) {
        val json = JSONObject()
            .put("title", doc.title)
            .put("createdAt", doc.createdAt)
            .put("updatedAt", doc.updatedAt)
            .put("folderId", doc.folderId ?: "")
            .put("pages", JSONArray(doc.pages.map { it.name }))
        atomicWrite(File(File(root, doc.id), META), json.toString())
    }

    // Write to a temp file first so a crash never leaves a half-written JSON file.
    private fun atomicWrite(file: File, content: String) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(content)
        tmp.renameTo(file)
    }

    private companion object {
        const val META = "meta.json"
        const val JPEG_QUALITY = 92
    }
}
