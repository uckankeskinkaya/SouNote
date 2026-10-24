package com.sounote.app.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import com.sounote.app.pdf.PdfFiles
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Notlu belgeyi arkadaşa göndermek için paket (.sounote = zip):
 * manifest.json + document.pdf + ink/ (sayfa notları, resimler, bilgi kartları).
 * Ses kayıtları büyük olduğu için pakete girmez. Alan kişi SouNote'ta açınca notlar düzenlenebilir olarak gelir.
 */
object SouNotePackage {
    const val EXTENSION = "sounote"
    private const val FORMAT = 1
    private const val MAX_ENTRY = 200L * 1024 * 1024
    private const val MAX_TOTAL = 500L * 1024 * 1024

    fun write(pdf: InputStream, inkDir: File, title: String, target: File) {
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(
                JSONObject().put("format", FORMAT).put("app", "SouNote").put("title", title)
                    .put("created", System.currentTimeMillis()).toString().toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("document.pdf"))
            pdf.use { it.copyTo(zip) }
            zip.closeEntry()
            if (inkDir.isDirectory) {
                inkDir.walkTopDown().filter { it.isFile }.forEach { f ->
                    val rel = f.relativeTo(inkDir).invariantSeparatorsPath
                    if (rel.startsWith("audio/")) return@forEach
                    zip.putNextEntry(ZipEntry("ink/$rel"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Dosyanın SouNote paketi olup olmadığı (zip imzası + adı). */
    fun looksLikePackage(context: Context, uri: Uri, name: String?): Boolean {
        if (name?.endsWith(".$EXTENSION", ignoreCase = true) == true) return true
        val head = runCatching { context.contentResolver.openInputStream(uri)?.use { s -> ByteArray(2).also { s.read(it) } } }.getOrNull()
        return head != null && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
    }

    /**
     * Paketi açar: PDF'i [createDoc] ile kütüphaneye ekletir (yeni kimlik), notları ink/<yeni kimlik>/ altına çıkarır.
     * Zip içindeki yollar denetlenir; ink klasörünün dışına yazılamaz.
     */
    fun <T> read(
        context: Context,
        uri: Uri,
        createDoc: (title: String, write: (File) -> Unit) -> T,
        docId: (T) -> String,
    ): T {
        val work = File(context.cacheDir, "package-in").apply { deleteRecursively(); mkdirs() }
        try {
            var title = "Paylaşılan belge"
            var hasManifest = false
            var total = 0L
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Dosya açılamadı")
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    val name = e.name.replace('\\', '/')
                    if (name.contains("..") || name.startsWith("/")) throw IOException("Paket bozuk")
                    val out = when {
                        name == "manifest.json" -> File(work, "manifest.json")
                        name == "document.pdf" -> File(work, "document.pdf")
                        name.startsWith("ink/") -> File(work, name)
                        else -> null
                    } ?: continue
                    out.parentFile?.mkdirs()
                    var size = 0L
                    out.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = zip.read(buf)
                            if (n < 0) break
                            size += n
                            total += n
                            // Sıkıştırma bombasına karşı: tek dosya ve toplam boyut sınırı
                            if (size > MAX_ENTRY || total > MAX_TOTAL) throw IOException("Paket çok büyük")
                            o.write(buf, 0, n)
                        }
                    }
                }
            }
            File(work, "manifest.json").takeIf { it.exists() }?.let { m ->
                val o = JSONObject(m.readText())
                if (o.optInt("format", 0) > FORMAT) throw IOException("Bu paket daha yeni bir SouNote sürümüyle hazırlanmış; uygulamayı güncelle")
                title = o.optString("title").ifBlank { title }
                hasManifest = true
            }
            val pdf = File(work, "document.pdf")
            if (!hasManifest || !pdf.exists()) throw IOException("Bu bir SouNote paketi değil")
            PdfFiles.validate(pdf)
            val doc = createDoc(title) { target -> pdf.copyTo(target, overwrite = true) }
            val ink = File(work, "ink")
            if (ink.isDirectory) {
                val dest = File(File(context.filesDir, "ink"), docId(doc))
                ink.copyRecursively(dest, overwrite = true)
            }
            return doc
        } finally {
            work.deleteRecursively()
        }
    }
}
