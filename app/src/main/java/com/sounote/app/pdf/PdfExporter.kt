package com.sounote.app.pdf

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Path
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.sounote.app.data.LibraryRepository
import com.sounote.app.ink.InkTool
import com.sounote.app.ink.PageContent
import com.sounote.app.ink.Stroke
import com.sounote.app.ink.StrokeRenderer
import com.sounote.app.ink.TextBox
import com.sounote.app.ink.TextBoxes
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.io.IOException
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode as PdfBlendMode

/**
 * Notları orijinal PDF'in üstüne ekleyip yeni bir PDF yazar. Orijinal sayfa içeriğine
 * dokunulmaz (yazılar seçilebilir kalır); her sayfaya notlar için ek bir içerik akışı eklenir.
 * Kalem ve fosforlu vektör çizgi, metin kutuları vektör yol olarak yazılır (font gömmeye gerek yok).
 */
object PdfExporter {

    /** [pages] verilirse sadece o sayfalar (0'dan başlayan) yeni PDF'te kalır. */
    fun export(
        context: Context, source: Uri, ink: Map<Int, PageContent>, imagesDir: File, target: File,
        pages: Set<Int>? = null,
    ) {
        PDFBoxResourceLoader.init(context.applicationContext)
        val sizes = pageSizes(context, source, ink.keys)
        val doc = if (source.scheme == "file") {
            PDDocument.load(File(requireNotNull(source.path)))
        } else {
            context.contentResolver.openInputStream(source)?.use { PDDocument.load(it) }
                ?: throw IOException("PDF açılamadı")
        }
        doc.use {
            doc.setAllSecurityToBeRemoved(true)
            for ((pageIndex, content) in ink) {
                if (content.isEmpty || pageIndex >= doc.numberOfPages) continue
                val ourWidth = sizes[pageIndex]?.first ?: continue
                writePage(doc, pageIndex, ourWidth, content, imagesDir)
            }
            if (pages != null) {
                for (i in doc.numberOfPages - 1 downTo 0) if (i !in pages) doc.removePage(i)
            }
            val tmp = File(target.path + ".tmp")
            doc.save(tmp)
            if (!tmp.renameTo(target)) throw IOException("Dosya yazılamadı")
        }
    }

    /** PdfRenderer'ın gördüğü sayfa boyutu (notlar bu ölçüde kaydedildi). */
    private fun pageSizes(context: Context, source: Uri, pages: Set<Int>): Map<Int, Pair<Float, Float>> =
        LibraryRepository.openFileDescriptor(context, source).use { pfd ->
            PdfRenderer(pfd).use { r ->
                pages.filter { it < r.pageCount }.associateWith { i ->
                    r.openPage(i).use { it.width.toFloat() to it.height.toFloat() }
                }
            }
        }

    private fun writePage(doc: PDDocument, index: Int, ourWidth: Float, content: PageContent, imagesDir: File) {
        val pencilState = PDExtendedGraphicsState().apply {
            strokingAlphaConstant = StrokeRenderer.PENCIL_ALPHA
            nonStrokingAlphaConstant = StrokeRenderer.PENCIL_ALPHA
        }
        val page = doc.getPage(index)
        val box = page.cropBox
        val rotation = ((page.rotation % 360) + 360) % 360
        val displayWidth = if (rotation == 90 || rotation == 270) box.height else box.width
        val s = displayWidth / ourWidth

        // Ekran koordinatı (sol üst köşe, y aşağı) → PDF kullanıcı alanı (sol alt, y yukarı), sayfa dönüşü dahil.
        val llx = box.lowerLeftX
        val lly = box.lowerLeftY
        val urx = box.upperRightX
        val ury = box.upperRightY
        val m = when (rotation) {
            90 -> Matrix(0f, s, s, 0f, llx, lly)
            180 -> Matrix(-s, 0f, 0f, s, urx, lly)
            270 -> Matrix(0f, -s, -s, 0f, urx, ury)
            else -> Matrix(s, 0f, 0f, -s, llx, ury)
        }

        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.saveGraphicsState()
            cs.transform(m)
            cs.setLineCapStyle(1)
            cs.setLineJoinStyle(1)

            // Resimler en altta. Birim kare y ekseninde ters çevrilerek yerleştirilir (ekran y'si aşağı doğru).
            content.images.forEach { im ->
                val f = File(imagesDir, im.file)
                if (!f.exists()) return@forEach
                val img = if (f.name.endsWith(".jpg")) {
                    f.inputStream().use { JPEGFactory.createFromStream(doc, it) }
                } else {
                    val bmp = BitmapFactory.decodeFile(f.path) ?: return@forEach
                    LosslessFactory.createFromImage(doc, bmp)
                }
                // Birim kare → resmin yeri (y ters), merkezi etrafında dönme dahil.
                val r = Math.toRadians(im.rotation.toDouble())
                val cos = kotlin.math.cos(r).toFloat()
                val sin = kotlin.math.sin(r).toFloat()
                val cx = im.x + im.width / 2
                val cy = im.y + im.height / 2
                val bottom = im.y + im.height
                cs.drawImage(
                    img,
                    Matrix(
                        im.width * cos, im.width * sin, im.height * sin, -im.height * cos,
                        cx + (im.x - cx) * cos - (bottom - cy) * sin,
                        cy + (im.x - cx) * sin + (bottom - cy) * cos,
                    ),
                )
            }

            val highlighters = content.strokes.filter { it.tool == InkTool.HIGHLIGHTER }
            if (highlighters.isNotEmpty()) {
                cs.saveGraphicsState()
                cs.setGraphicsStateParameters(PDExtendedGraphicsState().apply { setBlendMode(PdfBlendMode.MULTIPLY) })
                highlighters.forEach { drawStroke(cs, it) }
                cs.restoreGraphicsState()
            }
            content.texts.forEach { drawText(cs, it) }
            // Ezber bantları dışa aktarılmaz: çalışma aracı, paylaşılan PDF'te yazıyı kapatmasın.
            content.strokes.filter { it.tool != InkTool.HIGHLIGHTER && it.tool != InkTool.TAPE }.forEach { stroke ->
                if (stroke.tool == InkTool.PENCIL) {
                    cs.saveGraphicsState()
                    cs.setGraphicsStateParameters(pencilState)
                    drawStroke(cs, stroke)
                    cs.restoreGraphicsState()
                } else {
                    drawStroke(cs, stroke)
                }
            }
            cs.restoreGraphicsState()
        }
    }

    private fun setColor(cs: PDPageContentStream, color: Int, stroking: Boolean) {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        if (stroking) cs.setStrokingColor(r, g, b) else cs.setNonStrokingColor(r, g, b)
    }

    /** Ekrandaki [StrokeRenderer] ile aynı: kalınlık kademelerine bölünmüş, yuvarlak uçlu yollar. */
    private fun drawStroke(cs: PDPageContentStream, s: Stroke) {
        val pts = s.points
        val n = s.pointCount
        if (n == 0) return
        setColor(cs, s.color, stroking = true)
        setColor(cs, s.color, stroking = false)
        val levels = StrokeRenderer.levels(s.tool, s.width, s.pressureSensitive, pts, n)
        val l = StrokeRenderer.LEVELS

        if (n == 1) {
            circle(cs, pts[0], pts[1], s.width * (levels?.get(0) ?: l) / l / 2f)
            cs.fill()
            return
        }
        if (levels == null) {
            if (s.tool == InkTool.DASHED) {
                val (on, off) = StrokeRenderer.dashIntervals(s.width)
                cs.saveGraphicsState()
                cs.setLineDashPattern(floatArrayOf(on, off), 0f)
                run(cs, pts, 0, n - 1, s.width)
                cs.restoreGraphicsState()
            } else {
                run(cs, pts, 0, n - 1, s.width)
            }
            return
        }
        var runStart = 0
        var runLevel = levels[0]
        for (i in 1 until n) {
            if (i == n - 1) {
                run(cs, pts, runStart, i, s.width * runLevel / l)
            } else if (levels[i] != runLevel) {
                run(cs, pts, runStart, i, s.width * runLevel / l)
                runStart = i
                runLevel = levels[i]
            }
        }
    }

    private fun run(cs: PDPageContentStream, pts: FloatArray, from: Int, to: Int, width: Float) {
        cs.setLineWidth(width)
        var cx = pts[from * 3]
        var cy = pts[from * 3 + 1]
        cs.moveTo(cx, cy)
        for (j in from + 1 until to) {
            val qx = pts[j * 3]
            val qy = pts[j * 3 + 1]
            val ex = (qx + pts[(j + 1) * 3]) / 2f
            val ey = (qy + pts[(j + 1) * 3 + 1]) / 2f
            // İkinci derece eğri → üçüncü derece (PDF sadece bunu bilir).
            cs.curveTo(
                cx + 2f / 3f * (qx - cx), cy + 2f / 3f * (qy - cy),
                ex + 2f / 3f * (qx - ex), ey + 2f / 3f * (qy - ey),
                ex, ey,
            )
            cx = ex
            cy = ey
        }
        cs.lineTo(pts[to * 3], pts[to * 3 + 1])
        cs.stroke()
    }

    private fun circle(cs: PDPageContentStream, x: Float, y: Float, r: Float) {
        val k = 0.5523f * r
        cs.moveTo(x + r, y)
        cs.curveTo(x + r, y + k, x + k, y + r, x, y + r)
        cs.curveTo(x - k, y + r, x - r, y + k, x - r, y)
        cs.curveTo(x - r, y - k, x - k, y - r, x, y - r)
        cs.curveTo(x + k, y - r, x + r, y - k, x + r, y)
        cs.closePath()
    }

    /** Metni ekrandakiyle aynı satırlara böler, harfleri yol olarak yazar. */
    private fun drawText(cs: PDPageContentStream, box: TextBox) {
        val layout = TextBoxes.layout(box)
        val paint = TextBoxes.paint(box)
        val path = Path()
        val line = Path()
        for (i in 0 until layout.lineCount) {
            val start = layout.getLineStart(i)
            val end = layout.getLineVisibleEnd(i)
            if (end <= start) continue
            line.reset()
            paint.getTextPath(
                box.text, start, end,
                box.x + layout.getLineLeft(i),
                box.y + layout.getLineBaseline(i).toFloat(),
                line,
            )
            path.addPath(line)
        }
        if (path.isEmpty) return
        val center = TextBoxes.bounds(box, android.graphics.RectF())
        val turn = android.graphics.Matrix().apply { setRotate(box.rotation, center.centerX(), center.centerY()) }
        if (box.rotation != 0f) path.transform(turn)
        if (box.background != 0) {
            // Arka plan: (dönmüş) dörtgen
            val r = android.graphics.RectF(center)
            val pad = TextBoxes.backgroundPad(box)
            r.inset(-pad, -pad)
            val pts = floatArrayOf(r.left, r.top, r.right, r.top, r.right, r.bottom, r.left, r.bottom)
            turn.mapPoints(pts)
            setColor(cs, box.background, stroking = false)
            cs.moveTo(pts[0], pts[1])
            cs.lineTo(pts[2], pts[3])
            cs.lineTo(pts[4], pts[5])
            cs.lineTo(pts[6], pts[7])
            cs.closePath()
            cs.fill()
        }
        setColor(cs, box.color, stroking = false)
        // Path.approximate: [oran, x, y] üçlüleri; aynı oran tekrar ederse yeni kontur başlar.
        val a = path.approximate(0.02f)
        var i = 0
        var prevFraction = -1f
        var open = false
        while (i + 2 < a.size) {
            val f = a[i]
            val x = a[i + 1]
            val y = a[i + 2]
            if (!open || f == prevFraction) {
                if (open) cs.closePath()
                cs.moveTo(x, y)
                open = true
            } else {
                cs.lineTo(x, y)
            }
            prevFraction = f
            i += 3
        }
        if (open) cs.closePath()
        cs.fill()
    }

}
