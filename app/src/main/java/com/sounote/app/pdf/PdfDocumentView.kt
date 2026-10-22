package com.sounote.app.pdf

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.util.LruCache
import android.util.Size
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * PDF sayfalarını alt alta gösteren görünüm. Parmakla kaydırma, iki parmakla yakınlaştırma,
 * çift dokunuşla yakınlaştırma.
 *
 * Koordinatlar: "belge" koordinatı, yakınlaştırma 1'deyken piksel cinsinden yerleşimdir
 * (her sayfa görünüm genişliğine sığar). Ekran = belge * zoom - offset.
 *
 * Çizim üç katmanlı: önizleme (hızlı kaydırma için), temel (tam sayfa) ve detay
 * (yakınlaştırınca görünen kısım ekran çözünürlüğünde, hareket bitince çizilir).
 */
class PdfDocumentView(context: Context) : View(context) {

    var onPageChanged: ((page: Int, pageCount: Int) -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val pageGap = 12f * density

    private var renderer: PdfRenderThread? = null
    private var pageSizes: List<Size> = emptyList()
    private var pendingInitialPage = 0

    // Yerleşim (belge koordinatı)
    private var fitWidth = 0f
    private var pageTops = FloatArray(0)
    private var pageHeights = FloatArray(0)
    private var contentHeight = 0f
    private var baseWidthPx = 0

    // Görünüm durumu
    private var zoom = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var currentPage = -1

    // Önbellekler
    private val baseCache = object : LruCache<Int, Bitmap>(baseCacheBytes()) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }
    private val previewCache = object : LruCache<Int, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }

    private class Detail(val bitmap: Bitmap, val region: RectF, val pageWidthPx: Float)

    private val details = HashMap<Int, Detail>()

    // Hareket
    private var touching = false
    private var scaledThisGesture = false
    private var detailAllowed = false
    private val scroller = OverScroller(context)
    private var zoomAnimator: ValueAnimator? = null

    private val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val backgroundColor = if (night) Color.rgb(0x1E, 0x1F, 0x22) else Color.rgb(0xE6, 0xE7, 0xEA)
    private val pagePaint = Paint().apply { color = Color.WHITE }
    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = if (night) Color.rgb(0x3A, 0x3B, 0x40) else Color.rgb(0xC8, 0xC9, 0xCE)
    }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pageRect = RectF()
    private val tmpRect = RectF()

    private val renderListener = object : PdfRenderThread.Listener {
        override fun onOpened(pageSizes: List<Size>) {
            this@PdfDocumentView.pageSizes = pageSizes
            relayout(initial = true)
        }

        override fun onRendered(task: RenderTask, bitmap: Bitmap) {
            when (task) {
                is RenderTask.Preview -> previewCache.put(task.page, bitmap)
                is RenderTask.Base -> if (task.width == baseWidthPx) baseCache.put(task.page, bitmap)
                is RenderTask.Detail -> if (needsDetail()) {
                    details[task.page] = Detail(bitmap, task.region, task.pageWidthPx)
                }
            }
            invalidate()
            requestRender()
        }

        override fun onFailed(error: Throwable) {
            onError?.invoke(error)
        }
    }

    fun open(uri: Uri, initialPage: Int) {
        renderer?.close()
        pendingInitialPage = initialPage
        renderer = PdfRenderThread(context, uri, renderListener)
    }

    fun release() {
        renderer?.close()
        renderer = null
        zoomAnimator?.cancel()
        removeCallbacks(enableDetail)
        baseCache.evictAll()
        previewCache.evictAll()
        details.clear()
    }

    // ---- Yerleşim ----

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout(initial = false)
    }

    private fun relayout(initial: Boolean) {
        val n = pageSizes.size
        if (width == 0 || height == 0 || n == 0) return

        // Ekranın üstündeki noktayı (sayfa + sayfa içi oran) koru; döndürünce aynı yerde kal.
        val first = initial || fitWidth == 0f
        val anchorPage: Int
        val anchorFrac: Float
        if (first) {
            anchorPage = pendingInitialPage.coerceIn(0, n - 1)
            anchorFrac = 0f
        } else {
            val topDocY = offsetY / zoom
            anchorPage = pageAt(topDocY)
            anchorFrac = ((topDocY - pageTops[anchorPage]) / pageHeights[anchorPage]).coerceIn(-0.1f, 1f)
        }

        fitWidth = width.toFloat()
        pageTops = FloatArray(n)
        pageHeights = FloatArray(n)
        var y = pageGap
        for (i in 0 until n) {
            val s = pageSizes[i]
            pageTops[i] = y
            pageHeights[i] = fitWidth * s.height / s.width
            y += pageHeights[i] + pageGap
        }
        contentHeight = y
        val newBaseWidth = min(fitWidth, MAX_BASE_WIDTH).toInt()
        if (newBaseWidth != baseWidthPx) {
            baseWidthPx = newBaseWidth
            baseCache.evictAll()
        }
        details.clear()

        offsetY = if (first) {
            (pageTops[anchorPage] - pageGap) * zoom
        } else {
            (pageTops[anchorPage] + anchorFrac * pageHeights[anchorPage]) * zoom
        }
        clampOffsets()
        onViewportChanged()
        scheduleDetail()
    }

    private fun pageAt(docY: Float): Int {
        // İkili arama: docY'nin üstünde başlayan son sayfa.
        var lo = 0
        var hi = pageTops.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (pageTops[mid] <= docY) lo = mid else hi = mid - 1
        }
        return lo
    }

    private fun visibleRange(): IntRange {
        if (pageTops.isEmpty()) return IntRange.EMPTY
        val first = pageAt(offsetY / zoom)
        val last = pageAt((offsetY + height) / zoom)
        return first..last
    }

    private fun pageScreenRect(i: Int, out: RectF): RectF {
        val left = -offsetX
        val top = pageTops[i] * zoom - offsetY
        out.set(left, top, left + fitWidth * zoom, top + pageHeights[i] * zoom)
        return out
    }

    private fun minMaxX(): Pair<Float, Float> {
        val contentW = fitWidth * zoom
        return if (contentW <= width) {
            val c = -(width - contentW) / 2f
            c to c
        } else {
            0f to contentW - width
        }
    }

    private fun minMaxY(): Pair<Float, Float> {
        val contentH = contentHeight * zoom
        return if (contentH <= height) {
            val c = -(height - contentH) / 2f
            c to c
        } else {
            0f to contentH - height
        }
    }

    private fun clampOffsets() {
        val (minX, maxX) = minMaxX()
        val (minY, maxY) = minMaxY()
        offsetX = offsetX.coerceIn(minX, maxX)
        offsetY = offsetY.coerceIn(minY, maxY)
    }

    private fun onViewportChanged() {
        invalidate()
        if (pageTops.isEmpty()) return
        val page = pageAt((offsetY + height / 2f) / zoom)
        if (page != currentPage) {
            currentPage = page
            onPageChanged?.invoke(page, pageTops.size)
        }
        requestRender()
    }

    // ---- Çizim istekleri ----

    /** Ekranda sayfa ne kadar büyük görünüyor / temel bitmap ne kadar büyük. 1'den büyükse bulanıklaşır. */
    private fun needsDetail() = baseWidthPx > 0 && fitWidth * zoom > baseWidthPx * 1.1f

    private fun requestRender() {
        val r = renderer ?: return
        if (baseWidthPx == 0 || pageTops.isEmpty()) return
        val n = pageTops.size
        val visible = visibleRange()
        val skip = r.inFlight
        val tasks = ArrayList<RenderTask>()

        fun add(task: RenderTask) {
            if (task.key !in skip) tasks += task
        }

        // 1) Görünen ama hiç görüntüsü olmayan sayfalar için hızlı önizleme
        for (i in visible) if (baseCache[i] == null && previewCache[i] == null) add(RenderTask.Preview(i))
        // 2) Görünen sayfaların tam hali
        for (i in visible) if (baseCache[i] == null) add(RenderTask.Base(i, baseWidthPx))
        // 3) Yakınlaştırılmışsa ve hareket bittiyse görünen kısım net
        if (detailAllowed && needsDetail()) {
            for (i in visible) detailTask(i)?.let(::add)
        } else if (details.isNotEmpty() && !needsDetail()) {
            details.clear()
        }
        details.keys.retainAll { it in visible }
        // 4) Komşu sayfaları önceden hazırla
        for (i in max(0, visible.first - 1)..min(n - 1, visible.last + 2)) {
            if (i !in visible && baseCache[i] == null) add(RenderTask.Base(i, baseWidthPx))
        }
        for (i in max(0, visible.first - 8)..min(n - 1, visible.last + 8)) {
            if (previewCache[i] == null && baseCache[i] == null) add(RenderTask.Preview(i))
        }
        r.submit(tasks)
    }

    private fun detailTask(i: Int): RenderTask.Detail? {
        val page = pageScreenRect(i, tmpRect)
        val visLeft = max(page.left, 0f)
        val visTop = max(page.top, 0f)
        val visRight = min(page.right, width.toFloat())
        val visBottom = min(page.bottom, height.toFloat())
        if (visRight <= visLeft || visBottom <= visTop) return null
        val pageWidthPx = fitWidth * zoom
        val region = RectF(
            (visLeft - page.left) / page.width(),
            (visTop - page.top) / page.height(),
            (visRight - page.left) / page.width(),
            (visBottom - page.top) / page.height(),
        )
        details[i]?.let { d ->
            if (d.pageWidthPx == pageWidthPx && d.region.contains(region)) return null
        }
        val outW = ceil(region.width() * pageWidthPx).toInt().coerceAtLeast(1)
        val outH = ceil(region.height() * page.height()).toInt().coerceAtLeast(1)
        return RenderTask.Detail(i, region, pageWidthPx, outW, outH)
    }

    private val enableDetail = Runnable {
        detailAllowed = true
        requestRender()
    }

    /** Hareket bitince kısa bir bekleme sonrası net çizimi başlat. */
    private fun scheduleDetail() {
        removeCallbacks(enableDetail)
        postDelayed(enableDetail, 120)
    }

    private fun stopDetail() {
        detailAllowed = false
        removeCallbacks(enableDetail)
    }

    // ---- Ekrana çizim ----

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(backgroundColor)
        if (pageTops.isEmpty()) return
        for (i in visibleRange()) {
            val r = pageScreenRect(i, pageRect)
            canvas.drawRect(r, pagePaint)
            (baseCache[i] ?: previewCache[i])?.let { canvas.drawBitmap(it, null, r, bitmapPaint) }
            details[i]?.let { d ->
                tmpRect.set(
                    r.left + d.region.left * r.width(),
                    r.top + d.region.top * r.height(),
                    r.left + d.region.right * r.width(),
                    r.top + d.region.bottom * r.height(),
                )
                canvas.drawBitmap(d.bitmap, null, tmpRect, bitmapPaint)
            }
            canvas.drawRect(r, borderPaint)
        }
    }

    // ---- Dokunma ----

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaledThisGesture = true
            zoomAround(zoom * detector.scaleFactor, detector.focusX, detector.focusY)
            return true
        }
    }).apply { isQuickScaleEnabled = false }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            offsetX += distanceX
            offsetY += distanceY
            clampOffsets()
            onViewportChanged()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (scaledThisGesture) return false
            val (minX, maxX) = minMaxX()
            val (minY, maxY) = minMaxY()
            scroller.fling(
                offsetX.toInt(), offsetY.toInt(),
                -velocityX.toInt(), -velocityY.toInt(),
                minX.toInt(), maxX.toInt(), minY.toInt(), maxY.toInt(),
            )
            postInvalidateOnAnimation()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val target = if (zoom < 1.5f) DOUBLE_TAP_ZOOM else 1f
            animateZoom(target, e.x, e.y)
            return true
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Kalem şimdilik bir şey yapmaz; 3. adımda çizim için kullanılacak.
        val tool = event.getToolType(0)
        if (tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touching = true
                scaledThisGesture = false
                scroller.forceFinished(true)
                zoomAnimator?.cancel()
                stopDetail()
                parent?.requestDisallowInterceptTouchEvent(true)
            }
        }
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touching = false
                if (scroller.isFinished) scheduleDetail()
            }
        }
        return true
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            offsetX = scroller.currX.toFloat()
            offsetY = scroller.currY.toFloat()
            clampOffsets()
            onViewportChanged()
            if (scroller.isFinished) scheduleDetail() else postInvalidateOnAnimation()
        }
    }

    private fun zoomAround(newZoom: Float, focusX: Float, focusY: Float) {
        val z = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (z == zoom) return
        val docX = (focusX + offsetX) / zoom
        val docY = (focusY + offsetY) / zoom
        zoom = z
        offsetX = docX * z - focusX
        offsetY = docY * z - focusY
        clampOffsets()
        onViewportChanged()
    }

    private fun animateZoom(target: Float, focusX: Float, focusY: Float) {
        zoomAnimator?.cancel()
        stopDetail()
        zoomAnimator = ValueAnimator.ofFloat(zoom, target).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { zoomAround(it.animatedValue as Float, focusX, focusY) }
            doOnEndCompat { scheduleDetail() }
            start()
        }
    }

    private fun ValueAnimator.doOnEndCompat(action: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = action()
        })
    }

    companion object {
        private const val MIN_ZOOM = 0.4f
        private const val MAX_ZOOM = 6f
        private const val DOUBLE_TAP_ZOOM = 2.5f
        private const val MAX_BASE_WIDTH = 2200f

        private fun baseCacheBytes(): Int {
            val third = Runtime.getRuntime().maxMemory() / 3
            return third.coerceIn(48L * 1024 * 1024, 256L * 1024 * 1024).toInt()
        }
    }
}
