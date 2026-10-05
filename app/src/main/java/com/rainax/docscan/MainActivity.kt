package com.rainax.docscan

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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

    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
            if (r.resultCode == RESULT_OK) {
                val result = GmsDocumentScanningResult.fromActivityResultIntent(r.data)
                if (result != null) {
                    lifecycleScope.launch {
                        val doc = withContext(Dispatchers.IO) { Repo.saveScan(this@MainActivity, result) }
                        docs = Repo.list(this@MainActivity)
                        openDoc = doc
                        Ads.showInterstitial(this@MainActivity)
                    }
                }
            }
        }

    private fun startScan(pageLimit: Int) {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true) // import photos from the gallery
            .setPageLimit(pageLimit)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL) // crop, rotate, filters, clean
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener {
                Toast.makeText(this, "Scanner unavailable: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ads.init(this)
        docs = Repo.list(this)

        setContent {
            val scheme = if (isSystemInDarkTheme()) darkColorScheme()
            else lightColorScheme(primary = Color(0xFF1565C0))
            MaterialTheme(colorScheme = scheme) {
                BackHandler(enabled = openDoc != null) {
                    openDoc = null
                    docs = Repo.list(this@MainActivity)
                }
                val current = openDoc
                if (current == null) {
                    HomeScreen(
                        docs = docs,
                        onScan = { startScan(pageLimit = 50) },
                        onScanId = { startScan(pageLimit = 2) },
                        onOpen = { openDoc = it },
                        onMerge = { selected ->
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO) { Repo.merge(this@MainActivity, selected) }
                                docs = Repo.list(this@MainActivity)
                            }
                        },
                        onDelete = { selected ->
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO) { selected.forEach { Repo.delete(it) } }
                                docs = Repo.list(this@MainActivity)
                            }
                        },
                    )
                } else {
                    ViewerScreen(
                        doc = current,
                        onBack = {
                            openDoc = null
                            docs = Repo.list(this@MainActivity)
                        },
                        onChanged = { updated ->
                            docs = Repo.list(this@MainActivity)
                            openDoc = updated
                        },
                    )
                }
            }
        }
    }
}
