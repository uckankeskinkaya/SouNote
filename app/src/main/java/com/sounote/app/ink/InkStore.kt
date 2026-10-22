package com.sounote.app.ink

import android.content.Context
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException

/**
 * Notları diskte tutar: filesDir/ink/<belge>/<sayfa>.bin.
 * Sayfa başına ayrı dosya: büyük belgede sadece değişen sayfa yeniden yazılır.
 */
class InkStore(context: Context) {
    private val root = File(context.filesDir, "ink")

    private fun dir(docId: String) = File(root, docId)

    fun load(docId: String): Map<Int, PageContent> {
        val files = dir(docId).listFiles { f -> f.name.endsWith(".bin") } ?: return emptyMap()
        val result = HashMap<Int, PageContent>()
        for (f in files) {
            val page = f.name.removeSuffix(".bin").toIntOrNull() ?: continue
            try {
                result[page] = read(f)
            } catch (e: Exception) {
                Log.e(TAG, "Sayfa notları okunamadı: $f", e)
            }
        }
        return result
    }

    fun save(docId: String, page: Int, content: PageContent) {
        val d = dir(docId)
        val file = File(d, "$page.bin")
        if (content.isEmpty) {
            file.delete()
            return
        }
        d.mkdirs()
        // Önce geçici dosyaya yaz, sonra değiştir: yazarken kapanırsa eski hali bozulmaz.
        val tmp = File(d, "$page.tmp")
        DataOutputStream(BufferedOutputStream(tmp.outputStream(), 64 * 1024)).use { write(it, content) }
        if (!tmp.renameTo(file)) throw IOException("Notlar kaydedilemedi: $file")
    }

    fun hasInk(docId: String) = dir(docId).listFiles()?.any { it.name.endsWith(".bin") } == true

    fun delete(docId: String) {
        dir(docId).deleteRecursively()
    }

    private fun write(out: DataOutputStream, c: PageContent) {
        out.writeInt(MAGIC)
        out.writeInt(VERSION)
        out.writeInt(c.strokes.size)
        for (s in c.strokes) {
            out.writeByte(s.tool.ordinal)
            out.writeInt(s.color)
            out.writeFloat(s.width)
            out.writeBoolean(s.pressureSensitive)
            out.writeInt(s.points.size)
            for (f in s.points) out.writeFloat(f)
        }
        out.writeInt(c.texts.size)
        for (t in c.texts) {
            out.writeLong(t.id)
            out.writeFloat(t.x)
            out.writeFloat(t.y)
            out.writeFloat(t.width)
            out.writeFloat(t.fontSize)
            out.writeInt(t.color)
            val bytes = t.text.toByteArray(Charsets.UTF_8)
            out.writeInt(bytes.size)
            out.write(bytes)
        }
    }

    private fun read(file: File): PageContent =
        DataInputStream(BufferedInputStream(file.inputStream(), 64 * 1024)).use { inp ->
            if (inp.readInt() != MAGIC) throw IOException("Tanınmayan dosya")
            val version = inp.readInt()
            if (version > VERSION) throw IOException("Daha yeni sürümle kaydedilmiş: $version")
            val tools = InkTool.entries
            val strokes = List(inp.readInt()) {
                val tool = tools[inp.readByte().toInt()]
                val color = inp.readInt()
                val width = inp.readFloat()
                val pressure = inp.readBoolean()
                val points = FloatArray(inp.readInt()) { inp.readFloat() }
                Stroke(tool, color, width, pressure, points)
            }
            val texts = List(inp.readInt()) {
                val id = inp.readLong()
                val x = inp.readFloat()
                val y = inp.readFloat()
                val w = inp.readFloat()
                val size = inp.readFloat()
                val color = inp.readInt()
                val bytes = ByteArray(inp.readInt())
                inp.readFully(bytes)
                TextBox(id, x, y, w, size, color, String(bytes, Charsets.UTF_8))
            }
            PageContent(strokes, texts)
        }

    companion object {
        private const val TAG = "InkStore"
        private const val MAGIC = 0x534E5031 // "SNP1"
        private const val VERSION = 1
    }
}
