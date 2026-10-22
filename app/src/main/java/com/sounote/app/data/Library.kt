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

    private class ImportedEntry(val id: String, var title: String, val addedAt: Long)
    private class LinkedEntry(val uri: String, val name: String)

    private val docsDir = File(context.filesDir, "docs").apply { mkdirs() }
    private val libraryFile = File(context.filesDir, "library.json")
    private val lock = Any()
    private val imported = mutableListOf<ImportedEntry>()
    private val linked = mutableListOf<LinkedEntry>()

    init {
        load()
    }

    fun importedDocs(): List<DocRef> = synchronized(lock) {
        imported.sortedByDescending { it.addedAt }.mapNotNull { e ->
            val file = fileFor(e.id)
            if (file.exists()) DocRef(e.id, e.title, Uri.fromFile(file), file.lastModified(), imported = true) else null
        }
    }

    fun linkedFolders(): List<FolderRef> = synchronized(lock) {
        linked.map { e ->
            val tree = Uri.parse(e.uri)
            FolderRef(tree, DocumentsContract.getTreeDocumentId(tree), e.name)
        }
    }

    /** Dosyayı uygulamanın içine kopyalar; açılamayan (bozuk/şifreli) PDF'i reddeder. */
    fun importPdf(source: Uri): DocRef {
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
            imported += ImportedEntry(id, title, addedAt)
            save()
        }
        return DocRef(id, title, Uri.fromFile(target), target.lastModified(), imported = true)
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
                    imported += ImportedEntry(o.getString("id"), o.getString("title"), o.getLong("addedAt"))
                }
            }
            json.optJSONArray("folders")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    linked += LinkedEntry(o.getString("uri"), o.getString("name"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "library.json okunamadı", e)
        }
    }

    private fun save() {
        val json = JSONObject()
        json.put("imported", JSONArray().apply {
            imported.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("addedAt", it.addedAt)) }
        })
        json.put("folders", JSONArray().apply {
            linked.forEach { put(JSONObject().put("uri", it.uri).put("name", it.name)) }
        })
        // Önce geçici dosyaya yaz, sonra değiştir: yazarken kapanırsa eski kayıt bozulmaz.
        val tmp = File(libraryFile.path + ".tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(libraryFile)) throw IOException("library.json kaydedilemedi")
    }

    companion object {
        private const val TAG = "LibraryRepository"
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
