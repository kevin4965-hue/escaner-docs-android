package com.escanerdocs.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.escanerdocs.app.data.Folder
import com.escanerdocs.app.data.ScannedDocument

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    document: ScannedDocument,
    folders: List<Folder>,
    onBack: () -> Unit,
    onAddPages: () -> Unit,
    onSharePdf: () -> Unit,
    onSavePdf: () -> Unit,
    onExtractText: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDeletePage: (Int) -> Unit,
    onMovePage: (from: Int, to: Int) -> Unit,
    onMoveToFolder: (String?) -> Unit,
    onSignPage: (Int) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var choosingFolder by remember { mutableStateOf(false) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(document.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            pagesLabel(document.pages.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Más opciones")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Renombrar") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = { menuOpen = false; renaming = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Mover a carpeta") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) },
                            onClick = { menuOpen = false; choosingFolder = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar documento") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = { menuOpen = false; confirmDelete = true },
                        )
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BarAction(Icons.Outlined.AddAPhoto, "Añadir", onAddPages)
                    BarAction(Icons.Outlined.Share, "Compartir", onSharePdf)
                    BarAction(Icons.Outlined.Download, "Guardar PDF", onSavePdf)
                    BarAction(Icons.AutoMirrored.Outlined.TextSnippet, "Texto", onExtractText)
                }
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(document.pages, key = { _, file -> file.name }) { index, file ->
                Box {
                    AsyncImage(
                        model = file,
                        contentDescription = "Página ${index + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { previewIndex = index },
                    )
                    Surface(
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            "${index + 1}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }

    previewIndex?.let { index ->
        if (index in document.pages.indices) {
            PagePreview(
                document = document,
                index = index,
                onDismiss = { previewIndex = null },
                onDelete = { onDeletePage(index); previewIndex = null },
                onMove = { to -> onMovePage(index, to); previewIndex = to },
                onSign = { previewIndex = null; onSignPage(index) },
            )
        } else {
            previewIndex = null
        }
    }

    if (renaming) {
        var title by remember { mutableStateOf(document.title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Renombrar documento") },
            text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { onRename(title); renaming = false }, enabled = title.isNotBlank()) {
                    Text("Guardar")
                }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancelar") } },
        )
    }

    if (choosingFolder) {
        FolderPicker(
            folders = folders,
            current = document.folderId,
            onDismiss = { choosingFolder = false },
            onPick = { choosingFolder = false; onMoveToFolder(it) },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar documento?") },
            text = { Text("Se borrarán «${document.title}» y sus ${pagesLabel(document.pages.size)}. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun BarAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** Full-screen view of one page with actions to reorder or delete it. */
@Composable
private fun PagePreview(
    document: ScannedDocument,
    index: Int,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
    onSign: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cerrar", tint = Color.White)
                }
                Text(
                    "Página ${index + 1} de ${document.pages.size}",
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
            }
            AsyncImage(
                model = document.pages[index],
                contentDescription = "Página ${index + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { onMove(index - 1) }, enabled = index > 0) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Mover antes", tint = Color.White)
                }
                IconButton(onClick = onSign) {
                    Icon(Icons.Outlined.Draw, "Firmar página", tint = Color.White)
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Outlined.Delete, "Eliminar página", tint = Color.White)
                }
                IconButton(onClick = { onMove(index + 1) }, enabled = index < document.pages.lastIndex) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Mover después", tint = Color.White)
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar esta página?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

/** Lets the user choose a destination folder, or the top level, for a document. */
@Composable
private fun FolderPicker(
    folders: List<Folder>,
    current: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mover a carpeta") },
        text = {
            Column {
                FolderOption(Icons.Outlined.Home, "Documentos (sin carpeta)", current == null) { onPick(null) }
                folders.forEach { f ->
                    FolderOption(Icons.Outlined.Folder, f.name, current == f.id) { onPick(f.id) }
                }
                if (folders.isEmpty()) {
                    Text(
                        "Aún no tienes carpetas. Créalas desde la pantalla principal con el botón de carpeta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun FolderOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = !selected, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            if (selected) "$label (actual)" else label,
            modifier = Modifier.padding(start = 16.dp),
            color = if (selected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}