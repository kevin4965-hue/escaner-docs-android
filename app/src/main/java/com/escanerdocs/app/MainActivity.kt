package com.escanerdocs.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.escanerdocs.app.scan.rememberDocumentScanner
import com.escanerdocs.app.ui.DocumentScreen
import com.escanerdocs.app.ui.HomeScreen
import com.escanerdocs.app.ui.TextScreen
import com.escanerdocs.app.ui.theme.EscanerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EscanerTheme { App(viewModel) }
        }
    }
}

@Composable
private fun App(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.messages.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    BackHandler(enabled = state.screen != Screen.Home) { vm.back() }

    // Pages from a scan go to a new document, or to the open one when adding pages.
    val currentDocId = (state.screen as? Screen.Document)?.id
    val scanNew = rememberDocumentScanner(
        onResult = { vm.onScanned(it, addToId = null) },
        onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() },
    )
    val scanMore = rememberDocumentScanner(
        onResult = { vm.onScanned(it, addToId = currentDocId) },
        onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() },
    )

    Box(Modifier.fillMaxSize()) {
        when (val screen = state.screen) {
            Screen.Home -> HomeScreen(
                documents = state.documents,
                query = state.query,
                onQueryChange = vm::setQuery,
                onOpen = { vm.open(Screen.Document(it)) },
                onScan = scanNew,
            )
            is Screen.Document -> {
                val doc = vm.document(screen.id)
                if (doc == null) {
                    LaunchedEffect(Unit) { vm.back() }
                } else {
                    DocumentScreen(
                        document = doc,
                        onBack = { vm.back() },
                        onAddPages = scanMore,
                        onSharePdf = { vm.sharePdf(doc.id) },
                        onSavePdf = { vm.savePdf(doc.id) },
                        onExtractText = { vm.open(Screen.Text(doc.id)) },
                        onRename = { vm.rename(doc.id, it) },
                        onDelete = { vm.delete(doc.id) },
                        onDeletePage = { vm.deletePage(doc.id, it) },
                        onMovePage = { from, to -> vm.movePage(doc.id, from, to) },
                    )
                }
            }
            is Screen.Text -> TextScreen(
                title = vm.document(screen.id)?.title.orEmpty(),
                state = state.ocr,
                onBack = { vm.back() },
                onShare = vm::shareText,
            )
        }

        if (state.busy) {
            LinearProgressIndicator(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding()
            )
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp),
        )
    }
}
