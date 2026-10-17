package com.escanerdocs.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.escanerdocs.app.data.Folder
import com.escanerdocs.app.data.ScannedDocument
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    folder: Folder?,
    folders: List<Folder>,
    documents: List<ScannedDocument>,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (String) -> Unit,
    onDeleteFolder: () -> Unit,
    onOpenSignature: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var nameDialog by remember { mutableStateOf<NameDialog?>(null) }
    var confirmDeleteFolder by remember { mutableStateOf(false) }

    val searching = query.isNotBlank()
    val q = query.trim()
    // A search looks through every document, by title and by recognized text.
    val visibleDocs = if (searching) {
        documents.filter { it.title.contains(q, ignoreCase = true) || it.text.contains(q, ignoreCase = true) }
    } else {
        documents.filter { it.folderId == folder?.id }
    }
    val visibleFolders = if (searching || folder != null) emptyList() else folders

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folder?.name ?: "Escáner Docs", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (folder != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                },
                actions = {
                    if (folder == null) {
                        IconButton(onClick = { nameDialog = NameDialog.NewFolder }) {
                            Icon(Icons.Outlined.CreateNewFolder, contentDescription = "Nueva carpeta")
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Más opciones")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Mi firma") },
                            leadingIcon = { Icon(Icons.Outlined.Draw, null) },
                            onClick = { menuOpen = false; onOpenSignature() },
                        )
                        if (folder != null) {
                            DropdownMenuItem(
                                text = { Text("Renombrar carpeta") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                onClick = { menuOpen = false; nameDialog = NameDialog.RenameFolder },
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar carpeta") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = { menuOpen = false; confirmDeleteFolder = true },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Outlined.DocumentScanner, contentDescription = null) },
                text = { Text("Escanear") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (documents.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Buscar por nombre o texto") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                )
            }
            when {
                searching && visibleDocs.isEmpty() ->
                    EmptyState("Sin resultados", "Ningún documento contiene «$q».")
                visibleFolders.isEmpty() && visibleDocs.isEmpty() -> if (folder != null) {
                    EmptyState("Carpeta vacía", "Escanea aquí o mueve documentos a esta carpeta desde su menú.")
                } else {
                    EmptyState(
                        "Aún no tienes documentos",
                        "Toca «Escanear» para digitalizar tu primer documento con la cámara o desde la galería.",
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(visibleFolders, key = { "f-" + it.id }) { f ->
                        FolderRow(f, count = documents.count { it.folderId == f.id }, onClick = { onOpenFolder(f.id) })
                    }
                    items(visibleDocs, key = { it.id }) { doc ->
                        val matchInText = searching && !doc.title.contains(q, ignoreCase = true)
                        DocumentRow(
                            doc,
                            folderName = if (searching) folders.firstOrNull { it.id == doc.folderId }?.name else null,
                            snippet = if (matchInText) snippet(doc.text, q) else null,
                            onClick = { onOpen(doc.id) },
                        )
                    }
                }
            }
        }
    }

    nameDialog?.let { dialog ->
        NameInputDialog(
            title = if (dialog == NameDialog.NewFolder) "Nueva carpeta" else "Renombrar carpeta",
            initial = if (dialog == NameDialog.RenameFolder) folder?.name.orEmpty() else "",
            onDismiss = { nameDialog = null },
            onConfirm = {
                if (dialog == NameDialog.NewFolder) onCreateFolder(it) else onRenameFolder(it)
                nameDialog = null
            },
        )
    }

    if (confirmDeleteFolder && folder != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteFolder = false },
            title = { Text("¿Eliminar la carpeta «${folder.name}»?") },
            text = { Text("Los documentos que contiene no se borran: pasan a la lista principal.") },
            confirmButton = {
                TextButton(onClick = { confirmDeleteFolder = false; onDeleteFolder() }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteFolder = false }) { Text("Cancelar") } },
        )
    }
}

private enum class NameDialog { NewFolder, RenameFolder }

@Composable
fun NameInputDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun FolderRow(folder: Folder, count: Int, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(16.dp))
            Text(
                folder.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (count == 1) "1 documento" else "$count documentos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun DocumentRow(doc: ScannedDocument, folderName: String?, snippet: String?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = doc.pages.firstOrNull(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 56.dp, height = 72.dp).clip(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    doc.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(pagesLabel(doc.pages.size), folderName, formatDate(doc.updatedAt)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (snippet != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A short excerpt of [text] around the first match of [query]. */
private fun snippet(text: String, query: String): String {
    val i = text.indexOf(query, ignoreCase = true)
    if (i < 0) return ""
    val start = (i - 30).coerceAtLeast(0)
    val end = (i + query.length + 50).coerceAtMost(text.length)
    val body = text.substring(start, end).replace('\n', ' ')
    return (if (start > 0) "…" else "") + body + (if (end < text.length) "…" else "")
}

@Composable
fun EmptyState(title: String, body: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Description,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

fun pagesLabel(count: Int) = if (count == 1) "1 página" else "$count páginas"

private fun formatDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
