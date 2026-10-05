package com.rainax.docscan

import android.app.Activity
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Dlg {
    data object None : Dlg
    data object Rename : Dlg
    data object Filter : Dlg
    data object Sign : Dlg
    data object Protect : Dlg
    data object Delete : Dlg
    data class Ocr(val text: String?) : Dlg
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    doc: ScanDoc,
    onBack: () -> Unit,
    onChanged: (ScanDoc?) -> Unit,
) {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var shareMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<Dlg>(Dlg.None) }
    var busy by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

    /** Runs [block] off the main thread, then reloads the document. */
    fun work(block: () -> Unit) {
        scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { block() }
                onChanged(Repo.load(doc.dir))
            } catch (e: Exception) {
                toast("Failed: ${e.message}")
            } finally {
                busy = false
            }
        }
    }

    /** Runs [block] off the main thread and shares the resulting files. */
    fun exportAndShare(mime: String, block: () -> List<java.io.File>) {
        scope.launch {
            busy = true
            try {
                val files = withContext(Dispatchers.IO) { block() }
                Share.files(ctx, mime, files)
            } catch (e: Exception) {
                toast("Failed: ${e.message}")
            } finally {
                busy = false
            }
        }
    }

    fun runOcr() {
        Ads.showRewarded(activity) {
            scope.launch {
                dialog = Dlg.Ocr(null)
                dialog = try {
                    val text = withContext(Dispatchers.IO) { Ocr.read(doc.pages) }
                    Dlg.Ocr(text.ifBlank { "No text found." })
                } catch (e: Exception) {
                    Dlg.Ocr("OCR failed: ${e.message}")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(doc.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { runOcr() }) { Icon(Icons.Default.TextFields, "Extract text (OCR)") }
                    Box {
                        IconButton(onClick = { shareMenu = true }) { Icon(Icons.Default.Share, "Share") }
                        DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                            DropdownMenuItem(text = { Text("Share as PDF") }, onClick = {
                                shareMenu = false
                                exportAndShare("application/pdf") { listOf(Repo.exportPdf(ctx, doc)) }
                            })
                            DropdownMenuItem(text = { Text("Share as images (JPG)") }, onClick = {
                                shareMenu = false
                                exportAndShare("image/jpeg") { Repo.exportImages(ctx, doc) }
                            })
                            DropdownMenuItem(text = { Text("Share password-protected PDF") }, onClick = {
                                shareMenu = false
                                dialog = Dlg.Protect
                            })
                        }
                    }
                    Box {
                        IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { moreMenu = false; dialog = Dlg.Rename })
                            DropdownMenuItem(text = { Text("Filter (Original / Gray / B&W)") }, onClick = { moreMenu = false; dialog = Dlg.Filter })
                            DropdownMenuItem(text = { Text("Sign") }, onClick = { moreMenu = false; dialog = Dlg.Sign })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { moreMenu = false; dialog = Dlg.Delete })
                        }
                    }
                },
            )
        },
        bottomBar = { BannerAd() },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(doc.pages, key = { _, f -> f.name }) { i, f ->
                    Column {
                        AsyncImage(
                            model = f,
                            contentDescription = "Page ${i + 1}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().border(1.dp, Color.LightGray),
                        )
                        Text(
                            "Page ${i + 1} / ${doc.pages.size}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }

    when (val d = dialog) {
        Dlg.None -> {}

        Dlg.Rename -> {
            var name by remember { mutableStateOf(doc.name) }
            AlertDialog(
                onDismissRequest = { dialog = Dlg.None },
                title = { Text("Rename") },
                text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = { dialog = Dlg.None; work { Repo.rename(doc, name) } }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { dialog = Dlg.None }) { Text("Cancel") } },
            )
        }

        Dlg.Filter -> AlertDialog(
            onDismissRequest = { dialog = Dlg.None },
            title = { Text("Filter") },
            text = {
                Column {
                    TextButton(onClick = { dialog = Dlg.None; work { Repo.applyFilter(doc, PageFilter.ORIGINAL) } }) { Text("Original colours") }
                    TextButton(onClick = { dialog = Dlg.None; work { Repo.applyFilter(doc, PageFilter.GRAY) } }) { Text("Grayscale") }
                    TextButton(onClick = { dialog = Dlg.None; work { Repo.applyFilter(doc, PageFilter.BW) } }) { Text("Black & white (text)") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { dialog = Dlg.None }) { Text("Cancel") } },
        )

        Dlg.Sign -> SignDialog(
            pages = doc.pages.size,
            onDismiss = { dialog = Dlg.None },
            onApply = { page, sig ->
                dialog = Dlg.None
                work { Repo.sign(doc, page - 1, sig) }
            },
        )

        Dlg.Protect -> {
            var pw by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { dialog = Dlg.None },
                title = { Text("Password-protect PDF") },
                text = {
                    OutlinedTextField(
                        value = pw,
                        onValueChange = { pw = it },
                        singleLine = true,
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = pw.length >= 4,
                        onClick = {
                            dialog = Dlg.None
                            exportAndShare("application/pdf") { listOf(Repo.exportProtected(ctx, doc, pw)) }
                        },
                    ) { Text("Create & share") }
                },
                dismissButton = { TextButton(onClick = { dialog = Dlg.None }) { Text("Cancel") } },
            )
        }

        Dlg.Delete -> AlertDialog(
            onDismissRequest = { dialog = Dlg.None },
            title = { Text("Delete this document?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { dialog = Dlg.None; work { Repo.delete(doc) } }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { dialog = Dlg.None }) { Text("Cancel") } },
        )

        is Dlg.Ocr -> AlertDialog(
            onDismissRequest = { dialog = Dlg.None },
            title = { Text("Extracted text") },
            text = {
                if (d.text == null) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState()).height(320.dp)) { Text(d.text) }
                }
            },
            confirmButton = {
                if (d.text != null) {
                    Row {
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(d.text))
                            toast("Copied")
                        }) { Text("Copy") }
                        TextButton(onClick = { Share.text(ctx, d.text) }) { Text("Share") }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { dialog = Dlg.None }) { Text("Close") } },
        )
    }
}

@Composable
private fun SignDialog(
    pages: Int,
    onDismiss: () -> Unit,
    onApply: (page: Int, sig: Bitmap) -> Unit,
) {
    var page by remember { mutableIntStateOf(pages) }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign document") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Page: ")
                    TextButton(onClick = { if (page > 1) page-- }) { Text("-") }
                    Text("$page / $pages")
                    TextButton(onClick = { if (page < pages) page++ }) { Text("+") }
                }
                Text("Draw your signature below. It is placed at the bottom-right of the page.", style = MaterialTheme.typography.bodySmall)
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(Color.White)
                        .border(1.dp, Color.Gray)
                        .onSizeChanged { size = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { current = listOf(it) },
                                onDragEnd = { strokes.add(current); current = emptyList() },
                                onDragCancel = { current = emptyList() },
                                onDrag = { change, _ -> current = current + change.position },
                            )
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        (strokes + listOf(current)).forEach { s ->
                            val path = Path()
                            s.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                            drawPath(
                                path, Color.Black,
                                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (strokes.isNotEmpty() && size.width > 0) onApply(page, renderSignature(strokes, size))
            }) { Text("Apply") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { strokes.clear(); current = emptyList() }) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private fun renderSignature(strokes: List<List<Offset>>, size: IntSize): Bitmap {
    val w = 800
    val scale = w.toFloat() / size.width
    val h = (size.height * scale).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        isAntiAlias = true
    }
    strokes.forEach { s ->
        val path = android.graphics.Path()
        s.forEachIndexed { i, o ->
            if (i == 0) path.moveTo(o.x * scale, o.y * scale) else path.lineTo(o.x * scale, o.y * scale)
        }
        canvas.drawPath(path, paint)
    }
    return bmp
}
