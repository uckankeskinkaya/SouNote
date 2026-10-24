package com.sounote.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.sounote.app.data.CatalogRepo
import com.sounote.app.data.Exam
import com.sounote.app.data.ExamStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Sınav hatırlatıcıları: 3 gün ve 1 gün önce akşam 19:00'da, sınav günü 2 saat önce bildirim.
 * Alarm yaklaşık zamanlıdır (pil dostu, özel izin gerektirmez).
 */
object ExamReminders {
    private const val CHANNEL = "exams"
    private const val PREFS = "exam_reminders"

    fun enabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", true)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()
        rescheduleAll(context)
    }

    /** Kayıtlı ve ortak bütün sınavlar için alarmları yeniden kurar. */
    fun rescheduleAll(context: Context) {
        val prefs = context.getSharedPreferences("viewer", Context.MODE_PRIVATE)
        val level = prefs.getString("userLevel", null) ?: ""
        val doneGlobal = prefs.getStringSet("examDone", emptySet()).orEmpty()
        val global = CatalogRepo.cached(context).exams.filter { level.isEmpty() || it.levels.isEmpty() || level in it.levels }
        // Tamamlanan ödevler için hatırlatma kurulmaz.
        schedule(context, (ExamStore(context).load() + global).filter { !it.done && it.id !in doneGlobal })
    }

    fun schedule(context: Context, exams: List<Exam>) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Eski alarmları iptal et
        prefs.getStringSet("codes", emptySet())?.forEach { code ->
            code.toIntOrNull()?.let { am.cancel(pending(context, it, null)) }
        }
        val codes = HashSet<String>()
        if (enabled(context)) {
            val now = System.currentTimeMillis()
            for (exam in exams) {
                for ((k, at) in reminderTimes(exam.time).withIndex()) {
                    if (at <= now) continue
                    val code = (exam.id + k).hashCode()
                    val intent = Intent(context, ExamAlarmReceiver::class.java)
                        .putExtra("title", exam.title.ifBlank { exam.course })
                        .putExtra("course", exam.course)
                        .putExtra("time", exam.time)
                        .putExtra("place", exam.slot.ifBlank { exam.place })
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context, code, intent))
                    codes += code.toString()
                }
            }
        }
        prefs.edit().putStringSet("codes", codes).apply()
    }

    private fun reminderTimes(examTime: Long): List<Long> {
        fun eveningBefore(days: Int) = Calendar.getInstance().apply {
            timeInMillis = examTime
            add(Calendar.DAY_OF_YEAR, -days)
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis
        return listOf(eveningBefore(3), eveningBefore(1), examTime - 2 * 60 * 60 * 1000L)
    }

    private fun pending(context: Context, code: Int, intent: Intent?): PendingIntent =
        PendingIntent.getBroadcast(
            context, code, intent ?: Intent(context, ExamAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun notify(context: Context, title: String, course: String, time: Long, place: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Sınav hatırlatıcıları", NotificationManager.IMPORTANCE_HIGH))
        val days = daysUntil(time)
        val whenText = when {
            days <= 0 -> "Bugün " + SimpleDateFormat("HH:mm", Locale.ROOT).format(time)
            days == 1 -> "Yarın " + SimpleDateFormat("HH:mm", Locale.ROOT).format(time)
            else -> "$days gün sonra"
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_EXAMS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_shortcut_note)
            .setContentTitle("📅 $title")
            .setContentText(listOf(whenText, place).filter { it.isNotBlank() }.joinToString(" · "))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify((title + time).hashCode(), n)
    }

    /** Sınava kalan gün (takvim günü; bugün = 0). */
    fun daysUntil(time: Long): Int {
        fun day(t: Long) = Calendar.getInstance().apply {
            timeInMillis = t
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return Math.round((day(time) - day(System.currentTimeMillis())) / 86_400_000.0).toInt()
    }
}

class ExamAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> ExamReminders.rescheduleAll(context)
            else -> ExamReminders.notify(
                context,
                intent.getStringExtra("title") ?: "Sınav",
                intent.getStringExtra("course") ?: "",
                intent.getLongExtra("time", 0L),
                intent.getStringExtra("place") ?: "",
            )
        }
    }
}
