package com.sounote.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sounote.app.data.Backup
import com.sounote.app.data.DocRef
import com.sounote.app.data.FolderListing
import com.sounote.app.data.FolderRef
import com.sounote.app.data.LibraryRepository
import com.sounote.app.data.UserFolder
import com.sounote.app.ink.ContentPainter
import com.sounote.app.ink.Images
import com.sounote.app.ink.InkDocument
import com.sounote.app.ink.InkStore
import com.sounote.app.ink.PageContent
import com.sounote.app.ink.ToolState
import com.sounote.app.pdf.PageTemplate
import com.sounote.app.pdf.PdfExporter
import com.sounote.app.pdf.PdfPages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = LibraryRepository(app)
    private val store = InkStore(app)
    private val prefs = app.getSharedPreferences("viewer", Context.MODE_PRIVATE)
    private val toolPrefs = app.getSharedPreferences("tools", Context.MODE_PRIVATE)

    var imported by mutableStateOf<List<DocRef>>(emptyList())
        private set
    var rootFolders by mutableStateOf<List<FolderRef>>(emptyList())
        private set

    /** Bağlı klasör içinde gezinme yolu; boşsa kütüphane ana sayfası. */
    var path by mutableStateOf<List<FolderRef>>(emptyList())
        private set
    var listing by mutableStateOf(FolderListing(emptyList(), emptyList()))
        private set
    var loaded by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var openDoc by mutableStateOf<DocRef?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    var tools by mutableStateOf(ToolState.load(toolPrefs))
        private set

    fun updateTools(state: ToolState) {
        tools = state
        state.save(toolPrefs)
    }

    // ---- Kütüphane düzeni: kendi klasörlerim, arama, sıralama, tema ----

    enum class SortMode(val label: String) { RECENT("Son açılan"), ADDED("Son eklenen"), NAME("Ada göre") }
    enum class ThemeMode(val label: String) { SYSTEM("Sistem"), LIGHT("Açık"), DARK("Koyu") }

    var userFolders by mutableStateOf<List<UserFolder>>(emptyList())
        private set

    /** Açık olan kendi klasörüm; null = ana sayfa. */
    var currentFolder by mutableStateOf<String?>(null)
        private set

    var searchQuery by mutableStateOf("")
    var sortMode by mutableStateOf(enumValueOrNull<SortMode>(prefs.getString("sort", null)) ?: SortMode.RECENT)
        private set
    var themeMode by mutableStateOf(enumValueOrNull<ThemeMode>(prefs.getString("theme", null)) ?: ThemeMode.SYSTEM)
        private set

    fun updateSort(mode: SortMode) {
        sortMode = mode
        prefs.edit().putString("sort", mode.name).apply()
    }

    fun updateTheme(mode: ThemeMode) {
        themeMode = mode
        prefs.edit().putString("theme", mode.name).apply()
    }

    fun lastOpened(doc: DocRef) = prefs.getLong("opened_${doc.id}", 0L)

    fun sorted(docs: List<DocRef>): List<DocRef> = when (sortMode) {
        SortMode.NAME -> docs.sortedWith(compareBy(com.sounote.app.data.NaturalOrder) { it.title })
        SortMode.ADDED -> docs.sortedByDescending { it.addedAt }
        SortMode.RECENT -> docs.sortedByDescending { maxOf(lastOpened(it), it.addedAt) }
    }

    fun openUserFolder(id: String) {
        path = emptyList()
        currentFolder = id
    }

    fun createFolder(name: String, color: Int) = folderOp { repo.createFolder(name.trim().ifEmpty { "Klasör" }, currentFolder, color) }
    fun updateFolder(f: UserFolder, name: String, color: Int) = folderOp { repo.updateFolder(f.id, name.trim().ifEmpty { f.name }, color) }
    fun deleteFolder(f: UserFolder) = folderOp { repo.deleteFolder(f.id) }
    fun moveDoc(doc: DocRef, folderId: String?) = folderOp { repo.moveDoc(doc.id, folderId) }
    fun moveFolder(f: UserFolder, parentId: String?) = folderOp { repo.moveFolder(f.id, parentId) }

    private fun folderOp(block: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { block() }
            userFolders = withContext(Dispatchers.IO) { repo.userFolders() }
            imported = withContext(Dispatchers.IO) { repo.importedDocs() }
        }
    }

    // ---- Kaynağı kaybolan notlu belgeler ----

    /** Bağlı klasördeki kaynağı silinmiş/bağlantısı kaldırılmış ama notları olan belgeler. */
    var orphans by mutableStateOf<List<DocRef>>(emptyList())
        private set

    fun adoptOrphan(doc: DocRef) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.adoptCopy(doc) }
            refresh()
        }
    }

    fun deleteOrphan(doc: DocRef) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.deleteCopy(doc.id)
                store.delete(doc.id)
            }
            refresh()
        }
    }

    // ---- Görüntüleyici: tam ekran, sayfayı resim olarak paylaş ----

    var fullScreen by mutableStateOf(false)
        private set

    fun toggleFullScreen() {
        fullScreen = !fullScreen
    }

    /** Sayfayı notlarıyla birlikte PNG olarak hazırlar (WhatsApp vb. ile göndermek için). */
    fun exportPageImage(doc: DocRef, page: Int, onReady: (File) -> Unit) {
        if (exporting) return
        val content = openInk?.ink?.content(page) ?: PageContent.EMPTY
        viewModelScope.launch {
            exporting = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val source = repo.resolveForOpen(doc).uri
                    val dir = File(app.cacheDir, "exports").apply { mkdirs() }
                    val name = doc.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60)
                    val target = File(dir, "$name - sayfa ${page + 1}.png")
                    LibraryRepository.openFileDescriptor(app, source).use { pfd ->
                        PdfRenderer(pfd).use { r ->
                            r.openPage(page).use { p ->
                                val scale = (2400f / maxOf(p.width, p.height)).coerceIn(1f, 4f)
                                val bmp = Bitmap.createBitmap(
                                    (p.width * scale).toInt(), (p.height * scale).toInt(), Bitmap.Config.ARGB_8888,
                                )
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                p.render(bmp, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val canvas = Canvas(bmp)
                                canvas.scale(scale, scale)
                                ContentPainter(store.imagesDir(doc.id)).draw(canvas, content)
                                target.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            }
                        }
                    }
                    target
                }
            }
            exporting = false
            result.onSuccess(onReady).onFailure {
                ErrorLog.record("Sayfa resmi oluşturulamadı", it)
                message = "Sayfa resmi oluşturulamadı"
            }
        }
    }

    // ---- Otomatik yedek ----

    /** Otomatik yedeğin yazıldığı klasör (SAF ağacı); null = kapalı. */
    var autoBackupFolder by mutableStateOf(prefs.getString("autoBackupTree", null))
        private set

    fun enableAutoBackup(tree: Uri) {
        val resolver = getApplication<Application>().contentResolver
        try {
            resolver.takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: Exception) {
            ErrorLog.record("Yedek klasörü izni alınamadı", e)
            message = "Bu klasöre yazılamıyor"
            return
        }
        prefs.edit().putString("autoBackupTree", tree.toString()).remove("autoBackupFp").apply()
        autoBackupFolder = tree.toString()
        runAutoBackup(force = true)
    }

    fun disableAutoBackup() {
        autoBackupFolder?.let { t ->
            runCatching {
                getApplication<Application>().contentResolver.releasePersistableUriPermission(
                    Uri.parse(t),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        prefs.edit().remove("autoBackupTree").apply()
        autoBackupFolder = null
    }

    fun lastAutoBackup() = prefs.getLong("autoBackupTime", 0L)

    /** Günde bir, sadece kütüphane değiştiyse. */
    private fun maybeAutoBackup() {
        if (autoBackupFolder == null) return
        if (System.currentTimeMillis() - lastAutoBackup() < AUTO_BACKUP_INTERVAL_MS) return
        runAutoBackup(force = false)
    }

    private var autoBackupRunning = false

    /**
     * İki dosya dönüşümlü yazılır (…-1.zip, …-2.zip): yazarken bir şey olursa bile
     * diğeri sağlam bir yedek olarak kalır.
     */
    fun runAutoBackup(force: Boolean) {
        val tree = autoBackupFolder?.let(Uri::parse) ?: return
        if (autoBackupRunning) return
        autoBackupRunning = true
        viewModelScope.launch {
            val app = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val fp = Backup.fingerprint(app)
                    if (!force && fp == prefs.getString("autoBackupFp", null)) return@runCatching false
                    val slot = 1 - prefs.getInt("autoBackupSlot", 1)
                    val uri = findOrCreate(tree, "SouNote-otomatik-yedek-${slot + 1}.zip")
                    app.contentResolver.openOutputStream(uri, "wt")?.use { Backup.write(app, it) }
                        ?: error("Yedek dosyası açılamadı")
                    prefs.edit()
                        .putString("autoBackupFp", fp)
                        .putInt("autoBackupSlot", slot)
                        .putLong("autoBackupTime", System.currentTimeMillis())
                        .apply()
                    true
                }
            }
            autoBackupRunning = false
            result.onSuccess { if (it && force) message = "Otomatik yedek alındı" }
                .onFailure {
                    ErrorLog.record("Otomatik yedek alınamadı", it)
                    message = "Otomatik yedek alınamadı"
                }
        }
    }

    private fun findOrCreate(tree: Uri, name: String): Uri {
        val resolver = getApplication<Application>().contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name) return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
            }
        }
        return DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, rootId), "application/zip", name)
            ?: error("Yedek dosyası oluşturulamadı")
    }

    // ---- Yedekleme ----

    fun backup(target: Uri) {
        viewModelScope.launch {
            busy = true
            saveJob?.cancel()
            flushInk()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(target)?.use {
                        Backup.write(getApplication(), it)
                    } ?: error("Dosya açılamadı")
                }.onFailure { ErrorLog.record("Yedeklenemedi", it) }.isSuccess
            }
            busy = false
            message = if (ok) "Yedek kaydedildi" else "Yedeklenemedi"
        }
    }

    fun restore(source: Uri) {
        viewModelScope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(source)?.use {
                        Backup.restore(getApplication(), it)
                    } ?: error("Dosya açılamadı")
                    repo.reload()
                }
            }
            busy = false
            currentFolder = null
            path = emptyList()
            refresh()
            message = result.fold(
                onSuccess = { "Yedek geri yüklendi" },
                onFailure = { "Geri yüklenemedi: ${it.message ?: it.javaClass.simpleName}" },
            )
        }
    }

    fun consumeMessage() {
        message = null
    }

    fun showMessage(text: String) {
        message = text
    }

    // ---- Notlar: yükleme ve otomatik kayıt ----

    private class OpenInk(val doc: DocRef, val ink: InkDocument, val listener: (Int) -> Unit)

    private var openInk: OpenInk? = null
    private val dirtyPages = HashSet<Int>()
    private var saveJob: Job? = null

    /** Açık belgenin notları. Belge açılırken diskten yüklenir. */
    var ink by mutableStateOf(InkDocument())
        private set

    private fun attachInk(doc: DocRef, loaded: Map<Int, PageContent>) {
        detachInk()
        val document = InkDocument().apply { load(loaded) }
        val listener: (Int) -> Unit = { page -> onInkChanged(page) }
        document.addListener(listener)
        openInk = OpenInk(doc, document, listener)
        ink = document
    }

    private fun detachInk() {
        openInk?.let { it.ink.removeListener(it.listener) }
        openInk = null
    }

    private fun onInkChanged(page: Int) {
        dirtyPages += page
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            flushInk()
        }
    }

    /** Değişen sayfaları hemen diske yazar. Uygulama arka plana geçerken de çağrılır. */
    fun flush() {
        if (dirtyPages.isEmpty()) return
        saveJob?.cancel()
        viewModelScope.launch { flushInk() }
    }

    private suspend fun flushInk() {
        val open = openInk ?: return
        if (dirtyPages.isEmpty()) return
        val snapshot = dirtyPages.associateWith { open.ink.content(it) }
        dirtyPages.clear()
        withContext(NonCancellable + Dispatchers.IO) {
            try {
                // Bağlı klasördeki belgeye ilk not: PDF'in kopyasını al.
                repo.ensureCopy(open.doc)
            } catch (e: Exception) {
                ErrorLog.record("Kopya alınamadı", e)
            }
            snapshot.forEach { (page, content) ->
                try {
                    store.save(open.doc.id, page, content)
                } catch (e: Exception) {
                    ErrorLog.record("Notlar kaydedilemedi", e)
                    withContext(Dispatchers.Main) { message = "Notlar kaydedilemedi!" }
                }
            }
        }
    }

    /** Belgeye eklenen resimlerin klasörü. */
    fun imagesDir(doc: DocRef): File = store.imagesDir(doc.id)

    /** Galeriden seçilen resmi belgeye kaydeder; [onReady] dosya adı ve piksel boyutuyla çağrılır. */
    fun importImage(doc: DocRef, uri: Uri, onReady: (file: String, width: Int, height: Int) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Images.import(getApplication(), uri, store.imagesDir(doc.id)) }
            }
            result.onSuccess { onReady(it.file, it.width, it.height) }.onFailure {
                ErrorLog.record("Resim eklenemedi", it)
                message = "Resim eklenemedi"
            }
        }
    }

    /** Doldurulan formun yazılacağı dosya. */
    fun formSaveTarget(doc: DocRef): File = repo.docFile(doc.id)

    fun open(doc: DocRef) {
        prefs.edit().putLong("opened_${doc.id}", System.currentTimeMillis()).apply()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.rememberLinked(doc) }
            val (resolved, content) = withContext(Dispatchers.IO) { repo.resolveForOpen(doc) to store.load(doc.id) }
            bookmarks = withContext(Dispatchers.IO) { store.loadBookmarks(doc.id) }
            attachInk(resolved, content)
            openDoc = resolved
        }
    }

    // ---- Sayfalar: ekleme, silme, taşıma, yer imleri ----

    /** Sayfa yapısı değişince artar; görüntüleyici PDF'i yeniden açar. */
    var docVersion by mutableStateOf(0)
        private set

    var pagesBusy by mutableStateOf(false)
        private set

    var bookmarks by mutableStateOf<Set<Int>>(emptySet())
        private set

    var pagePanelOpen by mutableStateOf(false)
        private set

    fun togglePagePanel() {
        pagePanelOpen = !pagePanelOpen
    }

    var twoPage by mutableStateOf(prefs.getBoolean("twoPage", false))
        private set

    fun updateTwoPage(on: Boolean) {
        twoPage = on
        prefs.edit().putBoolean("twoPage", on).apply()
    }

    fun toggleBookmark(doc: DocRef, page: Int) {
        val next = if (page in bookmarks) bookmarks - page else bookmarks + page
        bookmarks = next
        viewModelScope.launch(Dispatchers.IO) { runCatching { store.saveBookmarks(doc.id, next) } }
    }

    fun insertBlankPage(doc: DocRef, at: Int, template: PageTemplate) =
        changePages(doc, focusPage = at, "Sayfa eklenemedi") { file ->
            PdfPages.insertBlank(getApplication(), file, at, template)
            ({ i: Int -> if (i >= at) i + 1 else i })
        }

    fun deletePage(doc: DocRef, at: Int) =
        changePages(doc, focusPage = (at - 1).coerceAtLeast(0), "Sayfa silinemedi") { file ->
            PdfPages.delete(getApplication(), file, at)
            ({ i: Int -> if (i == at) null else if (i > at) i - 1 else i })
        }

    fun movePage(doc: DocRef, from: Int, to: Int) =
        changePages(doc, focusPage = to, "Sayfa taşınamadı") { file ->
            PdfPages.move(getApplication(), file, from, to)
            ({ i: Int ->
                when {
                    i == from -> to
                    from < to && i in (from + 1)..to -> i - 1
                    from > to && i in to until from -> i + 1
                    else -> i
                }
            })
        }

    /**
     * PDF'in sayfa yapısını değiştirir: önce notlar kaydedilir, belge uygulama içine kopyalanır,
     * PDF düzenlenir, sonra notlar, yer imleri ve son sayfa yeni numaralara taşınır.
     * [edit] PDF'i değiştirip eski sayfa → yeni sayfa eşlemesini döndürür.
     */
    private fun changePages(doc: DocRef, focusPage: Int, error: String, edit: (File) -> (Int) -> Int?) {
        if (pagesBusy) return
        viewModelScope.launch {
            pagesBusy = true
            saveJob?.cancel()
            flushInk()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    repo.ensureCopy(doc)
                    val map = edit(repo.docFile(doc.id))
                    store.remapPages(doc.id, map)
                    val marks = store.loadBookmarks(doc.id).mapNotNull(map).toSet()
                    store.saveBookmarks(doc.id, marks)
                    Triple(repo.resolveForOpen(doc), store.load(doc.id), marks)
                }
            }
            pagesBusy = false
            result.onSuccess { (resolved, content, marks) ->
                bookmarks = marks
                saveLastPage(doc, focusPage)
                attachInk(resolved, content)
                openDoc = resolved
                docVersion++
            }.onFailure {
                ErrorLog.record(error, it)
                message = "$error: ${it.message ?: it.javaClass.simpleName}"
            }
        }
    }

    /** Boş defter: şablonlu A4 sayfalardan oluşan yeni bir PDF olarak kütüphaneye eklenir ve açılır. */
    fun createNotebook(title: String, template: PageTemplate) {
        viewModelScope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    repo.createDocument(title.trim().ifEmpty { "Defter" }, currentFolder) { file ->
                        PdfPages.createNotebook(getApplication(), file, template, pages = 1)
                    }
                }
            }
            busy = false
            imported = withContext(Dispatchers.IO) { repo.importedDocs() }
            result.onSuccess { open(it) }.onFailure {
                ErrorLog.record("Defter oluşturulamadı", it)
                message = "Defter oluşturulamadı"
            }
        }
    }

    fun closeDoc() {
        fullScreen = false
        viewModelScope.launch {
            saveJob?.cancel()
            flushInk()
            detachInk()
            openDoc = null
            refresh()
        }
    }

    // ---- Dışa aktarma ----

    var exporting by mutableStateOf(false)
        private set

    /** Notlu PDF'i oluşturur; [onReady] dosyayla çağrılır (paylaşmak ya da kaydetmek için). */
    fun export(doc: DocRef, onReady: (File) -> Unit) {
        if (exporting) return
        viewModelScope.launch {
            exporting = true
            saveJob?.cancel()
            flushInk()
            val snapshot = openInk?.ink?.snapshot() ?: emptyMap()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // Form doldurulduysa ya da not alındıysa uygulama içindeki kopya güncel olandır.
                    val source = repo.resolveForOpen(doc).uri
                    val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val name = doc.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
                    val target = File(dir, "$name (notlu).pdf")
                    PdfExporter.export(getApplication(), source, snapshot, store.imagesDir(doc.id), target)
                    target
                }
            }
            exporting = false
            result.onSuccess(onReady).onFailure {
                ErrorLog.record("Dışa aktarılamadı", it)
                message = "PDF oluşturulamadı: ${it.message ?: it.javaClass.simpleName}"
            }
        }
    }

    /** Oluşturulan PDF'i kullanıcının seçtiği yere (Dosyalar, Drive…) kopyalar. */
    fun saveExportTo(file: File, target: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(target)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    } ?: error("Dosya açılamadı")
                }.isSuccess
            }
            message = if (ok) "PDF kaydedildi" else "PDF kaydedilemedi"
        }
    }

    // ---- Kütüphane ----

    /** Uygulamaya her dönüldüğünde çağrılır: bağlı klasöre yeni eklenen PDF'ler böyle görünür. */
    fun refresh() {
        viewModelScope.launch {
            val (imp, roots) = withContext(Dispatchers.IO) { repo.importedDocs() to repo.linkedFolders() }
            imported = imp
            rootFolders = roots
            userFolders = withContext(Dispatchers.IO) { repo.userFolders() }
            if (currentFolder != null && userFolders.none { it.id == currentFolder }) currentFolder = null
            orphans = withContext(Dispatchers.IO) { runCatching { repo.orphanedCopies() }.getOrDefault(emptyList()) }
            if (openDoc == null) maybeAutoBackup()
            loaded = true
            path.lastOrNull()?.let { loadListing(it) }
        }
    }

    private suspend fun loadListing(folder: FolderRef) {
        try {
            val result = withContext(Dispatchers.IO) { repo.listFolder(folder) }
            if (path.lastOrNull() == folder) listing = result
        } catch (e: Exception) {
            ErrorLog.record("Klasör okunamadı", e)
            message = "\"${folder.name}\" klasörüne erişilemiyor. Silinmiş ya da izni kaldırılmış olabilir."
            path = emptyList()
        }
    }

    val canNavigateUp get() = path.isNotEmpty() || currentFolder != null

    fun openFolder(folder: FolderRef) {
        path = path + folder
        listing = FolderListing(emptyList(), emptyList())
        viewModelScope.launch { loadListing(folder) }
    }

    fun navigateUp() {
        if (path.isEmpty()) {
            currentFolder = userFolders.find { it.id == currentFolder }?.parentId
            return
        }
        path = path.dropLast(1)
        path.lastOrNull()?.let { f ->
            listing = FolderListing(emptyList(), emptyList())
            viewModelScope.launch { loadListing(f) }
        }
    }

    fun importPdfs(uris: List<Uri>, openAfter: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            busy = true
            var last: DocRef? = null
            var failed = 0
            for (uri in uris) {
                try {
                    val folder = currentFolder
                    last = withContext(Dispatchers.IO) { repo.importPdf(uri, folder) }
                } catch (e: Exception) {
                    ErrorLog.record("İçe aktarılamadı: $uri", e)
                    failed++
                }
            }
            busy = false
            imported = withContext(Dispatchers.IO) { repo.importedDocs() }
            val ok = uris.size - failed
            val resolver = getApplication<Application>().contentResolver
            val word = uris.any { u ->
                val type = runCatching { resolver.getType(u) }.getOrNull().orEmpty()
                "word" in type || "officedocument" in type ||
                    u.toString().endsWith(".docx", true) || u.toString().endsWith(".doc", true)
            }
            message = when {
                word && ok == 0 -> "Word dosyası doğrudan açılmaz. Önce Word ya da WPS'te \"PDF olarak kaydet\" de, sonra PDF'i içe aktar."
                failed == 0 && ok == 1 -> null
                failed == 0 -> "$ok PDF içe aktarıldı"
                ok == 0 -> "PDF açılamadı. Dosya bozuk ya da şifreli olabilir."
                else -> "$ok PDF içe aktarıldı, $failed tanesi açılamadı"
            }
            if (openAfter && last != null) {
                path = emptyList()
                open(last)
            }
        }
    }

    fun linkFolder(treeUri: Uri) {
        viewModelScope.launch {
            try {
                val folder = withContext(Dispatchers.IO) { repo.linkFolder(treeUri) }
                rootFolders = withContext(Dispatchers.IO) { repo.linkedFolders() }
                message = "\"${folder.name}\" bağlandı. İçine attığın PDF'ler burada görünecek."
            } catch (e: Exception) {
                ErrorLog.record("Klasör bağlanamadı", e)
                message = "Klasör bağlanamadı"
            }
        }
    }

    fun unlinkFolder(folder: FolderRef) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.unlinkFolder(folder.treeUri) }
            rootFolders = withContext(Dispatchers.IO) { repo.linkedFolders() }
        }
    }

    fun rename(doc: DocRef, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.rename(doc.id, clean) }
            imported = withContext(Dispatchers.IO) { repo.importedDocs() }
        }
    }

    fun delete(doc: DocRef) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.delete(doc.id)
                store.delete(doc.id)
            }
            prefs.edit().remove(pageKey(doc)).apply()
            imported = withContext(Dispatchers.IO) { repo.importedDocs() }
        }
    }

    fun lastPage(doc: DocRef) = prefs.getInt(pageKey(doc), 0)

    fun saveLastPage(doc: DocRef, page: Int) {
        prefs.edit().putInt(pageKey(doc), page).apply()
    }

    private fun pageKey(doc: DocRef) = "page_${doc.id}"

    override fun onCleared() {
        // Son değişiklikler kaybolmasın.
        val open = openInk ?: return
        val snapshot = dirtyPages.associateWith { open.ink.content(it) }
        snapshot.forEach { (page, content) -> runCatching { store.save(open.doc.id, page, content) } }
    }

    companion object {
        private inline fun <reified T : Enum<T>> enumValueOrNull(name: String?): T? =
            name?.let { n -> enumValues<T>().firstOrNull { it.name == n } }

        private const val TAG = "LibraryViewModel"
        private const val SAVE_DELAY_MS = 800L
        private const val AUTO_BACKUP_INTERVAL_MS = 20L * 60 * 60 * 1000
    }
}
