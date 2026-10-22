package com.sounote.app.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import java.io.File
import java.io.IOException

/** Boş sayfa şablonları. */
enum class PageTemplate(val label: String) {
    BLANK("Düz"),
    LINED("Çizgili"),
    GRID("Kareli"),
    DOTTED("Noktalı"),
    /** Cornell not düzeni: başlık, solda anahtar kelime sütunu, altta özet alanı. */
    CORNELL("Cornell"),
    /** 1 mm ince, 5 mm ve 10 mm kalın çizgili kâğıt. */
    MILLIMETRIC("Milimetrik"),
    /** Nota kâğıdı: 5 çizgili porteler. */
    MUSIC("Müzik"),
    /** Üçgen ızgara noktaları (izometrik çizim). */
    ISOMETRIC("İzometrik"),
}

/** Yeni defter kâğıt boyutu (dikey, nokta cinsinden). */
enum class PaperSize(val label: String, val width: Float, val height: Float) {
    A4("A4", 595f, 842f),
    A5("A5", 420f, 595f),
    LETTER("Letter", 612f, 792f),
}

/**
 * PDF'in sayfa yapısını değiştirir (PDFBox): boş sayfa ekleme, silme, taşıma, yeni defter.
 * Dosya önce geçici dosyaya yazılır, sonra eskisinin yerine konur.
 */
object PdfPages {
    private const val MM = 72f / 25.4f

    /** [at] konumuna (ondan önceye) şablonlu boş sayfa ekler; boyut komşu sayfayla aynı. */
    fun insertBlank(context: Context, file: File, at: Int, template: PageTemplate) = edit(context, file) { doc ->
        val count = doc.numberOfPages
        val neighbor = doc.getPage((if (at > 0) at - 1 else 0).coerceAtMost(count - 1))
        val box = neighbor.cropBox
        val rotated = neighbor.rotation % 180 != 0
        // Ekranda görünen boyutta, döndürülmemiş sayfa
        val size = if (rotated) PDRectangle(box.height, box.width) else PDRectangle(box.width, box.height)
        val page = PDPage(size)
        if (at >= count) doc.pages.add(page) else doc.pages.insertBefore(page, doc.getPage(at))
        drawTemplate(doc, page, template)
    }

    fun delete(context: Context, file: File, at: Int) = edit(context, file) { doc ->
        if (doc.numberOfPages <= 1) throw IOException("Son sayfa silinemez")
        doc.removePage(at)
    }

    /** Başka bir PDF'in sayfalarını [at] konumuna ekler; eklenen sayfa sayısını döndürür. */
    fun insertFrom(context: Context, file: File, at: Int, source: android.net.Uri): Int {
        PDFBoxResourceLoader.init(context.applicationContext)
        val src = context.contentResolver.openInputStream(source)?.use { PDDocument.load(it) }
            ?: throw IOException("PDF açılamadı")
        var added = 0
        src.use {
            edit(context, file) { doc ->
                val imported = (0 until src.numberOfPages).map { doc.importPage(src.getPage(it)) } // sona eklenir
                imported.forEach { doc.removePage(it) }
                val position = at.coerceIn(0, doc.numberOfPages)
                imported.forEachIndexed { k, page ->
                    val index = position + k
                    if (index >= doc.numberOfPages) doc.pages.add(page) else doc.pages.insertBefore(page, doc.getPage(index))
                }
                added = imported.size
            }
        }
        return added
    }

    /** [at] sayfasının bir kopyasını hemen arkasına ekler. */
    fun duplicate(context: Context, file: File, at: Int) = edit(context, file) { doc ->
        val copy = doc.importPage(doc.getPage(at)) // sona eklenir
        val last = doc.numberOfPages - 1
        doc.removePage(last)
        if (at + 1 >= doc.numberOfPages) doc.pages.add(copy) else doc.pages.insertBefore(copy, doc.getPage(at + 1))
    }

    /** PDF'in kendi içindekiler listesi (bölüm başlıkları → sayfa). */
    class OutlineEntry(val title: String, val level: Int, val page: Int)

    fun outline(context: Context, source: android.net.Uri): List<OutlineEntry> {
        PDFBoxResourceLoader.init(context.applicationContext)
        val doc = if (source.scheme == "file") {
            PDDocument.load(File(requireNotNull(source.path)))
        } else {
            context.contentResolver.openInputStream(source)?.use { PDDocument.load(it) } ?: return emptyList()
        }
        return doc.use { d ->
            val out = ArrayList<OutlineEntry>()
            fun walk(node: com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode, level: Int) {
                for (item in node.children()) {
                    if (out.size > 2000) return
                    val page = runCatching { item.findDestinationPage(d)?.let { d.pages.indexOf(it) } }.getOrNull() ?: -1
                    out += OutlineEntry(item.title?.trim().orEmpty().ifEmpty { "—" }, level, page)
                    if (level < 6) walk(item, level + 1)
                }
            }
            d.documentCatalog.documentOutline?.let { walk(it, 0) }
            out
        }
    }

    /** [from] sayfasını [to] konumuna taşır (son hal sırasına göre). */
    fun move(context: Context, file: File, from: Int, to: Int) = edit(context, file) { doc ->
        val page = doc.getPage(from)
        doc.removePage(from)
        if (to >= doc.numberOfPages) doc.pages.add(page) else doc.pages.insertBefore(page, doc.getPage(to))
    }

    /** Şablonlu sayfalardan oluşan yeni defter. */
    fun createNotebook(
        context: Context, file: File, template: PageTemplate, pages: Int,
        size: PaperSize = PaperSize.A4, landscape: Boolean = false,
    ) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val rect = if (landscape) PDRectangle(size.height, size.width) else PDRectangle(size.width, size.height)
        PDDocument().use { doc ->
            repeat(pages) {
                val page = PDPage(rect)
                doc.addPage(page)
                drawTemplate(doc, page, template)
            }
            save(doc, file)
        }
    }

    private fun edit(context: Context, file: File, block: (PDDocument) -> Unit) {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(file).use { doc ->
            doc.setAllSecurityToBeRemoved(true)
            block(doc)
            save(doc, file)
        }
    }

    private fun save(doc: PDDocument, file: File) {
        val tmp = File(file.path + ".edit")
        doc.save(tmp)
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IOException("PDF kaydedilemedi")
        }
    }

    /** Şablon çizgileri (PDF koordinatı: sol alt köşe, y yukarı). */
    private fun drawTemplate(doc: PDDocument, page: PDPage, template: PageTemplate) {
        if (template == PageTemplate.BLANK) return
        val w = page.mediaBox.width
        val h = page.mediaBox.height
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            when (template) {
                PageTemplate.LINED -> {
                    val gap = 8f * MM
                    cs.setStrokingColor(0.72f, 0.80f, 0.92f)
                    cs.setLineWidth(0.6f)
                    var y = h - 25f * MM
                    while (y > 12f * MM) {
                        cs.moveTo(0f, y)
                        cs.lineTo(w, y)
                        y -= gap
                    }
                    cs.stroke()
                    // Kenar boşluğu çizgisi
                    cs.setStrokingColor(0.93f, 0.62f, 0.62f)
                    cs.moveTo(25f * MM, 0f)
                    cs.lineTo(25f * MM, h)
                    cs.stroke()
                }
                PageTemplate.GRID -> {
                    val gap = 5f * MM
                    cs.setStrokingColor(0.80f, 0.85f, 0.92f)
                    cs.setLineWidth(0.4f)
                    var x = gap
                    while (x < w) {
                        cs.moveTo(x, 0f)
                        cs.lineTo(x, h)
                        x += gap
                    }
                    var y = h - gap
                    while (y > 0f) {
                        cs.moveTo(0f, y)
                        cs.lineTo(w, y)
                        y -= gap
                    }
                    cs.stroke()
                }
                PageTemplate.DOTTED -> {
                    val gap = 5f * MM
                    val d = 1.3f
                    cs.setNonStrokingColor(0.62f, 0.66f, 0.72f)
                    var y = h - gap
                    while (y > 0f) {
                        var x = gap
                        while (x < w) {
                            cs.addRect(x - d / 2, y - d / 2, d, d)
                            x += gap
                        }
                        y -= gap
                    }
                    cs.fill()
                }
                PageTemplate.CORNELL -> {
                    val top = h - 28f * MM // başlık alanının altı
                    val summary = h * 0.22f // özet alanının üstü
                    val cue = w * 0.3f // anahtar kelime sütunu
                    // Not alanında satırlar
                    cs.setStrokingColor(0.78f, 0.84f, 0.93f)
                    cs.setLineWidth(0.5f)
                    var y = top - 8f * MM
                    while (y > summary + 4f) {
                        cs.moveTo(cue, y)
                        cs.lineTo(w, y)
                        y -= 8f * MM
                    }
                    cs.stroke()
                    // Bölüm çizgileri
                    cs.setStrokingColor(0.45f, 0.55f, 0.72f)
                    cs.setLineWidth(1.2f)
                    cs.moveTo(0f, top)
                    cs.lineTo(w, top)
                    cs.moveTo(cue, top)
                    cs.lineTo(cue, summary)
                    cs.moveTo(0f, summary)
                    cs.lineTo(w, summary)
                    cs.stroke()
                }
                PageTemplate.MILLIMETRIC -> {
                    // İnceden kalına üç katman: 1 mm, 5 mm, 10 mm
                    for ((step, width, shade) in listOf(Triple(1f, 0.15f, 0.88f), Triple(5f, 0.35f, 0.78f), Triple(10f, 0.6f, 0.66f))) {
                        cs.setStrokingColor(shade, shade + 0.04f, 0.97f)
                        cs.setLineWidth(width)
                        val gap = step * MM
                        var x = 0f
                        while (x <= w) {
                            cs.moveTo(x, 0f)
                            cs.lineTo(x, h)
                            x += gap
                        }
                        var y = h
                        while (y >= 0f) {
                            cs.moveTo(0f, y)
                            cs.lineTo(w, y)
                            y -= gap
                        }
                        cs.stroke()
                    }
                }
                PageTemplate.MUSIC -> {
                    cs.setStrokingColor(0.55f, 0.58f, 0.64f)
                    cs.setLineWidth(0.5f)
                    val line = 2f * MM
                    var top = h - 22f * MM
                    while (top - 4 * line > 15f * MM) {
                        for (k in 0..4) {
                            val y = top - k * line
                            cs.moveTo(15f * MM, y)
                            cs.lineTo(w - 15f * MM, y)
                        }
                        top -= 4 * line + 12f * MM
                    }
                    cs.stroke()
                }
                PageTemplate.ISOMETRIC -> {
                    val gap = 6f * MM
                    val rowH = gap * 0.866f
                    val d = 1.2f
                    cs.setNonStrokingColor(0.62f, 0.66f, 0.72f)
                    var y = h - rowH
                    var row = 0
                    while (y > 0f) {
                        var x = if (row % 2 == 0) gap / 2 else gap
                        while (x < w) {
                            cs.addRect(x - d / 2, y - d / 2, d, d)
                            x += gap
                        }
                        y -= rowH
                        row++
                    }
                    cs.fill()
                }
                PageTemplate.BLANK -> {}
            }
        }
    }
}
