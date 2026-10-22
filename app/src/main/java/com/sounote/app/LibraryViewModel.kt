package com.sounote.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sounote.app.data.AudioNotes
import com.sounote.app.data.AudioRecorder
import com.sounote.app.data.Backup
import com.sounote.app.data.Card
import com.sounote.app.data.Flashcards
import com.sounote.app.data.Recording
import com.sounote.app.data.DocRef
import com.sounote.app.data.FolderListing
import com.sounote.app.data.FolderRef
import com.sounote.app.data.LibraryRepository
import com.sounote.app.data.NaturalOrder
import com.sounote.app.data.UserFolder
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.sounote.app.ink.ContentPainter
import com.sounote.app.ink.Images
import com.sounote.app.ink.InkDocument
import com.sounote.app.ink.InkStore
import com.sounote.app.ink.PageContent
import com.sounote.app.ink.ToolState
import com.sounote.app.pdf.PageTemplate
import com.sounote.app.pdf.PaperSize
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

    /** Seçili etiket filtresi; null = hepsi. */
    var tagFilter by mutableStateOf<String?>(null)

    val allTags: List<String> get() = imported.flatMap { it.tags }.distinct().sortedWith(NaturalOrder)

    fun setTags(doc: DocRef, tags: List<String>) = folderOp { repo.setTags(doc.id, tags) }

    // ---- Çalışma süresi ----

    private var studyStart = 0L
    private var studyDoc: String? = null

    /** Belge açıkken ve uygulama öndeyken geçen süre sayılır. */
    fun studyResume() {
        val d = openDoc ?: return
        studyDoc = d.id
        studyStart = System.currentTimeMillis()
    }

    fun studyPause() {
        val id = studyDoc ?: return
        val start = studyStart
        studyDoc = null
        if (start == 0L) return
        val ms = (System.currentTimeMillis() - start).coerceIn(0L, 12L * 60 * 60 * 1000)
        if (ms < 1000) return
        val day = dayKey(System.currentTimeMillis())
        prefs.edit()
            .putLong("study_$id", studyTime(id) + ms)
            .putLong("studyday_$day", prefs.getLong("studyday_$day", 0L) + ms)
            .apply()
    }

    fun studyTime(docId: String) = prefs.getLong("study_$docId", 0L)

    /** Son [days] günün çalışma süreleri (bugün başta). */
    fun studyDays(days: Int): List<Pair<Long, Long>> {
        val dayMs = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        return (0 until days).map { i ->
            val t = now - i * dayMs
            t to prefs.getLong("studyday_${dayKey(t)}", 0L)
        }
    }

    private fun dayKey(t: Long) = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date(t))

    // ---- Pomodoro ----

    /** Çalışan pomodoro: bitiş zamanı ve mola mı; null = kapalı. */
    var pomodoro by mutableStateOf<Pomodoro?>(null)
        private set

    data class Pomodoro(val endsAt: Long, val onBreak: Boolean, val workMin: Int, val breakMin: Int)

    private var pomodoroJob: Job? = null

    fun startPomodoro(workMin: Int, breakMin: Int) {
        pomodoroJob?.cancel()
        pomodoro = Pomodoro(System.currentTimeMillis() + workMin * 60_000L, false, workMin, breakMin)
        pomodoroJob = viewModelScope.launch {
            while (true) {
                val p = pomodoro ?: break
                val left = p.endsAt - System.currentTimeMillis()
                if (left > 0) {
                    delay(minOf(left, 1000L))
                    continue
                }
                // Süre doldu: çalışma ↔ mola
                val next = if (p.onBreak) p.workMin else p.breakMin
                pomodoro = p.copy(endsAt = System.currentTimeMillis() + next * 60_000L, onBreak = !p.onBreak)
                message = if (p.onBreak) "Mola bitti, çalışmaya devam! (${p.workMin} dk)" else "Mola zamanı! (${p.breakMin} dk)"
                vibrate()
            }
        }
    }

    fun stopPomodoro() {
        pomodoroJob?.cancel()
        pomodoro = null
    }

    private fun vibrate() {
        runCatching {
            val app = getApplication<Application>()
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                app.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(android.os.Vibrator::class.java)
            } ?: return
            vibrator.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300), -1))
        }
    }

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

    // ---- Ders ses kaydı ----

    private var recorder: AudioRecorder? = null

    /** Kayıt başlangıç zamanı (uptime değil, duvar saati); 0 = kayıt yok. */
    var recordingSince by mutableStateOf(0L)
        private set

    var recordings by mutableStateOf<List<Recording>>(emptyList())
        private set

    fun startRecording(doc: DocRef) {
        if (recorder != null) return
        try {
            val r = AudioRecorder(getApplication())
            r.start(AudioNotes.dir(getApplication(), doc.id))
            recorder = r
            recordingSince = System.currentTimeMillis()
        } catch (e: Exception) {
            ErrorLog.record("Ses kaydı başlatılamadı", e)
            message = "Ses kaydı başlatılamadı"
        }
    }

    fun stopRecording(doc: DocRef?) {
        val r = recorder ?: return
        recorder = null
        recordingSince = 0L
        val saved = r.stop()
        message = if (saved != null) "Ses kaydı kaydedildi (⋮ → Ses kayıtları)" else "Ses kaydı boş, kaydedilmedi"
        doc?.let(::loadRecordings)
    }

    fun loadRecordings(doc: DocRef) {
        viewModelScope.launch {
            recordings = withContext(Dispatchers.IO) { AudioNotes.list(getApplication(), doc.id) }
        }
    }

    fun deleteRecording(doc: DocRef, rec: Recording) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { rec.file.delete() }
            loadRecordings(doc)
        }
    }

    // ---- İçindekiler (PDF'in kendi bölüm listesi) ----

    var outline by mutableStateOf<List<PdfPages.OutlineEntry>?>(null)
        private set
    private var outlineFor: String? = null

    fun loadOutline(doc: DocRef) {
        val key = "${doc.id}:$docVersion"
        if (outlineFor == key) return
        outlineFor = key
        outline = null
        viewModelScope.launch {
            outline = withContext(Dispatchers.IO) {
                runCatching { PdfPages.outline(getApplication(), repo.resolveForOpen(doc).uri) }
                    .onFailure { ErrorLog.record("İçindekiler okunamadı", it) }
                    .getOrDefault(emptyList())
            }
        }
    }

    // ---- Bilgi kartları ----

    var cards by mutableStateOf<List<Card>>(emptyList())
        private set

    fun loadCards(doc: DocRef) {
        viewModelScope.launch {
            cards = withContext(Dispatchers.IO) { Flashcards.load(getApplication(), doc.id) }
        }
    }

    private fun saveCards(doc: DocRef, next: List<Card>) {
        cards = next
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { Flashcards.save(getApplication(), doc.id, next) }
                .onFailure { ErrorLog.record("Kartlar kaydedilemedi", it) }
        }
    }

    fun addTextCard(doc: DocRef, front: String, back: String, page: Int) {
        saveCards(doc, cards + Card(System.currentTimeMillis(), front.trim(), null, back.trim(), page, due = System.currentTimeMillis()))
        message = "Kart eklendi"
    }

    /** Sayfanın seçilen bölgesini (notlarıyla) görüntü olarak alıp kartın ön yüzü yapar. */
    fun addRegionCard(doc: DocRef, page: Int, region: RectF, front: String, back: String) {
        val content = openInk?.ink?.content(page) ?: PageContent.EMPTY
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val id = System.currentTimeMillis()
                    val dir = Flashcards.imagesDir(app, doc.id).apply { mkdirs() }
                    val name = "$id.png"
                    LibraryRepository.openFileDescriptor(app, repo.resolveForOpen(doc).uri).use { pfd ->
                        PdfRenderer(pfd).use { r ->
                            r.openPage(page).use { p ->
                                val rect = RectF(region).apply {
                                    inset(-6f, -6f)
                                    intersect(0f, 0f, p.width.toFloat(), p.height.toFloat())
                                }
                                val scale = (1200f / maxOf(rect.width(), rect.height())).coerceIn(1f, 4f)
                                val bmp = Bitmap.createBitmap(
                                    (rect.width() * scale).toInt().coerceAtLeast(1),
                                    (rect.height() * scale).toInt().coerceAtLeast(1),
                                    Bitmap.Config.ARGB_8888,
                                )
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                val m = Matrix().apply {
                                    setScale(scale, scale)
                                    postTranslate(-rect.left * scale, -rect.top * scale)
                                }
                                p.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val canvas = Canvas(bmp)
                                canvas.concat(m)
                                ContentPainter(store.imagesDir(doc.id)).draw(canvas, content)
                                File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            }
                        }
                    }
                    Card(id, front.trim(), name, back.trim(), page, due = System.currentTimeMillis())
                }
            }
            result.onSuccess {
                saveCards(doc, cards + it)
                message = "Kart eklendi (⋮ → Bilgi kartları)"
            }.onFailure {
                ErrorLog.record("Kart oluşturulamadı", it)
                message = "Kart oluşturulamadı"
            }
        }
    }

    fun reviewCard(doc: DocRef, card: Card, known: Boolean) {
        val updated = Flashcards.review(card, known)
        saveCards(doc, cards.map { if (it.id == card.id) updated else it })
    }

    fun deleteCard(doc: DocRef, card: Card) {
        saveCards(doc, cards.filter { it.id != card.id })
        card.frontImage?.let { name ->
            viewModelScope.launch(Dispatchers.IO) { File(Flashcards.imagesDir(getApplication(), doc.id), name).delete() }
        }
    }

    fun cardImage(doc: DocRef, card: Card): File? =
        card.frontImage?.let { File(Flashcards.imagesDir(getApplication(), doc.id), it) }

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
        studyPause()
        viewModelScope.launch {
            // Başka belge açıkken (kısayol, hızlı not) önceki belgenin son notları kaybolmasın.
            saveJob?.cancel()
            flushInk()
            withContext(Dispatchers.IO) { repo.rememberLinked(doc) }
            val (resolved, content) = withContext(Dispatchers.IO) { repo.resolveForOpen(doc) to store.load(doc.id) }
            bookmarks = withContext(Dispatchers.IO) { store.loadBookmarks(doc.id) }
            attachInk(resolved, content)
            openDoc = resolved
            studyResume()
            pushShortcut(doc)
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

    /** Gece okuma: görüntüleyicide renkler ters çevrilir. */
    var nightReading by mutableStateOf(prefs.getBoolean("nightReading", false))
        private set

    fun toggleNightReading() {
        nightReading = !nightReading
        prefs.edit().putBoolean("nightReading", nightReading).apply()
    }

    /** Başka bir PDF'in bütün sayfalarını [at] konumuna ekler (notlar kayar). */
    fun insertPdf(doc: DocRef, at: Int, source: Uri) =
        changePages(doc, focusPage = at, "PDF eklenemedi") { file ->
            val n = PdfPages.insertFrom(getApplication(), file, at, source)
            ({ i: Int -> if (i >= at) i + n else i })
        }

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

    fun duplicatePage(doc: DocRef, at: Int) =
        changePages(doc, focusPage = at + 1, "Sayfa çoğaltılamadı", afterRemap = { store.copyPage(doc.id, at, at + 1) }) { file ->
            PdfPages.duplicate(getApplication(), file, at)
            ({ i: Int -> if (i > at) i + 1 else i })
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
    private fun changePages(
        doc: DocRef,
        focusPage: Int,
        error: String,
        afterRemap: (() -> Unit)? = null,
        edit: (File) -> (Int) -> Int?,
    ) {
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
                    afterRemap?.invoke()
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

    /** Hızlı not: tarihli, çizgili yeni defter açar (kısayoldan da). */
    fun quickNote() {
        val name = "Hızlı not " + java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.forLanguageTag("tr")).format(java.util.Date())
        createNotebook(name, PageTemplate.LINED)
    }

    /** Kısayoldan gelen belgeyi aç. */
    fun openById(id: String) {
        viewModelScope.launch {
            val doc = withContext(Dispatchers.IO) { repo.importedDocs() }.find { it.id == id }
            if (doc != null) open(doc) else message = "Belge bulunamadı"
        }
    }

    /** Son açılan belgeyi uygulama simgesinin kısayollarına ekle. */
    private fun pushShortcut(doc: DocRef) {
        if (!doc.imported) return
        runCatching {
            val app = getApplication<Application>()
            val intent = Intent(app, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_DOC)
                .putExtra(MainActivity.EXTRA_DOC_ID, doc.id)
            val info = ShortcutInfoCompat.Builder(app, "doc_${doc.id}")
                .setShortLabel(doc.title.take(24).ifEmpty { "Belge" })
                .setLongLabel(doc.title.take(48).ifEmpty { "Belge" })
                .setIcon(IconCompat.createWithResource(app, R.drawable.ic_shortcut_doc))
                .setIntent(intent)
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(app, info)
        }
    }

    /** Boş defter: şablonlu A4 sayfalardan oluşan yeni bir PDF olarak kütüphaneye eklenir ve açılır. */
    fun createNotebook(title: String, template: PageTemplate, size: PaperSize = PaperSize.A4, landscape: Boolean = false) {
        viewModelScope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    repo.createDocument(title.trim().ifEmpty { "Defter" }, currentFolder) { file ->
                        PdfPages.createNotebook(getApplication(), file, template, pages = 1, size = size, landscape = landscape)
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
        studyPause()
        fullScreen = false
        stopRecording(null)
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
    fun export(doc: DocRef, pages: IntRange? = null, onReady: (File) -> Unit) {
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
                    val suffix = when {
                        pages == null -> ""
                        pages.first == pages.last -> " s${pages.first + 1}"
                        else -> " s${pages.first + 1}-${pages.last + 1}"
                    }
                    val target = File(dir, "$name (notlu)$suffix.pdf")
                    PdfExporter.export(getApplication(), source, snapshot, store.imagesDir(doc.id), target, pages)
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
            runCatching { ShortcutManagerCompat.removeDynamicShortcuts(getApplication(), listOf("doc_${doc.id}")) }
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
        recorder?.stop()
        recorder = null
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
