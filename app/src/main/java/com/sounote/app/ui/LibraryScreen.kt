@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sounote.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sounote.app.LibraryViewModel
import com.sounote.app.R
import com.sounote.app.data.DocRef
import com.sounote.app.data.FolderRef
import com.sounote.app.pdf.PageTemplate
import com.sounote.app.pdf.Thumbnails

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: LibraryViewModel) {
    val snackbar = remember { SnackbarHostState() }
    val pickPdfs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.importPdfs(uris)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(vm::linkFolder)
    }
    val importPdf = { pickPdfs.launch(arrayOf("application/pdf")) }
    val linkFolder = { pickFolder.launch(null) }

    var renaming by remember { mutableStateOf<DocRef?>(null) }
    var deleting by remember { mutableStateOf<DocRef?>(null) }
    var unlinking by remember { mutableStateOf<FolderRef?>(null) }
    var newNotebook by remember { mutableStateOf(false) }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val inFolder = vm.path.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (inFolder) vm.path.joinToString(" › ") { it.name } else "SouNote",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (inFolder) {
                        IconButton(onClick = vm::navigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Yenile")
                    }
                    if (!inFolder) {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Ekle")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Yeni defter") }, onClick = { menu = false; newNotebook = true })
                                DropdownMenuItem(text = { Text("PDF içe aktar") }, onClick = { menu = false; importPdf() })
                                DropdownMenuItem(text = { Text("Klasör bağla") }, onClick = { menu = false; linkFolder() })
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyVerticalGrid(
                columns = GridCells.Adaptive(170.dp),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (inFolder) {
                    val listing = vm.listing
                    items(listing.folders, key = { "f" + it.documentId }) { folder ->
                        FolderCard(folder, onClick = { vm.openFolder(folder) })
                    }
                    items(listing.docs, key = { it.id }) { doc ->
                        DocCard(doc, onClick = { vm.open(doc) })
                    }
                    if (listing.folders.isEmpty() && listing.docs.isEmpty()) {
                        fullWidth { Hint("Bu klasörde PDF yok. Dosya yöneticisinden buraya PDF atınca görünür.") }
                    }
                } else {
                    if (vm.rootFolders.isNotEmpty()) {
                        fullWidth { SectionTitle("Bağlı klasörler") }
                        items(vm.rootFolders, key = { "r" + it.treeUri }) { folder ->
                            FolderCard(folder, onClick = { vm.openFolder(folder) }, onLongClick = { unlinking = folder })
                        }
                    }
                    if (vm.imported.isNotEmpty()) {
                        fullWidth { SectionTitle("İçe aktarılanlar") }
                        items(vm.imported, key = { it.id }) { doc ->
                            DocCard(
                                doc,
                                onClick = { vm.open(doc) },
                                menu = { dismiss ->
                                    DropdownMenuItem(text = { Text("Yeniden adlandır") }, onClick = { dismiss(); renaming = doc })
                                    DropdownMenuItem(text = { Text("Sil") }, onClick = { dismiss(); deleting = doc })
                                },
                            )
                        }
                    }
                    if (vm.loaded && vm.rootFolders.isEmpty() && vm.imported.isEmpty()) {
                        fullWidth { EmptyLibrary(onImport = importPdf, onLinkFolder = linkFolder, onNewNotebook = { newNotebook = true }) }
                    }
                }
            }
        }
    }

    renaming?.let { doc ->
        var text by remember(doc.id) { mutableStateOf(doc.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Yeniden adlandır") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(doc, text); renaming = null }) { Text("Kaydet") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Vazgeç") } },
        )
    }
    deleting?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Silinsin mi?") },
            text = { Text("\"${doc.title}\" ve üzerindeki notlar kalıcı olarak silinecek.") },
            confirmButton = { TextButton(onClick = { vm.delete(doc); deleting = null }) { Text("Sil") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } },
        )
    }
    if (newNotebook) {
        NotebookDialog(
            onCreate = { name, template ->
                newNotebook = false
                vm.createNotebook(name, template)
            },
            onDismiss = { newNotebook = false },
        )
    }
    unlinking?.let { folder ->
        AlertDialog(
            onDismissRequest = { unlinking = null },
            title = { Text("Bağlantı kaldırılsın mı?") },
            text = { Text("\"${folder.name}\" artık kütüphanede görünmeyecek. Klasördeki dosyalar silinmez.") },
            confirmButton = { TextButton(onClick = { vm.unlinkFolder(folder); unlinking = null }) { Text("Kaldır") } },
            dismissButton = { TextButton(onClick = { unlinking = null }) { Text("Vazgeç") } },
        )
    }
}

/** Yeni boş defter: ad + sayfa şablonu. */
@Composable
private fun NotebookDialog(onCreate: (String, PageTemplate) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var template by remember { mutableStateOf(PageTemplate.LINED) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni defter") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Ad") },
                    placeholder = { Text("Defter") },
                )
                Text("Sayfa", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageTemplate.entries.forEach { t ->
                        FilterChip(selected = template == t, onClick = { template = t }, label = { Text(t.label) })
                    }
                }
                Text(
                    "Defterde sayfa paneli ya da ⋮ menüsünden yeni sayfa eklersin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, template) }) { Text("Oluştur") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    )
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit, onLinkFolder: () -> Unit, onNewNotebook: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
    ) {
        Text("Kütüphanen boş", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Boş bir defter aç, bir PDF içe aktar ya da ders notlarının olduğu klasörü bağla.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onNewNotebook) { Text("Yeni defter") }
            OutlinedButton(onClick = onImport) { Text("PDF içe aktar") }
            OutlinedButton(onClick = onLinkFolder) { Text("Klasör bağla") }
        }
    }
}

private val CardShape = RoundedCornerShape(10.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCard(folder: FolderRef, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Icon(
                painterResource(R.drawable.ic_folder),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(72.dp),
            )
        }
        CardTitle(folder.name)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocCard(
    doc: DocRef,
    onClick: () -> Unit,
    menu: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = if (menu != null) ({ menuOpen = true }) else null,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(CardShape)
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape),
        ) {
            Thumbnail(doc)
            if (menu != null) {
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    menu { menuOpen = false }
                }
            }
        }
        CardTitle(doc.title)
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
    )
}

@Composable
private fun Thumbnail(doc: DocRef) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(Thumbnails.cached(doc), doc.id, doc.lastModified) {
        if (value == null) value = Thumbnails.load(context, doc)
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_pdf),
                contentDescription = null,
                tint = Color(0xFFB0B4BC),
                modifier = Modifier.size(48.dp),
            )
        }
    }
}
