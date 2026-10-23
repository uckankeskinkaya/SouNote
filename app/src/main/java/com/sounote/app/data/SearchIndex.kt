package com.sounote.app.data

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.util.Locale

/**
 * Genel arama için PDF metinleri: filesDir/search/<belge>-<değişim zamanı>.txt, sayfalar \u000C ile ayrılır.
 * Belge değişince (dosya zamanı) yeniden çıkarılır; taranmış (görüntü) PDF'lerde metin olmaz.
 */
class SearchIndex(private val context: Context) {
    private val dir = File(context.filesDir, "search").apply { mkdirs() }

    private fun file(docId: String, stamp: Long) = File(dir, "$docId-$stamp.txt")

    fun has(docId: String, stamp: Long) = file(docId, stamp).exists()

    /** Belgenin sayfa metinlerini çıkarıp saklar (yavaş; arka planda çağır). */
    fun build(docId: String, stamp: Long, source: Uri) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val doc = if (source.scheme == "file") {
            PDDocument.load(File(requireNotNull(source.path)))
        } else {
            context.contentResolver.openInputStream(source)?.use { PDDocument.load(it) } ?: return
        }
        val pages = doc.use { d ->
            val stripper = PDFTextStripper()
            (1..d.numberOfPages).map { p ->
                stripper.startPage = p
                stripper.endPage = p
                runCatching { stripper.getText(d) }.getOrDefault("").replace(Regex("\\s+"), " ").trim()
            }
        }
        dir.listFiles { f -> f.name.startsWith("$docId-") }?.forEach { it.delete() }
        file(docId, stamp).writeText(pages.joinToString("\u000C"))
    }

    fun pages(docId: String, stamp: Long): List<String> =
        runCatching { file(docId, stamp).readText().split('\u000C') }.getOrDefault(emptyList())

    /** Silinen belgelerin metinlerini temizler. */
    fun prune(keep: Set<String>) {
        dir.listFiles()?.forEach { f -> if (f.name.substringBeforeLast('-') !in keep) f.delete() }
    }

    companion object {
        private val TR = Locale.forLanguageTag("tr")

        fun norm(s: String) = s.lowercase(TR)

        /** [text] içinde [q] geçen yerin çevresinden kısa alıntı (yoksa null). */
        fun snippet(text: String, q: String, radius: Int = 60): String? {
            val i = norm(text).indexOf(q)
            if (i < 0) return null
            val from = (i - radius).coerceAtLeast(0)
            val to = (i + q.length + radius).coerceAtMost(text.length)
            return (if (from > 0) "…" else "") + text.substring(from, to).trim() + (if (to < text.length) "…" else "")
        }
    }
}
