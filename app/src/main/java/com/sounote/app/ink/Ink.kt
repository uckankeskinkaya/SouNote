package com.sounote.app.ink

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

enum class InkTool { PEN, HIGHLIGHTER }

/**
 * Tek bir çizgi. Koordinatlar sayfanın PDF noktası (1/72 inç) cinsinden; böylece
 * yakınlaştırmadan bağımsızdır ve PDF'e birebir aktarılır.
 * [points]: x, y, basınç üçlüleri. [width]: basınç 0.5'teyken çizgi kalınlığı (nokta).
 */
class Stroke(
    val tool: InkTool,
    val color: Int,
    val width: Float,
    val pressureSensitive: Boolean,
    val points: FloatArray,
) {
    val pointCount get() = points.size / 3

    /** Çizginin kapladığı alan (kalınlık dahil). */
    val bounds: RectF = RectF().also { b ->
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in 0 until pointCount) {
            minX = min(minX, points[i * 3])
            minY = min(minY, points[i * 3 + 1])
            maxX = max(maxX, points[i * 3])
            maxY = max(maxY, points[i * 3 + 1])
        }
        val pad = maxHalfWidth
        b.set(minX - pad, minY - pad, maxX + pad, maxY + pad)
    }

    val maxHalfWidth get() = width * (if (pressureSensitive) StrokeRenderer.MAX_PRESSURE_FACTOR else 1f) / 2f
}

/** Çizim sırasında noktaları biriktirir. */
class StrokeBuilder(
    val tool: InkTool,
    val color: Int,
    val width: Float,
    val pressureSensitive: Boolean,
) {
    var points = FloatArray(3 * 256)
        private set
    var count = 0
        private set

    val lastX get() = points[(count - 1) * 3]
    val lastY get() = points[(count - 1) * 3 + 1]
    val lastPressure get() = points[(count - 1) * 3 + 2]

    fun add(x: Float, y: Float, pressure: Float) {
        if ((count + 1) * 3 > points.size) points = points.copyOf(points.size * 2)
        points[count * 3] = x
        points[count * 3 + 1] = y
        points[count * 3 + 2] = pressure
        count++
    }

    /** Son noktayı değiştirir (kalem kalkarken son konumu kaybetmemek için). */
    fun replaceLast(x: Float, y: Float, pressure: Float) {
        points[(count - 1) * 3] = x
        points[(count - 1) * 3 + 1] = y
        points[(count - 1) * 3 + 2] = pressure
    }

    fun build() = Stroke(tool, color, width, pressureSensitive, points.copyOf(count * 3))
}

/** Bir belgenin bütün çizimleri, sayfa sayfa. */
class InkDocument {
    private val pages = HashMap<Int, List<Stroke>>()

    /** Her değişiklikte çağrılır (4. adımda otomatik kayıt buna bağlanacak). */
    var onChanged: ((page: Int) -> Unit)? = null

    fun strokes(page: Int): List<Stroke> = pages[page] ?: emptyList()

    fun add(page: Int, stroke: Stroke) {
        pages[page] = strokes(page) + stroke
        onChanged?.invoke(page)
    }

    fun replace(page: Int, strokes: List<Stroke>) {
        if (strokes.isEmpty()) pages.remove(page) else pages[page] = strokes
        onChanged?.invoke(page)
    }

    val isEmpty get() = pages.isEmpty()
}
