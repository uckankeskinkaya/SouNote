package com.sounote.app

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hata kaydı. Bilgisayar olmadan hata ayıklamak için: çökme olursa bir sonraki açılışta
 * kullanıcıya gösterilir ve paylaşılabilir. Önemli ama çökme olmayan hatalar da eklenir.
 */
object ErrorLog {
    private const val MAX_BYTES = 200 * 1024
    private lateinit var dir: File
    private var versionName = "?"

    private val crashFile get() = File(dir, "crash.txt")
    private val errorsFile get() = File(dir, "errors.txt")

    fun install(app: Application) {
        dir = File(app.filesDir, "logs").apply { mkdirs() }
        versionName = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName
        }.getOrNull() ?: "?"
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { crashFile.writeText(report("ÇÖKME (${thread.name})", e)) }
            previous?.uncaughtException(thread, e)
        }
    }

    /** Çökmeyen ama kaydedilmeye değer hata (ör. dışa aktarma başarısız). */
    fun record(where: String, e: Throwable) {
        Log.e("SouNote", where, e)
        if (!::dir.isInitialized) return
        runCatching {
            val f = errorsFile
            if (f.exists() && f.length() > MAX_BYTES) f.writeText(f.readText().takeLast(MAX_BYTES / 2))
            f.appendText(report(where, e) + "\n")
        }
    }

    /** Son çökmenin raporu (bir kez gösterilsin diye okununca silinmez; [clearCrash] ile silinir). */
    fun pendingCrash(): String? = if (::dir.isInitialized && crashFile.exists()) runCatching { crashFile.readText() }.getOrNull() else null

    fun clearCrash() {
        if (::dir.isInitialized) crashFile.delete()
    }

    /** Paylaşmak için bütün kayıt: son çökme + son hatalar. */
    fun fullReport(context: Context): String = buildString {
        append("SouNote ").append(versionName).append(" · Android ").append(Build.VERSION.RELEASE)
            .append(" (API ").append(Build.VERSION.SDK_INT).append(") · ")
            .append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append("\n\n")
        if (!::dir.isInitialized) return@buildString
        if (crashFile.exists()) append(crashFile.readText()).append("\n")
        if (errorsFile.exists()) append(errorsFile.readText().takeLast(60_000))
        if (!crashFile.exists() && !errorsFile.exists()) append("Kayıtlı hata yok.")
    }

    private fun report(title: String, e: Throwable): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
        val trace = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
        return "=== $title · $time · sürüm $versionName ===\n$trace"
    }
}

class SouNoteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ErrorLog.install(this)
    }
}
