package com.sounote.app.ink

import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Akıllı fosforlu: elle çizilen fosforluyu altındaki metin satır(lar)ına oturtur.
 * Metin alanları PDF'ten gelir (Android 15+). Uygun satır bulunamazsa null döner, çizgi olduğu gibi kalır.
 */
object SmartHighlighter {

    fun snap(stroke: Stroke, textRects: List<RectF>): List<Stroke>? {
        if (textRects.isEmpty() || stroke.pointCount < 2) return null
        val p = stroke.points
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in 0 until stroke.pointCount) {
            minX = min(minX, p[i * 3]); maxX = max(maxX, p[i * 3])
            minY = min(minY, p[i * 3 + 1]); maxY = max(maxY, p[i * 3 + 1])
        }
        if (maxX - minX < 6f) return null
        val half = stroke.width / 2
        val bandTop = minY - half
        val bandBottom = maxY + half

        // Satır yüksekliğindeki alanlar (paragraf bloklarını dışarıda bırak), çizginin altında kalanlar
        val candidates = textRects.filter { r ->
            val h = r.height()
            h in 2f..60f &&
                r.right > minX && r.left < maxX &&
                overlap(r.top, r.bottom, bandTop, bandBottom) >= h * 0.4f
        }
        if (candidates.isEmpty()) return null

        // Satırlara grupla (dikey merkezleri yakın olanlar aynı satır)
        val lines = ArrayList<MutableList<RectF>>()
        for (r in candidates.sortedBy { it.centerY() }) {
            val line = lines.lastOrNull()
            if (line != null && abs(line.first().centerY() - r.centerY()) < line.first().height() * 0.5f) line += r else lines += mutableListOf(r)
        }

        return lines.map { line ->
            val top = line.minOf { it.top }
            val bottom = line.maxOf { it.bottom }
            val h = bottom - top
            // Kelimelere oturt, ama çizginin çok ötesine taşma (satırın tamamı tek alan olabilir).
            val left = max(line.minOf { it.left }, minX - h)
            val right = min(line.maxOf { it.right }, maxX + h)
            val cy = (top + bottom) / 2
            Stroke(stroke.tool, stroke.color, h * 1.15f, false, floatArrayOf(left, cy, 1f, right, cy, 1f))
        }
    }

    private fun overlap(a1: Float, a2: Float, b1: Float, b2: Float) = max(0f, min(a2, b2) - max(a1, b1))
}
