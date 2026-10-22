package com.sounote.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import android.util.Log
import com.sounote.app.pdf.PdfFiles
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/** Kütüphanede görünen tek bir PDF. İçe aktarılanlar file://, bağlı klasördekiler content:// adreslidir. */
data class DocRef(
    val id: String,
    val title: String,
    val uri: Uri,
    val lastModified: Long,
    val imported: Boolean,
    /** Kütüphanede bulunduğu kendi klasörü (içe aktarılanlar için); null = ana sayfa. */
    val folderId: String? = null,
    val addedAt: Long = 0L,
)

/** Kütüphanede kullanıcının oluşturduğu klasör (içe aktarılan belgeler için). */
data class UserFolder(
    val id: String,
    val name: String,
    val parentId: String?,
    val color: Int,
)

/** Bağlı bir klasör ya da onun alt klasörü. */
data class FolderRef(
    val treeUri: Uri,
    val documentId: String,
    val name: String,
)

data class FolderListing(
    val folders: List<FolderRef>,
    val docs: List<DocRef>,
)

/**
 * Kütüphane kaydı: filesDir/library.json + filesDir/docs/<id>.pdf.
 * Bağlı klasörler kopyalanmaz; her açılışta yerinde taranır.
 */
class LibraryRepository(private val context: Context) {

    private class ImportedEntry(val id: String, var title: String, val addedAt: Long, var folderId: String? = null)
    private class FolderEntry(val id: String, var name: String, var parentId: String?, var color: Int)
    private class LinkedEntry(val uri: String, val name: String)

    private val docsDir = File(context.filesDir, "docs").apply { mkdirs() }
    private val libraryFile = File(context.filesDir, "library.json")
    private val lock = Any()
    private val imported = mutableListOf<ImportedEntry>()
    private val linked = mutableListOf<LinkedEntry>()
    private val userFolders = mutableListOf<FolderEntry>()

    init {
        load()
    }

    /** Yedekten geri yükledikten sonra kaydı diskten yeniden okur. */
    fun reload() = synchronized(lock) {
        imported.clear()
        linked.clear()
        userFolders.clear()
        load()
    }

    fun importedDocs(): List<DocRef> = synchronized(lock) {
        imported.sortedByDescending { it.addedAt }.mapNotNull { e ->
            val file = fileFor(e.id)
            if (file.exists()) {
                DocRef(e.id, e.title, Uri.fromFile(file), file.lastModified(), imported = true, folderId = e.folderId, addedAt = e.addedAt)
            } else {
                null
            }
        }
    }

    // ---- Kullanıcı klasörleri ----

    fun userFolders(): List<UserFolder> = synchronized(lock) {
        userFolders.map { UserFolder(it.id, it.name, it.parentId, it.color) }
    }

    fun createFolder(name: String, parentId: String?, color: Int): UserFolder = synchronized(lock) {
        val e = FolderEntry(UUID.randomUUID().toString(), name, parentId, color)
        userFolders += e
        save()
        UserFolder(e.id, e.name, e.parentId, e.color)
    }

    fun updateFolder(id: String, name: String, color: Int) = synchronized(lock) {
        userFolders.find { it.id == id }?.let {
            it.name = name
            it.color = color
            save()
        }
    }

    /** Klasörü siler; içindeki belgeler ve alt klasörler bir üst klasöre geçer (hiçbir şey kaybolmaz). */
    fun deleteFolder(id: String) = synchronized(lock) {
        val folder = userFolders.find { it.id == id } ?: return@synchronized
        imported.forEach { if (it.folderId == id) it.folderId = folder.parentId }
        userFolders.forEach { if (it.parentId == id) it.parentId = folder.parentId }
        userFolders.remove(folder)
        save()
    }

    fun moveDoc(docId: String, folderId: String?) = synchronized(lock) {
        imported.find { it.id == docId }?.let {
            it.folderId = folderId
            save()
        }
    }

    /** Klasörü başka bir klasörün içine taşır (kendi altına taşınamaz). */
    fun moveFolder(id: String, parentId: String?) = synchronized(lock) {
        var p = parentId
        while (p != null) {
            if (p == id) return@synchronized
            p = userFolders.find { it.id == p }?.parentId
        }
        userFolders.find { it.id == id }?.let {
            it.parentId = parentId
            save()
        }
    }

    fun linkedFolders(): List<FolderRef> = synchronized(lock) {
        linked.map { e ->
            val tree = Uri.parse(e.uri)
            FolderRef(tree, DocumentsContract.getTreeDocumentId(tree), e.name)
        }
    }

    /** Dosyayı uygulamanın içine kopyalar; açılamayan (bozuk/şifreli) PDF'i reddeder. */
    fun importPdf(source: Uri, folderId: String? = null): DocRef {
        val title = queryDisplayName(source)?.let(::stripPdfExtension)?.ifBlank { null } ?: "Belge"
        val id = UUID.randomUUID().toString()
        val tmp = File(docsDir, "$id.tmp")
        val target = fileFor(id)
        try {
            val input = context.contentResolver.openInputStream(source) ?: throw IOException("Dosya açılamadı")
            input.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
            PdfFiles.validate(tmp)
            if (!tmp.renameTo(target)) throw IOException("Dosya kaydedilemedi")
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
        val addedAt = System.currentTimeMillis()
        synchronized(lock) {
            imported += ImportedEntry(id, title, addedAt, folderId)
            save()
        }
        return DocRef(id, title, Uri.fromFile(target), target.lastModified(), imported = true, folderId = folderId, addedAt = addedAt)
    }

    /** Belgenin uygulama içindeki PDF dosyası: içe aktarılanın kendisi, bağlı klasördekinin kopyası. */
    fun docFile(id: String) = fileFor(id)

    /**
     * Bağlı klasördeki belgeye ilk not alınınca PDF'in kopyasını uygulamaya alır.
     * Böylece dosya klasörden silinse ya da değişse bile notlar sayfalarıyla birlikte kalır.
     */
    fun ensureCopy(doc: DocRef) {
        if (doc.imported) return
        val target = fileFor(doc.id)
        if (target.exists()) return
        val tmp = File(docsDir, "${doc.id}.tmp")
        try {
            val input = context.contentResolver.openInputStream(doc.uri) ?: throw IOException("Dosya açılamadı")
            input.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
            if (!tmp.renameTo(target)) throw IOException("Kopya kaydedilemedi")
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    /**
     * Bağlı klasördeki belgenin adını ve kaynağını not et (docs/<id>.json). Kaynak dosya sonradan
     * silinir ya da klasör bağlantısı kaldırılırsa notlu kopya kütüphanede yine bulunabilsin diye.
     */
    fun rememberLinked(doc: DocRef) {
        if (doc.imported) return
        val meta = File(docsDir, "${doc.id}.json")
        if (meta.exists()) return
        runCatching { meta.writeText(JSONObject().put("title", doc.title).put("source", doc.uri.toString()).toString()) }
    }

    /** Kaynağı artık bulunamayan (silinmiş/bağlantısı kaldırılmış) ama notlu kopyası olan belgeler. */
    fun orphanedCopies(): List<DocRef> {
        val metas = docsDir.listFiles { f -> f.name.endsWith(".json") } ?: return emptyList()
        val adopted = synchronized(lock) { imported.map { it.id }.toSet() }
        return metas.mapNotNull { meta ->
            val id = meta.name.removeSuffix(".json")
            val copy = fileFor(id)
            if (id in adopted || !copy.exists()) return@mapNotNull null
            val json = runCatching { JSONObject(meta.readText()) }.getOrNull() ?: return@mapNotNull null
            val source = Uri.parse(json.optString("source"))
            val exists = runCatching {
                context.contentResolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { it.moveToFirst() } == true
            }.getOrDefault(false)
            if (exists) null else DocRef(id, json.optString("title", "Belge"), Uri.fromFile(copy), copy.lastModified(), imported = false)
        }
    }

    /** Kaynağı kaybolan notlu kopyayı normal içe aktarılmış belge yap. */
    fun adoptCopy(doc: DocRef) {
        synchronized(lock) {
            if (imported.none { it.id == doc.id }) {
                imported += ImportedEntry(doc.id, doc.title, System.currentTimeMillis())
                save()
            }
        }
        File(docsDir, "${doc.id}.json").delete()
    }

    /** Bağlı klasör belgesinin kopyasını siler (notlar ayrıca silinmeli). */
    fun deleteCopy(id: String) {
        fileFor(id).delete()
        File(docsDir, "$id.json").delete()
    }

    /** Açarken: bağlı klasördeki belgenin kopyası varsa onu kullan (notlar ona göre). */
    fun resolveForOpen(doc: DocRef): DocRef {
        if (doc.imported) return doc
        val copy = fileFor(doc.id)
        return if (copy.exists()) doc.copy(uri = Uri.fromFile(copy), lastModified = copy.lastModified()) else doc
    }

    /** Uygulama içinde oluşturulan yeni PDF (ör. boş defter): [write] dosyayı yazar. */
    fun createDocument(title: String, folderId: String? = null, write: (File) -> Unit): DocRef {
        val id = UUID.randomUUID().toString()
        val target = fileFor(id)
        try {
            write(target)
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        val addedAt = System.currentTimeMillis()
        synchronized(lock) {
            imported += ImportedEntry(id, title, addedAt, folderId)
            save()
        }
        return DocRef(id, title, Uri.fromFile(target), target.lastModified(), imported = true, folderId = folderId, addedAt = addedAt)
    }

    fun rename(id: String, title: String) = synchronized(lock) {
        imported.find { it.id == id }?.let {
            it.title = title
            save()
        }
    }

    fun delete(id: String) = synchronized(lock) {
        if (imported.removeAll { it.id == id }) {
            fileFor(id).delete()
            save()
        }
    }

    fun linkFolder(treeUri: Uri): FolderRef {
        context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val name = queryDisplayName(DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)) ?: "Klasör"
        synchronized(lock) {
            if (linked.none { it.uri == treeUri.toString() }) {
                linked += LinkedEntry(treeUri.toString(), name)
                save()
            }
        }
        return FolderRef(treeUri, docId, name)
    }

    fun unlinkFolder(treeUri: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        synchronized(lock) {
            if (linked.removeAll { it.uri == treeUri.toString() }) save()
        }
    }

    /** Klasörün o anki içeriği: alt klasörler ve PDF'ler, doğal sırada ("Hafta 2" < "Hafta 10"). */
    fun listFolder(folder: FolderRef): FolderListing {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folder.treeUri, folder.documentId)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
        )
        val folders = mutableListOf<FolderRef>()
        val docs = mutableListOf<DocRef>()
        val cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
            ?: throw IOException("Klasör okunamadı")
        cursor.use { c ->
            while (c.moveToNext()) {
                val docId = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                if (name.startsWith(".")) continue
                val mime = c.getString(2) ?: ""
                val modified = if (c.isNull(3)) 0L else c.getLong(3)
                when {
                    mime == Document.MIME_TYPE_DIR -> folders += FolderRef(folder.treeUri, docId, name)
                    mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true) -> {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(folder.treeUri, docId)
                        docs += DocRef(linkedId(uri), stripPdfExtension(name), uri, modified, imported = false)
                    }
                }
            }
        }
        return FolderListing(
            folders.sortedWith(compareBy(NaturalOrder) { it.name }),
            docs.sortedWith(compareBy(NaturalOrder) { it.title }),
        )
    }

    private fun fileFor(id: String) = File(docsDir, "$id.pdf")

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun load() {
        if (!libraryFile.exists()) return
        try {
            val json = JSONObject(libraryFile.readText())
            json.optJSONArray("imported")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    imported += ImportedEntry(
                        o.getString("id"), o.getString("title"), o.getLong("addedAt"),
                        o.optString("folder").ifEmpty { null },
                    )
                }
            }
            json.optJSONArray("folders")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    linked += LinkedEntry(o.getString("uri"), o.getString("name"))
                }
            }
            json.optJSONArray("userFolders")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    userFolders += FolderEntry(
                        o.getString("id"), o.getString("name"),
                        o.optString("parent").ifEmpty { null }, o.optInt("color", DEFAULT_FOLDER_COLOR),
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "library.json okunamadı", e)
        }
    }

    private fun save() {
        val json = JSONObject()
        json.put("imported", JSONArray().apply {
            imported.forEach {
                put(JSONObject().put("id", it.id).put("title", it.title).put("addedAt", it.addedAt).put("folder", it.folderId ?: ""))
            }
        })
        json.put("folders", JSONArray().apply {
            linked.forEach { put(JSONObject().put("uri", it.uri).put("name", it.name)) }
        })
        json.put("userFolders", JSONArray().apply {
            userFolders.forEach {
                put(JSONObject().put("id", it.id).put("name", it.name).put("parent", it.parentId ?: "").put("color", it.color))
            }
        })
        // Önce geçici dosyaya yaz, sonra değiştir: yazarken kapanırsa eski kayıt bozulmaz.
        val tmp = File(libraryFile.path + ".tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(libraryFile)) throw IOException("library.json kaydedilemedi")
    }

    companion object {
        private const val TAG = "LibraryRepository"
        const val DEFAULT_FOLDER_COLOR = 0xFF5B7FD6.toInt()
        private val PDF_EXT = Regex("\\.pdf$", RegexOption.IGNORE_CASE)

        fun stripPdfExtension(name: String) = name.replace(PDF_EXT, "")

        /** Bağlı klasördeki dosyanın kimliği: adresinden türetilir, böylece her taramada aynı kalır. */
        fun linkedId(uri: Uri): String {
            val digest = MessageDigest.getInstance("SHA-1").digest(uri.toString().toByteArray())
            return "l" + digest.take(10).joinToString("") { "%02x".format(it) }
        }

        fun openFileDescriptor(context: Context, uri: Uri): ParcelFileDescriptor =
            if (uri.scheme == "file") {
                ParcelFileDescriptor.open(File(requireNotNull(uri.path)), ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                context.contentResolver.openFileDescriptor(uri, "r") ?: throw IOException("Dosya açılamadı")
            }
    }
}

/** "Hafta 2" < "Hafta 10" sıralaması, büyük/küçük harf duyarsız. */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var ei = i
                while (ei < a.length && a[ei].isDigit()) ei++
                var ej = j
                while (ej < b.length && b[ej].isDigit()) ej++
                val na = a.substring(i, ei).trimStart('0')
                val nb = b.substring(j, ej).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
                i = ei
                j = ej
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
