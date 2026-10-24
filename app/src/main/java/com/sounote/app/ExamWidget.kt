package com.sounote.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sounote.app.data.CatalogRepo
import com.sounote.app.data.ExamStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ana ekran widget'ı: sıradaki sınav (geri sayım) ve bugünkü çalışma süresi. Dokununca Sınavlar açılır. */
class ExamWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = render(context)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        /** Sınavlar ya da çalışma süresi değişince çağrılır. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, ExamWidget::class.java))
            if (ids.isEmpty()) return
            runCatching { manager.updateAppWidget(ids, render(context)) }
                .onFailure { ErrorLog.record("Widget güncellenemedi", it) }
        }

        private fun render(context: Context): RemoteViews {
            val tr = Locale.forLanguageTag("tr")
            val prefs = context.getSharedPreferences("viewer", Context.MODE_PRIVATE)
            val level = prefs.getString("userLevel", null) ?: ""
            val now = System.currentTimeMillis()
            val upcoming = runCatching {
                val global = CatalogRepo.cached(context).exams.filter { level.isEmpty() || it.levels.isEmpty() || level in it.levels }
                (ExamStore(context).load() + global).filter { it.time > now }.sortedBy { it.time }
            }.getOrDefault(emptyList())
            val next = upcoming.firstOrNull()
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(now))
            val studyMin = prefs.getLong("studyday_$today", 0L) / 60_000

            val views = RemoteViews(context.packageName, R.layout.widget_exam)
            if (next != null) {
                val days = ExamReminders.daysUntil(next.time)
                views.setTextViewText(R.id.widget_label, "Sıradaki · ${next.course}")
                views.setTextViewText(R.id.widget_title, next.title.ifBlank { next.course })
                views.setTextViewText(
                    R.id.widget_date,
                    SimpleDateFormat("d MMMM EEEE", tr).format(Date(next.time)) + (if (next.slot.isNotBlank()) " · ${next.slot}" else ""),
                )
                views.setTextViewText(R.id.widget_days, if (days <= 1) "" else "$days")
                views.setTextViewText(
                    R.id.widget_countdown,
                    when (days) {
                        0 -> "Bugün"
                        1 -> "Yarın"
                        else -> "gün kaldı"
                    },
                )
                val after = upcoming.getOrNull(1)
                views.setTextViewText(
                    R.id.widget_next,
                    after?.let { "Sonra: ${it.title.ifBlank { it.course }} · ${ExamReminders.daysUntil(it.time)} gün" } ?: "Sonrasında sınav yok",
                )
            } else {
                views.setTextViewText(R.id.widget_label, "SouNote")
                views.setTextViewText(R.id.widget_title, "Yaklaşan sınav yok")
                views.setTextViewText(R.id.widget_date, "")
                views.setTextViewText(R.id.widget_days, "")
                views.setTextViewText(R.id.widget_countdown, "")
                views.setTextViewText(R.id.widget_next, "Planlayıcıdan sınav ekle")
            }
            views.setTextViewText(
                R.id.widget_study,
                if (studyMin >= 60) "${studyMin / 60} sa ${studyMin % 60} dk" else "$studyMin dk",
            )
            val open = Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_EXAMS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, 7, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            return views
        }
    }
}
