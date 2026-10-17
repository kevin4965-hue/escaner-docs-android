package com.escanerdocs.app.scan

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Google's on-device document scanner (ML Kit via Google Play services): camera capture,
 * automatic edge detection, perspective correction, filters, cleanup and gallery import.
 * Returns a function that starts a scan; [onResult] receives the page image URIs.
 */
@Composable
fun rememberDocumentScanner(
    onResult: (List<Uri>) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val activity = LocalContext.current as Activity
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            onResult(scan?.pages?.map { it.imageUri }.orEmpty())
        }
    }
    val client = remember {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .setGalleryImportAllowed(true)
                .setPageLimit(MAX_PAGES)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .build()
        )
    }
    return {
        client.getStartScanIntent(activity)
            .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { onError("No se pudo abrir el escáner: ${it.message}") }
    }
}

private const val MAX_PAGES = 50
