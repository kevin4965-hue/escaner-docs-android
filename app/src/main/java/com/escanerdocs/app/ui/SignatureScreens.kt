package com.escanerdocs.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.escanerdocs.app.sign.SignaturePlacement
import java.io.File
import kotlin.math.roundToInt

/** Dark blue, like a ballpoint pen, so signatures stand out from black printed text. */
private val InkColor = Color(0xFF0B2A6F)
private const val INK_ARGB = 0xFF0B2A6F.toInt()

/** Draw the user's signature with a finger and save it as a transparent PNG. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureEditorScreen(
    current: File?,
    onBack: () -> Unit,
    onSave: (Bitmap) -> Unit,
    onDelete: () -> Unit,
) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var activeStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val strokeWidthPx = with(LocalDensity.current) { 3.dp.toPx() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi firma") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (current != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Eliminar firma guardada")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (current != null) {
                Text("Firma guardada", style = MaterialTheme.typography.labelLarge)
                AsyncImage(
                    model = current,
                    contentDescription = "Firma guardada",
                    modifier = Modifier.fillMaxWidth().height(80.dp).padding(vertical = 8.dp),
                )
                Text("Dibuja abajo para reemplazarla.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            } else {
                Text(
                    "Firma con el dedo dentro del recuadro. Se guardará en este teléfono para firmar tus documentos.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            ) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { activeStroke = listOf(it) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    activeStroke = activeStroke + change.position
                                },
                                onDragEnd = {
                                    if (activeStroke.size > 1) strokes.add(activeStroke)
                                    activeStroke = emptyList()
                                },
                                onDragCancel = { activeStroke = emptyList() },
                            )
                        }
                ) {
                    // A baseline to sign on.
                    val lineY = size.height * 0.75f
                    drawLine(Color.LightGray, Offset(24f, lineY), Offset(size.width - 24f, lineY), strokeWidth = 2f)
                    (strokes + listOf(activeStroke)).forEach { points ->
                        if (points.size > 1) {
                            drawPath(
                                pathOf(points),
                                InkColor,
                                style = Stroke(strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
                            )
                        }
                    }
                }
                if (strokes.isEmpty() && activeStroke.isEmpty()) {
                    Text(
                        "Firma aquí",
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Center),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { strokes.clear() },
                    enabled = strokes.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("Limpiar") }
                Button(
                    onClick = { renderSignature(strokes, strokeWidthPx)?.let(onSave) },
                    enabled = strokes.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("Guardar firma") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar la firma guardada?") },
            text = { Text("Las páginas que ya firmaste no cambian.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

private fun pathOf(points: List<Offset>) = Path().apply {
    moveTo(points[0].x, points[0].y)
    // Quadratic segments through midpoints give a smooth line instead of a polyline.
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val cur = points[i]
        quadraticTo(prev.x, prev.y, (prev.x + cur.x) / 2, (prev.y + cur.y) / 2)
    }
    lineTo(points.last().x, points.last().y)
}

/** Renders the strokes, cropped to their bounds, at 2x into a transparent bitmap. */
private fun renderSignature(strokes: List<List<Offset>>, strokeWidthPx: Float): Bitmap? {
    val all = strokes.flatten()
    if (all.isEmpty()) return null
    val pad = strokeWidthPx * 2
    val minX = all.minOf { it.x } - pad
    val minY = all.minOf { it.y } - pad
    val maxX = all.maxOf { it.x } + pad
    val maxY = all.maxOf { it.y } + pad
    val scale = 2f
    val bitmap = Bitmap.createBitmap(
        ((maxX - minX) * scale).roundToInt().coerceAtLeast(1),
        ((maxY - minY) * scale).roundToInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = INK_ARGB
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = strokeWidthPx * scale
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    strokes.forEach { points ->
        val path = android.graphics.Path()
        fun x(o: Offset) = (o.x - minX) * scale
        fun y(o: Offset) = (o.y - minY) * scale
        path.moveTo(x(points[0]), y(points[0]))
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val cur = points[i]
            path.quadTo(x(prev), y(prev), (x(prev) + x(cur)) / 2, (y(prev) + y(cur)) / 2)
        }
        path.lineTo(x(points.last()), y(points.last()))
        canvas.drawPath(path, paint)
    }
    return bitmap
}

/** Place the saved signature on a page: drag to move, pinch or use the slider to resize. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignPageScreen(
    page: File,
    pageNumber: Int,
    signature: File,
    onBack: () -> Unit,
    onApply: (SignaturePlacement) -> Unit,
    onEditSignature: () -> Unit,
) {
    val pageAspect = remember(page) { aspectOf(page) }
    val sigAspect = remember(signature, signature.lastModified()) { aspectOf(signature) }
    // Position as fractions of the page; starts near the bottom right, where signatures usually go.
    var left by remember { mutableFloatStateOf(0.55f) }
    var top by remember { mutableFloatStateOf(0.78f) }
    var width by remember { mutableFloatStateOf(0.35f) }

    // Signature height as a fraction of the page height.
    fun heightFraction() = width * pageAspect / sigAspect

    // Resizes while keeping the signature inside the page.
    fun resize(newWidth: Float) {
        width = newWidth.coerceIn(MIN_WIDTH, MAX_WIDTH)
        left = left.coerceIn(0f, (1f - width).coerceAtLeast(0f))
        top = top.coerceIn(0f, (1f - heightFraction()).coerceAtLeast(0f))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Firmar página $pageNumber") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = { TextButton(onClick = onEditSignature) { Text("Cambiar firma") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Size of the page as displayed (ContentScale.Fit inside the available space).
                val boxAspect = maxWidth / maxHeight
                val pageW = if (pageAspect > boxAspect) maxWidth else maxHeight * pageAspect
                val pageH = if (pageAspect > boxAspect) maxWidth / pageAspect else maxHeight
                val density = LocalDensity.current
                val pageWPx = with(density) { pageW.toPx() }
                val pageHPx = with(density) { pageH.toPx() }

                Box(Modifier.size(pageW, pageH)) {
                    AsyncImage(
                        model = page,
                        contentDescription = "Página $pageNumber",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                    AsyncImage(
                        model = signature,
                        contentDescription = "Firma",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .offset { IntOffset((left * pageWPx).roundToInt(), (top * pageHPx).roundToInt()) }
                            .size(pageW * width, pageW * width / sigAspect)
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                            .pointerInput(pageWPx, pageHPx) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    resize(width * zoom)
                                    left = (left + pan.x / pageWPx).coerceIn(0f, 1f - width)
                                    top = (top + pan.y / pageHPx).coerceIn(0f, (1f - heightFraction()).coerceAtLeast(0f))
                                }
                            },
                    )
                }
            }

            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    "Arrastra la firma a su lugar. Pellizca o usa la barra para cambiar el tamaño.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(value = width, onValueChange = ::resize,valueRange = MIN_WIDTH..MAX_WIDTH)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                    Button(
                        onClick = { onApply(SignaturePlacement(left, top, width)) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Firmar") }
                }
            }
        }
    }
}

private const val MIN_WIDTH = 0.08f
private const val MAX_WIDTH = 0.9f

/** Width / height of an image file, or A4 portrait if it cannot be read. */
private fun aspectOf(file: File): Float {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    return if (bounds.outWidth > 0 && bounds.outHeight > 0) {
        bounds.outWidth.toFloat() / bounds.outHeight
    } else {
        1f / 1.414f
    }
}
