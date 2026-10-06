package com.rainax.docscan

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

data class ScanDoc(
    val dir: File,
    val name: String,
    val pdf: File,
    val pages: List<File>,
    val modified: Long,
)

enum class PageFilter { ORIGINAL, GRAY, BW }

/** All documents live in filesDir/scans/<id>/ : scan.pdf, p000.jpg..., name.txt, orig/ (filter backups). */
object Repo {
    private fun root(c: Context) = File(c.filesDir, "scans").apply { mkdirs() }

    private fun pageFiles(dir: File): List<File> =
        dir.listFiles { f -> f.isFile && f.name.startsWith("p") && f.name.endsWith(".jpg") }
            .orEmpty().sortedBy { it.name }

    fun list(c: Context): List<ScanDoc> =
        root(c).listFiles { f -> f.isDirectory }.orEmpty()
            .mapNotNull { load(it) }
            .sortedByDescending { it.modified }

    fun load(dir: File): ScanDoc? {
        val pdf = File(dir, "scan.pdf")
        if (!dir.exists() || !pdf.exists()) return null
        val nameFile = File(dir, "name.txt")
        val name = if (nameFile.exists()) nameFile.readText().trim() else ""
        return ScanDoc(dir, name.ifEmpty { dir.name }, pdf, pageFiles(dir), pdf.lastModified())
    }

    private fun defaultName(): String =
        "Scan " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

    private fun newDir(c: Context, name: String): File {
        val dir = File(root(c), System.currentTimeMillis().toString()).apply { mkdirs() }
        File(dir, "name.txt").writeText(name)
        return dir
    }

    fun saveScan(c: Context, result: GmsDocumentScanningResult): ScanDoc? {
        val dir = newDir(c, defaultName())
        result.pages?.forEachIndexed { i, p ->
            c.contentResolver.openInputStream(p.imageUri)?.use { input ->
                File(dir, "p%03d.jpg".format(i)).outputStream().use { input.copyTo(it) }
            }
        }
        val pdfUri = result.pdf?.uri
        if (pdfUri != null) {
            c.contentResolver.openInputStream(pdfUri)?.use { input ->
                File(dir, "scan.pdf").outputStream().use { input.copyTo(it) }
            }
        }
        if (!File(dir, "scan.pdf").exists()) buildPdf(dir)
        return load(dir)
    }

    /** Rebuilds scan.pdf from the page images (used after sign / filter / merge). */
    fun buildPdf(dir: File) {
        val pdf = PdfDocument()
        pageFiles(dir).forEachIndexed { i, f ->
            val bmp = BitmapFactory.decodeFile(f.path) ?: return@forEachIndexed
            val info = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, i + 1).create()
            val page = pdf.startPage(info)
            page.canvas.drawBitmap(bmp, 0f, 0f, null)
            pdf.finishPage(page)
            bmp.recycle()
        }
        File(dir, "scan.pdf").outputStream().use { pdf.writeTo(it) }
        pdf.close()
    }

    fun merge(c: Context, docs: List<ScanDoc>): ScanDoc? {
        val dir = newDir(c, "Merged " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))
        var n = 0
        docs.forEach { d -> d.pages.forEach { p -> p.copyTo(File(dir, "p%03d.jpg".format(n++))) } }
        buildPdf(dir)
        return load(dir)
    }

    fun rename(doc: ScanDoc, name: String) {
        File(doc.dir, "name.txt").writeText(name.trim().ifEmpty { doc.name })
    }

    fun delete(doc: ScanDoc) {
        doc.dir.deleteRecursively()
    }

    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9 _.-]"), "_").trim().ifEmpty { "scan" }

    private fun exports(c: Context) = File(c.cacheDir, "exports").apply { mkdirs() }

    fun exportPdf(c: Context, doc: ScanDoc): File =
        File(exports(c), safe(doc.name) + ".pdf").also { doc.pdf.copyTo(it, overwrite = true) }

    fun exportImages(c: Context, doc: ScanDoc): List<File> =
        doc.pages.mapIndexed { i, f ->
            File(exports(c), "${safe(doc.name)}_${i + 1}.jpg").also { f.copyTo(it, overwrite = true) }
        }

    /** Creates a password-protected copy for sharing (the stored original is not changed). */
    fun exportProtected(c: Context, doc: ScanDoc, password: String): File {
        PDFBoxResourceLoader.init(c)
        val out = File(exports(c), safe(doc.name) + "_protected.pdf")
        PDDocument.load(doc.pdf).use { d ->
            val policy = StandardProtectionPolicy(password, password, AccessPermission())
            policy.encryptionKeyLength = 128
            d.protect(policy)
            d.save(out)
        }
        return out
    }

    /** Stamps [sig] (transparent bitmap) at the bottom-right of page [pageIdx]. */
    fun sign(doc: ScanDoc, pageIdx: Int, sig: Bitmap) {
        val file = doc.pages[pageIdx]
        val base = BitmapFactory.decodeFile(file.path).copy(Bitmap.Config.ARGB_8888, true)
        val w = (base.width * 0.35f).toInt()
        val h = (sig.height * (w.toFloat() / sig.width)).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(sig, w, h, true)
        Canvas(base).drawBitmap(
            scaled,
            base.width - w - base.width * 0.06f,
            base.height - h - base.height * 0.06f,
            null
        )
        file.outputStream().use { base.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        base.recycle()
        buildPdf(doc.dir)
    }

    /** Applies a colour filter to every page. Originals are backed up in orig/ so ORIGINAL restores them. */
    fun applyFilter(doc: ScanDoc, mode: PageFilter) {
        val backup = File(doc.dir, "orig").apply { mkdirs() }
        doc.pages.forEach { p ->
            val o = File(backup, p.name)
            if (!o.exists()) p.copyTo(o)
        }
        doc.pages.forEach { p ->
            val src = BitmapFactory.decodeFile(File(backup, p.name).path) ?: return@forEach
            val out = if (mode == PageFilter.ORIGINAL) src else {
                val bmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
                val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrixFor(mode)) }
                Canvas(bmp).drawBitmap(src, 0f, 0f, paint)
                bmp
            }
            p.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        }
        buildPdf(doc.dir)
    }

    /** Photos picked from the gallery/files -> one new document. */
    fun fromImages(c: Context, uris: List<Uri>): ScanDoc? {
        val dir = newDir(c, defaultName())
        var n = 0
        uris.forEach { u ->
            val bmp = decodeScaled(c, u, 2200) ?: return@forEach
            File(dir, "p%03d.jpg".format(n++)).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bmp.recycle()
        }
        if (n == 0) { dir.deleteRecursively(); return null }
        buildPdf(dir)
        return load(dir)
    }

    /** Imports an existing PDF: keeps the original file and renders page images for viewing / OCR. */
    fun importPdf(c: Context, uri: Uri, name: String): ScanDoc? {
        val dir = newDir(c, name.ifBlank { defaultName() })
        c.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val renderer = PdfRenderer(pfd)
            try {
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    try {
                        val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        File(dir, "p%03d.jpg".format(i)).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                        bmp.recycle()
                    } finally {
                        page.close()
                    }
                }
            } finally {
                renderer.close()
            }
        }
        if (pageFiles(dir).isEmpty()) { dir.deleteRecursively(); return null }
        c.contentResolver.openInputStream(uri)?.use { input ->
            File(dir, "scan.pdf").outputStream().use { input.copyTo(it) }
        }
        if (!File(dir, "scan.pdf").exists()) buildPdf(dir)
        return load(dir)
    }

    /** Copies a PDF into Downloads/Rainax DocScan (Android 10+). Returns false when not possible. */
    fun saveToDownloads(c: Context, src: File, displayName: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Rainax DocScan")
        }
        val uri = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        c.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } } ?: return false
        return true
    }

    fun pdfFileName(doc: ScanDoc) = safe(doc.name) + ".pdf"

    fun decodeScaled(c: Context, uri: Uri, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val orientation = try {
            c.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        } catch (e: Exception) { null } ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bmp
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
        if (rotated !== bmp) bmp.recycle()
        return rotated
    }

    private fun matrixFor(mode: PageFilter): ColorMatrix {
        val gray = ColorMatrix().apply { setSaturation(0f) }
        if (mode == PageFilter.GRAY) return gray
        val c = 2.6f
        val t = (-0.5f * c + 0.5f) * 255f
        val contrast = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
        gray.postConcat(contrast)
        return gray
    }
}

object Ocr {
    /** On-device text recognition (Latin scripts). Call from a background thread. */
    fun read(files: List<File>): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            return files.mapIndexed { i, f ->
                val bmp = BitmapFactory.decodeFile(f.path) ?: return@mapIndexed ""
                try {
                    val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0))).text
                    if (files.size > 1) "--- Page ${i + 1} ---\n$text" else text
                } finally {
                    bmp.recycle()
                }
            }.joinToString("\n\n").trim()
        } finally {
            recognizer.close()
        }
    }
    /** OCR for a single picked image. Call from a background thread. */
    fun readUri(c: Context, uri: Uri): String {
        val bmp = Repo.decodeScaled(c, uri, 2400) ?: return ""
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            return Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0))).text.trim()
        } finally {
            recognizer.close()
            bmp.recycle()
        }
    }
}

object Share {
    fun files(c: Context, mime: String, files: List<File>) {
        val uris = ArrayList(files.map { FileProvider.getUriForFile(c, "${c.packageName}.provider", it) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        intent.type = mime
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        c.startActivity(Intent.createChooser(intent, "Share"))
    }

    fun text(c: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        c.startActivity(Intent.createChooser(intent, "Share text"))
    }
}
