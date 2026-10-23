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
    val displayTitle get() = title.ifBlank { body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(60) ?: "Başlıksız not" }
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

/** Yazılı notu A4 PDF'e çevirir (paylaşmak ya da üstüne kalemle yazmak için). */
object TextNotePdf {
    private const val W = 595
    private const val H = 842
    private const val MARGIN = 56

    fun write(note: TextNote, target: File) {
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
            val title = note.displayTitle
            val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, width).build()
            val body = note.body
            val bodyLayout = StaticLayout.Builder.obtain(body, 0, body.length, bodyPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.3f)
                .build()

            var line = 0
            var pageNo = 1
            while (true) {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
                val c = page.canvas
                var y = MARGIN.toFloat()
                if (pageNo == 1) {
                    c.save()
                    c.translate(MARGIN.toFloat(), y)
                    titleLayout.draw(c)
                    c.restore()
                    y += titleLayout.height + 18f
                }
                val avail = H - MARGIN - y
                val startTop = bodyLayout.getLineTop(line)
                var end = line
                while (end < bodyLayout.lineCount && bodyLayout.getLineBottom(end) - startTop <= avail) end++
                if (end == line) end = line + 1
                val bottom = bodyLayout.getLineBottom(end - 1)
                c.save()
                c.translate(MARGIN.toFloat(), y - startTop)
                c.clipRect(0f, startTop.toFloat(), width.toFloat(), bottom.toFloat())
                bodyLayout.draw(c)
                c.restore()
                c.drawText("$pageNo", (W / 2).toFloat(), (H - MARGIN / 2).toFloat(), footPaint)
                doc.finishPage(page)
                line = end
                pageNo++
                if (line >= bodyLayout.lineCount) break
            }
            target.parentFile?.mkdirs()
            target.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }
}
