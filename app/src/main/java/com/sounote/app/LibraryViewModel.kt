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
import com.sounote.app.ink.InkDocument
import com.sounote.app.ink.ToolState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = LibraryRepository(app)
    private val prefs = app.getSharedPreferences("viewer", Context.MODE_PRIVATE)
    private val toolPrefs = app.getSharedPreferences("tools", Context.MODE_PRIVATE)

    /** Açık kaldığı sürece belgelerin çizimleri. 4. adımda diske kaydedilecek. */
    private val inks = HashMap<String, InkDocument>()

    var tools by mutableStateOf(ToolState.load(toolPrefs))
        private set

    fun updateTools(state: ToolState) {
        tools = state
        state.save(toolPrefs)
    }

    fun inkFor(doc: DocRef): InkDocument = inks.getOrPut(doc.id) { InkDocument() }

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

    fun consumeMessage() {
        message = null
    }

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
                openDoc = last
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
            withContext(Dispatchers.IO) { repo.delete(doc.id) }
            inks.remove(doc.id)
            prefs.edit().remove(pageKey(doc)).apply()
            imported = withContext(Dispatchers.IO) { repo.importedDocs() }
        }
    }

    fun open(doc: DocRef) {
        openDoc = doc
    }

    fun closeDoc() {
        openDoc = null
        refresh()
    }

    fun lastPage(doc: DocRef) = prefs.getInt(pageKey(doc), 0)

    fun saveLastPage(doc: DocRef, page: Int) {
        prefs.edit().putInt(pageKey(doc), page).apply()
    }

    private fun pageKey(doc: DocRef) = "page_${doc.id}"

    companion object {
        private const val TAG = "LibraryViewModel"
    }
}
