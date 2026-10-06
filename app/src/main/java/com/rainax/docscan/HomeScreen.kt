package com.rainax.docscan

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Replace with the URL of your own privacy policy page (Google Play requires one). */
const val PRIVACY_URL = "https://github.com/raowaheedbhkpk-dev/RainaxDocScan"

class AppActions(
    val scan: () -> Unit,
    val scanId: () -> Unit,
    val importImages: () -> Unit,
    val importFiles: () -> Unit,
    val ocrImage: () -> Unit,
    val open: (ScanDoc) -> Unit,
    val share: (ScanDoc) -> Unit,
    val savePdf: (ScanDoc) -> Unit,
    val merge: (List<ScanDoc>) -> Unit,
    val delete: (List<ScanDoc>) -> Unit,
)

private class Tool(val icon: ImageVector, val label: String, val desc: String, val onClick: () -> Unit)

@Composable
fun MainShell(docs: List<ScanDoc>, a: AppActions) {
    val ctx = LocalContext.current
    var tabIdx by rememberSaveable { mutableIntStateOf(0) }

    fun needDoc(msg: String) {
        tabIdx = 1
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    }

    val tools = listOf(
        Tool(Icons.Default.DocumentScanner, "Smart Scan", "Camera scan with crop and filters", a.scan),
        Tool(Icons.Default.PictureAsPdf, "Merge PDF", "Combine several documents into one") {
            needDoc("Long-press documents to select them, then tap the merge icon")
        },
        Tool(Icons.Default.Image, "Import Images", "Turn photos into a PDF", a.importImages),
        Tool(Icons.Default.FolderOpen, "Import Files", "Open an existing PDF", a.importFiles),
        Tool(Icons.Default.Badge, "ID Cards", "Scan front and back of a card", a.scanId),
        Tool(Icons.Default.TextFields, "Extract Text", "Read text from an image (OCR)", a.ocrImage),
        Tool(Icons.Default.Draw, "Sign", "Add your signature to a document") {
            needDoc("Open a document, then tap the ⋮ menu > Sign")
        },
        Tool(Icons.Default.Lock, "Protect PDF", "Share a password-protected PDF") {
            needDoc("Open a document, then tap Share > password-protected PDF")
        },
    )

    val labels = listOf("Home", "All Documents", "Tools", "Me")
    val icons = listOf(Icons.Default.Home, Icons.Default.Description, Icons.Default.Apps, Icons.Default.Person)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                BannerAd()
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    labels.forEachIndexed { i, label ->
                        NavigationBarItem(
                            selected = tabIdx == i,
                            onClick = { tabIdx = i },
                            icon = { Icon(icons[i], label) },
                            label = { Text(label, fontSize = 12.sp, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Teal,
                                selectedTextColor = Teal,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (tabIdx <= 1) {
                FloatingActionButton(
                    onClick = a.scan,
                    containerColor = Teal,
                    contentColor = Color.White,
                    shape = CircleShape,
                ) { Icon(Icons.Default.CameraAlt, "Scan") }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tabIdx) {
                0 -> HomeTab(docs, tools, a, onViewAll = { tabIdx = 1 }, onAllTools = { tabIdx = 2 })
                1 -> DocsTab(docs, a)
                2 -> ToolsTab(tools)
                else -> MeTab()
            }
        }
    }
}

// ---------------------------------------------------------------- Home

@Composable
private fun HomeTab(
    docs: List<ScanDoc>,
    tools: List<Tool>,
    a: AppActions,
    onViewAll: () -> Unit,
    onAllTools: () -> Unit,
) {
    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val sheet = MaterialTheme.colorScheme.surfaceVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val gridTools = tools.take(7) + Tool(Icons.Default.GridView, "All", "", onAllTools)

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF18181A))
                    .clickable(onClick = onViewAll)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, null, tint = muted)
                Spacer(Modifier.width(12.dp))
                Text("Search", color = muted, fontSize = 18.sp)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 8.dp)) {
                gridTools.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        row.forEach { t -> ToolCell(t, Modifier.weight(1f)) }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(sheet)
                    .padding(start = 16.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recents", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Row(Modifier.clickable(onClick = onViewAll), verticalAlignment = Alignment.CenterVertically) {
                    Text("View All", color = muted)
                    Icon(Icons.Default.ChevronRight, null, tint = muted)
                }
            }
        }
        if (docs.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().background(sheet).padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("No scans yet.\nTap the camera button to start.", color = muted, textAlign = TextAlign.Center)
                }
            }
        }
        itemsIndexed(docs.take(6), key = { _, d -> d.dir.name }) { i, d ->
            Column(Modifier.fillMaxWidth().background(sheet)) {
                Row(
                    Modifier.fillMaxWidth().clickable { a.open(d) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Thumb(d)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text("${dateFmt.format(Date(d.modified))}  |  ${d.pages.size} page(s)", color = muted, fontSize = 15.sp)
                    }
                    Icon(Icons.Default.CheckBoxOutlineBlank, null, tint = muted)
                }
                if (i == 0) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ActionBtn("Share") { a.share(d) }
                        ActionBtn("Save as PDF") { a.savePdf(d) }
                        ActionBtn("View") { a.open(d) }
                    }
                }
            }
        }
        item { Box(Modifier.fillMaxWidth().height(90.dp).background(sheet)) }
    }
}

@Composable
private fun Thumb(d: ScanDoc) {
    AsyncImage(
        model = d.pages.firstOrNull(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(68.dp, 84.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF45474B), RoundedCornerShape(6.dp)),
    )
}

@Composable
private fun RowScope.ActionBtn(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF45474B), contentColor = Color.White),
    ) { Text(text, maxLines = 1) }
}

@Composable
private fun ToolCell(t: Tool, modifier: Modifier) {
    Column(
        modifier.clickable(onClick = t.onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF2B2C30)),
            contentAlignment = Alignment.Center,
        ) { Icon(t.icon, t.label, tint = Teal, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(t.label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------- All documents

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocsTab(docs: List<ScanDoc>, a: AppActions) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    val shown = docs.filter { it.name.contains(query, ignoreCase = true) }
    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    BackHandler(enabled = selecting) { selected = emptySet() }
    fun selectedDocs() = docs.filter { it.dir.name in selected }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (selecting) "${selected.size} selected" else "All Documents",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (selecting) {
                if (selected.size >= 2) {
                    IconButton(onClick = { a.merge(selectedDocs()); selected = emptySet() }) {
                        Icon(Icons.Default.MergeType, "Merge into one PDF")
                    }
                }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search documents") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (docs.isEmpty()) "No scans yet." else "No matches.", color = muted)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.dir.name }) { d ->
                    val isSel = d.dir.name in selected
                    fun toggle() { selected = if (isSel) selected - d.dir.name else selected + d.dir.name }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .combinedClickable(
                                onClick = { if (selecting) toggle() else a.open(d) },
                                onLongClick = { toggle() },
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Thumb(d)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.name, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${dateFmt.format(Date(d.modified))}  |  ${d.pages.size} page(s)", color = muted, fontSize = 14.sp)
                        }
                        if (isSel) Icon(Icons.Default.CheckCircle, null, tint = Teal)
                    }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${selected.size} document(s)?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    a.delete(selectedDocs())
                    selected = emptySet()
                    confirmDelete = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- Tools

@Composable
private fun ToolsTab(tools: List<Tool>) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Tools", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        tools.forEach { t ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = t.onClick)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF232427)),
                    contentAlignment = Alignment.Center,
                ) { Icon(t.icon, null, tint = Teal) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(t.label, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text(t.desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Me

@Composable
private fun MeTab() {
    val ctx = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
    }
    fun open(url: String) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) {
            Toast.makeText(ctx, "Cannot open link", Toast.LENGTH_SHORT).show()
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Rainax DocScan", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Version $version", color = muted)
        Spacer(Modifier.height(12.dp))
        MeRow("Rate this app") {
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ctx.packageName}")))
            } catch (e: Exception) {
                open("https://play.google.com/store/apps/details?id=${ctx.packageName}")
            }
        }
        MeRow("Share this app") {
            Share.text(ctx, "Rainax DocScan - free document scanner\nhttps://play.google.com/store/apps/details?id=${ctx.packageName}")
        }
        MeRow("Privacy policy") { open(PRIVACY_URL) }
        Spacer(Modifier.height(8.dp))
        Text("Scans are processed on your phone and stored only on your device.", color = muted, fontSize = 13.sp)
    }
}

@Composable
private fun MeRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- OCR result dialog

@Composable
fun OcrResultDialog(text: String?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Extracted text") },
        text = {
            if (text == null) {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()).height(320.dp)) { Text(text) }
            }
        },
        confirmButton = {
            if (text != null) {
                Row {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(text))
                        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy") }
                    TextButton(onClick = { Share.text(ctx, text) }) { Text("Share") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}
