@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sounote.app.ui

import android.content.Intent
import android.view.KeyEvent
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.graphics.RectF
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star as OutlinedStar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import com.sounote.app.pdf.PageThumbLoader
import com.sounote.app.pdf.PdfContainer
import com.sounote.app.pdf.SearchHit
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.sounote.app.LibraryViewModel
import com.sounote.app.R
import com.sounote.app.data.DocRef
import com.sounote.app.ink.TextBox
import com.sounote.app.ink.Tool
import com.sounote.app.pdf.FormField
import com.sounote.app.pdf.PdfDocumentView
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(doc: DocRef, vm: LibraryViewModel) {
    val context = LocalContext.current
    val ink = vm.ink
    val tools = vm.tools
    var page by remember(doc.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    var error by remember(doc.id) { mutableStateOf<String?>(null) }
    var view by remember { mutableStateOf<PdfDocumentView?>(null) }
    var canUndo by remember(ink) { mutableStateOf(ink.canUndo) }
    var canRedo by remember(ink) { mutableStateOf(ink.canRedo) }
    var editingText by remember { mutableStateOf<Triple<Int, TextBox, Boolean>?>(null) }
    var editingField by remember { mutableStateOf<FormField?>(null) }
    var exportMenu by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<File?>(null) }
    val initialPage = remember(doc.id) { vm.lastPage(doc) }
    var selCount by remember { mutableStateOf(0) }
    var hasClip by remember { mutableStateOf(false) }
    val currentVm by rememberUpdatedState(vm)
    val snackbar = remember { SnackbarHostState() }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var hitIndex by remember { mutableStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var insertAfterCurrent by remember { mutableStateOf(false) }
    var goToPageDialog by remember { mutableStateOf(false) }
    var recordingsDialog by remember { mutableStateOf(false) }
    // Dışa aktarma penceresi: (dosyaya mı kaydedilecek, açık mı)
    var exportDialog by remember { mutableStateOf(false to false) }
    var pomodoroDialog by remember { mutableStateOf(false) }
    // Bilgi kartları
    var cardsDialog by remember { mutableStateOf(false) }
    var assistantOpen by remember { mutableStateOf(false) }
    // Lasso ile çizilen son bölge ve asistana gönderilecek metni
    var lassoRegion by remember { mutableStateOf<Pair<Int, RectF>?>(null) }
    var askSelection by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val reading = vm.readingMode
    // Telefonda üst çubuk dar: bazı düğmeler ⋮ menüsüne taşınır.
    val narrowBar = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600
    val wideViewer = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 840
    var sideNote by remember { mutableStateOf(false) }
    fun askRegion() {
        val (p, r) = lassoRegion ?: view?.selectionRegion() ?: return
        scope.launch {
            val text = vm.regionText(doc, p, r)
            if (text.isBlank()) {
                vm.showMessage("Seçili bölgede okunabilir metin yok (taranmış sayfa olabilir); sayfanın tamamı gönderilecek")
                askSelection = null
            } else {
                askSelection = text
            }
            assistantOpen = true
        }
    }
    // Kayda bağlı çizgiye dokununca: (dosya adı, an, dokunma sayacı)
    var audioPlay by remember { mutableStateOf<Triple<String, Long, Int>?>(null) }
    var makeCard by remember { mutableStateOf<Pair<Int, RectF>?>(null) }
    var textCardDialog by remember { mutableStateOf(false) }
    var study by remember { mutableStateOf<List<com.sounote.app.data.Card>?>(null) }
    LaunchedEffect(doc.id) { vm.loadCards(doc) }
    val insertPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.insertPdf(doc, (page?.first ?: 0) + 1, uri)
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording(doc) else vm.showMessage("Ses kaydı için mikrofon izni gerekiyor")
    }
    fun toggleRecording() {
        if (vm.recordingSince != 0L) {
            vm.stopRecording(doc)
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.startRecording(doc)
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    // Kayıt süresi göstergesi
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(vm.recordingSince, vm.pomodoro != null) {
        while (vm.recordingSince != 0L || vm.pomodoro != null) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    LaunchedEffect(doc.id) { vm.loadRecordings(doc) }
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // Tam ekran: sistem çubukları da gizlenir, kaydırınca geçici olarak görünür.
    val activity = context as? Activity
    DisposableEffect(vm.fullScreen, activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (vm.fullScreen) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    val loader = remember(doc.uri, vm.docVersion) { PageThumbLoader(context, doc.uri, vm.imagesDir(doc)) }
    DisposableEffect(loader) { onDispose { loader.close() } }

    fun showHit() {
        val byPage = hits.groupBy { it.page }.mapValues { e -> e.value.flatMap { it.rects } }
        val current = hits.getOrNull(hitIndex)
        view?.setSearchHighlights(byPage, current?.let { it.page to it.rects })
        current?.let { h ->
            if (h.rects.isNotEmpty()) {
                val r = RectF(h.rects.first())
                h.rects.forEach { r.union(it) }
                view?.scrollToRect(h.page, r)
            }
        }
    }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        searching = true
        view?.search(q) { result ->
            searching = false
            hits = result
            hitIndex = 0
            showHit()
            if (result.isEmpty()) currentVm.showMessage("\"$q\" bulunamadı")
        }
    }

    fun closeSearch() {
        searchOpen = false
        hits = emptyList()
        view?.setSearchHighlights(emptyMap(), null)
    }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    DisposableEffect(ink) {
        val listener = {
            canUndo = ink.canUndo
            canRedo = ink.canRedo
        }
        ink.addHistoryListener(listener)
        onDispose { ink.removeHistoryListener(listener) }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val file = pendingExport
        if (uri != null && file != null) vm.saveExportTo(file, uri)
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            vm.importImage(doc, uri) { file, w, h ->
                // Eklenen resim seçili gelir: hemen taşınıp boyutlandırılabilsin.
                vm.updateTools(vm.tools.copy(tool = Tool.LASSO))
                view?.insertImage(file, w, h)
            }
        }
    }

    fun share(file: File, mime: String = "application/pdf") {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Paylaş"))
    }

    fun printDoc() {
        vm.export(doc) { file -> printPdf(context, file, doc.title) }
    }

    // Klavye kısayolları (klavyeli kılıf): geri al/ileri al, sayfa çevir, ara, yazdır.
    DisposableEffect(vm, ink) {
        vm.keyHandler = handler@{ e ->
            val ctrl = e.isCtrlPressed
            val current = page?.first ?: 0
            when {
                ctrl && e.keyCode == KeyEvent.KEYCODE_Z && !searchOpen -> {
                    if (e.isShiftPressed) ink.redo() else ink.undo()
                }
                ctrl && e.keyCode == KeyEvent.KEYCODE_Y && !searchOpen -> ink.redo()
                ctrl && e.keyCode == KeyEvent.KEYCODE_F -> searchOpen = true
                ctrl && e.keyCode == KeyEvent.KEYCODE_P -> printDoc()
                ctrl && e.keyCode == KeyEvent.KEYCODE_S -> {
                    currentVm.flush()
                    currentVm.showMessage("Kaydedildi")
                }
                e.keyCode == KeyEvent.KEYCODE_PAGE_DOWN -> view?.goToPage(current + 1)
                e.keyCode == KeyEvent.KEYCODE_PAGE_UP -> view?.goToPage(current - 1)
                ctrl && e.keyCode == KeyEvent.KEYCODE_MOVE_HOME -> view?.goToPage(0)
                ctrl && e.keyCode == KeyEvent.KEYCODE_MOVE_END -> view?.goToPage(Int.MAX_VALUE)
                else -> return@handler false
            }
            true
        }
        onDispose { vm.keyHandler = null }
    }

    val fullScreenButtons: @Composable () -> Unit = {
        if (vm.recordingSince != 0L) {
            TextButton(onClick = { toggleRecording() }) {
                Text("● " + formatDuration(now - vm.recordingSince), color = Color(0xFFE53935))
            }
        }
        IconButton(onClick = { vm.toggleFullScreen() }) {
            Icon(painterResource(R.drawable.ic_fullscreen_exit), contentDescription = "Tam ekrandan çık")
        }
        IconButton(onClick = { ink.undo() }, enabled = canUndo) {
            Icon(painterResource(R.drawable.ic_undo), contentDescription = "Geri al")
        }
        IconButton(onClick = { ink.redo() }, enabled = canRedo) {
            Icon(painterResource(R.drawable.ic_redo), contentDescription = "İleri al")
        }
    }

    Scaffold(
        topBar = {
            if (!vm.fullScreen) TopAppBar(
                title = {
                    Column {
                        Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        page?.let { (p, n) ->
                            Text(
                                "Sayfa ${p + 1} / $n  ▾",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.clickable { goToPageDialog = true },
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = vm::closeDoc) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    vm.pomodoro?.let { p ->
                        TextButton(onClick = { pomodoroDialog = true }) {
                            Text(
                                (if (p.onBreak) "☕ " else "🍅 ") + formatDuration((p.endsAt - now).coerceAtLeast(0L)),
                                color = if (p.onBreak) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    // Okuma ↔ not alma modu
                    IconButton(onClick = { vm.updateReadingMode(!reading) }) {
                        Text(
                            if (reading) "✏️" else "📖",
                            fontSize = 20.sp,
                            modifier = Modifier.semantics { contentDescription = if (reading) "Not al" else "Oku" },
                        )
                    }
                    if (vm.recordingSince != 0L) {
                        TextButton(onClick = { toggleRecording() }) {
                            Text(
                                "● " + formatDuration(now - vm.recordingSince) + "  Durdur",
                                color = Color(0xFFE53935),
                            )
                        }
                    } else if (!reading && !narrowBar) {
                        IconButton(onClick = { toggleRecording() }) {
                            Icon(painterResource(R.drawable.ic_mic), contentDescription = "Ses kaydı başlat")
                        }
                    }
                    IconButton(onClick = { vm.togglePagePanel() }) {
                        Icon(painterResource(R.drawable.ic_pages), contentDescription = "Sayfalar")
                    }
                    if (!narrowBar) {
                        IconButton(onClick = { askSelection = null; assistantOpen = true }) {
                            Text("🤖", fontSize = 20.sp)
                        }
                        IconButton(onClick = { if (searchOpen) closeSearch() else searchOpen = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Ara")
                        }
                    }
                    if (!narrowBar) page?.let { (p, _) ->
                        val marked = p in vm.bookmarks
                        IconButton(onClick = { vm.toggleBookmark(doc, p) }) {
                            Icon(
                                if (marked) Icons.Filled.Star else Icons.Outlined.OutlinedStar,
                                contentDescription = "Yer imi",
                                tint = if (marked) Color(0xFFF2B705) else LocalContentColor.current,
                            )
                        }
                    }
                    if (!reading) {
                        IconButton(onClick = { ink.undo() }, enabled = canUndo) {
                            Icon(painterResource(R.drawable.ic_undo), contentDescription = "Geri al")
                        }
                        IconButton(onClick = { ink.redo() }, enabled = canRedo) {
                            Icon(painterResource(R.drawable.ic_redo), contentDescription = "İleri al")
                        }
                    }
                    Box {
                        if (vm.exporting) {
                            CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.5.dp)
                        } else {
                            IconButton(onClick = { exportMenu = true }) {
                                Icon(Icons.Default.Share, contentDescription = "Dışa aktar")
                            }
                        }
                        DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("SouNote'lu arkadaşa gönder (notlar düzenlenebilir)") },
                                onClick = {
                                    exportMenu = false
                                    vm.sharePackage(doc) { sharePackageFile(context, it) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Notlu PDF'i paylaş…") },
                                onClick = {
                                    exportMenu = false
                                    exportDialog = false to true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Notlu PDF'i dosyaya kaydet…") },
                                onClick = {
                                    exportMenu = false
                                    exportDialog = true to true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Yazdır…") },
                                onClick = {
                                    exportMenu = false
                                    printDoc()
                                },
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { moreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Diğer")
                        }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            // Dar ekranda (telefon) üst çubuğa sığmayanlar burada.
                            if (narrowBar) {
                                DropdownMenuItem(
                                    text = { Text("🤖 Asistan") },
                                    onClick = { moreMenu = false; askSelection = null; assistantOpen = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("🔍 Belgede ara") },
                                    onClick = { moreMenu = false; searchOpen = true },
                                )
                                page?.let { (p, _) ->
                                    DropdownMenuItem(
                                        text = { Text(if (p in vm.bookmarks) "★ Yer imini kaldır" else "☆ Yer imi ekle") },
                                        onClick = { moreMenu = false; vm.toggleBookmark(doc, p) },
                                    )
                                }
                                if (!reading && vm.recordingSince == 0L) {
                                    DropdownMenuItem(
                                        text = { Text("🎙️ Ses kaydı başlat") },
                                        onClick = { moreMenu = false; toggleRecording() },
                                    )
                                }
                            }
                            if (wideViewer) {
                                DropdownMenuItem(
                                    text = { Text(if (sideNote) "📝 Yan notu kapat" else "📝 Yan yana not aç") },
                                    onClick = { moreMenu = false; sideNote = !sideNote },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Ses kayıtları (${vm.recordings.size})") },
                                onClick = {
                                    moreMenu = false
                                    vm.loadRecordings(doc)
                                    recordingsDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Bilgi kartları (${vm.cards.size})") },
                                onClick = {
                                    moreMenu = false
                                    cardsDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Çalışma sayacı (pomodoro)") },
                                onClick = {
                                    moreMenu = false
                                    pomodoroDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Tam ekran") },
                                onClick = {
                                    moreMenu = false
                                    vm.toggleFullScreen()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Bu sayfayı resim olarak paylaş") },
                                onClick = {
                                    moreMenu = false
                                    page?.let { (p, _) -> vm.exportPageImage(doc, p) { share(it, "image/png") } }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Sayfaya git…") },
                                onClick = {
                                    moreMenu = false
                                    goToPageDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Bu sayfadan sonra boş sayfa ekle") },
                                onClick = {
                                    moreMenu = false
                                    insertAfterCurrent = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Başka PDF'ten sayfa ekle…") },
                                onClick = {
                                    moreMenu = false
                                    insertPdfPicker.launch(arrayOf("application/pdf"))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Gece okuma (renkleri ters çevir)") },
                                trailingIcon = { Checkbox(checked = vm.nightReading, onCheckedChange = null) },
                                onClick = {
                                    moreMenu = false
                                    vm.toggleNightReading()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Yatayda çift sayfa") },
                                trailingIcon = { Checkbox(checked = vm.twoPage, onCheckedChange = null) },
                                onClick = {
                                    moreMenu = false
                                    vm.updateTwoPage(!vm.twoPage)
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (vm.pagesBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (searchOpen) {
                Surface(tonalElevation = 2.dp) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text("PDF'te ara") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                            modifier = Modifier.weight(1f),
                        )
                        if (searching) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        } else if (hits.isNotEmpty()) {
                            Text("${hitIndex + 1} / ${hits.size}", style = MaterialTheme.typography.labelLarge)
                        }
                        IconButton(onClick = {
                            if (hits.isNotEmpty()) {
                                hitIndex = (hitIndex - 1 + hits.size) % hits.size
                                showHit()
                            }
                        }, enabled = hits.isNotEmpty()) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Önceki")
                        }
                        IconButton(onClick = {
                            if (hits.isNotEmpty()) {
                                hitIndex = (hitIndex + 1) % hits.size
                                showHit()
                            }
                        }, enabled = hits.isNotEmpty()) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Sonraki")
                        }
                        TextButton(onClick = { runSearch() }) { Text("Ara") }
                        IconButton(onClick = { closeSearch() }) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat")
                        }
                    }
                }
            }
            if (!reading) InkToolbar(
                state = tools,
                onChange = vm::updateTools,
                selection = SelectionUi(
                    count = selCount,
                    hasClipboard = hasClip,
                    onRecolor = { view?.recolorSelection(it) },
                    onCopy = { view?.copySelection() },
                    onPaste = { view?.paste() },
                    onDelete = { view?.deleteSelection() },
                    onDone = { view?.clearSelection() },
                    onMakeCard = { makeCard = view?.selectionRegion() },
                    canAsk = lassoRegion != null || selCount > 0,
                    onAsk = { askRegion() },
                ),
                onInsertImage = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                leading = if (vm.fullScreen) fullScreenButtons else null,
                onRevealTapes = { view?.setAllTapes(it) },
            )
            audioPlay?.let { (name, ms, tapCount) ->
                val f = vm.recordingFileFor(doc, name)
                if (f.exists()) {
                    AudioPlayerBar(f, ms, key = tapCount, onClose = { audioPlay = null })
                } else {
                    LaunchedEffect(name) {
                        vm.showMessage("Bu nota bağlı kayıt silinmiş")
                        audioPlay = null
                    }
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
            val pageInfo = page
            if (vm.pagePanelOpen && pageInfo != null) {
                PagePanel(
                    pageCount = pageInfo.second,
                    currentPage = pageInfo.first,
                    bookmarks = vm.bookmarks,
                    ink = ink,
                    loader = loader,
                    actions = PageActions(
                        onGoTo = { view?.goToPage(it) },
                        onInsert = { at, t -> vm.insertBlankPage(doc, at, t) },
                        onDelete = { vm.deletePage(doc, it) },
                        onMove = { from, to -> vm.movePage(doc, from, to) },
                        onToggleBookmark = { vm.toggleBookmark(doc, it) },
                        onDuplicate = { vm.duplicatePage(doc, it) },
                    ),
                    outline = vm.outline,
                    onShowOutline = { vm.loadOutline(doc) },
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight().clipToBounds(), contentAlignment = Alignment.Center) {
                val message = error
                if (message != null) {
                    Text(message, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
                } else {
                    AndroidView(
                        factory = { ctx ->
                            PdfContainer(ctx).also { container ->
                            container.setLowLatency(tools.lowLatency)
                            container.pdfView.apply {
                                onPageChanged = { p, n ->
                                    page = p to n
                                    currentVm.saveLastPage(doc, p)
                                }
                                onError = { error = "PDF açılamadı. Dosya taşınmış, bozuk ya da şifreli olabilir." }
                                onOperationError = { e ->
                                    currentVm.showMessage("İşlem yapılamadı: ${e.message ?: e.javaClass.simpleName}")
                                }
                                onTextBoxRequest = { p, box, isNew -> editingText = Triple(p, box, isNew) }
                                onFormFieldRequest = { editingField = it }
                                onUndoGesture = { this.ink.undo() }
                                onRedoGesture = { this.ink.redo() }
                                onOpenLink = { uri ->
                                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                                        .onFailure { currentVm.showMessage("Bağlantı açılamadı") }
                                }
                                onFormSaved = { currentVm.showMessage("Form kaydedildi") }
                                formSaveTarget = currentVm.formSaveTarget(doc)
                                imagesDir = currentVm.imagesDir(doc)
                                twoPage = currentVm.twoPage
                                setDarkTheme(darkTheme)
                                recordingNow = { currentVm.currentRecording() }
                                onPlayAudio = { file, ms ->
                                    if (currentVm.recordingSince != 0L) {
                                        currentVm.showMessage("Kayıt sürerken dinlenemez")
                                    } else {
                                        audioPlay = Triple(file, ms, (audioPlay?.third ?: 0) + 1)
                                    }
                                }
                                onLassoRegion = { p, r -> lassoRegion = p to RectF(r) }
                                readOnly = currentVm.readingMode
                                onSelectionChanged = { n, clip ->
                                    selCount = n
                                    hasClip = clip
                                }
                                this.ink = ink
                                this.tools = tools
                                open(doc.uri, initialPage)
                                view = this
                            }
                            }
                        },
                        update = { container ->
                            container.setLowLatency(tools.lowLatency)
                            val v = container.pdfView
                            v.tools = tools
                            v.twoPage = vm.twoPage
                            v.setDarkTheme(darkTheme)
                            v.setInverted(vm.nightReading)
                            v.readOnly = vm.readingMode
                            if (v.ink !== ink) v.ink = ink
                        },
                        onRelease = {
                            it.release()
                            view = null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (reading && vm.fullScreen) {
                        Row(
                            Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)),
                        ) {
                            IconButton(onClick = { vm.toggleFullScreen() }) {
                                Icon(painterResource(R.drawable.ic_fullscreen_exit), contentDescription = "Tam ekrandan çık")
                            }
                            IconButton(onClick = { vm.updateReadingMode(false) }) { Text("✏️") }
                        }
                    }
                    page?.let { (p, n) ->
                        if (n > 3) {
                            PageScrubber(
                                page = p,
                                pageCount = n,
                                onScrub = { view?.goToPage(it) },
                                modifier = Modifier.align(Alignment.CenterEnd),
                            )
                        }
                    }
                }
            }
            // Geniş ekranda belgeye bağlı yazılı not, PDF ile yan yana.
            if (sideNote && wideViewer) {
                androidx.compose.material3.VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.width(360.dp).fillMaxHeight()) { SideNotePanel(vm, doc) { sideNote = false } }
            }
            }
        }
    }

    if (assistantOpen) {
        AssistantDialog(
            vm,
            AssistantSource.Document(doc, lassoRegion?.first?.takeIf { askSelection != null } ?: page?.first ?: 0, page?.second ?: 1, askSelection),
            onDismiss = {
                assistantOpen = false
                askSelection = null
            },
        )
    }

    if (exportDialog.second) {
        val toFile = exportDialog.first
        ExportDialog(
            pageCount = page?.second ?: 1,
            currentPage = page?.first ?: 0,
            toFile = toFile,
            inkPages = remember { ink.snapshot().filterValues { !it.isEmpty }.keys },
            bookmarkPages = vm.bookmarks,
            onExport = { pages, label ->
                exportDialog = false to false
                vm.export(doc, pages, label) { file ->
                    if (toFile) {
                        pendingExport = file
                        saveLauncher.launch(file.name)
                    } else {
                        share(file)
                    }
                }
            },
            onDismiss = { exportDialog = false to false },
        )
    }

    makeCard?.let { (p, region) ->
        MakeCardDialog(
            fromRegion = true,
            onSave = { front, back ->
                vm.addRegionCard(doc, p, region, front, back)
                makeCard = null
                view?.clearSelection()
            },
            onDismiss = { makeCard = null },
        )
    }
    if (textCardDialog) {
        MakeCardDialog(
            fromRegion = false,
            onSave = { front, back ->
                vm.addTextCard(doc, front, back, page?.first ?: -1)
                textCardDialog = false
            },
            onDismiss = { textCardDialog = false },
        )
    }
    if (cardsDialog) {
        FlashcardsDialog(
            cards = vm.cards,
            image = { vm.cardImage(doc, it) },
            onStudy = { all ->
                val now = System.currentTimeMillis()
                study = if (all) vm.cards else vm.cards.filter { it.due <= now }
                cardsDialog = false
            },
            onNewText = {
                cardsDialog = false
                textCardDialog = true
            },
            onDelete = { vm.deleteCard(doc, it) },
            onDismiss = { cardsDialog = false },
        )
    }
    study?.let { list ->
        StudyDialog(
            cards = list,
            image = { vm.cardImage(doc, it) },
            onReview = { card, known -> vm.reviewCard(doc, card, known) },
            onGoToPage = { p ->
                study = null
                view?.goToPage(p)
            },
            onDismiss = { study = null },
        )
    }

    if (pomodoroDialog) {
        AlertDialog(
            onDismissRequest = { pomodoroDialog = false },
            title = { Text("Çalışma sayacı") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Belirli süre çalış, sonra kısa mola ver. Süre dolunca tablet titrer ve haber verir.")
                    vm.pomodoro?.let { p ->
                        Text(
                            (if (p.onBreak) "Mola: " else "Çalışma: ") + formatDuration((p.endsAt - now).coerceAtLeast(0L)) + " kaldı",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.OutlinedButton(onClick = { vm.startPomodoro(25, 5); pomodoroDialog = false }) { Text("25 / 5 dk") }
                        androidx.compose.material3.OutlinedButton(onClick = { vm.startPomodoro(50, 10); pomodoroDialog = false }) { Text("50 / 10 dk") }
                    }
                }
            },
            confirmButton = {
                if (vm.pomodoro != null) {
                    TextButton(onClick = { vm.stopPomodoro(); pomodoroDialog = false }) { Text("Durdur") }
                }
            },
            dismissButton = { TextButton(onClick = { pomodoroDialog = false }) { Text("Kapat") } },
        )
    }

    if (recordingsDialog) {
        RecordingsDialog(
            recordings = vm.recordings,
            onDelete = { vm.deleteRecording(doc, it) },
            onDismiss = { recordingsDialog = false },
        )
    }

    if (goToPageDialog) {
        val count = page?.second ?: 0
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { goToPageDialog = false },
            title = { Text("Sayfaya git") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() }.take(5) },
                    singleLine = true,
                    label = { Text("Sayfa (1–$count)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        text.toIntOrNull()?.let { view?.goToPage(it - 1) }
                        goToPageDialog = false
                    }),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    text.toIntOrNull()?.let { view?.goToPage(it - 1) }
                    goToPageDialog = false
                }) { Text("Git") }
            },
            dismissButton = { TextButton(onClick = { goToPageDialog = false }) { Text("Vazgeç") } },
        )
    }

    if (insertAfterCurrent) {
        TemplateDialog(
            title = "Bu sayfadan sonra boş sayfa",
            onPick = { t ->
                insertAfterCurrent = false
                vm.insertBlankPage(doc, (page?.first ?: 0) + 1, t)
            },
            onDismiss = { insertAfterCurrent = false },
        )
    }

    editingText?.let { (p, box, isNew) ->
        TextBoxDialog(
            box = box,
            isNew = isNew,
            onSave = { edited ->
                val c = ink.content(p)
                val texts = when {
                    edited.text.isBlank() -> c.texts.filter { it.id != box.id }
                    isNew -> c.texts + edited
                    else -> c.texts.map { if (it.id == box.id) edited else it }
                }
                ink.commit(p, c.copy(texts = texts))
                if (edited.text.isNotBlank()) {
                    vm.updateTools(
                        tools.copy(
                            textSizePt = edited.fontSize, textColor = edited.color,
                            textBold = edited.bold, textBackground = edited.background,
                        ),
                    )
                }
                editingText = null
            },
            onDelete = {
                val c = ink.content(p)
                ink.commit(p, c.copy(texts = c.texts.filter { it.id != box.id }))
                editingText = null
            },
            onDismiss = { editingText = null },
        )
    }

    editingField?.let { field ->
        when (field.type) {
            FormField.Type.CHOICE -> FormChoiceDialog(
                field = field,
                onSave = { indices ->
                    view?.editFormField(field, indices = indices)
                    editingField = null
                },
                onDismiss = { editingField = null },
            )
            else -> FormTextDialog(
                field = field,
                onSave = { text ->
                    view?.editFormField(field, text = text)
                    editingField = null
                },
                onDismiss = { editingField = null },
            )
        }
    }
}
