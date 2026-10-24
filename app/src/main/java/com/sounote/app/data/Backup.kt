package com.sounote.app.data

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Bütün kütüphanenin tek dosyalık yedeği (zip): library.json, PDF'ler (docs/), notlar (ink/) ve yazılı notlar (texts/).
 * Bağlı klasörlerin kendisi yedeğe girmez (zaten tablette duruyorlar), not alınmış olanların kopyası girer.
 */
object Backup {
    private val PARTS = listOf("library.json", "docs", "ink", TextNoteStore.DIR, ExamStore.FILE, GradeStore.FILE, EssayStore.DIR, SpeakingStore.DIR)
    private const val MARKER = "sounote-yedek.txt"

    /** Kütüphanenin o anki hali için kısa bir iz: değişmediyse otomatik yedek tekrar alınmaz. */
    fun fingerprint(context: Context): String {
        var count = 0
        var size = 0L
        var latest = 0L
        for (part in PARTS) {
            File(context.filesDir, part).walkTopDown().filter { it.isFile }.forEach {
                count++
                size += it.length()
                latest = maxOf(latest, it.lastModified())
            }
        }
        return "$count-$size-$latest"
    }

    fun write(context: Context, out: OutputStream) {
        val root = context.filesDir
        ZipOutputStream(BufferedOutputStream(out, 64 * 1024)).use { zip ->
            zip.putNextEntry(ZipEntry(MARKER))
            zip.write("SouNote yedeği, sürüm 1\n".toByteArray())
            zip.closeEntry()
            for (part in PARTS) {
                val f = File(root, part)
                if (f.exists()) addRecursively(zip, f, part)
            }
        }
    }

    private fun addRecursively(zip: ZipOutputStream, file: File, name: String) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { addRecursively(zip, it, "$name/${it.name}") }
            return
        }
        // Yarım kalmış geçici dosyalar yedeğe girmesin.
        if (file.name.endsWith(".tmp") || file.name.endsWith(".edit") || file.name.endsWith(".move")) return
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    /**
     * Yedeği geri yükler: önce ayrı bir klasöre açılır ve doğrulanır, sonra mevcut kütüphanenin yerine konur.
     * Böylece bozuk bir dosya mevcut notları silmez.
     */
    fun restore(context: Context, input: InputStream) {
        val root = context.filesDir
        val staging = File(root, "restore_tmp")
        staging.deleteRecursively()
        staging.mkdirs()
        val base = staging.canonicalPath + File.separator
        var marker = false
        ZipInputStream(BufferedInputStream(input, 64 * 1024)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == MARKER) marker = true
                val target = File(staging, entry.name)
                // Zip içinden klasör dışına yazmaya çalışan girdileri reddet.
                if (!target.canonicalPath.startsWith(base)) throw IOException("Geçersiz yedek dosyası")
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
            }
        }
        if (!marker || !File(staging, "library.json").exists()) {
            staging.deleteRecursively()
            throw IOException("Bu bir SouNote yedeği değil")
        }
        for (part in PARTS) {
            File(root, part).deleteRecursively()
            val f = File(staging, part)
            if (f.exists() && !f.renameTo(File(root, part))) throw IOException("Yedek yerleştirilemedi")
        }
        staging.deleteRecursively()
    }
}
