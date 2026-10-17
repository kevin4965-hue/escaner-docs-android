package com.escanerdocs.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.escanerdocs.app.data.DocumentRepository
import com.escanerdocs.app.data.ScannedDocument
import com.escanerdocs.app.export.FileSharing
import com.escanerdocs.app.export.PdfExporter
import com.escanerdocs.app.ocr.TextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface Screen {
    data object Home : Screen
    data class Document(val id: String) : Screen
    data class Text(val id: String) : Screen
}

sealed interface OcrState {
    data object Idle : OcrState
    data class Running(val done: Int, val total: Int) : OcrState
    data class Done(val text: String) : OcrState
    data class Failed(val message: String) : OcrState
}

data class UiState(
    val screen: Screen = Screen.Home,
    val documents: List<ScannedDocument> = emptyList(),
    val query: String = "",
    val busy: Boolean = false,
    val ocr: OcrState = OcrState.Idle,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DocumentRepository(app)
    private val ocr = TextExtractor(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** One-off messages for a snackbar. */
    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()

    init {
        refresh()
    }

    fun document(id: String): ScannedDocument? = _state.value.documents.firstOrNull { it.id == id }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun open(screen: Screen) {
        _state.update { it.copy(screen = screen) }
        if (screen is Screen.Text) runOcr(screen.id)
    }

    /** Handles the system back gesture; returns false when the app should close. */
    fun back(): Boolean {
        val target = when (val s = _state.value.screen) {
            Screen.Home -> return false
            is Screen.Document -> Screen.Home
            is Screen.Text -> Screen.Document(s.id)
        }
        _state.update { it.copy(screen = target, ocr = OcrState.Idle) }
        return true
    }

    fun messageShown() {
        _messages.value = null
    }

    fun onScanned(pages: List<Uri>, addToId: String?) = io("No se pudieron guardar las páginas") {
        if (pages.isEmpty()) return@io
        val doc = if (addToId != null) {
            repo.addPages(addToId, pages)
        } else {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.getDefault()).format(Date())
            repo.create("Escaneo $stamp", pages)
        }
        reload()
        if (doc != null) _state.update { it.copy(screen = Screen.Document(doc.id)) }
    }

    fun rename(id: String, title: String) = io("No se pudo renombrar") {
        if (title.isNotBlank()) repo.rename(id, title.trim())
        reload()
    }

    fun delete(id: String) = io("No se pudo eliminar") {
        repo.delete(id)
        reload()
        _state.update { it.copy(screen = Screen.Home) }
    }

    fun deletePage(id: String, index: Int) = io("No se pudo eliminar la página") {
        val doc = repo.deletePage(id, index)
        // A document without pages is meaningless; remove it entirely.
        if (doc != null && doc.pages.isEmpty()) {
            repo.delete(id)
            _state.update { it.copy(screen = Screen.Home) }
        }
        reload()
    }

    fun movePage(id: String, from: Int, to: Int) = io("No se pudo mover la página") {
        repo.movePage(id, from, to)
        reload()
    }

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

    private fun runOcr(id: String) {
        val doc = document(id) ?: return
        viewModelScope.launch {
            _state.update { it.copy(ocr = OcrState.Running(0, doc.pages.size)) }
            val result = runCatching {
                ocr.extractAll(doc.pages) { done, total ->
                    _state.update { it.copy(ocr = OcrState.Running(done, total)) }
                }
            }
            _state.update {
                it.copy(
                    ocr = result.fold(
                        onSuccess = { text -> OcrState.Done(text.ifBlank { "No se encontró texto en el documento." }) },
                        onFailure = { e -> OcrState.Failed(e.message ?: "Error desconocido") },
                    )
                )
            }
        }
    }

    private fun buildPdf(id: String): File? {
        val doc = repo.read(id) ?: return null
        val file = File(FileSharing.exportDir(getApplication()), FileSharing.safeName(doc.title) + ".pdf")
        PdfExporter.export(doc.pages, file)
        return file
    }

    private fun reload() {
        val docs = repo.list()
        _state.update { it.copy(documents = docs) }
    }

    private fun refresh() = io("No se pudieron cargar los documentos") { reload() }

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
