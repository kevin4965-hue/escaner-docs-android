package com.escanerdocs.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.escanerdocs.app.OcrState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextScreen(
    title: String,
    state: OcrState,
    onBack: () -> Unit,
    onShare: (String) -> Unit,
) {
    @Suppress("DEPRECATION") // LocalClipboard's suspend API adds nothing for plain text.
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val text = (state as? OcrState.Done)?.text

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Texto · $title", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (text != null) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(text))
                            Toast.makeText(context, "Texto copiado", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copiar")
                        }
                        IconButton(onClick = { onShare(text) }) {
                            Icon(Icons.Outlined.Share, contentDescription = "Compartir")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                OcrState.Idle -> Unit
                is OcrState.Running -> Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Reconociendo texto… página ${minOf(state.done + 1, state.total)} de ${state.total}")
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is OcrState.Done -> SelectionContainer {
                    Text(
                        state.text,
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                is OcrState.Failed -> EmptyState("No se pudo reconocer el texto", state.message)
            }
        }
    }
}
