package com.sounote.app.ink

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Şekil aracı: başlangıç ve bitiş noktasından şeklin çizgi noktaları (x, y, basınç). */
object Shapes {
    fun points(kind: ShapeKind, x0: Float, y0: Float, x1: Float, y1: Float, width: Float): FloatArray = when (kind) {
        ShapeKind.LINE -> sharp(floatArrayOf(x0, y0, x1, y1))
        ShapeKind.ARROW -> {
            // Tek çizgi hâlinde: gövde, sonra uçtan iki kanada gidip dön.
            val angle = atan2(y1 - y0, x1 - x0)
            val len = hypot(x1 - x0, y1 - y0)
            val head = (len * 0.25f).coerceIn(width * 3f, width * 3f + 40f)
            val spread = (PI / 7).toFloat()
            sharp(
                floatArrayOf(
                    x0, y0, x1, y1,
                    x1 - head * cos(angle - spread), y1 - head * sin(angle - spread),
                    x1, y1,
                    x1 - head * cos(angle + spread), y1 - head * sin(angle + spread),
                ),
            )
        }
        ShapeKind.RECT -> sharp(floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1, x0, y0))
        ShapeKind.TRIANGLE -> sharp(floatArrayOf((x0 + x1) / 2, y0, x1, y1, x0, y1, (x0 + x1) / 2, y0))
        ShapeKind.ELLIPSE -> {
            val cx = (x0 + x1) / 2
            val cy = (y0 + y1) / 2
            val rx = abs(x1 - x0) / 2
            val ry = abs(y1 - y0) / 2
            val n = 72
            val out = FloatArray((n + 1) * 3)
            for (k in 0..n) {
                val a = 2.0 * PI * k / n
                out[k * 3] = cx + rx * cos(a).toFloat()
                out[k * 3 + 1] = cy + ry * sin(a).toFloat()
                out[k * 3 + 2] = 1f
            }
            out
        }
    }

    /**
     * Köşeli çizgi noktaları (x, y, basınç). Çizim noktalar arasını yumuşattığı için köşeler yuvarlanıyordu;
     * her köşenin hemen önüne ve arkasına nokta konunca köşe sivri kalır, kenarlar düz kalır.
     */
    fun sharp(xy: FloatArray, pressure: Float = 1f): FloatArray {
        val n = xy.size / 2
        val out = ArrayList<Float>(n * 9)
        fun add(x: Float, y: Float) {
            out += x; out += y; out += pressure
        }
        for (i in 0 until n) {
            val x = xy[i * 2]
            val y = xy[i * 2 + 1]
            if (i > 0) {
                val px = xy[(i - 1) * 2]
                val py = xy[(i - 1) * 2 + 1]
                val len = hypot(x - px, y - py)
                if (len > 0f) {
                    val e = minOf(0.3f, len * 0.1f) / len
                    add(px + (x - px) * e, py + (y - py) * e)
                    add(x - (x - px) * e, y - (y - py) * e)
                }
            }
            add(x, y)
        }
        return out.toFloatArray()
    }
}
