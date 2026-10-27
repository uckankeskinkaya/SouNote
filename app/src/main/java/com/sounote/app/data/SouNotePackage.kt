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

    fun write(pdf: InputStream, inkDir: File, title: String, target: File, sender: String = "") {
        // PDF'in özeti: alan kişide aynı belge varsa notlar onunkine birleştirilebilsin
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val pdfBytes = java.io.ByteArrayOutputStream()
        pdf.use { it.copyTo(pdfBytes) }
        val bytes = pdfBytes.toByteArray()
        digest.update(bytes)
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(
                JSONObject().put("format", FORMAT).put("app", "SouNote").put("title", title)
                    .put("created", System.currentTimeMillis()).put("pdfSha256", hex(digest.digest()))
                    .put("sender", sender).toString().toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("document.pdf"))
            zip.write(bytes)
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

    /** Açılmış (geçici klasöre çıkarılmış) paket; [finish] ya da [discard] ile kapanır. */
    class Extracted(val title: String, val sha256: String, val sender: String, val work: File) {
        val pdf get() = File(work, "document.pdf")
        val ink get() = File(work, "ink")
        fun discard() {
            work.deleteRecursively()
        }
    }

    /** Paketi geçici klasöre çıkarır ve denetler (yollar, boyut, PDF). */
    fun extract(context: Context, uri: Uri): Extracted {
        val work = File(context.cacheDir, "package-in-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            var title = "Paylaşılan belge"
            var sender = ""
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
                sender = o.optString("sender").trim()
                hasManifest = true
            }
            val pdf = File(work, "document.pdf")
            if (!hasManifest || !pdf.exists()) throw IOException("Bu bir SouNote paketi değil")
            PdfFiles.validate(pdf)
            return Extracted(title, sha256(pdf), sender, work)
        } catch (e: Exception) {
            work.deleteRecursively()
            throw e
        }
    }

    /** Paketi yeni belge olarak ekler: PDF [createDoc] ile kütüphaneye (yeni kimlik), notlar ink/<yeni kimlik>/ altına. */
    fun <T> finish(context: Context, ext: Extracted, createDoc: (title: String, write: (File) -> Unit) -> T, docId: (T) -> String): T {
        try {
            val doc = createDoc(ext.title) { target -> ext.pdf.copyTo(target, overwrite = true) }
            if (ext.ink.isDirectory) ext.ink.copyRecursively(File(File(context.filesDir, "ink"), docId(doc)), overwrite = true)
            return doc
        } finally {
            ext.discard()
        }
    }

    fun sha256(file: File): String {
        val d = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                d.update(buf, 0, n)
            }
        }
        return hex(d.digest())
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
}
