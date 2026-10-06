package com.rainax.docscan

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var docs by mutableStateOf(emptyList<ScanDoc>())
    private var openDoc by mutableStateOf<ScanDoc?>(null)
    private var ocrOpen by mutableStateOf(false)
    private var ocrText by mutableStateOf<String?>(null)

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun refresh() { docs = Repo.list(this) }

    /** Runs [block] in the background, then opens the resulting document. */
    private fun importFrom(block: () -> ScanDoc?) {
        lifecycleScope.launch {
            try {
                val doc = withContext(Dispatchers.IO) { block() }
                refresh()
                if (doc != null) {
                    openDoc = doc
                    Ads.showInterstitial(this@MainActivity)
                } else {
                    toast("Nothing could be imported")
                }
            } catch (e: Exception) {
                toast("Import failed: ${e.message}")
            }
        }
    }

    private fun displayName(uri: Uri): String {
        var name = ""
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) name = it.getString(0) ?: ""
            }
        } catch (_: Exception) {
        }
        return name.removeSuffix(".pdf").removeSuffix(".PDF")
    }

    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
            if (r.resultCode == RESULT_OK) {
                val result = GmsDocumentScanningResult.fromActivityResultIntent(r.data)
                if (result != null) importFrom { Repo.saveScan(this@MainActivity, result) }
            }
        }

    private val pickImages =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) importFrom { Repo.fromImages(this@MainActivity, uris) }
        }

    private val pickPdf =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importFrom { Repo.importPdf(this@MainActivity, uri, displayName(uri)) }
        }

    private val pickOcrImage =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) runOcrOnImage(uri)
        }

    private fun runOcrOnImage(uri: Uri) {
        Ads.showRewarded(this) {
            ocrOpen = true
            ocrText = null
            lifecycleScope.launch {
                ocrText = try {
                    withContext(Dispatchers.IO) { Ocr.readUri(this@MainActivity, uri) }.ifBlank { "No text found." }
                } catch (e: Exception) {
                    "OCR failed: ${e.message}"
                }
            }
        }
    }

    private fun startScan(pageLimit: Int) {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(pageLimit)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { toast("Scanner unavailable: ${it.message}") }
    }

    private fun shareDoc(d: ScanDoc) {
        lifecycleScope.launch {
            try {
                val f = withContext(Dispatchers.IO) { Repo.exportPdf(this@MainActivity, d) }
                Share.files(this@MainActivity, "application/pdf", listOf(f))
            } catch (e: Exception) {
                toast("Share failed: ${e.message}")
            }
        }
    }

    private fun savePdf(d: ScanDoc) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try { Repo.saveToDownloads(this@MainActivity, d.pdf, Repo.pdfFileName(d)) } catch (e: Exception) { false }
            }
            if (ok) toast("Saved to Downloads/Rainax DocScan") else shareDoc(d)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ads.init(this)
        refresh()

        val actions = AppActions(
            scan = { startScan(50) },
            scanId = { startScan(2) },
            importImages = { pickImages.launch(arrayOf("image/*")) },
            importFiles = { pickPdf.launch(arrayOf("application/pdf")) },
            ocrImage = { pickOcrImage.launch(arrayOf("image/*")) },
            open = { openDoc = it },
            share = { shareDoc(it) },
            savePdf = { savePdf(it) },
            merge = { selected ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { Repo.merge(this@MainActivity, selected) }
                    refresh()
                }
            },
            delete = { selected ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { selected.forEach { Repo.delete(it) } }
                    refresh()
                }
            },
        )

        setContent {
            MaterialTheme(colorScheme = AppDark) {
                BackHandler(enabled = openDoc != null) {
                    openDoc = null
                    refresh()
                }
                val current = openDoc
                if (current == null) {
                    MainShell(docs, actions)
                } else {
                    ViewerScreen(
                        doc = current,
                        onBack = {
                            openDoc = null
                            refresh()
                        },
                        onChanged = { updated ->
                            refresh()
                            openDoc = updated
                        },
                    )
                }
                if (ocrOpen) OcrResultDialog(ocrText) { ocrOpen = false }
            }
        }
    }
}
