package com.sounote.app.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/** Yazılı nottaki çizimin bir çizgisi; noktalar çizim alanı birimi cinsinden (genişlik [Sketch.width]). */
class SketchStroke(val color: Int, val width: Float, val points: FloatArray)

/** Yazılı nota eklenen çizim alanı. Notun metninde [Sketches.MARKER] + kimlik satırıyla yer alır. */
data class Sketch(
    val id: String,
    val strokes: List<SketchStroke>,
    val width: Float = 1000f,
    val height: Float = 600f,
)

/** Çizimler: filesDir/texts/sketch/<id>.json (yedeğe girer). */
object Sketches {
    /** Metindeki çizim satırının başı (görünmez nesne karakteri). */
    const val MARKER = "￼sketch:"

    fun newId(): String = UUID.randomUUID().toString().take(12)

    private fun file(context: Context, id: String) = File(context.filesDir, "texts/sketch/$id.json")

    fun load(context: Context, id: String): Sketch = runCatching {
        val o = JSONObject(file(context, id).readText())
        val strokes = o.getJSONArray("strokes").let { arr ->
            (0 until arr.length()).map { i ->
                val s = arr.getJSONObject(i)
                val p = s.getJSONArray("p")
                SketchStroke(s.getInt("c"), s.getDouble("w").toFloat(), FloatArray(p.length()) { p.getDouble(it).toFloat() })
            }
        }
        Sketch(id, strokes, o.optDouble("width", 1000.0).toFloat(), o.optDouble("height", 600.0).toFloat())
    }.getOrDefault(Sketch(id, emptyList()))

    fun save(context: Context, sketch: Sketch) {
        val f = file(context, sketch.id)
        f.parentFile?.mkdirs()
        val arr = JSONArray()
        sketch.strokes.forEach { s ->
            arr.put(
                JSONObject().put("c", s.color).put("w", s.width.toDouble())
                    .put("p", JSONArray().apply { s.points.forEach { put(Math.round(it * 10) / 10.0) } }),
            )
        }
        val json = JSONObject().put("width", sketch.width.toDouble()).put("height", sketch.height.toDouble()).put("strokes", arr)
        val tmp = File(f.path + ".tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(f)) throw IOException("Çizim kaydedilemedi")
    }

    /** Çizimi Android tuvaline çizer (PDF için); [scale] birim → piksel. */
    fun draw(canvas: Canvas, sketch: Sketch, scale: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = Path()
        sketch.strokes.forEach { s ->
            if (s.points.size < 2) return@forEach
            paint.color = s.color
            paint.strokeWidth = s.width * scale
            path.reset()
            path.moveTo(s.points[0] * scale, s.points[1] * scale)
            var i = 2
            while (i + 1 < s.points.size) {
                path.lineTo(s.points[i] * scale, s.points[i + 1] * scale)
                i += 2
            }
            if (s.points.size == 2) path.lineTo(s.points[0] * scale + 0.1f, s.points[1] * scale)
            canvas.drawPath(path, paint)
        }
    }
}

/** Not metninin parçaları: yazı ya da çizim. */
sealed interface BodyPart {
    data class Text(val text: String) : BodyPart
    data class Drawing(val id: String) : BodyPart
}

/** Not metnini yazı ve çizim parçalarına ayırır; her çizimin önünde ve arkasında birer yazı parçası olur. */
fun splitBody(body: String): List<BodyPart> {
    val out = ArrayList<BodyPart>()
    val cur = StringBuilder()
    var fresh = true
    for (line in body.split("\n")) {
        if (line.startsWith(Sketches.MARKER)) {
            out += BodyPart.Text(cur.toString())
            cur.clear()
            fresh = true
            out += BodyPart.Drawing(line.removePrefix(Sketches.MARKER))
        } else {
            if (!fresh) cur.append('\n')
            cur.append(line)
            fresh = false
        }
    }
    out += BodyPart.Text(cur.toString())
    return out
}

fun joinBody(parts: List<BodyPart>): String = parts.joinToString("\n") {
    when (it) {
        is BodyPart.Text -> it.text
        is BodyPart.Drawing -> Sketches.MARKER + it.id
    }
}
