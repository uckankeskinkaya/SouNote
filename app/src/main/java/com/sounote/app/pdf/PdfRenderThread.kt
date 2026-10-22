package com.sounote.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.RenderParams
import android.graphics.pdf.models.FormEditRecord
import android.graphics.pdf.models.FormWidgetInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.Size
import androidx.annotation.RequiresApi
import com.sounote.app.data.LibraryRepository
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Çizilecek iş. [key] aynı işi iki kez istemeyi önler. */
sealed class RenderTask {
    abstract val page: Int
    abstract val key: String

    /** Hızlı kaydırmada gösterilen düşük çözünürlüklü sayfa. */
    data class Preview(override val page: Int) : RenderTask() {
        override val key get() = "p$page"
    }

    /** Sayfanın tamamı, ekran genişliğine yakın çözünürlükte. */
    data class Base(override val page: Int, val width: Int) : RenderTask() {
        override val key get() = "b${page}_$width"
    }

    /**
     * Yakınlaştırınca sayfanın sadece görünen kısmı, ekran çözünürlüğünde.
     * [region] sayfanın 0..1 oranlarıyla ifade edilen bölgesi; [pageWidthPx] sayfanın o anki ekran genişliği.
     */
    data class Detail(
        override val page: Int,
        val region: RectF,
        val pageWidthPx: Float,
        val outWidth: Int,
        val outHeight: Int,
    ) : RenderTask() {
        override val key get() = "d${page}_${pageWidthPx.toInt()}_${region.left}_${region.top}_${region.right}_${region.bottom}"
    }
}

/** Aramada bulunan bir eşleşme: sayfa ve (birden çok satıra yayılabilen) alanları, sayfa noktası. */
class SearchHit(val page: Int, val rects: List<RectF>)

/** PDF'in doldurulabilir form alanı (Android 15+). Koordinatlar sayfa noktası. */
data class FormField(
    val page: Int,
    val index: Int,
    val type: Type,
    val rect: RectF,
    val value: String,
    val multiLine: Boolean,
    val maxLength: Int,
    val readOnly: Boolean,
    val items: List<String>,
    val selected: List<Int>,
    val multiSelect: Boolean,
) {
    enum class Type { TEXT, CHECKBOX, RADIO, BUTTON, CHOICE, OTHER }

    val editable get() = !readOnly && type != Type.OTHER
}

/**
 * PdfRenderer'ı tek bir arka plan iş parçacığında tutar (PdfRenderer iş parçacığı güvenli değil).
 * [submit] bekleyen listeyi tamamen değiştirir: görünüm her değiştiğinde en güncel istek listesi işlenir.
 */
class PdfRenderThread(
    context: Context,
    uri: Uri,
    private val listener: Listener,
) {
    interface Listener {
        fun onOpened(pageSizes: List<Size>, hasForms: Boolean)
        fun onRendered(task: RenderTask, bitmap: Bitmap)
        fun onFailed(error: Throwable)
        fun onFormFields(page: Int, fields: List<FormField>) {}
        fun onFormEdited(page: Int, fields: List<FormField>) {}
    }

    private val thread = HandlerThread("sounote-pdf").apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var pending: List<RenderTask> = emptyList()
    private var pfd: ParcelFileDescriptor? = null
    private var pdf: PdfRenderer? = null
    private var renderParams: Any? = null

    @Volatile
    private var closed = false

    /** Çizilmekte olan ya da sonucu ana iş parçacığına gönderilmiş ama henüz işlenmemiş işler. */
    val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()

    init {
        val appContext = context.applicationContext
        handler.post {
            try {
                val fd = LibraryRepository.openFileDescriptor(appContext, uri)
                pfd = fd
                val renderer = PdfRenderer(fd)
                pdf = renderer
                if (renderer.pageCount == 0) throw IllegalStateException("PDF'te sayfa yok")
                val sizes = (0 until renderer.pageCount).map { i ->
                    renderer.openPage(i).use { Size(it.width, it.height) }
                }
                val hasForms = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && detectForms(renderer)
                main.post { if (!closed) listener.onOpened(sizes, hasForms) }
            } catch (e: Throwable) {
                Log.e(TAG, "PDF açılamadı", e)
                main.post { if (!closed) listener.onFailed(e) }
            }
        }
    }

    fun submit(tasks: List<RenderTask>) {
        synchronized(lock) { pending = tasks }
        handler.removeCallbacks(drain)
        handler.post(drain)
    }

    fun close() {
        closed = true
        synchronized(lock) { pending = emptyList() }
        handler.post {
            runCatching { pdf?.close() }
            runCatching { pfd?.close() }
            thread.quitSafely()
        }
    }

    private val drain = Runnable {
        while (!closed) {
            val task = synchronized(lock) {
                pending.firstOrNull()?.also { pending = pending.subList(1, pending.size) }
            } ?: break
            if (!inFlight.add(task.key)) continue
            val bitmap = try {
                render(task)
            } catch (e: Throwable) {
                Log.w(TAG, "Sayfa çizilemedi: $task", e)
                null
            }
            if (bitmap == null) {
                inFlight.remove(task.key)
                continue
            }
            main.post {
                inFlight.remove(task.key)
                if (!closed) listener.onRendered(task, bitmap)
            }
        }
    }

    private fun render(task: RenderTask): Bitmap? {
        val renderer = pdf ?: return null
        renderer.openPage(task.page).use { page ->
            val pageW = page.width.toFloat()
            val pageH = page.height.toFloat()
            return when (task) {
                is RenderTask.Preview -> renderWhole(page, PREVIEW_WIDTH)
                is RenderTask.Base -> renderWhole(page, task.width)
                is RenderTask.Detail -> {
                    val bitmap = Bitmap.createBitmap(task.outWidth, task.outHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    val scale = task.pageWidthPx / pageW
                    val matrix = Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(-task.region.left * pageW * scale, -task.region.top * pageH * scale)
                    }
                    renderPage(page, bitmap, matrix)
                    bitmap
                }
            }
        }
    }

    private fun renderWhole(page: PdfRenderer.Page, requestedWidth: Int): Bitmap {
        var width = requestedWidth
        var height = (width.toFloat() * page.height / page.width).toInt()
        // Çok uzun sayfalarda bellek patlamasın.
        if (height > MAX_SIDE) {
            width = (width.toFloat() * MAX_SIDE / height).toInt()
            height = MAX_SIDE
        }
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val matrix = Matrix().apply { setScale(width / page.width.toFloat(), height / page.height.toFloat()) }
        renderPage(page, bitmap, matrix)
        return bitmap
    }

    /** Android 15+: PDF'in kendi vurgu/not açıklamaları ve form içeriği de çizilir. */
    private fun renderPage(page: PdfRenderer.Page, bitmap: Bitmap, matrix: Matrix) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            renderWithParams(page, bitmap, matrix)
        } else {
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun renderWithParams(page: PdfRenderer.Page, bitmap: Bitmap, matrix: Matrix) {
        val params = (renderParams as? RenderParams) ?: RenderParams.Builder(RenderParams.RENDER_MODE_FOR_DISPLAY)
            .setRenderFlags(RenderParams.FLAG_RENDER_HIGHLIGHT_ANNOTATIONS or RenderParams.FLAG_RENDER_TEXT_ANNOTATIONS)
            .build()
            .also { renderParams = it }
        page.render(bitmap, null, matrix, params)
    }

    // ---- Form alanları (Android 15+) ----

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun detectForms(renderer: PdfRenderer): Boolean =
        runCatching { renderer.pdfFormType != PdfRenderer.PDF_FORM_TYPE_NONE }.getOrDefault(false)

    @Volatile
    private var searchGeneration = 0

    /** Bütün sayfalarda metin arar (Android 15+). Yeni arama başlayınca eskisinin sonucu atılır. */
    fun search(query: String, onResult: (List<SearchHit>) -> Unit) {
        val generation = ++searchGeneration
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM || query.isBlank()) {
            main.post { onResult(emptyList()) }
            return
        }
        handler.post {
            val r = pdf ?: return@post
            val hits = ArrayList<SearchHit>()
            for (i in 0 until r.pageCount) {
                if (generation != searchGeneration || closed) return@post
                runCatching {
                    r.openPage(i).use { page -> searchPage(page, i, query, hits) }
                }.onFailure { Log.w(TAG, "Sayfada aranamadı: $i", it) }
            }
            main.post { if (!closed && generation == searchGeneration) onResult(hits) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun searchPage(page: PdfRenderer.Page, index: Int, query: String, out: MutableList<SearchHit>) {
        page.searchText(query).forEach { m -> out += SearchHit(index, m.bounds.map { RectF(it) }) }
    }

    /** Sayfanın form alanlarını okur; sonuç [Listener.onFormFields] ile gelir. */
    fun requestFormFields(page: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        handler.post {
            val r = pdf ?: return@post
            val fields = runCatching { r.openPage(page).use { readFields(page, it) } }
                .onFailure { Log.w(TAG, "Form alanları okunamadı", it) }
                .getOrNull() ?: return@post
            main.post { if (!closed) listener.onFormFields(page, fields) }
        }
    }

    /**
     * Form alanını değiştirir ve PDF'i [saveTo] dosyasına yazar (doldurulmuş form kalıcı olur).
     * [text] metin alanı için, [indices] liste için; ikisi de null ise tıklama (onay kutusu vb.).
     */
    fun editFormField(field: FormField, text: String?, indices: IntArray?, saveTo: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        handler.post {
            val r = pdf ?: return@post
            try {
                val fields = r.openPage(field.page).use { page ->
                    page.applyEdit(buildEdit(field, text, indices))
                    readFields(field.page, page)
                }
                writeTo(r, saveTo)
                main.post { if (!closed) listener.onFormEdited(field.page, fields) }
            } catch (e: Exception) {
                Log.e(TAG, "Form alanı değiştirilemedi", e)
                main.post { if (!closed) listener.onFailed(e) }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun buildEdit(field: FormField, text: String?, indices: IntArray?): FormEditRecord = when {
        text != null -> FormEditRecord.Builder(FormEditRecord.EDIT_TYPE_SET_TEXT, field.page, field.index)
            .setText(text).build()
        indices != null -> FormEditRecord.Builder(FormEditRecord.EDIT_TYPE_SET_INDICES, field.page, field.index)
            .setSelectedIndices(indices).build()
        else -> FormEditRecord.Builder(FormEditRecord.EDIT_TYPE_CLICK, field.page, field.index)
            .setClickPoint(Point(field.rect.centerX().toInt(), field.rect.centerY().toInt())).build()
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun readFields(pageIndex: Int, page: PdfRenderer.Page): List<FormField> =
        page.formWidgetInfos.map { w ->
            val type = when (w.widgetType) {
                FormWidgetInfo.WIDGET_TYPE_TEXTFIELD -> FormField.Type.TEXT
                FormWidgetInfo.WIDGET_TYPE_CHECKBOX -> FormField.Type.CHECKBOX
                FormWidgetInfo.WIDGET_TYPE_RADIOBUTTON -> FormField.Type.RADIO
                FormWidgetInfo.WIDGET_TYPE_PUSHBUTTON -> FormField.Type.BUTTON
                FormWidgetInfo.WIDGET_TYPE_COMBOBOX, FormWidgetInfo.WIDGET_TYPE_LISTBOX -> FormField.Type.CHOICE
                else -> FormField.Type.OTHER
            }
            val items = w.listItems
            FormField(
                page = pageIndex,
                index = w.widgetIndex,
                type = type,
                rect = RectF(w.widgetRect),
                value = w.textValue ?: "",
                multiLine = w.isMultiLineText,
                maxLength = w.maxLength,
                readOnly = w.isReadOnly || (type == FormField.Type.TEXT && !w.isEditableText),
                items = items.map { it.label },
                selected = items.indices.filter { items[it].isSelected },
                multiSelect = w.isMultiSelect,
            )
        }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun writeTo(r: PdfRenderer, target: File) {
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".tmp")
        ParcelFileDescriptor.open(
            tmp,
            ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
        ).use { r.write(it, false) }
        if (!tmp.renameTo(target)) throw IOException("PDF kaydedilemedi")
    }

    companion object {
        private const val TAG = "PdfRenderThread"
        const val PREVIEW_WIDTH = 360
        private const val MAX_SIDE = 6000
    }
}
