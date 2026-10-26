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
 * Kalınlığı değişen çizgilerin (basınçlı dolma/jel kalem, fırça, kaligrafi) dış hattı.
 * Çizgi, iki kenarı ve yuvarlak uçlarıyla tek bir kapalı şekil olarak doldurulur: kalınlık kademeleri
 * arasında boncuklanma olmaz. Kalınlık basınçtan yumuşatılarak çıkarılır; başta ve sonda kısa bir incelme
 * kalemin durduğu yerde damla oluşmasını önler. Ekran (StrokeRenderer) ve PDF (PdfExporter) aynı hattı kullanır.
 */
object StrokeOutline {

    /** Bu çizgi dış hatla mı çizilir (kalınlığı değişiyor mu). */
    fun variable(tool: InkTool, pressureSensitive: Boolean) = when (tool) {
        InkTool.PEN -> pressureSensitive
        InkTool.BRUSH, InkTool.CALLIGRAPHY -> true
        else -> false
    }

    /** Hat: [poly] kapalı çokgen (x,y çiftleri, [count] köşe); [dots] keskin dönüşlerde eklenen daireler (x, y, r). */
    class Outline(val poly: FloatArray, val count: Int, val dots: FloatArray, val dotCount: Int)

    /** Dolma/jel: basınç 0.5 → seçilen kalınlık. Dar aralık: hafif ve sert basınç arasında ~2 kat fark. */
    fun penFactor(p: Float) = (0.62f + 0.76f * p).coerceIn(0.62f, 1.38f)

    private fun brushFactor(p: Float) = (0.2f + 1.6f * p).coerceIn(0.2f, 1.8f)

    /** Kaligrafi ucunun açısı: bu yönde giden çizgi en ince olur (sağ üste doğru). */
    private val NIB_ANGLE = (-PI / 4).toFloat()

    /**
     * [taper]: uçlarda incelme ve yumuşatma (bitmiş çizgi). Ön tamponda parça parça çizerken false:
     * parçaların birleştiği yerde incelme görünmesin.
     */
    fun build(tool: InkTool, width: Float, pressureSensitive: Boolean, pts: FloatArray, n: Int, taper: Boolean = true): Outline {
        // 1) Çok yakın noktaları at (kalem dururken biriken noktalar damla yapıyordu)
        val minGap = max(width * 0.12f, 0.08f)
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        val ps = FloatArray(n)
        var m = 0
        for (i in 0 until n) {
            val x = pts[i * 3]
            val y = pts[i * 3 + 1]
            val p = pts[i * 3 + 2]
            if (m > 0 && i < n - 1 && hypot(x - xs[m - 1], y - ys[m - 1]) < minGap) {
                // Basıncı birleştir ki dururken bastırma ani kalınlaşma yapmasın
                ps[m - 1] = (ps[m - 1] + p) / 2f
                continue
            }
            if (m > 0 && i == n - 1 && hypot(x - xs[m - 1], y - ys[m - 1]) < minGap) {
                xs[m - 1] = x
                ys[m - 1] = y
                continue
            }
            xs[m] = x
            ys[m] = y
            ps[m] = p
            m++
        }
        // 2) Her noktanın kalınlık katsayısı
        val dist = FloatArray(m)
        for (i in 1 until m) dist[i] = dist[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        val total = if (m > 0) dist[m - 1] else 0f
        val f = FloatArray(m)
        for (i in 0 until m) {
            f[i] = when (tool) {
                InkTool.PEN -> penFactor(ps[i])
                InkTool.BRUSH -> if (pressureSensitive) brushFactor(ps[i]) else 1f
                InkTool.CALLIGRAPHY -> {
                    val a = max(0, i - 2)
                    val b = min(m - 1, i + 2)
                    val dx = xs[b] - xs[a]
                    val dy = ys[b] - ys[a]
                    if (a == b || (abs(dx) < 1e-4f && abs(dy) < 1e-4f)) 0.7f
                    else 0.18f + 1.0f * abs(sin(atan2(dy, dx) - NIB_ANGLE))
                }
                else -> 1f
            }
        }
        // Basınç gürültüsünü yumuşat (ileri + geri)
        if (m > 2) {
            val a = if (tool == InkTool.CALLIGRAPHY) 0.5f else 0.3f
            for (i in 1 until m) f[i] = f[i - 1] + (f[i] - f[i - 1]) * a
            for (i in m - 2 downTo 0) f[i] = f[i + 1] + (f[i] - f[i + 1]) * a
        }
        if (taper && m > 1) {
            // Uçlarda incelme: fırçada belirgin, kalemde hafif (damla yerine doğal uç)
            val (len, minT) = when (tool) {
                InkTool.BRUSH -> max(width * 3f, 4f) to 0.2f
                InkTool.CALLIGRAPHY -> max(width * 1.2f, 1.5f) to 0.6f
                else -> max(width * 2.2f, 2f) to 0.5f
            }
            val l = min(len, total / 2f).coerceAtLeast(1e-3f)
            for (i in 0 until m) {
                val t = min(1f, min(dist[i], total - dist[i]) / l)
                // Yumuşak geçiş (smoothstep)
                val s = t * t * (3f - 2f * t)
                f[i] *= minT + (1f - minT) * s
            }
        }
        if (m == 1) {
            val r = width * f[0] / 2f
            return Outline(circle(xs[0], ys[0], r), 16, FloatArray(0), 0)
        }

        // 3) Kenarlar: her noktada teğete dik, yarıçap kadar sağa ve sola
        val lx = FloatArray(m)
        val ly = FloatArray(m)
        val rx = FloatArray(m)
        val ry = FloatArray(m)
        val r = FloatArray(m) { width * f[it] / 2f }
        var tx = 1f
        var ty = 0f
        val dots = ArrayList<Float>()
        for (i in 0 until m) {
            val a = max(0, i - 1)
            val b = min(m - 1, i + 1)
            var dx = xs[b] - xs[a]
            var dy = ys[b] - ys[a]
            val d = sqrt(dx * dx + dy * dy)
            if (d > 1e-5f) {
                dx /= d
                dy /= d
                tx = dx
                ty = dy
            }
            lx[i] = xs[i] - ty * r[i]
            ly[i] = ys[i] + tx * r[i]
            rx[i] = xs[i] + ty * r[i]
            ry[i] = ys[i] - tx * r[i]
            // Keskin dönüş: dış kenarda boşluk kalmasın diye nokta yerinde daire
            if (i in 1 until m - 1) {
                val ax = xs[i] - xs[i - 1]
                val ay = ys[i] - ys[i - 1]
                val bx = xs[i + 1] - xs[i]
                val by = ys[i + 1] - ys[i]
                val la = hypot(ax, ay)
                val lb = hypot(bx, by)
                if (la > 1e-5f && lb > 1e-5f) {
                    val c = ((ax * bx + ay * by) / (la * lb)).coerceIn(-1f, 1f)
                    // Dönüş kalınlığa göre sıkıysa iç kenar kendini keser: orayı daireyle kapat
                    val turn = kotlin.math.acos(c)
                    if (c < 0.6f || turn * r[i] > min(la, lb) * 0.9f) {
                        dots += xs[i]; dots += ys[i]; dots += r[i]
                    }
                }
            }
        }
        // 4) Kapalı çokgen: sol kenar → son uç (yarım daire) → sağ kenar (geri) → ilk uç
        val cap = 8
        val poly = FloatArray((m * 2 + cap * 2 + 2) * 2)
        var k = 0
        fun put(x: Float, y: Float) {
            poly[k++] = x
            poly[k++] = y
        }
        for (i in 0 until m) put(lx[i], ly[i])
        capPoints(xs[m - 1], ys[m - 1], lx[m - 1], ly[m - 1], r[m - 1], endTangent(xs, ys, m, true), cap, ::put)
        for (i in m - 1 downTo 0) put(rx[i], ry[i])
        capPoints(xs[0], ys[0], rx[0], ry[0], r[0], endTangent(xs, ys, m, false), cap, ::put)
        return Outline(poly, k / 2, dots.toFloatArray(), dots.size / 3)
    }

    /** Ucun dışa bakan yönü (sonda ileri, başta geri). */
    private fun endTangent(xs: FloatArray, ys: FloatArray, m: Int, end: Boolean): Pair<Float, Float> {
        val (a, b) = if (end) (m - 2) to (m - 1) else 1 to 0
        val dx = xs[b] - xs[a]
        val dy = ys[b] - ys[a]
        val d = hypot(dx, dy)
        return if (d < 1e-5f) 1f to 0f else (dx / d) to (dy / d)
    }

    /** [sx],[sy] kenar noktasından merkez etrafında dışa doğru yarım daire (uç noktalar hariç). */
    private inline fun capPoints(
        cx: Float, cy: Float, sx: Float, sy: Float, r: Float, out: Pair<Float, Float>, steps: Int, put: (Float, Float) -> Unit,
    ) {
        val a0 = atan2(sy - cy, sx - cx)
        // Dönüş yönü: yarı yolda dışa (out) bakmalı
        val mid = a0 + (PI / 2).toFloat()
        val sign = if (cos(mid) * out.first + sin(mid) * out.second >= 0f) 1f else -1f
        for (s in 1 until steps) {
            val a = a0 + sign * (PI.toFloat() * s / steps)
            put(cx + cos(a) * r, cy + sin(a) * r)
        }
    }

    fun circle(cx: Float, cy: Float, r: Float, steps: Int = 16): FloatArray = FloatArray(steps * 2).also {
        for (s in 0 until steps) {
            val a = 2 * PI.toFloat() * s / steps
            it[s * 2] = cx + cos(a) * r
            it[s * 2 + 1] = cy + sin(a) * r
        }
    }
}
