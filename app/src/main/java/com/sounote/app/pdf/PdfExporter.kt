package com.sounote.app.pdf

import android.content.Context
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
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode as PdfBlendMode

/**
 * Notları orijinal PDF'in üstüne ekleyip yeni bir PDF yazar. Orijinal sayfa içeriğine
 * dokunulmaz (yazılar seçilebilir kalır); her sayfaya notlar için ek bir içerik akışı eklenir.
 * Kalem ve fosforlu vektör çizgi, metin kutuları vektör yol olarak yazılır (font gömmeye gerek yok).
 */
object PdfExporter {

    fun export(context: Context, source: Uri, ink: Map<Int, PageContent>, target: File) {
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
                writePage(doc, pageIndex, ourWidth, content)
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

    private fun writePage(doc: PDDocument, index: Int, ourWidth: Float, content: PageContent) {
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

            val highlighters = content.strokes.filter { it.tool == InkTool.HIGHLIGHTER }
            if (highlighters.isNotEmpty()) {
                cs.saveGraphicsState()
                cs.setGraphicsStateParameters(PDExtendedGraphicsState().apply { setBlendMode(PdfBlendMode.MULTIPLY) })
                highlighters.forEach { drawStroke(cs, it) }
                cs.restoreGraphicsState()
            }
            content.texts.forEach { drawText(cs, it) }
            content.strokes.filter { it.tool != InkTool.HIGHLIGHTER }.forEach { stroke ->
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

    /** Ekrandaki [StrokeRenderer] ile aynı: basınç kademelerine bölünmüş, yuvarlak uçlu yollar. */
    private fun drawStroke(cs: PDPageContentStream, s: Stroke) {
        val pts = s.points
        val n = s.pointCount
        if (n == 0) return
        setColor(cs, s.color, stroking = true)
        setColor(cs, s.color, stroking = false)
        fun level(i: Int) =
            if (s.pressureSensitive && s.tool == InkTool.PEN) {
                (StrokeRenderer.pressureFactor(pts[i * 3 + 2]) * LEVELS).roundToInt()
            } else {
                LEVELS
            }

        if (n == 1) {
            circle(cs, pts[0], pts[1], s.width * level(0) / LEVELS / 2f)
            cs.fill()
            return
        }
        if (s.tool != InkTool.PEN) {
            run(cs, pts, 0, n - 1, s.width)
            return
        }
        var runStart = 0
        var runLevel = level(0)
        for (i in 1 until n) {
            if (i == n - 1) {
                run(cs, pts, runStart, i, s.width * runLevel / LEVELS)
            } else {
                val l = level(i)
                if (l != runLevel) {
                    run(cs, pts, runStart, i, s.width * runLevel / LEVELS)
                    runStart = i
                    runLevel = l
                }
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

    private const val LEVELS = 12
}
