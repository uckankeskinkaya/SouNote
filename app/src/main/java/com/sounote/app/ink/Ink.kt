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

/**
 * Sayfaya klavyeyle yazılmış metin. Konum ve boyutlar sayfa noktası cinsinden;
 * [x],[y] kutunun sol üst köşesi, [width] satır kaydırma genişliği.
 */
data class TextBox(
    val id: Long,
    val x: Float,
    val y: Float,
    val width: Float,
    val fontSize: Float,
    val color: Int,
    val text: String,
)

/** Bir sayfanın bütün notları. Değişmez: her değişiklik yeni bir nesne üretir. */
data class PageContent(
    val strokes: List<Stroke> = emptyList(),
    val texts: List<TextBox> = emptyList(),
) {
    val isEmpty get() = strokes.isEmpty() && texts.isEmpty()

    companion object {
        val EMPTY = PageContent()
    }
}

/** Bir belgenin bütün notları (sayfa sayfa) ve geri al / ileri al geçmişi. */
class InkDocument {
    private val pages = HashMap<Int, PageContent>()

    class Change(val page: Int, val before: PageContent, val after: PageContent)

    private val undoStack = ArrayDeque<List<Change>>()
    private val redoStack = ArrayDeque<List<Change>>()
    private val listeners = mutableListOf<(page: Int) -> Unit>()
    private val historyListeners = mutableListOf<() -> Unit>()

    fun content(page: Int): PageContent = pages[page] ?: PageContent.EMPTY
    fun strokes(page: Int): List<Stroke> = content(page).strokes
    fun texts(page: Int): List<TextBox> = content(page).texts

    /** Not içeren sayfalar ve içerikleri (kopya). */
    fun snapshot(): Map<Int, PageContent> = HashMap(pages)

    val isEmpty get() = pages.isEmpty()
    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    fun addListener(l: (page: Int) -> Unit) { listeners += l }
    fun removeListener(l: (page: Int) -> Unit) { listeners -= l }
    fun addHistoryListener(l: () -> Unit) { historyListeners += l }
    fun removeHistoryListener(l: () -> Unit) { historyListeners -= l }

    /** Diskten yüklerken: dinleyicilere haber verilmez, geçmişe yazılmaz. */
    fun load(content: Map<Int, PageContent>) {
        pages.clear()
        content.forEach { (page, c) -> if (!c.isEmpty) pages[page] = c }
    }

    /** Geçmişe yazmadan değiştirir (silgi ya da sürükleme sürerken). Sonunda [record] çağrılmalı. */
    fun set(page: Int, content: PageContent) {
        if (content.isEmpty) pages.remove(page) else pages[page] = content
        listeners.toList().forEach { it(page) }
    }

    /** Tek adımlık değişiklik; geri alınabilir. */
    fun commit(page: Int, content: PageContent) {
        val before = content(page)
        if (before == content) return
        set(page, content)
        record(listOf(Change(page, before, content)))
    }

    fun addStroke(page: Int, stroke: Stroke) {
        val c = content(page)
        commit(page, c.copy(strokes = c.strokes + stroke))
    }

    /** [set] ile yapılmış değişiklikleri tek bir geri alma adımı olarak kaydeder. */
    fun record(changes: List<Change>) {
        val real = changes.filter { it.before != it.after }
        if (real.isEmpty()) return
        undoStack.addLast(real)
        while (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        historyListeners.toList().forEach { it() }
    }

    fun undo(): Boolean {
        val changes = undoStack.removeLastOrNull() ?: return false
        changes.asReversed().forEach { set(it.page, it.before) }
        redoStack.addLast(changes)
        historyListeners.toList().forEach { it() }
        return true
    }

    fun redo(): Boolean {
        val changes = redoStack.removeLastOrNull() ?: return false
        changes.forEach { set(it.page, it.after) }
        undoStack.addLast(changes)
        historyListeners.toList().forEach { it() }
        return true
    }

    companion object {
        private const val MAX_HISTORY = 200
    }
}
