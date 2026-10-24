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
    /** Haftalık plan: günlere bölünmüş kutular ve notlar alanı. */
    WEEKLY("Haftalık plan"),
    /** Kelime defteri: kelime | anlam | örnek cümle sütunları. */
    VOCAB("Kelime defteri"),
    /** Renkli Cornell: renkli başlık, anahtar kelime ve özet alanları. */
    CORNELL_COLOR("Cornell (renkli)"),
}

/** Yeni defter kâğıt boyutu (dikey, nokta cinsinden). */
enum class PaperSize(val label: String, val width: Float, val height: Float) {
    A4("A4", 595f, 842f),
    A5("A5", 420f, 595f),
    LETTER("Letter", 612f, 792f),
}

/** Yeni defter kâğıt rengi (0–1 RGB). */
enum class PaperColor(val label: String, val r: Float, val g: Float, val b: Float) {
    WHITE("Beyaz", 1f, 1f, 1f),
    CREAM("Krem", 0.99f, 0.96f, 0.88f),
    BLUE("Mavi", 0.90f, 0.95f, 1f),
    GREEN("Yeşil", 0.91f, 0.97f, 0.91f),
    GRAY("Gri", 0.93f, 0.93f, 0.93f),
    ;

    val argb get() = (0xFF shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
}

/**
 * PDF'in sayfa yapısını değiştirir (PDFBox): boş sayfa ekleme, silme, taşıma, yeni defter.
 * Dosya önce geçici dosyaya yazılır, sonra eskisinin yerine konur.
 */
object PdfPages {
    private const val MM = 72f / 25.4f

    /** [at] konumuna (ondan önceye) şablonlu boş sayfa ekler; boyut komşu sayfayla aynı. */
    fun insertBlank(context: Context, file: File, at: Int, template: PageTemplate, paper: PaperColor = PaperColor.WHITE) = edit(context, file) { doc ->
        val count = doc.numberOfPages
        val neighbor = doc.getPage((if (at > 0) at - 1 else 0).coerceAtMost(count - 1))
        val box = neighbor.cropBox
        val rotated = neighbor.rotation % 180 != 0
        // Ekranda görünen boyutta, döndürülmemiş sayfa
        val size = if (rotated) PDRectangle(box.height, box.width) else PDRectangle(box.width, box.height)
        val page = PDPage(size)
        if (at >= count) doc.pages.add(page) else doc.pages.insertBefore(page, doc.getPage(at))
        drawTemplate(doc, page, template, paper)
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
        size: PaperSize = PaperSize.A4, landscape: Boolean = false, paper: PaperColor = PaperColor.WHITE,
    ) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val rect = if (landscape) PDRectangle(size.height, size.width) else PDRectangle(size.width, size.height)
        PDDocument().use { doc ->
            repeat(pages) {
                val page = PDPage(rect)
                doc.addPage(page)
                drawTemplate(doc, page, template, paper)
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

    /** Şablondaki küçük başlık (Helvetica; sadece WinAnsi harfleri: ı, ş, ğ, İ kullanılmaz). */
    private fun label(cs: PDPageContentStream, text: String, x: Float, y: Float, size: Float) {
        runCatching {
            cs.beginText()
            cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, size)
            cs.setNonStrokingColor(0.30f, 0.34f, 0.62f)
            cs.newLineAtOffset(x, y)
            cs.showText(text)
            cs.endText()
        }
    }

    /** Şablon çizgileri (PDF koordinatı: sol alt köşe, y yukarı). */
    private fun drawTemplate(doc: PDDocument, page: PDPage, template: PageTemplate, paper: PaperColor) {
        if (template == PageTemplate.BLANK && paper == PaperColor.WHITE) return
        val w = page.mediaBox.width
        val h = page.mediaBox.height
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            if (paper != PaperColor.WHITE) {
                cs.setNonStrokingColor(paper.r, paper.g, paper.b)
                cs.addRect(0f, 0f, w, h)
                cs.fill()
            }
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
                PageTemplate.WEEKLY -> {
                    val m = 12f * MM
                    val top = h - 26f * MM
                    val cols = 2
                    val rows = 4
                    val cw = (w - 2 * m - 6f * MM) / cols
                    val rh = (top - m - 3 * 4f * MM) / rows
                    val days = listOf("PZT", "SAL", "ÇAR", "PER", "CUM", "CMT", "PAZ", "NOTLAR")
                    for (i in 0 until cols * rows) {
                        val c = i / rows
                        val r = i % rows
                        val x = m + c * (cw + 6f * MM)
                        val y = top - (r + 1) * rh - r * 4f * MM
                        // Kutu: açık lavanta başlık şeridi + ince çerçeve + satırlar
                        cs.setNonStrokingColor(0.91f, 0.92f, 0.99f)
                        cs.addRect(x, y + rh - 7f * MM, cw, 7f * MM)
                        cs.fill()
                        cs.setStrokingColor(0.70f, 0.73f, 0.92f)
                        cs.setLineWidth(0.8f)
                        cs.addRect(x, y, cw, rh)
                        cs.stroke()
                        cs.setStrokingColor(0.85f, 0.87f, 0.95f)
                        cs.setLineWidth(0.4f)
                        var ly = y + rh - 7f * MM - 7f * MM
                        while (ly > y + 2f * MM) {
                            cs.moveTo(x + 2f * MM, ly)
                            cs.lineTo(x + cw - 2f * MM, ly)
                            ly -= 7f * MM
                        }
                        cs.stroke()
                        label(cs, days[i], x + 3f * MM, y + rh - 5f * MM, 9f)
                    }
                }
                PageTemplate.VOCAB -> {
                    val m = 10f * MM
                    val top = h - 24f * MM
                    val c1 = m + (w - 2 * m) * 0.28f
                    val c2 = m + (w - 2 * m) * 0.58f
                    cs.setNonStrokingColor(0.91f, 0.92f, 0.99f)
                    cs.addRect(m, top - 9f * MM, w - 2 * m, 9f * MM)
                    cs.fill()
                    cs.setStrokingColor(0.80f, 0.83f, 0.94f)
                    cs.setLineWidth(0.5f)
                    var y = top - 9f * MM
                    while (y > m) {
                        cs.moveTo(m, y)
                        cs.lineTo(w - m, y)
                        y -= 9f * MM
                    }
                    cs.stroke()
                    cs.setStrokingColor(0.62f, 0.66f, 0.90f)
                    cs.setLineWidth(0.9f)
                    for (x in listOf(c1, c2)) {
                        cs.moveTo(x, top)
                        cs.lineTo(x, m)
                    }
                    cs.stroke()
                    label(cs, "Kelime", m + 3f * MM, top - 6f * MM, 10f)
                    label(cs, "Anlam", c1 + 3f * MM, top - 6f * MM, 10f)
                    label(cs, "Örnek cümle", c2 + 3f * MM, top - 6f * MM, 10f)
                }
                PageTemplate.CORNELL_COLOR -> {
                    val top = h - 28f * MM
                    val summary = h * 0.22f
                    val cue = w * 0.3f
                    // Renkli alanlar: başlık, anahtar kelime sütunu, özet
                    cs.setNonStrokingColor(0.89f, 0.90f, 0.99f)
                    cs.addRect(0f, top, w, h - top)
                    cs.fill()
                    cs.setNonStrokingColor(0.94f, 0.96f, 1f)
                    cs.addRect(0f, summary, cue, top - summary)
                    cs.fill()
                    cs.setNonStrokingColor(0.96f, 0.94f, 1f)
                    cs.addRect(0f, 0f, w, summary)
                    cs.fill()
                    cs.setStrokingColor(0.80f, 0.84f, 0.95f)
                    cs.setLineWidth(0.5f)
                    var y = top - 8f * MM
                    while (y > summary + 4f) {
                        cs.moveTo(cue, y)
                        cs.lineTo(w, y)
                        y -= 8f * MM
                    }
                    cs.stroke()
                    cs.setStrokingColor(0.36f, 0.42f, 0.86f)
                    cs.setLineWidth(1.2f)
                    cs.moveTo(0f, top)
                    cs.lineTo(w, top)
                    cs.moveTo(cue, top)
                    cs.lineTo(cue, summary)
                    cs.moveTo(0f, summary)
                    cs.lineTo(w, summary)
                    cs.stroke()
                    label(cs, "Anahtar", 4f * MM, top - 6f * MM, 9f)
                    label(cs, "Notlar", cue + 4f * MM, top - 6f * MM, 9f)
                    label(cs, "Özet", 4f * MM, summary - 6f * MM, 9f)
                }
                PageTemplate.BLANK -> {}
            }
        }
    }
}
