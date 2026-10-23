package com.sounote.app.ink

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Elle çizilmiş çizgiyi düzgün şekle çevirir: düz çizgi, elips/daire, dikdörtgen, üçgen, dörtgen.
 * Girdi ve çıktı: x, y, basınç üçlüleri (sayfa noktası). Tanınmazsa null.
 */
object ShapeRecognizer {

    class Shape(val points: FloatArray, val count: Int)

    fun recognize(pts: FloatArray, n: Int, lineOnly: Boolean): Shape? {
        if (n < 3) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var length = 0f
        var pressureSum = 0f
        for (i in 0 until n) {
            val x = pts[i * 3]
            val y = pts[i * 3 + 1]
            minX = min(minX, x); maxX = max(maxX, x)
            minY = min(minY, y); maxY = max(maxY, y)
            pressureSum += pts[i * 3 + 2]
            if (i > 0) length += hypot(x - pts[i * 3 - 3], y - pts[i * 3 - 2])
        }
        val diag = hypot(maxX - minX, maxY - minY)
        // Küçük çizgiler (el yazısı harfleri) şekle çevrilmesin.
        if (diag < 14f || length < 20f) return null
        val p = pressureSum / n

        val sx = pts[0]
        val sy = pts[1]
        val ex = pts[(n - 1) * 3]
        val ey = pts[(n - 1) * 3 + 1]

        line(pts, n, sx, sy, ex, ey, p)?.let { return it }
        if (lineOnly) return null

        // Kapalı şekil mi: başlangıç ve bitiş yakın olmalı.
        if (hypot(ex - sx, ey - sy) > diag * 0.3f) return null
        polygon(pts, n, diag, p)?.let { return it }
        return ellipse(pts, n, minX, minY, maxX, maxY, p)
    }

    /** Uçları birleştiren doğrudan sapma çok azsa düz çizgi; yataya/dikeye/45°'ye yakınsa ona oturt. */
    private fun line(pts: FloatArray, n: Int, sx: Float, sy: Float, ex: Float, ey: Float, p: Float): Shape? {
        val len = hypot(ex - sx, ey - sy)
        if (len < 10f) return null
        var maxDev = 0f
        for (i in 0 until n) maxDev = max(maxDev, Eraser.distPointSeg(pts[i * 3], pts[i * 3 + 1], sx, sy, ex, ey))
        if (maxDev > max(len * 0.06f, 2f)) return null
        val angle = atan2(ey - sy, ex - sx)
        val step = (PI / 4).toFloat()
        val snapped = Math.round(angle / step) * step
        val (x2, y2) = if (abs(angle - snapped) < Math.toRadians(4.0).toFloat()) {
            (sx + cos(snapped) * len) to (sy + sin(snapped) * len)
        } else {
            ex to ey
        }
        return Shape(floatArrayOf(sx, sy, p, x2, y2, p), 2)
    }

    /** Köşeleri belirgin kapalı çokgen: 3 köşe üçgen, 4 köşe dikdörtgen/dörtgen. */
    private fun polygon(pts: FloatArray, n: Int, diag: Float, p: Float): Shape? {
        val idx = simplify(pts, n, diag * 0.06f)
        val v = ArrayList<FloatArray>()
        for (i in idx) v += floatArrayOf(pts[i * 3], pts[i * 3 + 1])
        // Başlangıç ve bitiş aynı köşe sayılır.
        if (v.size >= 2 && hypot(v.first()[0] - v.last()[0], v.first()[1] - v.last()[1]) < diag * 0.3f) {
            v.removeAt(v.size - 1)
        }
        // Neredeyse düz devam eden (dönüşü küçük) köşeleri at; başlangıç çoğu zaman kenarın ortasındadır.
        var changed = true
        while (changed && v.size > 3) {
            changed = false
            for (i in v.indices) {
                if (turn(v[(i - 1 + v.size) % v.size], v[i], v[(i + 1) % v.size]) < 30.0) {
                    v.removeAt(i)
                    changed = true
                    break
                }
            }
        }
        if (v.size != 3 && v.size != 4) return null
        // Her köşe belirgin olmalı (yuvarlak şekiller burada elenir).
        for (i in v.indices) {
            if (turn(v[(i - 1 + v.size) % v.size], v[i], v[(i + 1) % v.size]) < 45.0) return null
        }
        // Kenarlar el çizimine yeterince yakın mı: her nokta en yakın kenara uzak olmamalı.
        var maxDev = 0f
        for (i in 0 until n) {
            var best = Float.MAX_VALUE
            for (k in v.indices) {
                val a = v[k]
                val b = v[(k + 1) % v.size]
                best = min(best, Eraser.distPointSeg(pts[i * 3], pts[i * 3 + 1], a[0], a[1], b[0], b[1]))
            }
            maxDev = max(maxDev, best)
        }
        if (maxDev > diag * 0.1f) return null

        val corners = if (v.size == 4 && isAxisAlignedRect(v)) {
            val xs = v.map { it[0] }.sorted()
            val ys = v.map { it[1] }.sorted()
            val l = (xs[0] + xs[1]) / 2f
            val r = (xs[2] + xs[3]) / 2f
            val t = (ys[0] + ys[1]) / 2f
            val b = (ys[2] + ys[3]) / 2f
            listOf(floatArrayOf(l, t), floatArrayOf(r, t), floatArrayOf(r, b), floatArrayOf(l, b))
        } else {
            v
        }
        val xy = FloatArray((corners.size + 1) * 2)
        for (k in 0..corners.size) {
            val c = corners[k % corners.size]
            xy[k * 2] = c[0]
            xy[k * 2 + 1] = c[1]
        }
        // Köşeler yumuşatılıp yuvarlanmasın.
        val out = Shapes.sharp(xy, p)
        return Shape(out, out.size / 3)
    }

    private fun isAxisAlignedRect(v: List<FloatArray>): Boolean {
        for (i in v.indices) {
            val a = v[i]
            val b = v[(i + 1) % v.size]
            val deg = Math.toDegrees(atan2((b[1] - a[1]).toDouble(), (b[0] - a[0]).toDouble()))
            val off = abs(((deg % 90) + 90) % 90)
            if (min(off, 90 - off) > 12) return false
        }
        return true
    }

    /** Eksenlere hizalı elips; en/boy yakınsa daire. */
    private fun ellipse(pts: FloatArray, n: Int, minX: Float, minY: Float, maxX: Float, maxY: Float, p: Float): Shape? {
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        var rx = (maxX - minX) / 2f
        var ry = (maxY - minY) / 2f
        if (rx < 4f || ry < 4f) return null
        var err = 0f
        for (i in 0 until n) {
            val dx = (pts[i * 3] - cx) / rx
            val dy = (pts[i * 3 + 1] - cy) / ry
            err += abs(sqrt(dx * dx + dy * dy) - 1f)
        }
        if (err / n > 0.13f) return null
        if (abs(rx - ry) / max(rx, ry) < 0.15f) {
            val r = (rx + ry) / 2f
            rx = r
            ry = r
        }
        val segments = 72
        val out = FloatArray((segments + 1) * 3)
        for (k in 0..segments) {
            val a = 2.0 * PI * k / segments
            out[k * 3] = cx + rx * cos(a).toFloat()
            out[k * 3 + 1] = cy + ry * sin(a).toFloat()
            out[k * 3 + 2] = p
        }
        return Shape(out, segments + 1)
    }

    /** Köşede yön değişimi (derece): 0 düz devam, 180 tam geri dönüş. */
    private fun turn(a: FloatArray, b: FloatArray, c: FloatArray): Double {
        val a1 = atan2((b[1] - a[1]).toDouble(), (b[0] - a[0]).toDouble())
        val a2 = atan2((c[1] - b[1]).toDouble(), (c[0] - b[0]).toDouble())
        var d = Math.toDegrees(a2 - a1)
        while (d > 180) d -= 360
        while (d < -180) d += 360
        return abs(d)
    }

    /** Ramer–Douglas–Peucker: çizgiyi köşe noktalarına indirger. Dönen: nokta indisleri. */
    private fun simplify(pts: FloatArray, n: Int, epsilon: Float): List<Int> {
        val keep = BooleanArray(n)
        keep[0] = true
        keep[n - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to n - 1)
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            var best = -1
            var bestDist = 0f
            for (i in a + 1 until b) {
                val d = Eraser.distPointSeg(pts[i * 3], pts[i * 3 + 1], pts[a * 3], pts[a * 3 + 1], pts[b * 3], pts[b * 3 + 1])
                if (d > bestDist) {
                    bestDist = d
                    best = i
                }
            }
            // Kapalı şekilde a ve b neredeyse aynı nokta: en uzak noktayı her durumda tut.
            if (best >= 0 && (bestDist > epsilon || (a == 0 && b == n - 1))) {
                keep[best] = true
                stack.addLast(a to best)
                stack.addLast(best to b)
            }
        }
        return (0 until n).filter { keep[it] }
    }
}
