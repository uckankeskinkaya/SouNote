@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sounote.app.ui

import android.graphics.Bitmap
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import com.sounote.app.ErrorLog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import com.sounote.app.data.NaturalOrder
import com.sounote.app.data.UserFolder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
import com.sounote.app.pdf.PaperSize
import com.sounote.app.pdf.Thumbnails
import com.sounote.app.ink.InkStore

@Composable
fun LibraryScreen(vm: LibraryViewModel) {
    val snackbar = remember { SnackbarHostState() }
    val pickPdfs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.importPdfs(uris)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(vm::linkFolder)
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(vm::backup)
    }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var autoBackupDialog by remember { mutableStateOf(false) }
    val autoBackupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(vm::enableAutoBackup)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreUri = uri
    }
    val importPdf = { pickPdfs.launch(arrayOf("application/pdf")) }
    val linkFolder = { pickFolder.launch(null) }

    var renaming by remember { mutableStateOf<DocRef?>(null) }
    var deleting by remember { mutableStateOf<DocRef?>(null) }
    var unlinking by remember { mutableStateOf<FolderRef?>(null) }
    var newNotebook by remember { mutableStateOf(false) }
    var folderDialog by remember { mutableStateOf<UserFolder?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var deletingFolder by remember { mutableStateOf<UserFolder?>(null) }
    var movingDoc by remember { mutableStateOf<DocRef?>(null) }
    var movingFolder by remember { mutableStateOf<UserFolder?>(null) }
    var searching by remember { mutableStateOf(false) }
    var deletingOrphan by remember { mutableStateOf<DocRef?>(null) }
    val context = LocalContext.current
    var crash by remember { mutableStateOf(ErrorLog.pendingCrash()) }

    fun shareReport(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "SouNote hata raporu")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Hata raporunu paylaş"))
    }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val inLinked = vm.path.isNotEmpty()
    val folders = vm.userFolders
    val folder = folders.find { it.id == vm.currentFolder }
    val title = when {
        inLinked -> vm.path.joinToString(" › ") { it.name }
        folder != null -> generateSequence(folder) { f -> folders.find { it.id == f.parentId } }
            .toList().asReversed().joinToString(" › ") { it.name }
        else -> "SouNote"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (vm.canNavigateUp) {
                        IconButton(onClick = vm::navigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) vm.searchQuery = ""
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Ara")
                    }
                    var sortMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Sırala")
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            LibraryViewModel.SortMode.entries.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m.label) },
                                    trailingIcon = { RadioButton(selected = vm.sortMode == m, onClick = null) },
                                    onClick = { sortMenu = false; vm.updateSort(m) },
                                )
                            }
                        }
                    }
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Yenile")
                    }
                    if (!inLinked) {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Ekle")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Yeni defter") }, onClick = { menu = false; newNotebook = true })
                                DropdownMenuItem(text = { Text("Yeni klasör") }, onClick = { menu = false; newFolder = true })
                                DropdownMenuItem(text = { Text("PDF içe aktar") }, onClick = { menu = false; importPdf() })
                                if (folder == null) {
                                    DropdownMenuItem(text = { Text("Klasör bağla") }, onClick = { menu = false; linkFolder() })
                                }
                            }
                        }
                    }
                    var more by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { more = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Diğer")
                        }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            DropdownMenuItem(
                                text = { Text("Yedekle") },
                                onClick = {
                                    more = false
                                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                                    backupLauncher.launch("SouNote-yedek-$date.zip")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (vm.autoBackupFolder != null) "Otomatik yedek: açık" else "Otomatik yedek…") },
                                onClick = {
                                    more = false
                                    autoBackupDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Yedekten geri yükle") },
                                onClick = {
                                    more = false
                                    restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Hata raporu gönder") },
                                onClick = {
                                    more = false
                                    shareReport(ErrorLog.fullReport(context))
                                },
                            )
                            HorizontalDivider()
                            Text(
                                "Tema",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                            LibraryViewModel.ThemeMode.entries.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m.label) },
                                    trailingIcon = { RadioButton(selected = vm.themeMode == m, onClick = null) },
                                    onClick = { more = false; vm.updateTheme(m) },
                                )
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
            if (searching) {
                OutlinedTextField(
                    value = vm.searchQuery,
                    onValueChange = { vm.searchQuery = it },
                    singleLine = true,
                    placeholder = { Text("Belge ya da klasör ara") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            val docMenu: @Composable (DocRef, () -> Unit) -> Unit = { doc, dismiss ->
                DropdownMenuItem(text = { Text("Yeniden adlandır") }, onClick = { dismiss(); renaming = doc })
                DropdownMenuItem(text = { Text("Klasöre taşı") }, onClick = { dismiss(); movingDoc = doc })
                DropdownMenuItem(text = { Text("Sil") }, onClick = { dismiss(); deleting = doc })
            }
            val folderMenu: @Composable (UserFolder, () -> Unit) -> Unit = { f, dismiss ->
                DropdownMenuItem(text = { Text("Adı / rengi değiştir") }, onClick = { dismiss(); folderDialog = f })
                DropdownMenuItem(text = { Text("Başka klasöre taşı") }, onClick = { dismiss(); movingFolder = f })
                DropdownMenuItem(text = { Text("Sil") }, onClick = { dismiss(); deletingFolder = f })
            }
            fun countIn(f: UserFolder) = vm.imported.count { it.folderId == f.id } + folders.count { it.parentId == f.id }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(170.dp),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                val query = vm.searchQuery.trim().lowercase(TR)
                if (query.isNotEmpty()) {
                    // Arama: bütün kendi klasörlerim ve belgeler (bağlı klasördeysek onun belgeleri de).
                    val fs = folders.filter { it.name.lowercase(TR).contains(query) }
                    val linkedDocs = if (inLinked) vm.listing.docs else emptyList()
                    val ds = vm.sorted((vm.imported + linkedDocs).filter { it.title.lowercase(TR).contains(query) })
                    if (fs.isNotEmpty()) {
                        fullWidth { SectionTitle("Klasörler") }
                        items(fs, key = { "u" + it.id }) { f ->
                            UserFolderCard(f, countIn(f), onClick = { vm.searchQuery = ""; searching = false; vm.openUserFolder(f.id) }, menu = { folderMenu(f, it) })
                        }
                    }
                    if (ds.isNotEmpty()) {
                        fullWidth { SectionTitle("Belgeler") }
                        items(ds, key = { "s" + it.id }) { doc ->
                            if (doc.imported) {
                                DocCard(doc, onClick = { vm.open(doc) }, menu = { d -> docMenu(doc, d) })
                            } else {
                                DocCard(doc, onClick = { vm.open(doc) })
                            }
                        }
                    }
                    if (fs.isEmpty() && ds.isEmpty()) fullWidth { Hint("Sonuç yok") }
                } else if (inLinked) {
                    val listing = vm.listing
                    items(listing.folders, key = { "f" + it.documentId }) { f ->
                        FolderCard(f, onClick = { vm.openFolder(f) })
                    }
                    items(vm.sorted(listing.docs), key = { it.id }) { doc ->
                        DocCard(doc, onClick = { vm.open(doc) })
                    }
                    if (listing.folders.isEmpty() && listing.docs.isEmpty()) {
                        fullWidth { Hint("Bu klasörde PDF yok. Dosya yöneticisinden buraya PDF atınca görünür.") }
                    }
                } else {
                    // Son açılanlar: kaldığın belgeye hızlı dönüş
                    val recent = if (folder == null) {
                        vm.imported.filter { vm.lastOpened(it) > 0 }.sortedByDescending { vm.lastOpened(it) }.take(10)
                    } else {
                        emptyList()
                    }
                    if (recent.size >= 2) {
                        fullWidth {
                            Column {
                                SectionTitle("Son açılanlar")
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(top = 12.dp),
                                ) {
                                    items(recent, key = { "recent" + it.id }) { doc ->
                                        Box(Modifier.width(130.dp)) {
                                            DocCard(doc, onClick = { vm.open(doc) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (folder == null && vm.rootFolders.isNotEmpty()) {
                        fullWidth { SectionTitle("Bağlı klasörler") }
                        items(vm.rootFolders, key = { "r" + it.treeUri }) { f ->
                            FolderCard(f, onClick = { vm.openFolder(f) }, onLongClick = { unlinking = f })
                        }
                    }
                    val subfolders = folders.filter { it.parentId == vm.currentFolder }.sortedWith(compareBy(NaturalOrder) { it.name })
                    if (subfolders.isNotEmpty()) {
                        fullWidth { SectionTitle("Klasörler") }
                        items(subfolders, key = { "u" + it.id }) { f ->
                            UserFolderCard(f, countIn(f), onClick = { vm.openUserFolder(f.id) }, menu = { folderMenu(f, it) })
                        }
                    }
                    val docs = vm.sorted(vm.imported.filter { it.folderId == vm.currentFolder })
                    if (docs.isNotEmpty()) {
                        fullWidth { SectionTitle("Belgeler") }
                        items(docs, key = { it.id }) { doc ->
                            DocCard(doc, onClick = { vm.open(doc) }, menu = { d -> docMenu(doc, d) })
                        }
                    }
                    if (folder == null && vm.orphans.isNotEmpty()) {
                        fullWidth {
                            Column {
                                SectionTitle("Kaynağı bulunamayan notlu belgeler")
                                Text(
                                    "Bağlı klasördeki dosyası silinmiş ya da klasör bağlantısı kaldırılmış; notların burada duruyor. " +
                                        "Basılı tutup \"Kütüphaneye al\" diyebilirsin.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(vm.orphans, key = { "o" + it.id }) { doc ->
                            DocCard(
                                doc,
                                onClick = { vm.open(doc) },
                                menu = { d ->
                                    DropdownMenuItem(text = { Text("Kütüphaneye al") }, onClick = { d(); vm.adoptOrphan(doc) })
                                    DropdownMenuItem(text = { Text("Sil") }, onClick = { d(); deletingOrphan = doc })
                                },
                            )
                        }
                    }
                    if (folder == null && vm.loaded && vm.rootFolders.isEmpty() && vm.imported.isEmpty() && folders.isEmpty() && vm.orphans.isEmpty()) {
                        fullWidth { EmptyLibrary(onImport = importPdf, onLinkFolder = linkFolder, onNewNotebook = { newNotebook = true }) }
                    } else if (folder != null && subfolders.isEmpty() && docs.isEmpty()) {
                        fullWidth {
                            Hint("Bu klasör boş. + ile defter ya da PDF ekle; başka yerdeki belgelere basılı tutup \"Klasöre taşı\" de.")
                        }
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
            onCreate = { name, template, size, landscape ->
                newNotebook = false
                vm.createNotebook(name, template, size, landscape)
            },
            onDismiss = { newNotebook = false },
        )
    }
    if (newFolder) {
        FolderDialog(
            initial = null,
            onSave = { name, color ->
                newFolder = false
                vm.createFolder(name, color)
            },
            onDismiss = { newFolder = false },
        )
    }
    folderDialog?.let { f ->
        FolderDialog(
            initial = f,
            onSave = { name, color ->
                folderDialog = null
                vm.updateFolder(f, name, color)
            },
            onDismiss = { folderDialog = null },
        )
    }
    deletingFolder?.let { f ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text("\"${f.name}\" silinsin mi?") },
            text = { Text("İçindeki belgeler ve klasörler silinmez, bir üst klasöre taşınır.") },
            confirmButton = { TextButton(onClick = { vm.deleteFolder(f); deletingFolder = null }) { Text("Sil") } },
            dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text("Vazgeç") } },
        )
    }
    movingDoc?.let { doc ->
        MoveDialog(
            title = "\"${doc.title}\" nereye taşınsın?",
            folders = folders,
            exclude = null,
            onPick = { target ->
                movingDoc = null
                vm.moveDoc(doc, target)
            },
            onDismiss = { movingDoc = null },
        )
    }
    movingFolder?.let { f ->
        MoveDialog(
            title = "\"${f.name}\" nereye taşınsın?",
            folders = folders,
            exclude = f.id,
            onPick = { target ->
                movingFolder = null
                vm.moveFolder(f, target)
            },
            onDismiss = { movingFolder = null },
        )
    }
    deletingOrphan?.let { doc ->
        AlertDialog(
            onDismissRequest = { deletingOrphan = null },
            title = { Text("Silinsin mi?") },
            text = { Text("\"${doc.title}\" ve üzerindeki notlar kalıcı olarak silinecek.") },
            confirmButton = { TextButton(onClick = { vm.deleteOrphan(doc); deletingOrphan = null }) { Text("Sil") } },
            dismissButton = { TextButton(onClick = { deletingOrphan = null }) { Text("Vazgeç") } },
        )
    }
    if (autoBackupDialog) {
        val enabled = vm.autoBackupFolder != null
        AlertDialog(
            onDismissRequest = { autoBackupDialog = false },
            title = { Text("Otomatik yedek") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Açıkken bütün kütüphane günde bir (değişiklik olduysa) seçtiğin klasöre yedeklenir. " +
                            "İki dosya dönüşümlü yazılır, biri her zaman sağlam kalır. Drive gibi bir bulut klasörü seçersen tablet bozulsa da notların kurtulur.",
                    )
                    if (enabled) {
                        val last = vm.lastAutoBackup()
                        Text(
                            if (last > 0) "Son yedek: " + SimpleDateFormat("d MMMM yyyy HH:mm", TR).format(Date(last)) else "Henüz yedek alınmadı",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                if (enabled) {
                    TextButton(onClick = { vm.runAutoBackup(force = true); autoBackupDialog = false }) { Text("Şimdi yedekle") }
                } else {
                    TextButton(onClick = { autoBackupPicker.launch(null); autoBackupDialog = false }) { Text("Klasör seç") }
                }
            },
            dismissButton = {
                Row {
                    if (enabled) {
                        TextButton(onClick = { vm.disableAutoBackup(); autoBackupDialog = false }) { Text("Kapat") }
                        TextButton(onClick = { autoBackupPicker.launch(null); autoBackupDialog = false }) { Text("Klasörü değiştir") }
                    } else {
                        TextButton(onClick = { autoBackupDialog = false }) { Text("Vazgeç") }
                    }
                }
            },
        )
    }
    crash?.let { report ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Uygulama beklenmedik şekilde kapandı") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Raporu paylaşırsan (ya da kopyalayıp yapıştırırsan) sorunu bulup düzeltebilirim. Notların bundan etkilenmez.")
                    Text(
                        report.lineSequence().take(6).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    shareReport(ErrorLog.fullReport(context))
                    ErrorLog.clearCrash()
                    crash = null
                }) { Text("Paylaş") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        cm?.setPrimaryClip(ClipData.newPlainText("SouNote hata raporu", ErrorLog.fullReport(context)))
                        ErrorLog.clearCrash()
                        crash = null
                    }) { Text("Kopyala") }
                    TextButton(onClick = {
                        ErrorLog.clearCrash()
                        crash = null
                    }) { Text("Kapat") }
                }
            },
        )
    }
    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Yedek geri yüklensin mi?") },
            text = { Text("Şu anki kütüphane (belgeler, notlar, klasörler) yedektekiyle değiştirilecek. Emin değilsen önce şimdikini yedekle.") },
            confirmButton = { TextButton(onClick = { vm.restore(uri); restoreUri = null }) { Text("Geri yükle") } },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Vazgeç") } },
        )
    }
    unlinking?.let { f ->
        AlertDialog(
            onDismissRequest = { unlinking = null },
            title = { Text("Bağlantı kaldırılsın mı?") },
            text = { Text("\"${f.name}\" artık kütüphanede görünmeyecek. Klasördeki dosyalar silinmez.") },
            confirmButton = { TextButton(onClick = { vm.unlinkFolder(f); unlinking = null }) { Text("Kaldır") } },
            dismissButton = { TextButton(onClick = { unlinking = null }) { Text("Vazgeç") } },
        )
    }
}

private val TR = Locale.forLanguageTag("tr")

private val FOLDER_COLORS = listOf(
    0xFF5B7FD6.toInt(), 0xFF3FA36B.toInt(), 0xFFE0893A.toInt(), 0xFFD65B7F.toInt(),
    0xFF8E63CE.toInt(), 0xFF3AA6B9.toInt(), 0xFF8A8F99.toInt(), 0xFFC9A227.toInt(),
)

/** Kendi klasörüm: renkli kapak + ad + içindeki öğe sayısı. Basılı tutunca menü. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserFolderCard(
    folder: UserFolder,
    count: Int,
    onClick: () -> Unit,
    menu: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = { open = true })) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(CardShape)
                .background(Color(folder.color)),
        ) {
            Icon(
                painterResource(R.drawable.ic_folder),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(72.dp),
            )
            Text(
                "$count",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
        }
        CardTitle(folder.name)
    }
}

/** Yeni klasör ya da klasörü düzenle: ad + renk. */
@Composable
private fun FolderDialog(initial: UserFolder?, onSave: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableStateOf(initial?.color ?: FOLDER_COLORS[0]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Yeni klasör" else "Klasörü düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Ad") },
                    placeholder = { Text("ör. Matematik") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FOLDER_COLORS.forEach { c ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    if (c == color) 3.dp else 0.dp,
                                    MaterialTheme.colorScheme.onSurface,
                                    CircleShape,
                                )
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, color) }) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

/** Hedef klasör seçimi (ağaç görünümü). [exclude]: kendisi ve altı seçilemez (klasör taşırken). */
@Composable
private fun MoveDialog(
    title: String,
    folders: List<UserFolder>,
    exclude: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    // Ağacı düz listeye çevir: (klasör, derinlik)
    val rows = remember(folders, exclude) {
        val out = ArrayList<Pair<UserFolder, Int>>()
        fun walk(parent: String?, depth: Int) {
            folders.filter { it.parentId == parent && it.id != exclude }
                .sortedWith(compareBy(NaturalOrder) { it.name })
                .forEach {
                    out += it to depth
                    walk(it.id, depth + 1)
                }
        }
        walk(null, 0)
        out
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                item {
                    TextButton(onClick = { onPick(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Ana sayfa", modifier = Modifier.fillMaxWidth())
                    }
                }
                items(rows, key = { it.first.id }) { (f, depth) ->
                    TextButton(onClick = { onPick(f.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(start = (depth * 20).dp),
                        ) {
                            Box(Modifier.size(14.dp).clip(CircleShape).background(Color(f.color)))
                            Text(f.name, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
                if (rows.isEmpty()) {
                    item {
                        Text(
                            "Henüz klasör yok. Kütüphanede + → Yeni klasör.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

/** Yeni boş defter: ad + sayfa şablonu. */
@Composable
private fun NotebookDialog(onCreate: (String, PageTemplate, PaperSize, Boolean) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var template by remember { mutableStateOf(PageTemplate.LINED) }
    var size by remember { mutableStateOf(PaperSize.A4) }
    var landscape by remember { mutableStateOf(false) }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    PageTemplate.entries.forEach { t ->
                        FilterChip(selected = template == t, onClick = { template = t }, label = { Text(t.label) })
                    }
                }
                Text("Boyut", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    PaperSize.entries.forEach { p ->
                        FilterChip(selected = size == p, onClick = { size = p }, label = { Text(p.label) })
                    }
                    FilterChip(selected = !landscape, onClick = { landscape = false }, label = { Text("Dikey") })
                    FilterChip(selected = landscape, onClick = { landscape = true }, label = { Text("Yatay") })
                }
                Text(
                    "Defterde sayfa paneli ya da ⋮ menüsünden yeni sayfa eklersin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, template, size, landscape) }) { Text("Oluştur") } },
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
    // İlk sayfanın notları değişince kapak da yenilensin.
    val inkStamp = InkStore(context).pageStamp(doc.id, 0)
    val bitmap by produceState<Bitmap?>(Thumbnails.cached(context, doc), doc.id, doc.lastModified, inkStamp) {
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
