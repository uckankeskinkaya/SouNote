package com.sounote.app

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sounote.app.data.DocRef
import com.sounote.app.data.FolderListing
import com.sounote.app.data.FolderRef
import com.sounote.app.data.LibraryRepository
import com.sounote.app.ink.Images
import com.sounote.app.ink.InkDocument
import com.sounote.app.ink.InkStore
import com.sounote.app.ink.PageContent
import com.sounote.app.ink.ToolState
import com.sounote.app.pdf.PdfExporter
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
                Log.w(TAG, "Kopya alınamadı", e)
            }
            snapshot.forEach { (page, content) ->
                try {
                    store.save(open.doc.id, page, content)
                } catch (e: Exception) {
                    Log.e(TAG, "Notlar kaydedilemedi", e)
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
                Log.e(TAG, "Resim eklenemedi", it)
                message = "Resim eklenemedi"
            }
        }
    }

    /** Doldurulan formun yazılacağı dosya. */
    fun formSaveTarget(doc: DocRef): File = repo.docFile(doc.id)

    fun open(doc: DocRef) {
        viewModelScope.launch {
            val (resolved, content) = withContext(Dispatchers.IO) { repo.resolveForOpen(doc) to store.load(doc.id) }
            attachInk(resolved, content)
            openDoc = resolved
        }
    }

    fun closeDoc() {
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
                Log.e(TAG, "Dışa aktarılamadı", it)
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
            loaded = true
            path.lastOrNull()?.let { loadListing(it) }
        }
    }

    private suspend fun loadListing(folder: FolderRef) {
        try {
            val result = withContext(Dispatchers.IO) { repo.listFolder(folder) }
            if (path.lastOrNull() == folder) listing = result
        } catch (e: Exception) {
            Log.w(TAG, "Klasör okunamadı", e)
            message = "\"${folder.name}\" klasörüne erişilemiyor. Silinmiş ya da izni kaldırılmış olabilir."
            path = emptyList()
        }
    }

    fun openFolder(folder: FolderRef) {
        path = path + folder
        listing = FolderListing(emptyList(), emptyList())
        viewModelScope.launch { loadListing(folder) }
    }

    fun navigateUp() {
        if (path.isEmpty()) return
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
                    last = withContext(Dispatchers.IO) { repo.importPdf(uri) }
                } catch (e: Exception) {
                    Log.w(TAG, "İçe aktarılamadı: $uri", e)
                    failed++
                }
            }
            busy = false
            imported = withContext(Dispatchers.IO) { repo.importedDocs() }
            val ok = uris.size - failed
            message = when {
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
                Log.w(TAG, "Klasör bağlanamadı", e)
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
        private const val TAG = "LibraryViewModel"
        private const val SAVE_DELAY_MS = 800L
    }
}
