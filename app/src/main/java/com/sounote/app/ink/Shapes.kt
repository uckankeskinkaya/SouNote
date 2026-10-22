package com.sounote.app.ink

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Şekil aracı: başlangıç ve bitiş noktasından şeklin çizgi noktaları (x, y, basınç). */
object Shapes {
    fun points(kind: ShapeKind, x0: Float, y0: Float, x1: Float, y1: Float, width: Float): FloatArray {
        val pts = ArrayList<Float>()
        fun add(x: Float, y: Float) {
            pts += x; pts += y; pts += 1f
        }
        when (kind) {
            ShapeKind.LINE -> {
                add(x0, y0)
                add(x1, y1)
            }
            ShapeKind.ARROW -> {
                // Tek çizgi hâlinde: gövde, sonra uçtan iki kanada gidip dön.
                val angle = atan2(y1 - y0, x1 - x0)
                val len = hypot(x1 - x0, y1 - y0)
                val head = (len * 0.25f).coerceIn(width * 3f, width * 3f + 40f)
                val spread = (PI / 7).toFloat()
                add(x0, y0)
                add(x1, y1)
                add(x1 - head * cos(angle - spread), y1 - head * sin(angle - spread))
                add(x1, y1)
                add(x1 - head * cos(angle + spread), y1 - head * sin(angle + spread))
            }
            ShapeKind.RECT -> {
                add(x0, y0); add(x1, y0); add(x1, y1); add(x0, y1); add(x0, y0)
            }
            ShapeKind.ELLIPSE -> {
                val cx = (x0 + x1) / 2
                val cy = (y0 + y1) / 2
                val rx = abs(x1 - x0) / 2
                val ry = abs(y1 - y0) / 2
                val n = 72
                for (k in 0..n) {
                    val a = 2.0 * PI * k / n
                    add(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat())
                }
            }
            ShapeKind.TRIANGLE -> {
                add((x0 + x1) / 2, y0); add(x1, y1); add(x0, y1); add((x0 + x1) / 2, y0)
            }
        }
        return pts.toFloatArray()
    }
}
