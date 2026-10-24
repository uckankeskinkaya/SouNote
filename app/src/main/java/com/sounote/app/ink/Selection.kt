package com.sounote.app.ink

import android.graphics.RectF

/** Lasso ile seçilmiş öğeler (tek sayfa). Çizgiler nesne kimliğiyle, yazı ve resimler kimlik numarasıyla tutulur. */
class Selection(
    val page: Int,
    val strokes: Set<Stroke>,
    val textIds: Set<Long>,
    val imageIds: Set<Long>,
) {
    val count get() = strokes.size + textIds.size + imageIds.size
    val isEmpty get() = count == 0
}

/** Kopyalanan öğeler (yapıştırmak için). */
class Clip(val strokes: List<Stroke>, val texts: List<TextBox>, val images: List<ImageItem>)

object Selections {

    /** Lasso çokgeninin (x,y çiftleri, sayfa noktası) içinde kalan öğeler. */
    fun select(page: Int, c: PageContent, poly: FloatArray, n: Int, canSelect: (Stroke) -> Boolean = { true }): Selection {
        if (n < 3) return Selection(page, emptySet(), emptySet(), emptySet())
        val strokes = c.strokes.filterTo(HashSet()) { s ->
            if (!canSelect(s)) return@filterTo false
            // Noktalarının yarısı içerideyse seçilir: kenardan taşan çizgiler de yakalanır.
            var inside = 0
            for (i in 0 until s.pointCount) if (contains(poly, n, s.points[i * 3], s.points[i * 3 + 1])) inside++
            inside * 2 >= s.pointCount && inside > 0
        }
        val r = RectF()
        val texts = c.texts.filter { TextBoxes.bounds(it, r); contains(poly, n, r.centerX(), r.centerY()) }.map { it.id }.toSet()
        val images = c.images.filter { contains(poly, n, it.x + it.width / 2, it.y + it.height / 2) }.map { it.id }.toSet()
        return Selection(page, strokes, texts, images)
    }

    /** Işın yöntemiyle nokta-çokgen testi. */
    private fun contains(poly: FloatArray, n: Int, x: Float, y: Float): Boolean {
        var inside = false
        var j = n - 1
        for (i in 0 until n) {
            val xi = poly[i * 2]
            val yi = poly[i * 2 + 1]
            val xj = poly[j * 2]
            val yj = poly[j * 2 + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    fun bounds(c: PageContent, sel: Selection, out: RectF = RectF()): RectF {
        out.setEmpty()
        val r = RectF()
        c.strokes.forEach { if (it in sel.strokes) union(out, it.bounds) }
        c.texts.forEach { if (it.id in sel.textIds) union(out, TextBoxes.visualBounds(it, r)) }
        c.images.forEach {
            if (it.id in sel.imageIds) {
                r.set(it.x, it.y, it.x + it.width, it.y + it.height)
                if (it.rotation != 0f) Rotation.boundsOf(r, it.rotation, r)
                union(out, r)
            }
        }
        return out
    }

    private fun union(out: RectF, r: RectF) {
        if (out.isEmpty) out.set(r) else out.union(r)
    }

    /**
     * Seçimi (ax, ay) etrafında [scale] kadar büyütüp (tx, ty) kadar kaydırır.
     * Yeni sayfa içeriğini ve yeni nesnelerle güncellenmiş seçimi döndürür.
     */
    fun transform(
        c: PageContent, sel: Selection, scale: Float, ax: Float, ay: Float, tx: Float, ty: Float,
    ): Pair<PageContent, Selection> {
        fun mx(x: Float) = ax + (x - ax) * scale + tx
        fun my(y: Float) = ay + (y - ay) * scale + ty
        val newStrokes = HashSet<Stroke>()
        val strokes = c.strokes.map { s ->
            if (s !in sel.strokes) return@map s
            val p = s.points.copyOf()
            for (i in 0 until s.pointCount) {
                p[i * 3] = mx(p[i * 3])
                p[i * 3 + 1] = my(p[i * 3 + 1])
            }
            s.copy(width = s.width * scale, points = p).also { newStrokes += it }
        }
        val texts = c.texts.map { t ->
            if (t.id !in sel.textIds) t
            else t.copy(x = mx(t.x), y = my(t.y), fontSize = t.fontSize * scale, width = t.width * scale)
        }
        val images = c.images.map { im ->
            if (im.id !in sel.imageIds) im
            else im.copy(x = mx(im.x), y = my(im.y), width = im.width * scale, height = im.height * scale)
        }
        return c.copy(strokes = strokes, texts = texts, images = images) to
            Selection(sel.page, newStrokes, sel.textIds, sel.imageIds)
    }

    /** Seçimi (cx, cy) etrafında [degrees] kadar döndürür. Yazı ve resimlerin merkezi döner, açıları artar. */
    fun rotate(c: PageContent, sel: Selection, degrees: Float, cx: Float, cy: Float): Pair<PageContent, Selection> {
        val newStrokes = HashSet<Stroke>()
        val strokes = c.strokes.map { s ->
            if (s !in sel.strokes) return@map s
            val p = s.points.copyOf()
            for (i in 0 until s.pointCount) {
                val (x, y) = Rotation.rotate(p[i * 3], p[i * 3 + 1], cx, cy, degrees)
                p[i * 3] = x
                p[i * 3 + 1] = y
            }
            s.copy(points = p).also { newStrokes += it }
        }
        val r = RectF()
        val texts = c.texts.map { t ->
            if (t.id !in sel.textIds) return@map t
            TextBoxes.bounds(t, r)
            val (ncx, ncy) = Rotation.rotate(r.centerX(), r.centerY(), cx, cy, degrees)
            t.copy(x = t.x + ncx - r.centerX(), y = t.y + ncy - r.centerY(), rotation = Rotation.normalize(t.rotation + degrees))
        }
        val images = c.images.map { im ->
            if (im.id !in sel.imageIds) return@map im
            val (ncx, ncy) = Rotation.rotate(im.x + im.width / 2, im.y + im.height / 2, cx, cy, degrees)
            im.copy(x = ncx - im.width / 2, y = ncy - im.height / 2, rotation = Rotation.normalize(im.rotation + degrees))
        }
        return c.copy(strokes = strokes, texts = texts, images = images) to
            Selection(sel.page, newStrokes, sel.textIds, sel.imageIds)
    }

    /** Seçili çizgi ve yazıların rengini değiştirir. */
    fun recolor(c: PageContent, sel: Selection, color: Int): Pair<PageContent, Selection> {
        val newStrokes = HashSet<Stroke>()
        val strokes = c.strokes.map { s ->
            if (s !in sel.strokes) s
            else s.copy(color = color).also { newStrokes += it }
        }
        val texts = c.texts.map { if (it.id in sel.textIds) it.copy(color = color) else it }
        return c.copy(strokes = strokes, texts = texts) to Selection(sel.page, newStrokes, sel.textIds, sel.imageIds)
    }

    fun delete(c: PageContent, sel: Selection) = c.copy(
        strokes = c.strokes.filter { it !in sel.strokes },
        texts = c.texts.filter { it.id !in sel.textIds },
        images = c.images.filter { it.id !in sel.imageIds },
    )

    fun copy(c: PageContent, sel: Selection) = Clip(
        c.strokes.filter { it in sel.strokes },
        c.texts.filter { it.id in sel.textIds },
        c.images.filter { it.id in sel.imageIds },
    )

    /** Kopyalanan öğeleri (dx, dy) kaydırarak sayfaya ekler; eklenenler seçili döner. */
    fun paste(page: Int, c: PageContent, clip: Clip, dx: Float, dy: Float, layer: Int = 0): Pair<PageContent, Selection> {
        var nextId = System.currentTimeMillis()
        val strokes = clip.strokes.map { s ->
            val p = s.points.copyOf()
            for (i in 0 until s.pointCount) {
                p[i * 3] += dx
                p[i * 3 + 1] += dy
            }
            s.copy(points = p, layer = layer)
        }
        val texts = clip.texts.map { it.copy(id = nextId++, x = it.x + dx, y = it.y + dy) }
        val images = clip.images.map { it.copy(id = nextId++, x = it.x + dx, y = it.y + dy) }
        return c.copy(strokes = c.strokes + strokes, texts = c.texts + texts, images = c.images + images) to
            Selection(page, strokes.toHashSet(), texts.map { it.id }.toSet(), images.map { it.id }.toSet())
    }
}
