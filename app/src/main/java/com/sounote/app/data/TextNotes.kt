package com.sounote.app.data

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Klavyeyle yazılan not (kalemli belgelerden ayrı). */
data class TextNote(
    val id: String,
    val title: String,
    val body: String,
    val created: Long,
    val updated: Long,
    val pinned: Boolean = false,
) {
    /** Çizim satırları çıkarılmış metin (önizleme, arama, paylaşma). */
    val plainBody get() = body.lineSequence().filterNot { it.startsWith(Sketches.MARKER) }.joinToString("\n")
    val hasSketch get() = body.contains(Sketches.MARKER)
    val displayTitle get() = title.ifBlank { plainBody.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(60) ?: "Başlıksız not" }
}

/** Yazılı notlar: filesDir/texts/<id>.json (yedeğe girer). */
class TextNoteStore(context: Context) {
    private val dir = File(context.filesDir, DIR)

    fun list(): List<TextNote> =
        (dir.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray())
            .mapNotNull { f -> runCatching { read(f) }.getOrNull() }
            .sortedWith(ORDER)

    fun save(note: TextNote) {
        dir.mkdirs()
        val json = JSONObject()
            .put("id", note.id)
            .put("title", note.title)
            .put("body", note.body)
            .put("created", note.created)
            .put("updated", note.updated)
            .put("pinned", note.pinned)
        val target = File(dir, "${note.id}.json")
        val tmp = File(dir, "${note.id}.json.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw IOException("Not kaydedilemedi")
        }
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }

    private fun read(f: File): TextNote {
        val o = JSONObject(f.readText())
        return TextNote(
            id = o.getString("id"),
            title = o.optString("title"),
            body = o.optString("body"),
            created = o.optLong("created"),
            updated = o.optLong("updated"),
            pinned = o.optBoolean("pinned"),
        )
    }

    companion object {
        const val DIR = "texts"

        /** Sabitlenenler önce, sonra son düzenlenen. */
        val ORDER: Comparator<TextNote> = compareByDescending<TextNote> { it.pinned }.thenByDescending { it.updated }
    }
}

/** Yazılı notu A4 PDF'e çevirir (paylaşmak ya da üstüne kalemle yazmak için); çizimler de çizilir. */
object TextNotePdf {
    private const val W = 595
    private const val H = 842
    private const val MARGIN = 56

    fun write(context: Context, note: TextNote, target: File) {
        val doc = PdfDocument()
        try {
            val width = W - 2 * MARGIN
            val titlePaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                color = Color.BLACK
            }
            val bodyPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                textSize = 12f
                color = 0xFF222222.toInt()
            }
            val footPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                textSize = 9f
                color = Color.GRAY
            }
            var pageNo = 0
            var page: PdfDocument.Page? = null
            var y = 0f
            fun newPage() {
                page?.let {
                    it.canvas.drawText("$pageNo", (W / 2).toFloat(), (H - MARGIN / 2).toFloat(), footPaint)
                    doc.finishPage(it)
                }
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
                y = MARGIN.toFloat()
            }
            newPage()
            val title = note.displayTitle
            val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, width).build()
            page!!.canvas.apply {
                save()
                translate(MARGIN.toFloat(), y)
                titleLayout.draw(this)
                restore()
            }
            y += titleLayout.height + 18f

            for (part in splitBody(note.body)) {
                when (part) {
                    is BodyPart.Text -> {
                        if (part.text.isEmpty()) continue
                        val layout = StaticLayout.Builder.obtain(part.text, 0, part.text.length, bodyPaint, width)
                            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                            .setLineSpacing(0f, 1.3f)
                            .build()
                        var line = 0
                        while (line < layout.lineCount) {
                            val avail = H - MARGIN - y
                            val startTop = layout.getLineTop(line)
                            var end = line
                            while (end < layout.lineCount && layout.getLineBottom(end) - startTop <= avail) end++
                            if (end == line) {
                                if (y > MARGIN) {
                                    newPage()
                                    continue
                                }
                                end = line + 1
                            }
                            val bottom = layout.getLineBottom(end - 1)
                            page!!.canvas.apply {
                                save()
                                translate(MARGIN.toFloat(), y - startTop)
                                clipRect(0f, startTop.toFloat(), width.toFloat(), bottom.toFloat())
                                layout.draw(this)
                                restore()
                            }
                            y += bottom - startTop
                            line = end
                            if (line < layout.lineCount) newPage()
                        }
                        y += 6f
                    }
                    is BodyPart.Drawing -> {
                        val sketch = Sketches.load(context, part.id)
                        val scale = width / sketch.width
                        val h = sketch.height * scale
                        if (y + h > H - MARGIN && y > MARGIN) newPage()
                        page!!.canvas.apply {
                            save()
                            translate(MARGIN.toFloat(), y)
                            Sketches.draw(this, sketch, scale)
                            restore()
                        }
                        y += h + 12f
                    }
                }
            }
            page?.let {
                it.canvas.drawText("$pageNo", (W / 2).toFloat(), (H - MARGIN / 2).toFloat(), footPaint)
                doc.finishPage(it)
            }
            target.parentFile?.mkdirs()
            target.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }
}
