package com.escanerdocs.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.escanerdocs.app.data.DocumentRepository
import com.escanerdocs.app.data.Folder
import com.escanerdocs.app.data.OcrPage
import com.escanerdocs.app.data.ScannedDocument
import com.escanerdocs.app.export.FileSharing
import com.escanerdocs.app.export.PdfExporter
import com.escanerdocs.app.ocr.TextExtractor
import com.escanerdocs.app.sign.SignaturePlacement
import com.escanerdocs.app.sign.Signer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface Screen {
    /** Document list; [folderId] null is the top level. */
    data class Home(val folderId: String? = null) : Screen
    data class Document(val id: String) : Screen
    data class Text(val id: String) : Screen
    /** Draw and save the user's signature, then go back to [returnTo]. */
    data class SignatureEditor(val returnTo: Screen) : Screen
    data class SignPage(val id: String, val pageIndex: Int) : Screen
}

sealed interface OcrState {
    data object Idle : OcrState
    data class Running(val done: Int, val total: Int) : OcrState
    data class Done(val text: String) : OcrState
    data class Failed(val message: String) : OcrState
}

data class UiState(
    val screen: Screen = Screen.Home(),
    val documents: List<ScannedDocument> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val query: String = "",
    val busy: Boolean = false,
    val ocr: OcrState = OcrState.Idle,
    val signature: File? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DocumentRepository(app)
    private val ocr = TextExtractor()

    // Serializes OCR so background recognition and an explicit request never process a page twice.
    private val ocrMutex = Mutex()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** One-off messages for a snackbar. */
    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()

    init {
        PdfExporter.init(app)
        io("No se pudieron cargar los documentos") { reload() }
    }

    fun document(id: String): ScannedDocument? = _state.value.documents.firstOrNull { it.id == id }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun open(screen: Screen) {
        _state.update { it.copy(screen = screen) }
        if (screen is Screen.Text) showText(screen.id)
    }

    /** Handles the system back gesture; returns false when the app should close. */
    fun back(): Boolean {
        val target = when (val s = _state.value.screen) {
            is Screen.Home -> if (s.folderId != null) Screen.Home() else return false
            is Screen.Document -> Screen.Home(document(s.id)?.folderId)
            is Screen.Text -> Screen.Document(s.id)
            is Screen.SignatureEditor -> s.returnTo
            is Screen.SignPage -> Screen.Document(s.id)
        }
        _state.update { it.copy(screen = target, ocr = OcrState.Idle) }
        return true
    }

    fun messageShown() {
        _messages.value = null
    }

    // ---- Documents ----

    fun onScanned(pages: List<Uri>, addToId: String?) = io("No se pudieron guardar las páginas") {
        if (pages.isEmpty()) return@io
        val doc = if (addToId != null) {
            repo.addPages(addToId, pages)
        } else {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.getDefault()).format(Date())
            val folderId = (_state.value.screen as? Screen.Home)?.folderId
            repo.create("Escaneo $stamp", pages, folderId)
        }
        reload()
        if (doc != null) {
            _state.update { it.copy(screen = Screen.Document(doc.id)) }
            recognizeInBackground(doc.id)
        }
    }

    fun rename(id: String, title: String) = io("No se pudo renombrar") {
        if (title.isNotBlank()) repo.rename(id, title.trim())
        reload()
    }

    fun delete(id: String) = io("No se pudo eliminar") {
        val folderId = document(id)?.folderId
        repo.delete(id)
        reload()
        _state.update { it.copy(screen = Screen.Home(folderId)) }
    }

    fun deletePage(id: String, index: Int) = io("No se pudo eliminar la página") {
        val doc = repo.deletePage(id, index)
        // A document without pages is meaningless; remove it entirely.
        if (doc != null && doc.pages.isEmpty()) {
            repo.delete(id)
            _state.update { it.copy(screen = Screen.Home(doc.folderId)) }
        }
        reload()
    }

    fun movePage(id: String, from: Int, to: Int) = io("No se pudo mover la página") {
        repo.movePage(id, from, to)
        reload()
    }

    fun moveToFolder(id: String, folderId: String?) = io("No se pudo mover el documento") {
        repo.moveToFolder(id, folderId)
        reload()
        val name = _state.value.folders.firstOrNull { it.id == folderId }?.name
        _messages.value = if (name != null) "Movido a «$name»" else "Movido a Documentos"
    }

    // ---- Folders ----

    fun createFolder(name: String) = io("No se pudo crear la carpeta") {
        if (name.isNotBlank()) repo.createFolder(name.trim())
        reload()
    }

    fun renameFolder(id: String, name: String) = io("No se pudo renombrar la carpeta") {
        if (name.isNotBlank()) repo.renameFolder(id, name.trim())
        reload()
    }

    fun deleteFolder(id: String) = io("No se pudo eliminar la carpeta") {
        repo.deleteFolder(id)
        reload()
        _state.update { it.copy(screen = Screen.Home()) }
    }

    // ---- Export ----

    fun sharePdf(id: String) = io("No se pudo crear el PDF") {
        val pdf = buildPdf(id) ?: return@io
        FileSharing.share(getApplication(), pdf, "application/pdf")
    }

    fun savePdf(id: String) = io("No se pudo guardar el PDF") {
        val pdf = buildPdf(id) ?: return@io
        if (FileSharing.saveToDownloads(getApplication(), pdf, "application/pdf")) {
            _messages.value = "PDF guardado en Descargas: ${pdf.name}"
        } else {
            FileSharing.share(getApplication(), pdf, "application/pdf")
        }
    }

    fun shareText(text: String) = FileSharing.shareText(getApplication(), text)

    /** Builds a searchable PDF, running OCR first on any page that does not have it yet. */
    private suspend fun buildPdf(id: String): File? {
        val doc = repo.read(id) ?: return null
        _messages.value = "Preparando PDF con texto buscable…"
        val pages = ensureOcr(doc) { _, _ -> }
        val file = File(FileSharing.exportDir(getApplication()), FileSharing.safeName(doc.title) + ".pdf")
        PdfExporter.export(doc.pages, pages, file)
        reload()
        return file
    }

    // ---- OCR ----

    private fun showText(id: String) {
        val doc = document(id) ?: return
        viewModelScope.launch {
            _state.update { it.copy(ocr = OcrState.Running(0, doc.pages.size)) }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    ensureOcr(doc) { done, total ->
                        _state.update { it.copy(ocr = OcrState.Running(done, total)) }
                    }
                }
            }
            _state.update {
                it.copy(
                    ocr = result.fold(
                        onSuccess = { pages -> OcrState.Done(joinText(pages)) },
                        onFailure = { e -> OcrState.Failed(e.message ?: "Error desconocido") },
                    )
                )
            }
            withContext(Dispatchers.IO) { reload() }
        }
    }

    /** Recognizes new pages right after a scan so search and PDF export are ready sooner. */
    private fun recognizeInBackground(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val doc = repo.read(id) ?: return@launch
            runCatching { ensureOcr(doc) { _, _ -> } }
            reload()
        }
    }

    /** Returns the OCR of every page, recognizing and caching the ones that are missing. */
    private suspend fun ensureOcr(doc: ScannedDocument, onProgress: (Int, Int) -> Unit): List<OcrPage?> =
        ocrMutex.withLock {
            doc.pages.mapIndexed { index, page ->
                onProgress(index, doc.pages.size)
                if (!page.exists()) return@mapIndexed null
                repo.readOcr(page) ?: ocr.recognize(page).also { repo.writeOcr(page, it) }
            }.also { onProgress(doc.pages.size, doc.pages.size) }
        }

    private fun joinText(pages: List<OcrPage?>): String {
        val text = pages.mapIndexed { index, page ->
            val body = page?.text?.trim().orEmpty()
            if (pages.size > 1) "--- Página ${index + 1} ---\n$body" else body
        }.joinToString("\n\n").trim()
        return if (pages.all { it?.text.isNullOrBlank() }) "No se encontró texto en el documento." else text
    }

    // ---- Signature ----

    fun saveSignature(bitmap: Bitmap) = io("No se pudo guardar la firma") {
        repo.saveSignature(bitmap)
        reload()
        _messages.value = "Firma guardada"
        val screen = _state.value.screen
        if (screen is Screen.SignatureEditor) _state.update { it.copy(screen = screen.returnTo) }
    }

    fun deleteSignature() = io("No se pudo eliminar la firma") {
        repo.deleteSignature()
        reload()
    }

    /** Opens page signing, asking the user to draw a signature first if there is none. */
    fun startSigning(id: String, pageIndex: Int) {
        val signPage = Screen.SignPage(id, pageIndex)
        open(if (_state.value.signature == null) Screen.SignatureEditor(returnTo = signPage) else signPage)
    }

    fun signPage(id: String, pageIndex: Int, placement: SignaturePlacement) = io("No se pudo firmar la página") {
        val doc = repo.read(id) ?: return@io
        val page = doc.pages.getOrNull(pageIndex) ?: return@io
        val signed = Signer.stamp(page, repo.signatureFile, placement)
        try {
            repo.replacePage(id, pageIndex, signed)
        } finally {
            signed.recycle()
        }
        reload()
        _state.update { it.copy(screen = Screen.Document(id)) }
        _messages.value = "Página ${pageIndex + 1} firmada"
    }

    // ---- Internals ----

    private fun reload() {
        val docs = repo.list()
        val folders = repo.folders()
        val signature = repo.signatureFile.takeIf { it.exists() }
        _state.update { it.copy(documents = docs, folders = folders, signature = signature) }
    }

    /** Runs disk work off the main thread with a busy indicator, reporting failures as a message. */
    private fun io(errorMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                _messages.value = "$errorMessage: ${e.message}"
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    override fun onCleared() {
        ocr.close()
    }
}
