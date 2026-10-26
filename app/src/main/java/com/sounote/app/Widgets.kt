package com.sounote.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.sounote.app.data.CatalogRepo
import com.sounote.app.ui.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Ana ekran widget'ları (sınav widget'ı ExamWidget.kt'de). Veriler tercihlerden ve katalogdan okunur;
 * uygulama ilgili şey değişince Widgets.updateAll çağırır.
 */

object Widgets {
    /** Bütün widget'ları yeniler (sınav, bugün, hafta listesi, hızlı işlemler). */
    fun updateAll(context: Context) {
        ExamWidget.updateAll(context)
        update(context, TodayWidget::class.java) { TodayWidget.render(it) }
        update(context, WeekWidget::class.java) { WeekWidget.render(it) }
        update(context, QuickWidget::class.java) { QuickWidget.render(it) }
    }

    private fun update(context: Context, cls: Class<*>, render: (Context) -> RemoteViews) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, cls))
        if (ids.isEmpty()) return
        runCatching { manager.updateAppWidget(ids, render(context)) }
            .onFailure { ErrorLog.record("Widget güncellenemedi", it) }
    }

    internal fun prefs(context: Context) = context.getSharedPreferences("viewer", Context.MODE_PRIVATE)

    /** Seçili tema (LibraryViewModel.appTheme ile aynı kural). */
    internal fun theme(context: Context): AppTheme {
        val p = prefs(context)
        return p.getString("logoTheme", null)?.let { n -> AppTheme.entries.firstOrNull { it.name == n } }
            ?: AppTheme.fromLegacy(p.getString("darkPalette", null)) ?: AppTheme.ELECTRIC
    }

    private fun background(t: AppTheme) = when (t) {
        AppTheme.ELECTRIC -> R.drawable.widget_bg_electric
        AppTheme.SAPPHIRE -> R.drawable.widget_bg_sapphire
        AppTheme.SILVER -> R.drawable.widget_bg_silver
        AppTheme.MONO -> R.drawable.widget_bg_mono
        AppTheme.EMERALD -> R.drawable.widget_bg_emerald
        AppTheme.ROSE -> R.drawable.widget_bg_rose
    }

    /** Widget'ı temaya boyar: zemin, logo, vurgu yazıları ve [icons] simgeleri. */
    internal fun style(context: Context, v: RemoteViews, accentTexts: IntArray = intArrayOf(), icons: IntArray = intArrayOf()) {
        val t = theme(context)
        v.setInt(R.id.widget_root, "setBackgroundResource", background(t))
        v.setImageViewResource(R.id.widget_logo, t.logo)
        val accent = t.darkPrimary.toInt()
        accentTexts.forEach { v.setTextColor(it, accent) }
        icons.forEach { v.setInt(it, "setColorFilter", accent) }
    }

    internal fun dayKey(t: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(t))

    /** Uygulamayı verilen eylemle açan dokunuş. */
    internal fun open(context: Context, code: Int, action: String?, extra: Pair<String, String>? = null): PendingIntent {
        val intent = Intent(context, SouNoteActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (action != null) intent.action = action
        extra?.let { intent.putExtra(it.first, it.second) }
        return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/** "Bugün": çalışma serisi, bugünkü çalışma süresi, tekrar zamanı gelen kartlar. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = render(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        fun render(context: Context): RemoteViews {
            val prefs = Widgets.prefs(context)
            val now = System.currentTimeMillis()
            val days = prefs.getStringSet("activeDays", emptySet()).orEmpty()
            val dayMs = 24L * 60 * 60 * 1000
            var i = if (Widgets.dayKey(now) in days) 0 else 1
            var streak = 0
            while (Widgets.dayKey(now - i * dayMs) in days) { streak++; i++ }
            val studyMin = prefs.getLong("studyday_${Widgets.dayKey(now)}", 0L) / 60_000
            val due = prefs.getInt("dueCount", 0)
            val name = prefs.getString("userName", null)?.takeIf { it.isNotBlank() }

            val v = RemoteViews(context.packageName, R.layout.widget_today)
            v.setTextViewText(R.id.widget_label, if (name != null) "Bugün · $name" else "Bugün")
            v.setTextViewText(R.id.widget_side, if (Widgets.dayKey(now) in days) "Tamam" else "")
            v.setTextViewText(R.id.today_streak, "$streak")
            v.setTextViewText(R.id.today_study, if (studyMin >= 60) "${studyMin / 60} sa ${studyMin % 60} dk" else "$studyMin dk")
            v.setTextViewText(R.id.today_cards, if (due > 0) "$due kart tekrar bekliyor" else "Tekrar bekleyen kart yok")
            v.setOnClickPendingIntent(R.id.widget_root, Widgets.open(context, 11, SouNoteActivity.ACTION_OPEN_HOME))
            Widgets.style(context, v, intArrayOf(R.id.widget_side))
            return v
        }
    }
}

/** "Hafta listesi": dönemin bu haftasının ilerlemesi ve işaretlenmemiş ilk işler. */
class WeekWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = render(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        private val ROWS = intArrayOf(R.id.week_row0, R.id.week_row1, R.id.week_row2, R.id.week_row3)
        private val ITEMS = intArrayOf(R.id.week_item0, R.id.week_item1, R.id.week_item2, R.id.week_item3)

        fun render(context: Context): RemoteViews {
            val prefs = Widgets.prefs(context)
            val done = prefs.getStringSet("weekDone", emptySet()).orEmpty()
            val catalog = runCatching { CatalogRepo.cached(context) }.getOrNull()
            val week = catalog?.weekOf(System.currentTimeMillis()) ?: 0
            // LibraryViewModel.weekTasks ile aynı sıra ve anahtarlar: (anahtar, başlık)
            val tasks = if (catalog == null || week == 0) emptyList() else {
                val res = catalog.courses.flatMap { c -> c.topics.flatMap { it.resources } }.filter { it.week == week }
                    .map { Triple(it.day, it.order, ("r:" + it.url.ifBlank { it.week.toString() + ":" + it.title }) to it.title) }
                val missing = catalog.weekItems.filter { it.week == week }
                    .map { Triple(it.day, it.order, "w:${it.week}:${it.title}" to it.title) }
                (res + missing).sortedWith(compareBy({ if (it.first == 0) 9 else it.first }, { it.second }))
                    .map { it.third }.distinctBy { it.first }
            }
            val finished = tasks.count { it.first in done }
            val open = tasks.filter { it.first !in done }

            val v = RemoteViews(context.packageName, R.layout.widget_week)
            v.setTextViewText(R.id.widget_label, if (week > 0) "Hafta $week listesi" else "Hafta listesi")
            v.setTextViewText(R.id.widget_side, if (tasks.isNotEmpty()) "$finished/${tasks.size}" else "")
            v.setProgressBar(R.id.week_progress, 100, if (tasks.isEmpty()) 0 else finished * 100 / tasks.size, false)
            for (k in ROWS.indices) {
                val item = open.getOrNull(k)
                v.setViewVisibility(ROWS[k], if (item != null) View.VISIBLE else View.GONE)
                if (item != null) v.setTextViewText(ITEMS[k], item.second)
            }
            val empty = when {
                tasks.isEmpty() -> "Bu hafta için liste yok"
                open.isEmpty() -> "Bu haftanın hepsi tamam"
                else -> null
            }
            v.setViewVisibility(R.id.week_empty, if (empty != null) View.VISIBLE else View.GONE)
            if (empty != null) v.setTextViewText(R.id.week_empty, empty)
            v.setOnClickPendingIntent(R.id.widget_root, Widgets.open(context, 12, SouNoteActivity.ACTION_OPEN_WEEK))
            Widgets.style(context, v, intArrayOf(R.id.widget_side), intArrayOf(R.id.week_dot0, R.id.week_dot1, R.id.week_dot2, R.id.week_dot3))
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                v.setColorStateList(R.id.week_progress, "setProgressTintList", android.content.res.ColorStateList.valueOf(Widgets.theme(context).darkPrimary.toInt()))
            }
            return v
        }
    }
}

/** "Hızlı işlemler": hızlı not, son açılan belge, sonsuz tuval, asistan. */
class QuickWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = render(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        fun render(context: Context): RemoteViews {
            val prefs = Widgets.prefs(context)
            val lastId = prefs.getString("lastDocId", null)
            val lastTitle = prefs.getString("lastDocTitle", null)
            val v = RemoteViews(context.packageName, R.layout.widget_quick)
            v.setOnClickPendingIntent(R.id.quick_note, Widgets.open(context, 21, SouNoteActivity.ACTION_QUICK_NOTE))
            v.setOnClickPendingIntent(R.id.quick_canvas, Widgets.open(context, 22, SouNoteActivity.ACTION_NEW_CANVAS))
            v.setOnClickPendingIntent(R.id.quick_assistant, Widgets.open(context, 23, SouNoteActivity.ACTION_OPEN_ASSISTANT))
            if (lastId != null) {
                v.setTextViewText(R.id.quick_doc_label, lastTitle?.takeIf { it.isNotBlank() } ?: "Son belge")
                v.setOnClickPendingIntent(R.id.quick_doc, Widgets.open(context, 24, SouNoteActivity.ACTION_OPEN_DOC, SouNoteActivity.EXTRA_DOC_ID to lastId))
            } else {
                v.setTextViewText(R.id.quick_doc_label, "Kütüphane")
                v.setOnClickPendingIntent(R.id.quick_doc, Widgets.open(context, 24, SouNoteActivity.ACTION_OPEN_LIBRARY))
            }
            Widgets.style(context, v, icons = intArrayOf(R.id.quick_note_icon, R.id.quick_doc_icon, R.id.quick_canvas_icon, R.id.quick_assistant_icon))
            return v
        }
    }
}
