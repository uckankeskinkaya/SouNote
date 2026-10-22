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
import android.graphics.RenderNode
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.LruCache
import android.util.Size
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.MotionPredictor
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import com.sounote.app.ink.Eraser
import com.sounote.app.ink.EraserMode
import com.sounote.app.ink.InkDocument
import com.sounote.app.ink.InkTool
import com.sounote.app.ink.StrokeBuilder
import com.sounote.app.ink.StrokeRenderer
import com.sounote.app.ink.Tool
import com.sounote.app.ink.ToolState
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
 * Mürekkep en üstte, sayfa başına bir RenderNode olarak (vektör, her yakınlaştırmada net).
 *
 * Giriş: kalem çizer/siler. Parmak kaydırır; "parmakla çiz" açıksa tek parmak çizer,
 * iki parmak kaydırır. Kalem ekrana yakınken parmak dokunuşları yok sayılır (avuç içi).
 */
class PdfDocumentView(context: Context) : View(context) {

    var onPageChanged: ((page: Int, pageCount: Int) -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null

    var ink: InkDocument = InkDocument()
        set(value) {
            field = value
            discardInkNodes()
            invalidate()
        }

    var tools: ToolState = ToolState()

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

    // Mürekkep
    private val strokeRenderer = StrokeRenderer()
    private val inkNodes = HashMap<Int, RenderNode>()
    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Color.argb(160, 90, 90, 90)
    }

    // Çizim girişi
    private var drawPointerId = -1
    private var inputFromStylus = false
    private var erasing = false
    private var inputPage = -1
    private var current: StrokeBuilder? = null
    private var eraserX = 0f
    private var eraserY = 0f
    private var lastEraseX = 0f
    private var lastEraseY = 0f
    private var lastStylusTime = Long.MIN_VALUE / 2
    private var ignoringFingers = false
    private var multiTouch = false
    private var fingerGestureActive = false
    private val predictor: MotionPredictor? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) MotionPredictor(context) else null
    private var predicted = FloatArray(3 * 16)
    private var predictedCount = 0
    private var drawScratch = FloatArray(0)

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
        discardInkNodes()
    }

    private fun discardInkNodes() {
        inkNodes.values.forEach { it.discardDisplayList() }
        inkNodes.clear()
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
            drawInk(canvas, i, r)
            canvas.drawRect(r, borderPaint)
        }
        if (erasing && drawPointerId != -1) {
            canvas.drawCircle(eraserX, eraserY, tools.eraserSizeDp * density, eraserPaint)
        }
    }

    private fun drawInk(canvas: Canvas, page: Int, r: RectF) {
        val strokes = ink.strokes(page)
        val active = current?.takeIf { inputPage == page }
        if (strokes.isEmpty() && active == null) return
        val scale = r.width() / pageSizes[page].width
        canvas.save()
        canvas.clipRect(r)
        canvas.translate(r.left, r.top)
        canvas.scale(scale, scale)
        if (strokes.isNotEmpty()) {
            if (canvas.isHardwareAccelerated) {
                canvas.drawRenderNode(inkNode(page))
            } else {
                strokes.forEach { if (it.tool == InkTool.HIGHLIGHTER) strokeRenderer.draw(canvas, it) }
                strokes.forEach { if (it.tool == InkTool.PEN) strokeRenderer.draw(canvas, it) }
            }
        }
        if (active != null) {
            // Kalemin gideceği tahmin edilen noktaları da ekle: gecikme daha az hissedilir.
            val n = active.count + predictedCount
            if (drawScratch.size < n * 3) drawScratch = FloatArray(n * 3 * 2)
            System.arraycopy(active.points, 0, drawScratch, 0, active.count * 3)
            System.arraycopy(predicted, 0, drawScratch, active.count * 3, predictedCount * 3)
            strokeRenderer.draw(canvas, active.tool, active.color, active.width, active.pressureSensitive, drawScratch, n)
        }
        canvas.restore()
    }

    /** Sayfanın kayıtlı çizgileri bir kez kaydedilir, her karede sadece tekrar oynatılır. */
    private fun inkNode(page: Int): RenderNode = inkNodes.getOrPut(page) {
        val size = pageSizes[page]
        RenderNode("ink$page").apply {
            setPosition(0, 0, size.width, size.height)
            val c = beginRecording()
            val strokes = ink.strokes(page)
            // Fosforlu altta, kalem üstte.
            strokes.forEach { if (it.tool == InkTool.HIGHLIGHTER) strokeRenderer.draw(c, it) }
            strokes.forEach { if (it.tool == InkTool.PEN) strokeRenderer.draw(c, it) }
            endRecording()
        }
    }

    private fun pageChanged(page: Int) {
        inkNodes.remove(page)?.discardDisplayList()
        invalidate()
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
            if (tools.fingerDraw && !multiTouch) return false
            offsetX += distanceX
            offsetY += distanceY
            clampOffsets()
            onViewportChanged()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (scaledThisGesture || (tools.fingerDraw && !multiTouch)) return false
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
            if (tools.fingerDraw) return false
            val target = if (zoom < 1.5f) DOUBLE_TAP_ZOOM else 1f
            animateZoom(target, e.x, e.y)
            return true
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (pageTops.isEmpty()) return true
        if ((drawPointerId != -1 && inputFromStylus) || stylusIndex(event) >= 0) {
            handleStylus(event)
        } else {
            handleFingers(event)
        }
        return true
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (isStylusTool(event.getToolType(0))) lastStylusTime = event.eventTime
        return super.onHoverEvent(event)
    }

    private fun isStylusTool(type: Int) =
        type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER

    private fun stylusIndex(event: MotionEvent): Int {
        for (i in 0 until event.pointerCount) if (isStylusTool(event.getToolType(i))) return i
        return -1
    }

    private fun handleStylus(event: MotionEvent) {
        lastStylusTime = event.eventTime
        if (fingerGestureActive) cancelFingerGesture(event)
        if (drawPointerId != -1 && !inputFromStylus) cancelInput()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                if (drawPointerId == -1 && isStylusTool(event.getToolType(idx))) {
                    val erase = tools.tool == Tool.ERASER ||
                        event.getToolType(idx) == MotionEvent.TOOL_TYPE_ERASER ||
                        (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
                    beginInput(event, idx, fromStylus = true, erase = erase)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = event.findPointerIndex(drawPointerId)
                if (idx >= 0) moveInput(event, idx)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == drawPointerId) {
                    moveInput(event, event.actionIndex)
                    endInput()
                }
            }
            MotionEvent.ACTION_CANCEL -> cancelInput()
        }
    }

    private fun handleFingers(event: MotionEvent) {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            // Kalem az önce ekrandaydı ya da üstünde duruyorsa bu dokunuş büyük ihtimalle avuç içi.
            ignoringFingers = event.eventTime - lastStylusTime < PALM_WINDOW_MS
            multiTouch = false
        }
        if (ignoringFingers) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) ignoringFingers = false
            return
        }

        if (tools.fingerDraw) {
            when (action) {
                MotionEvent.ACTION_DOWN -> beginInput(event, 0, fromStylus = false, erase = tools.tool == Tool.ERASER)
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // İkinci parmak geldi: bu bir kaydırma/yakınlaştırma, yarım çizgiyi at.
                    multiTouch = true
                    if (drawPointerId != -1) cancelInput()
                }
                MotionEvent.ACTION_MOVE -> if (!multiTouch && drawPointerId != -1) {
                    val idx = event.findPointerIndex(drawPointerId)
                    if (idx >= 0) moveInput(event, idx)
                }
                MotionEvent.ACTION_UP -> if (drawPointerId != -1) {
                    // Sistem avuç içi olarak işaretlediyse çizgiyi at.
                    if ((event.flags and MotionEvent.FLAG_CANCELED) != 0) {
                        cancelInput()
                    } else {
                        moveInput(event, 0)
                        endInput()
                    }
                }
                MotionEvent.ACTION_CANCEL -> cancelInput()
            }
        }

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                fingerGestureActive = true
                scaledThisGesture = false
                scroller.forceFinished(true)
                zoomAnimator?.cancel()
                stopDetail()
                parent?.requestDisallowInterceptTouchEvent(true)
            }
        }
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        when (action) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                fingerGestureActive = false
                if (scroller.isFinished) scheduleDetail()
            }
        }
    }

    private fun cancelFingerGesture(event: MotionEvent) {
        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
        scaleDetector.onTouchEvent(cancel)
        gestureDetector.onTouchEvent(cancel)
        cancel.recycle()
        fingerGestureActive = false
        scheduleDetail()
    }

    // ---- Çizim / silme ----

    /** Ekran noktası → sayfa noktası (PDF birimi). */
    private fun pageScale(page: Int) = fitWidth * zoom / pageSizes[page].width
    private fun toPageX(page: Int, sx: Float) = (sx + offsetX) / pageScale(page)
    private fun toPageY(page: Int, sy: Float) = (sy + offsetY - pageTops[page] * zoom) / pageScale(page)

    private fun beginInput(event: MotionEvent, idx: Int, fromStylus: Boolean, erase: Boolean) {
        val x = event.getX(idx)
        val y = event.getY(idx)
        drawPointerId = event.getPointerId(idx)
        inputFromStylus = fromStylus
        erasing = erase
        inputPage = pageAt((y + offsetY) / zoom)
        predictedCount = 0
        requestUnbufferedDispatch(event)
        scroller.forceFinished(true)
        parent?.requestDisallowInterceptTouchEvent(true)

        if (erase) {
            eraserX = x
            eraserY = y
            lastEraseX = x
            lastEraseY = y
            eraseAlong(x, y, x, y)
        } else {
            val t = tools
            val highlighter = t.tool == Tool.HIGHLIGHTER
            current = StrokeBuilder(
                tool = if (highlighter) InkTool.HIGHLIGHTER else InkTool.PEN,
                color = if (highlighter) t.highlighterColor else t.penColor,
                width = mmToPt(if (highlighter) t.highlighterWidthMm else t.penWidthMm),
                pressureSensitive = fromStylus && !highlighter,
            ).also { it.add(toPageX(inputPage, x), toPageY(inputPage, y), pressureOf(event, idx, null)) }
            predictor?.record(event)
        }
        invalidate()
    }

    private fun moveInput(event: MotionEvent, idx: Int) {
        if (erasing) {
            for (h in 0 until event.historySize) {
                eraseStep(event.getHistoricalX(idx, h), event.getHistoricalY(idx, h))
            }
            eraseStep(event.getX(idx), event.getY(idx))
            invalidate()
            return
        }
        val b = current ?: return
        for (h in 0 until event.historySize) {
            addPoint(b, event.getHistoricalX(idx, h), event.getHistoricalY(idx, h), event.getHistoricalPressure(idx, h))
        }
        addPoint(b, event.getX(idx), event.getY(idx), event.getPressure(idx))
        updatePrediction(event)
        invalidate()
    }

    private fun addPoint(b: StrokeBuilder, sx: Float, sy: Float, rawPressure: Float) {
        val x = toPageX(inputPage, sx)
        val y = toPageY(inputPage, sy)
        val p = if (b.pressureSensitive) b.lastPressure * 0.5f + rawPressure.coerceIn(0f, 1f) * 0.5f else 1f
        val minStep = MIN_POINT_DISTANCE_PX / pageScale(inputPage)
        val dx = x - b.lastX
        val dy = y - b.lastY
        if (dx * dx + dy * dy < minStep * minStep) {
            // Çok yakın nokta: yenisini ekleme, sadece son noktayı güncelle (basınç değişimi kaybolmasın).
            if (b.count > 1) b.replaceLast(x, y, p)
            return
        }
        b.add(x, y, p)
    }

    private fun pressureOf(event: MotionEvent, idx: Int, prev: Float?): Float =
        if (inputFromStylus) event.getPressure(idx).coerceIn(0f, 1f).let { if (prev == null) it else prev * 0.5f + it * 0.5f } else 1f

    private fun updatePrediction(event: MotionEvent) {
        predictedCount = 0
        val p = predictor ?: return
        if (!inputFromStylus) return
        p.record(event)
        val b = current ?: return
        val target = (SystemClock.uptimeMillis() + PREDICTION_MS) * 1_000_000L
        val prediction = p.predict(target) ?: return
        val n = minOf(prediction.historySize + 1, predicted.size / 3)
        var k = 0
        for (h in 0 until n) {
            val px = if (h < prediction.historySize) prediction.getHistoricalX(0, h) else prediction.getX(0)
            val py = if (h < prediction.historySize) prediction.getHistoricalY(0, h) else prediction.getY(0)
            predicted[k * 3] = toPageX(inputPage, px)
            predicted[k * 3 + 1] = toPageY(inputPage, py)
            predicted[k * 3 + 2] = b.lastPressure
            k++
        }
        predictedCount = k
        prediction.recycle()
    }

    private fun eraseStep(x: Float, y: Float) {
        eraserX = x
        eraserY = y
        eraseAlong(lastEraseX, lastEraseY, x, y)
        lastEraseX = x
        lastEraseY = y
    }

    private fun eraseAlong(ax: Float, ay: Float, bx: Float, by: Float) {
        val page = pageAt((by + offsetY) / zoom)
        val strokes = ink.strokes(page)
        if (strokes.isEmpty()) return
        val r = tools.eraserSizeDp * density / pageScale(page)
        val x0 = toPageX(page, ax)
        val y0 = toPageY(page, ay)
        val x1 = toPageX(page, bx)
        val y1 = toPageY(page, by)
        val result = when (tools.eraserMode) {
            EraserMode.STROKE -> Eraser.eraseStrokes(strokes, x0, y0, x1, y1, r, tools.eraserHighlighterOnly)
            EraserMode.PRECISE -> Eraser.erasePrecise(strokes, x0, y0, x1, y1, r, tools.eraserHighlighterOnly)
        } ?: return
        ink.replace(page, result)
        pageChanged(page)
    }

    private fun endInput() {
        current?.let { b ->
            ink.add(inputPage, b.build())
            pageChanged(inputPage)
        }
        resetInput()
    }

    private fun cancelInput() {
        resetInput()
    }

    private fun resetInput() {
        current = null
        drawPointerId = -1
        erasing = false
        predictedCount = 0
        invalidate()
    }

    private fun mmToPt(mm: Float) = mm * 72f / 25.4f

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
        private const val PALM_WINDOW_MS = 400L
        private const val PREDICTION_MS = 16L
        private const val MIN_POINT_DISTANCE_PX = 0.75f

        private fun baseCacheBytes(): Int {
            val third = Runtime.getRuntime().maxMemory() / 3
            return third.coerceIn(48L * 1024 * 1024, 256L * 1024 * 1024).toInt()
        }
    }
}
