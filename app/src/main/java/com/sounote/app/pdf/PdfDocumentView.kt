package com.sounote.app.pdf

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderNode
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.LruCache
import android.util.Size
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.MotionPredictor
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.ViewConfiguration
import android.widget.OverScroller
import java.io.File
import com.sounote.app.ink.Eraser
import com.sounote.app.ink.EraserMode
import com.sounote.app.ink.InkDocument
import com.sounote.app.ink.Clip
import com.sounote.app.ink.ImageItem
import com.sounote.app.ink.Images
import com.sounote.app.ink.InkTool
import com.sounote.app.ink.Selection
import com.sounote.app.ink.Selections
import com.sounote.app.ink.PageContent
import com.sounote.app.ink.ShapeRecognizer
import com.sounote.app.ink.TextBox
import com.sounote.app.ink.TextBoxes
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
    /** PDF açılamadı: görüntüleyici kullanılamaz. */
    var onError: ((Throwable) -> Unit)? = null

    /** Bir işlem yapılamadı ama belge açık kalabilir. */
    var onOperationError: ((Throwable) -> Unit)? = null

    var ink: InkDocument = InkDocument()
        set(value) {
            field.removeListener(inkListener)
            field = value
            value.addListener(inkListener)
            discardInkNodes()
            invalidate()
        }

    var tools: ToolState = ToolState()
        set(value) {
            val formsChanged = (field.tool == Tool.TEXT) != (value.tool == Tool.TEXT)
            if (field.tool == Tool.LASSO && value.tool != Tool.LASSO) clearSelection()
            field = value
            if (formsChanged) {
                if (value.tool == Tool.TEXT) requestRender()
                invalidate()
            }
        }

    /** Yazı aracıyla metin kutusuna (yeni ya da var olan) dokunuldu. */
    var onTextBoxRequest: ((page: Int, box: TextBox, isNew: Boolean) -> Unit)? = null

    /** Yazı aracıyla doldurulabilir metin/liste alanına dokunuldu. */
    var onFormFieldRequest: ((FormField) -> Unit)? = null

    /** İki parmakla dokununca geri al. */
    var onUndoGesture: (() -> Unit)? = null

    /** Üç parmakla dokununca ileri al. */
    var onRedoGesture: (() -> Unit)? = null

    /** PDF'teki web bağlantısına dokunuldu. */
    var onOpenLink: ((Uri) -> Unit)? = null

    /** Doldurulan formun yazılacağı dosya (belgenin uygulama içindeki kopyası). */
    var formSaveTarget: File? = null

    /** Form doldurulup PDF yeniden yazıldığında. */
    var onFormSaved: (() -> Unit)? = null

    /** Lasso seçimi değişti: seçili öğe sayısı ve yapıştırılacak bir şey olup olmadığı. */
    var onSelectionChanged: ((count: Int, hasClipboard: Boolean) -> Unit)? = null

    /** Belgeye eklenen resimlerin klasörü. */
    var imagesDir: File? = null

    /** Düşük gecikmeli çizim katmanı (açıksa). */
    var frontInk: FrontInk? = null

    private val density = resources.displayMetrics.density
    private val pageGap = 12f * density

    private var renderer: PdfRenderThread? = null
    private var pageSizes: List<Size> = emptyList()
    private var pendingInitialPage = 0

    // Yerleşim (belge koordinatı)
    private var fitWidth = 0f
    private var pageLefts = FloatArray(0)
    private var pageWidths = FloatArray(0)
    private var columnWidth = 0f
    private var columns = 1

    /** Yatayda iki sayfa yan yana. Dikey tutulunca her zaman tek sayfa. */
    var twoPage = false
        set(value) {
            if (field == value) return
            field = value
            relayout(initial = false)
        }
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

    private var night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private var backgroundColor = if (night) Color.rgb(0x1E, 0x1F, 0x22) else Color.rgb(0xE6, 0xE7, 0xEA)
    private val pagePaint = Paint().apply { color = Color.WHITE }
    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = if (night) Color.rgb(0x3A, 0x3B, 0x40) else Color.rgb(0xC8, 0xC9, 0xCE)
    }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pageRect = RectF()

    /** Uygulama teması (sistemden bağımsız seçilebildiği için dışarıdan verilir). */
    fun setDarkTheme(dark: Boolean) {
        if (dark == night) return
        night = dark
        backgroundColor = if (dark) Color.rgb(0x1E, 0x1F, 0x22) else Color.rgb(0xE6, 0xE7, 0xEA)
        borderPaint.color = if (dark) Color.rgb(0x3A, 0x3B, 0x40) else Color.rgb(0xC8, 0xC9, 0xCE)
        invalidate()
    }

    // PDF bağlantıları
    private val links = HashMap<Int, List<PageLink>>()
    private val linksRequested = HashSet<Int>()
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

    // Silme ve sürükleme tek geri alma adımı olsun diye hareket başındaki hal
    private val gestureBefore = HashMap<Int, PageContent>()

    // Yazı aracı
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var textDownX = 0f
    private var textDownY = 0f
    private var textPage = -1
    private var draggingBox: TextBox? = null
    private var dragMoved = false
    private var dragOrigin = 0f to 0f

    // Form alanları
    private var hasForms = false
    private val formFields = HashMap<Int, List<FormField>>()
    private val formRequested = HashSet<Int>()
    private val formPaint = Paint().apply { color = Color.argb(46, 33, 110, 230) }
    private val formBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.argb(140, 33, 110, 230)
    }

    // İki parmakla dokunma (geri al)
    private var tapCandidate = false
    private var tapStartTime = 0L
    private var tapMaxPointers = 0
    private val tapStart = FloatArray(6)

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 33, 110, 230) }

    private val inkListener: (Int) -> Unit = { page ->
        pageChanged(page)
        // Geri al/ileri al gibi dışarıdan gelen değişiklik seçimi geçersiz kılar.
        if (!selfChange && selMode == SelMode.NONE && selection?.page == page) clearSelection()
    }
    private val boundsRect = RectF()

    private val renderListener = object : PdfRenderThread.Listener {
        override fun onOpened(pageSizes: List<Size>, hasForms: Boolean) {
            this@PdfDocumentView.pageSizes = pageSizes
            this@PdfDocumentView.hasForms = hasForms
            relayout(initial = true)
        }

        override fun onFormFields(page: Int, fields: List<FormField>) {
            formFields[page] = fields
            if (tools.tool == Tool.TEXT) invalidate()
        }

        override fun onFormEdited(page: Int, fields: List<FormField>) {
            formFields[page] = fields
            // Sayfanın görüntüsü değişti: yeniden çiz.
            baseCache.remove(page)
            previewCache.remove(page)
            details.remove(page)
            requestRender()
            invalidate()
            onFormSaved?.invoke()
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

        override fun onOperationFailed(error: Throwable) {
            onOperationError?.invoke(error)
        }

        override fun onLinks(page: Int, links: List<PageLink>) {
            this@PdfDocumentView.links[page] = links
        }
    }

    fun open(uri: Uri, initialPage: Int) {
        renderer?.close()
        pendingInitialPage = initialPage
        renderer = PdfRenderThread(context, uri, renderListener)
    }

    init {
        ink.addListener(inkListener)
    }

    /** Form alanını değiştir: metin, liste seçimi ya da (ikisi de null) tıklama. */
    fun editFormField(field: FormField, text: String? = null, indices: IntArray? = null) {
        val target = formSaveTarget ?: return
        renderer?.editFormField(field, text, indices, target)
    }

    fun release() {
        ink.removeListener(inkListener)
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
        columns = if (twoPage && width > height) 2 else 1
        columnWidth = if (columns == 2) (fitWidth - pageGap * 3) / 2f else fitWidth
        pageTops = FloatArray(n)
        pageHeights = FloatArray(n)
        pageLefts = FloatArray(n)
        pageWidths = FloatArray(n)
        var y = pageGap
        var i = 0
        while (i < n) {
            // Bir satırda 1 ya da 2 sayfa; satır yüksekliği en uzun sayfa kadar.
            var rowHeight = 0f
            for (c in 0 until columns) {
                val k = i + c
                if (k >= n) break
                val s = pageSizes[k]
                pageTops[k] = y
                pageWidths[k] = columnWidth
                pageLefts[k] = if (columns == 2) pageGap + c * (columnWidth + pageGap) else 0f
                pageHeights[k] = columnWidth * s.height / s.width
                rowHeight = max(rowHeight, pageHeights[k])
            }
            y += rowHeight + pageGap
            i += columns
        }
        contentHeight = y
        val newBaseWidth = min(columnWidth, MAX_BASE_WIDTH).toInt()
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

    /** Aynı satırdaki ilk sayfa (çift sayfa düzeninde soldaki). */
    private fun rowStart(i: Int): Int {
        var k = i
        while (k > 0 && pageTops[k - 1] == pageTops[k]) k--
        return k
    }

    /** Ekran noktasının üstündeki (ya da en yakın) sayfa. */
    private fun pageAtScreen(sx: Float, sy: Float): Int {
        val i = pageAt((sy + offsetY) / zoom)
        if (columns == 1) return i
        val docX = (sx + offsetX) / zoom
        val first = rowStart(i)
        return if (i > first && docX < pageLefts[i] - pageGap / 2f) first else i
    }

    private fun visibleRange(): IntRange {
        if (pageTops.isEmpty()) return IntRange.EMPTY
        val first = rowStart(pageAt(offsetY / zoom))
        val last = pageAt((offsetY + height) / zoom)
        return first..last
    }

    private fun pageScreenRect(i: Int, out: RectF): RectF {
        val left = pageLefts[i] * zoom - offsetX
        val top = pageTops[i] * zoom - offsetY
        out.set(left, top, left + pageWidths[i] * zoom, top + pageHeights[i] * zoom)
        return out
    }

    val pageCount get() = pageSizes.size

    /** Sayfaya atla (sayfa paneli, yer imleri). */
    fun goToPage(page: Int) {
        if (pageTops.isEmpty()) return
        val p = page.coerceIn(0, pageTops.size - 1)
        scroller.forceFinished(true)
        offsetY = (pageTops[p] - pageGap) * zoom
        clampOffsets()
        onViewportChanged()
        scheduleDetail()
    }

    /** Sayfadaki bir alanı (sayfa noktası) görünür yap: arama sonucuna gitmek için. */
    fun scrollToRect(page: Int, rect: RectF) {
        if (pageTops.isEmpty() || page !in pageTops.indices) return
        scroller.forceFinished(true)
        val s = pageWidths[page] * zoom / pageSizes[page].width
        val cy = pageTops[page] * zoom + rect.centerY() * s
        val cx = pageLefts[page] * zoom + rect.centerX() * s
        offsetY = cy - height * 0.4f
        if (fitWidth * zoom > width) offsetX = cx - width / 2f
        clampOffsets()
        onViewportChanged()
        scheduleDetail()
    }

    /** Arama sonuçlarını sayfada vurgula; [current] o anki sonuç. */
    fun setSearchHighlights(hits: Map<Int, List<RectF>>, current: Pair<Int, List<RectF>>?) {
        searchHits = hits
        currentHit = current
        invalidate()
    }

    /** PDF'te metin arar (Android 15+). Sonuç ana iş parçacığında gelir. */
    fun search(query: String, onResult: (List<SearchHit>) -> Unit) {
        val r = renderer
        if (r == null) onResult(emptyList()) else r.search(query, onResult)
    }

    private var searchHits: Map<Int, List<RectF>> = emptyMap()
    private var currentHit: Pair<Int, List<RectF>>? = null
    private val searchPaint = Paint().apply { color = Color.argb(90, 255, 200, 0) }
    private val currentHitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(255, 120, 0)
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
        val page = rowStart(pageAt((offsetY + height / 2f) / zoom))
        if (page != currentPage) {
            currentPage = page
            onPageChanged?.invoke(page, pageTops.size)
        }
        requestRender()
    }

    // ---- Çizim istekleri ----

    /** Ekranda sayfa ne kadar büyük görünüyor / temel bitmap ne kadar büyük. 1'den büyükse bulanıklaşır. */
    private fun needsDetail() = baseWidthPx > 0 && columnWidth * zoom > baseWidthPx * 1.1f

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
        if (hasForms && tools.tool == Tool.TEXT) {
            for (i in visible) if (formRequested.add(i)) r.requestFormFields(i)
        }
        for (i in visible) if (linksRequested.add(i)) r.requestLinks(i)
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
        val pageWidthPx = pageWidths[i] * zoom
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
        // Kendi alanının dışına taşma: Compose içinde tuval görünüme kırpılmıyor ve
        // arka plan rengi araç çubuğunun üstünü boyuyordu.
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
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
        if (selMode == SelMode.LASSO && lassoCount > 1) {
            lassoPath.rewind()
            lassoPath.moveTo(lasso[0], lasso[1])
            for (i in 1 until lassoCount) lassoPath.lineTo(lasso[i * 2], lasso[i * 2 + 1])
            canvas.drawPath(lassoPath, lassoPaint)
        }
    }

    private fun drawInk(canvas: Canvas, page: Int, r: RectF) {
        val content = ink.content(page)
        val strokes = content.strokes
        val active = current?.takeIf { inputPage == page && !useFront }
        val hits = searchHits[page]
        val showOverlay = hits != null || (tools.tool == Tool.TEXT && (content.texts.isNotEmpty() || formFields[page]?.isNotEmpty() == true)) ||
            (tools.tool == Tool.LASSO && selection?.page == page)
        if (content.isEmpty && active == null && !showOverlay) return
        val scale = r.width() / pageSizes[page].width
        canvas.save()
        canvas.clipRect(r)
        canvas.translate(r.left, r.top)
        canvas.scale(scale, scale)
        if (!content.isEmpty) {
            if (canvas.isHardwareAccelerated) {
                canvas.drawRenderNode(inkNode(page))
            } else {
                drawImages(canvas, page)
                strokes.forEach { if (it.tool == InkTool.HIGHLIGHTER) strokeRenderer.draw(canvas, it) }
                ink.texts(page).forEach { TextBoxes.draw(canvas, it) }
                strokes.forEach { if (it.tool != InkTool.HIGHLIGHTER) strokeRenderer.draw(canvas, it) }
            }
        }
        if (tools.tool == Tool.TEXT) {
            // Yazı aracındayken metin kutularının ve form alanlarının yerini göster.
            ink.texts(page).forEach { box ->
                TextBoxes.bounds(box, boundsRect)
                canvas.drawCircle(boundsRect.right, boundsRect.bottom, HANDLE_DP * density / scale, handlePaint)
                boundsRect.inset(-3f, -3f)
                canvas.drawRect(boundsRect, formBorderPaint.apply { strokeWidth = 1f * density / scale })
            }
            formFields[page]?.forEach { f ->
                if (f.editable) {
                    canvas.drawRect(f.rect, formPaint)
                    canvas.drawRect(f.rect, formBorderPaint)
                }
            }
        }
        hits?.forEach { canvas.drawRect(it, searchPaint) }
        currentHit?.takeIf { it.first == page }?.second?.forEach {
            currentHitPaint.strokeWidth = 2f * density / scale
            canvas.drawRect(it, currentHitPaint)
        }
        selection?.takeIf { it.page == page && tools.tool == Tool.LASSO }?.let { sel ->
            Selections.bounds(content, sel, boundsRect)
            val pad = 4f * density / scale
            boundsRect.inset(-pad, -pad)
            selectionPaint.strokeWidth = 1.5f * density / scale
            selectionPaint.pathEffect = DashPathEffect(floatArrayOf(6f * density / scale, 4f * density / scale), 0f)
            canvas.drawRect(boundsRect, selectionPaint)
            canvas.drawCircle(boundsRect.right, boundsRect.bottom, HANDLE_DP * 1.4f * density / scale, handlePaint)
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
            drawImages(c, page)
            // Fosforlu altta, kalem üstte.
            strokes.forEach { if (it.tool == InkTool.HIGHLIGHTER) strokeRenderer.draw(c, it) }
            ink.texts(page).forEach { TextBoxes.draw(c, it) }
            strokes.forEach { if (it.tool != InkTool.HIGHLIGHTER) strokeRenderer.draw(c, it) }
            endRecording()
        }
    }

    private fun pageChanged(page: Int) {
        inkNodes.remove(page)?.discardDisplayList()
        invalidate()
    }

    // ---- Dokunma ----

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            // Yazı aracında iki parmak bir yazının üstündeyse sayfa değil yazı büyür/küçülür.
            if (tools.tool == Tool.TEXT) beginBoxPinch(detector.focusX, detector.focusY)
            // Seçimin üstünde iki parmak: seçim büyür/küçülür.
            if (tools.tool == Tool.LASSO) beginSelectionPinch(detector.focusX, detector.focusY)
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaledThisGesture = true
            if (selMode == SelMode.PINCH) {
                updateSelectionPinch(detector)
            } else if (pinchBox != null) {
                updateBoxPinch(detector)
            } else {
                zoomAround(zoom * detector.scaleFactor, detector.focusX, detector.focusY)
            }
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            if (selMode == SelMode.PINCH) selUp()
            if (pinchBox != null) {
                recordGesture()
                pinchBox = null
            }
        }
    }).apply { isQuickScaleEnabled = false }

    // ---- Yazıyı büyütme/küçültme: iki parmakla ya da kalemle köşe tutamacından ----

    private var pinchBox: TextBox? = null
    private var pinchPage = -1
    private var pinchScale = 1f
    private var pinchFocusX = 0f
    private var pinchFocusY = 0f

    private fun beginBoxPinch(fx: Float, fy: Float) {
        if (pageTops.isEmpty()) return
        val page = pageAtScreen(fx, fy)
        val px = toPageX(page, fx)
        val py = toPageY(page, fy)
        val pad = PINCH_PAD_DP * density / pageScale(page)
        val box = ink.texts(page).lastOrNull {
            TextBoxes.bounds(it, boundsRect).inset(-pad, -pad)
            boundsRect.contains(px, py)
        } ?: return
        pinchBox = box
        pinchPage = page
        pinchScale = 1f
        pinchFocusX = px
        pinchFocusY = py
        gestureBefore[page] = ink.content(page)
    }

    private fun updateBoxPinch(d: ScaleGestureDetector) {
        val start = pinchBox ?: return
        pinchScale *= d.scaleFactor
        val sized = scaledBox(start, pinchScale)
        val k = sized.fontSize / start.fontSize
        // Parmakların arasındaki nokta yazının aynı yerinde kalsın; iki parmakla kaydırınca yazı da kayar.
        val fx = toPageX(pinchPage, d.focusX)
        val fy = toPageY(pinchPage, d.focusY)
        replaceBox(pinchPage, sized.copy(x = fx - (pinchFocusX - start.x) * k, y = fy - (pinchFocusY - start.y) * k))
    }

    private fun scaledBox(start: TextBox, factor: Float): TextBox {
        val size = (start.fontSize * factor).coerceIn(MIN_FONT_PT, MAX_FONT_PT)
        val k = size / start.fontSize
        return start.copy(fontSize = size, width = start.width * k)
    }

    private fun replaceBox(page: Int, box: TextBox) {
        val c = ink.content(page)
        ink.set(page, c.copy(texts = c.texts.map { if (it.id == box.id) box else it }))
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (tools.fingerDraw && !multiTouch) return false
            if (pinchBox != null || selMode == SelMode.PINCH) return true
            if (fingerSelecting && !multiTouch) return true
            offsetX += distanceX
            offsetY += distanceY
            clampOffsets()
            onViewportChanged()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (scaledThisGesture || (tools.fingerDraw && !multiTouch) || (fingerSelecting && !multiTouch)) return false
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

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            // Kalem/fosforlu/silgideyken parmakla dokunulan bağlantıyı aç (yazı ve seçim araçları dokunmayı kendisi kullanır).
            if (tools.fingerDraw || tools.tool == Tool.TEXT || tools.tool == Tool.LASSO) return false
            if (pageTops.isEmpty()) return false
            val page = pageAtScreen(e.x, e.y)
            val px = toPageX(page, e.x)
            val py = toPageY(page, e.y)
            val link = links[page]?.firstOrNull { l -> l.rects.any { it.contains(px, py) } } ?: return false
            link.page?.let { goToPage(it) }
            link.uri?.let { onOpenLink?.invoke(it) }
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (tools.tool == Tool.TEXT && !tools.fingerDraw) textTap(e.x, e.y)
            if (tools.tool == Tool.LASSO && !tools.fingerDraw) clearSelection()
            return false
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (tools.fingerDraw || tools.tool == Tool.TEXT) return false
            val target = if (zoom < 1.5f) DOUBLE_TAP_ZOOM else 1f
            animateZoom(target, e.x, e.y)
            return true
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (pageTops.isEmpty()) return true
        // Parmak hareketleri ancak parmakla başlamış bir dokunuşta işlenir. Kalem dokunduysa o
        // dokunuş bitene kadar kalan parmak (avuç içi) olayları yok sayılır; yoksa kalem kalkınca
        // ekranda kalan avuç sayfayı kaydırırdı.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) fingerStreamOpen = !isStylusTool(event.getToolType(0))
        if ((drawPointerId != -1 && inputFromStylus) || stylusIndex(event) >= 0) {
            fingerStreamOpen = false
            handleStylus(event)
        } else if (fingerStreamOpen) {
            handleFingers(event)
        }
        return true
    }

    private var fingerStreamOpen = false

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
        if (drawPointerId != -1 && !inputFromStylus) {
            when {
                selMode != SelMode.NONE -> selCancel()
                textPage >= 0 -> textCancel()
                else -> cancelInput()
            }
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                if (drawPointerId == -1 && isStylusTool(event.getToolType(idx))) {
                    if (tools.tool == Tool.TEXT && event.getToolType(idx) != MotionEvent.TOOL_TYPE_ERASER) {
                        drawPointerId = event.getPointerId(idx)
                        inputFromStylus = true
                        textDown(event.getX(idx), event.getY(idx))
                        return
                    }
                    if (tools.tool == Tool.LASSO && event.getToolType(idx) != MotionEvent.TOOL_TYPE_ERASER) {
                        drawPointerId = event.getPointerId(idx)
                        inputFromStylus = true
                        selDown(event.getX(idx), event.getY(idx))
                        return
                    }
                    val erase = tools.tool == Tool.ERASER ||
                        event.getToolType(idx) == MotionEvent.TOOL_TYPE_ERASER ||
                        (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
                    beginInput(event, idx, fromStylus = true, erase = erase)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = event.findPointerIndex(drawPointerId)
                if (idx >= 0) {
                    when {
                        selMode != SelMode.NONE -> selMove(event.getX(idx), event.getY(idx))
                        textPage >= 0 -> textMove(event.getX(idx), event.getY(idx))
                        else -> moveInput(event, idx)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == drawPointerId) {
                    if (selMode != SelMode.NONE) {
                        selUp()
                    } else if (textPage >= 0) {
                        textUp()
                    } else {
                        moveInput(event, event.actionIndex)
                        endInput()
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> when {
                selMode != SelMode.NONE -> selCancel()
                textPage >= 0 -> textCancel()
                else -> cancelInput()
            }
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

        trackTwoFingerTap(event)

        if (tools.tool == Tool.LASSO && handleFingerSelection(event)) return

        if (tools.fingerDraw && tools.tool == Tool.TEXT) {
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    drawPointerId = event.getPointerId(0)
                    inputFromStylus = false
                    textDown(event.getX(0), event.getY(0))
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    multiTouch = true
                    if (textPage >= 0) textCancel()
                }
                MotionEvent.ACTION_MOVE -> if (!multiTouch && textPage >= 0) textMove(event.getX(0), event.getY(0))
                MotionEvent.ACTION_UP -> if (textPage >= 0) textUp()
                MotionEvent.ACTION_CANCEL -> if (textPage >= 0) textCancel()
            }
        } else if (tools.fingerDraw) {
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
    private fun pageScale(page: Int) = pageWidths[page] * zoom / pageSizes[page].width
    private fun toPageX(page: Int, sx: Float) = (sx + offsetX - pageLefts[page] * zoom) / pageScale(page)
    private fun toPageY(page: Int, sy: Float) = (sy + offsetY - pageTops[page] * zoom) / pageScale(page)

    private fun beginInput(event: MotionEvent, idx: Int, fromStylus: Boolean, erase: Boolean) {
        val x = event.getX(idx)
        val y = event.getY(idx)
        drawPointerId = event.getPointerId(idx)
        inputFromStylus = fromStylus
        erasing = erase
        inputPage = pageAtScreen(x, y)
        predictedCount = 0
        requestUnbufferedDispatch(event)
        scroller.forceFinished(true)
        parent?.requestDisallowInterceptTouchEvent(true)

        if (erase) {
            eraserX = x
            eraserY = y
            lastEraseX = x
            lastEraseY = y
            gestureBefore.clear()
            eraseAlong(x, y, x, y)
        } else {
            val t = tools
            val highlighter = t.tool == Tool.HIGHLIGHTER
            current = StrokeBuilder(
                tool = if (highlighter) InkTool.HIGHLIGHTER else t.penType.inkTool,
                color = if (highlighter) t.highlighterColor else t.penColor,
                width = mmToPt(if (highlighter) t.highlighterWidthMm else t.penWidthMm),
                pressureSensitive = fromStylus && !highlighter && t.penType.usesPressure,
            ).also {
                val p = if (it.pressureSensitive) t.applyPressureCurve(event.getPressure(idx)) else 1f
                it.add(toPageX(inputPage, x), toPageY(inputPage, y), p)
            }
            straightLine = highlighter && t.highlighterStraight
            snapped = false
            holdX = x
            holdY = y
            useFront = fromStylus && frontInk?.available == true && FrontInk.supports(current!!.tool)
            frontDrawn = 0
            frontGeneration++
            if (useFront) pushFront() else predictor?.record(event)
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
        // Şekle dönüştüyse kalem kalkana kadar şekil sabit kalır.
        if (snapped) return
        for (h in 0 until event.historySize) {
            addPoint(b, event.getHistoricalX(idx, h), event.getHistoricalY(idx, h), event.getHistoricalPressure(idx, h))
        }
        val x = event.getX(idx)
        val y = event.getY(idx)
        addPoint(b, x, y, event.getPressure(idx))
        when {
            useFront -> pushFront()
            straightLine -> predictedCount = 0
            else -> updatePrediction(event)
        }
        watchForHold(x, y)
        // Ön tamponda çizilirken görünümü her noktada yeniden çizmeye gerek yok.
        if (!useFront) invalidate()
    }

    // ---- Düşük gecikmeli çizim ----

    private var useFront = false
    private var frontDrawn = 0
    private var frontGeneration = 0

    /** Çizginin henüz ön tampona çizilmemiş kuyruğunu çizer (bir önceki parçayla biraz üst üste binerek). */
    private fun pushFront() {
        val f = frontInk ?: return
        val b = current ?: return
        val from = (frontDrawn - 2).coerceAtLeast(0)
        val n = b.count - from
        if (n <= 0) return
        val pts = FloatArray(n * 3)
        System.arraycopy(b.points, from * 3, pts, 0, n * 3)
        val r = pageScreenRect(inputPage, RectF())
        val clip = RectF(r).apply { if (!intersect(0f, 0f, width.toFloat(), height.toFloat())) setEmpty() }
        f.draw(FrontInk.Segment(pts, n, b.tool, b.color, b.width, b.pressureSensitive, r.left, r.top, pageScale(inputPage), clip))
        frontDrawn = b.count
    }

    /** Biten çizgi görünüme geçtikten (iki kare) sonra ön tamponu temizle; arada yeni çizgi başladıysa bekle. */
    private fun clearFrontLater() {
        val generation = frontGeneration
        postOnAnimation {
            postOnAnimation {
                if (generation == frontGeneration && current == null) frontInk?.clear()
            }
        }
    }

    // ---- Şekil düzeltme: çizginin sonunda kalemi bekletince ----

    private var snapped = false
    private var straightLine = false
    private var holdX = 0f
    private var holdY = 0f

    private val holdCheck = Runnable {
        val b = current ?: return@Runnable
        if (snapped || erasing) return@Runnable
        val lineOnly = b.tool == InkTool.HIGHLIGHTER
        val shape = ShapeRecognizer.recognize(b.points, b.count, lineOnly) ?: return@Runnable
        b.replaceAll(shape.points, shape.count)
        snapped = true
        predictedCount = 0
        if (useFront) {
            frontInk?.clear()
            frontDrawn = 0
            pushFront()
        }
        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        invalidate()
    }

    private fun watchForHold(x: Float, y: Float) {
        if (!tools.shapeAssist || straightLine) return
        if (kotlin.math.hypot(x - holdX, y - holdY) > HOLD_SLOP_DP * density) {
            holdX = x
            holdY = y
            removeCallbacks(holdCheck)
            postDelayed(holdCheck, HOLD_MS)
        }
    }

    private fun addPoint(b: StrokeBuilder, sx: Float, sy: Float, rawPressure: Float) {
        var x = toPageX(inputPage, sx)
        var y = toPageY(inputPage, sy)
        if (straightLine) {
            // Düz fosforlu: başlangıçtan bu noktaya tek çizgi; yataya/dikeye yakınsa ona oturt.
            val x0 = b.points[0]
            val y0 = b.points[1]
            if (kotlin.math.abs(y - y0) < kotlin.math.abs(x - x0) * 0.09f) y = y0
            else if (kotlin.math.abs(x - x0) < kotlin.math.abs(y - y0) * 0.09f) x = x0
            if (b.count == 1) b.add(x, y, 1f) else b.replaceLast(x, y, 1f)
            return
        }
        val p = if (b.pressureSensitive) b.lastPressure * 0.5f + tools.applyPressureCurve(rawPressure) * 0.5f else 1f
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
        val page = pageAtScreen(bx, by)
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
        val before = ink.content(page)
        gestureBefore.getOrPut(page) { before }
        ink.set(page, before.copy(strokes = result))
    }

    private fun endInput() {
        current?.let { b -> ink.addStroke(inputPage, b.build()) }
        val wasFront = useFront
        recordGesture()
        resetInput()
        if (wasFront) clearFrontLater()
    }

    /** Silgi hareketinin yaptığı bütün değişiklikleri tek geri alma adımı yap. */
    private fun recordGesture() {
        if (gestureBefore.isEmpty()) return
        ink.record(gestureBefore.map { (page, before) -> InkDocument.Change(page, before, ink.content(page)) })
        gestureBefore.clear()
    }

    private fun cancelInput() {
        if (useFront) frontInk?.clear()
        recordGesture()
        resetInput()
    }

    // ---- Yazı aracı ----

    private fun textDown(x: Float, y: Float) {
        textDownX = x
        textDownY = y
        textPage = pageAtScreen(x, y)
        dragMoved = false
        val px = toPageX(textPage, x)
        val py = toPageY(textPage, y)
        val texts = ink.texts(textPage)
        // Önce köşe tutamacı: sürükleyince yazı büyür/küçülür.
        val handleR = HANDLE_TOUCH_DP * density / pageScale(textPage)
        resizing = false
        draggingBox = texts.lastOrNull {
            TextBoxes.bounds(it, boundsRect)
            kotlin.math.hypot(px - boundsRect.right, py - boundsRect.bottom) <= handleR
        }
        if (draggingBox != null) {
            resizing = true
        } else {
            draggingBox = texts.lastOrNull { TextBoxes.bounds(it, boundsRect).contains(px, py) }
        }
        draggingBox?.let {
            dragOrigin = it.x to it.y
            resizeStartDist = kotlin.math.hypot(px - it.x, py - it.y).coerceAtLeast(4f)
        }
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private var resizing = false
    private var resizeStartDist = 1f

    private fun textMove(x: Float, y: Float) {
        val box = draggingBox ?: return
        if (!dragMoved && kotlin.math.hypot(x - textDownX, y - textDownY) < touchSlop) return
        if (!dragMoved) {
            dragMoved = true
            gestureBefore[textPage] = ink.content(textPage)
        }
        if (resizing) {
            // Sol üst köşe sabit; tutamacın köşeye uzaklığı kadar büyür.
            val d = kotlin.math.hypot(toPageX(textPage, x) - box.x, toPageY(textPage, y) - box.y)
            replaceBox(textPage, scaledBox(box, d / resizeStartDist))
            return
        }
        val s = pageScale(textPage)
        val moved = box.copy(
            x = dragOrigin.first + (x - textDownX) / s,
            y = dragOrigin.second + (y - textDownY) / s,
        )
        replaceBox(textPage, moved)
        draggingBox = moved
    }

    private fun textUp() {
        if (dragMoved) recordGesture() else textTap(textDownX, textDownY)
        textCancelState()
    }

    private fun textCancel() {
        recordGesture()
        textCancelState()
    }

    private fun textCancelState() {
        textPage = -1
        resizing = false
        draggingBox = null
        dragMoved = false
        drawPointerId = -1
    }

    /** Dokunulan yerde form alanı varsa onu, metin kutusu varsa onu düzenle; yoksa yeni kutu aç. */
    private fun textTap(x: Float, y: Float) {
        if (pageTops.isEmpty()) return
        val page = pageAtScreen(x, y)
        val px = toPageX(page, x)
        val py = toPageY(page, y)
        val pageW = pageSizes[page].width.toFloat()
        val pageH = pageSizes[page].height.toFloat()
        if (px < 0 || py < 0 || px > pageW || py > pageH) return

        formFields[page]?.lastOrNull { it.editable && it.rect.contains(px, py) }?.let { f ->
            when (f.type) {
                FormField.Type.TEXT, FormField.Type.CHOICE -> onFormFieldRequest?.invoke(f)
                else -> editFormField(f)
            }
            return
        }
        ink.texts(page).lastOrNull { TextBoxes.bounds(it, boundsRect).contains(px, py) }?.let {
            onTextBoxRequest?.invoke(page, it, false)
            return
        }
        val size = tools.textSizePt
        val bx = px.coerceAtMost(pageW - 90f).coerceAtLeast(4f)
        val by = (py - size * 0.6f).coerceAtLeast(0f)
        val box = TextBox(
            id = System.currentTimeMillis(),
            x = bx,
            y = by,
            width = (pageW - bx - 12f).coerceAtLeast(80f),
            fontSize = size,
            color = tools.textColor,
            text = "",
        )
        onTextBoxRequest?.invoke(page, box, true)
    }

    // ---- Lasso: seç, taşı, büyüt/küçült ----

    private enum class SelMode { NONE, LASSO, MOVE, SCALE, PINCH }

    private var selMode = SelMode.NONE
    private var selection: Selection? = null
    private var clipboard: Clip? = null
    private var selfChange = false
    private var lasso = FloatArray(512)
    private var lassoCount = 0
    private var lassoPage = 0
    private val lassoPath = Path()
    private var selBase: PageContent? = null
    private var selBaseSelection: Selection? = null
    private var selDownX = 0f
    private var selDownY = 0f
    private var selMoved = false
    private var selAnchorX = 0f
    private var selAnchorY = 0f
    private var selStartDist = 1f
    private var selPinchScale = 1f
    private var fingerSelecting = false
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(220, 33, 110, 230)
    }
    private val lassoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.argb(220, 33, 110, 230)
        pathEffect = DashPathEffect(floatArrayOf(8f * density, 6f * density), 0f)
    }

    private inline fun changing(block: () -> Unit) {
        selfChange = true
        try {
            block()
        } finally {
            selfChange = false
        }
    }

    private fun notifySelection() {
        onSelectionChanged?.invoke(selection?.count ?: 0, clipboard != null)
        invalidate()
    }

    fun clearSelection() {
        if (selection == null) return
        selection = null
        notifySelection()
    }

    /** Seçimin sayfadaki alanı (dokunma payıyla), sayfa noktası. */
    private fun selectionHit(sel: Selection, px: Float, py: Float): Boolean {
        Selections.bounds(ink.content(sel.page), sel, boundsRect)
        val pad = SELECTION_PAD_DP * density / pageScale(sel.page)
        boundsRect.inset(-pad, -pad)
        return boundsRect.contains(px, py)
    }

    private fun selDown(x: Float, y: Float) {
        selDownX = x
        selDownY = y
        selMoved = false
        parent?.requestDisallowInterceptTouchEvent(true)
        selection?.let { sel ->
            val px = toPageX(sel.page, x)
            val py = toPageY(sel.page, y)
            val b = Selections.bounds(ink.content(sel.page), sel, RectF())
            val handleR = HANDLE_TOUCH_DP * density / pageScale(sel.page)
            selMode = when {
                kotlin.math.hypot(px - b.right, py - b.bottom) <= handleR -> SelMode.SCALE
                selectionHit(sel, px, py) -> SelMode.MOVE
                else -> SelMode.NONE
            }
            if (selMode != SelMode.NONE) {
                selBase = ink.content(sel.page)
                selBaseSelection = sel
                selAnchorX = b.left
                selAnchorY = b.top
                selStartDist = kotlin.math.hypot(px - b.left, py - b.top).coerceAtLeast(4f)
                return
            }
            clearSelection()
        }
        // Yeni lasso
        selMode = SelMode.LASSO
        lassoPage = pageAtScreen(x, y)
        lassoCount = 0
        addLassoPoint(x, y)
    }

    private fun addLassoPoint(x: Float, y: Float) {
        if (lassoCount * 2 + 2 > lasso.size) lasso = lasso.copyOf(lasso.size * 2)
        lasso[lassoCount * 2] = x
        lasso[lassoCount * 2 + 1] = y
        lassoCount++
    }

    private fun selMove(x: Float, y: Float) {
        when (selMode) {
            SelMode.LASSO -> {
                val lx = lasso[(lassoCount - 1) * 2]
                val ly = lasso[(lassoCount - 1) * 2 + 1]
                if (kotlin.math.hypot(x - lx, y - ly) > 2f * density) {
                    addLassoPoint(x, y)
                    invalidate()
                }
            }
            SelMode.MOVE, SelMode.SCALE -> {
                if (!selMoved && kotlin.math.hypot(x - selDownX, y - selDownY) < touchSlop) return
                selMoved = true
                val page = selBaseSelection?.page ?: return
                if (selMode == SelMode.MOVE) {
                    val s = pageScale(page)
                    applySelectionTransform(1f, 0f, 0f, (x - selDownX) / s, (y - selDownY) / s)
                } else {
                    val d = kotlin.math.hypot(toPageX(page, x) - selAnchorX, toPageY(page, y) - selAnchorY)
                    val f = (d / selStartDist).coerceIn(MIN_SELECTION_SCALE, MAX_SELECTION_SCALE)
                    applySelectionTransform(f, selAnchorX, selAnchorY, 0f, 0f)
                }
            }
            else -> {}
        }
    }

    private fun applySelectionTransform(scale: Float, ax: Float, ay: Float, tx: Float, ty: Float) {
        val base = selBase ?: return
        val sel0 = selBaseSelection ?: return
        val (content, sel) = Selections.transform(base, sel0, scale, ax, ay, tx, ty)
        changing { ink.set(sel0.page, content) }
        selection = sel
    }

    private fun selUp() {
        when (selMode) {
            SelMode.LASSO -> {
                val page = lassoPage
                val poly = FloatArray(lassoCount * 2)
                for (i in 0 until lassoCount) {
                    poly[i * 2] = toPageX(page, lasso[i * 2])
                    poly[i * 2 + 1] = toPageY(page, lasso[i * 2 + 1])
                }
                val sel = Selections.select(page, ink.content(page), poly, lassoCount)
                selection = if (sel.isEmpty) null else sel
                notifySelection()
            }
            SelMode.MOVE, SelMode.SCALE, SelMode.PINCH -> {
                val base = selBase
                val sel0 = selBaseSelection
                if (base != null && sel0 != null && (selMoved || selMode == SelMode.PINCH)) {
                    ink.record(listOf(InkDocument.Change(sel0.page, base, ink.content(sel0.page))))
                }
                notifySelection()
            }
            SelMode.NONE -> {}
        }
        resetSelectionGesture()
    }

    private fun selCancel() {
        if (selMode == SelMode.MOVE || selMode == SelMode.SCALE || selMode == SelMode.PINCH) {
            // Yarım kalan taşımayı geri koy.
            val base = selBase
            val sel0 = selBaseSelection
            if (base != null && sel0 != null) {
                changing { ink.set(sel0.page, base) }
                selection = sel0
            }
        }
        resetSelectionGesture()
        notifySelection()
    }

    private fun resetSelectionGesture() {
        selMode = SelMode.NONE
        lassoCount = 0
        selBase = null
        selBaseSelection = null
        drawPointerId = -1
        invalidate()
    }

    /** Parmakla seçim: parmakla çizme açıksa her şey; değilse seçimi sürüklemek. Olayı tükettiyse true. */
    private fun handleFingerSelection(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            val sel = selection
            fingerSelecting = tools.fingerDraw ||
                (sel != null && selectionHit(sel, toPageX(sel.page, event.x), toPageY(sel.page, event.y)))
            if (fingerSelecting) {
                drawPointerId = event.getPointerId(0)
                inputFromStylus = false
                selDown(event.x, event.y)
            }
        }
        if (!fingerSelecting) return false
        when (action) {
            MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1 && selMode != SelMode.PINCH) selMove(event.x, event.y)
            MotionEvent.ACTION_POINTER_DOWN -> {
                // İkinci parmak: yarım lasso/taşımayı bitir, iki parmak hareketine geç.
                multiTouch = true
                if (selMode == SelMode.LASSO) resetSelectionGesture() else if (selMode != SelMode.NONE) selUp()
            }
            MotionEvent.ACTION_UP -> {
                if (selMode != SelMode.NONE && selMode != SelMode.PINCH) selUp()
                fingerSelecting = false
            }
            MotionEvent.ACTION_CANCEL -> {
                selCancel()
                fingerSelecting = false
            }
        }
        // İki parmakla kaydırma/yakınlaştırma (sayfa ya da seçim) yine çalışsın.
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    private var pinchFocusSelX = 0f
    private var pinchFocusSelY = 0f

    private fun beginSelectionPinch(fx: Float, fy: Float) {
        val sel = selection ?: return
        val px = toPageX(sel.page, fx)
        val py = toPageY(sel.page, fy)
        if (!selectionHit(sel, px, py)) return
        selMode = SelMode.PINCH
        selBase = ink.content(sel.page)
        selBaseSelection = sel
        selPinchScale = 1f
        pinchFocusSelX = px
        pinchFocusSelY = py
    }

    private fun updateSelectionPinch(d: ScaleGestureDetector) {
        val page = selBaseSelection?.page ?: return
        selPinchScale = (selPinchScale * d.scaleFactor).coerceIn(MIN_SELECTION_SCALE, MAX_SELECTION_SCALE)
        val fx = toPageX(page, d.focusX)
        val fy = toPageY(page, d.focusY)
        applySelectionTransform(selPinchScale, pinchFocusSelX, pinchFocusSelY, fx - pinchFocusSelX, fy - pinchFocusSelY)
    }

    // ---- Seçim işlemleri (araç çubuğundan) ----

    fun recolorSelection(color: Int) {
        val sel = selection ?: return
        val (content, newSel) = Selections.recolor(ink.content(sel.page), sel, color)
        changing { ink.commit(sel.page, content) }
        selection = newSel
        notifySelection()
    }

    fun deleteSelection() {
        val sel = selection ?: return
        changing { ink.commit(sel.page, Selections.delete(ink.content(sel.page), sel)) }
        selection = null
        notifySelection()
    }

    fun copySelection() {
        val sel = selection ?: return
        clipboard = Selections.copy(ink.content(sel.page), sel)
        notifySelection()
    }

    /** Kopyalananı şu an bakılan sayfaya yapıştırır; aynı sayfaysa biraz kaydırarak. */
    fun paste() {
        val clip = clipboard ?: return
        val page = currentPage.coerceIn(0, pageTops.size - 1)
        val offset = if (selection?.page == page) 16f else 0f
        val (content, sel) = Selections.paste(page, ink.content(page), clip, offset, offset)
        changing { ink.commit(page, content) }
        selection = sel
        notifySelection()
    }

    /** Resmi bakılan sayfanın ortasına, sayfa genişliğinin yarısı boyunda ekler ve seçer. */
    fun insertImage(file: String, pixelWidth: Int, pixelHeight: Int) {
        if (pageTops.isEmpty() || pixelWidth <= 0 || pixelHeight <= 0) return
        val page = currentPage.coerceIn(0, pageTops.size - 1)
        val pageW = pageSizes[page].width.toFloat()
        val pageH = pageSizes[page].height.toFloat()
        var w = pageW * 0.5f
        var h = w * pixelHeight / pixelWidth
        if (h > pageH * 0.6f) {
            h = pageH * 0.6f
            w = h * pixelWidth / pixelHeight
        }
        // Ekranın ortasına denk gelen sayfa noktası
        val cx = toPageX(page, width / 2f).coerceIn(w / 2, pageW - w / 2)
        val cy = toPageY(page, height / 2f).coerceIn(h / 2, pageH - h / 2)
        val item = ImageItem(System.currentTimeMillis(), cx - w / 2, cy - h / 2, w, h, file)
        val c = ink.content(page)
        changing { ink.commit(page, c.copy(images = c.images + item)) }
        selection = Selection(page, emptySet(), emptySet(), setOf(item.id))
        notifySelection()
    }

    private fun drawImages(canvas: Canvas, page: Int) {
        val images = ink.images(page)
        if (images.isEmpty()) return
        val dir = imagesDir ?: return
        for (im in images) {
            tmpRect.set(im.x, im.y, im.x + im.width, im.y + im.height)
            val bmp = Images.get(File(dir, im.file)) { pageChanged(page) }
            if (bmp != null) canvas.drawBitmap(bmp, null, tmpRect, bitmapPaint) else canvas.drawRect(tmpRect, imagePlaceholderPaint)
        }
    }

    private val imagePlaceholderPaint = Paint().apply { color = Color.argb(40, 120, 120, 120) }

    // ---- İki parmakla dokununca geri al ----

    private fun trackTwoFingerTap(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tapCandidate = false
                tapMaxPointers = 1
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                tapMaxPointers = maxOf(tapMaxPointers, event.pointerCount)
                when (event.pointerCount) {
                    2 -> {
                        tapCandidate = true
                        tapStartTime = event.eventTime
                    }
                    3 -> {}
                    else -> tapCandidate = false
                }
                // Her parmağın başlangıç yeri (hareket ederse dokunma sayılmaz)
                for (i in 0 until minOf(event.pointerCount, 3)) {
                    tapStart[i * 2] = event.getX(i)
                    tapStart[i * 2 + 1] = event.getY(i)
                }
            }
            MotionEvent.ACTION_MOVE -> if (tapCandidate && event.pointerCount >= 2) {
                for (i in 0 until minOf(event.pointerCount, 3)) {
                    if (kotlin.math.hypot(event.getX(i) - tapStart[i * 2], event.getY(i) - tapStart[i * 2 + 1]) > touchSlop) {
                        tapCandidate = false
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (tapCandidate && event.eventTime - tapStartTime < MULTI_TAP_MS) {
                    when (tapMaxPointers) {
                        2 -> onUndoGesture?.invoke()
                        3 -> onRedoGesture?.invoke()
                    }
                }
                tapCandidate = false
            }
            MotionEvent.ACTION_CANCEL -> tapCandidate = false
        }
    }

    private fun resetInput() {
        useFront = false
        removeCallbacks(holdCheck)
        snapped = false
        straightLine = false
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
        private const val MULTI_TAP_MS = 350L
        private const val HOLD_MS = 550L
        private const val PINCH_PAD_DP = 24f
        private const val HANDLE_DP = 5f
        private const val HANDLE_TOUCH_DP = 22f
        private const val MIN_FONT_PT = 4f
        private const val MAX_FONT_PT = 144f
        private const val SELECTION_PAD_DP = 16f
        private const val MIN_SELECTION_SCALE = 0.05f
        private const val MAX_SELECTION_SCALE = 20f
        private const val HOLD_SLOP_DP = 3f

        private fun baseCacheBytes(): Int {
            val third = Runtime.getRuntime().maxMemory() / 3
            return third.coerceIn(48L * 1024 * 1024, 256L * 1024 * 1024).toInt()
        }
    }
}
