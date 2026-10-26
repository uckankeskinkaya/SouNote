package com.sounote.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Drive'daki ders klasörünü kendiliğinden izler: kök klasördeki "Hafta NN …" klasörleri ve içlerindeki dosyalar
 * Drive'ın herkese açık klasör görünümünden (embeddedfolderview, anahtar gerekmez) okunur. Katalogda olmayan dosyalar
 * "Yeni gelenler" dersinde ve kendi haftasında "Bu hafta" altında görünür; sınıflandırılınca (katalogda aynı Drive
 * kimliği olunca) buradan kendiliğinden düşer. Son liste filesDir/drive-auto.json'da saklanır (internetsiz de görünür).
 */
object DriveAuto {
    /** "Ingilizce calismalar" klasörü; katalog "driveRoot" verirse o kullanılır. */
    const val DEFAULT_ROOT = "1f2chdgREDpdIpHoKh4I43oeBRC0tlo8q"
    const val COURSE_ID = "auto"
    private const val FILE = "drive-auto.json"

    data class Entry(val id: String, val name: String, val folder: Boolean)
    data class WeekFolder(val week: Int, val name: String, val files: List<Entry>)

    private val ENTRY = Regex(
        """<div class="flip-entry" id="entry-([A-Za-z0-9_-]+)".*?<a href="([^"]+)".*?<div class="flip-entry-title">([^<]*)</div>""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val WEEK = Regex("""(?i)hafta\s*0*(\d{1,2})""")

    /** Klasörün içeriği (dosyalar ve alt klasörler). */
    fun list(folderId: String): List<Entry> {
        val html = httpGet("https://drive.google.com/embeddedfolderview?id=$folderId")
        return ENTRY.findAll(html).map { m ->
            Entry(m.groupValues[1], unescape(m.groupValues[3]).trim(), "/drive/folders/" in m.groupValues[2])
        }.toList()
    }

    /** Kökteki hafta klasörlerini ve içlerini indirip saklar; "Diğer" gibi haftasız klasörler 0. hafta. */
    fun refresh(context: Context, root: String) {
        val folders = list(root).filter { it.folder }
        val weeks = folders.map { f ->
            val week = WEEK.find(f.name)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            WeekFolder(week, f.name, list(f.id).filter { !it.folder })
        }
        val arr = JSONArray()
        weeks.forEach { w ->
            arr.put(
                JSONObject().put("week", w.week).put("name", w.name).put(
                    "files",
                    JSONArray().apply { w.files.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) } },
                ),
            )
        }
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.writeText(JSONObject().put("updated", System.currentTimeMillis()).put("weeks", arr).toString())
        if (!tmp.renameTo(File(context.filesDir, FILE))) throw IOException("Drive listesi kaydedilemedi")
    }

    fun cached(context: Context): List<WeekFolder> = runCatching {
        val o = JSONObject(File(context.filesDir, FILE).readText())
        val a = o.getJSONArray("weeks")
        (0 until a.length()).map { i ->
            val w = a.getJSONObject(i)
            val f = w.getJSONArray("files")
            WeekFolder(w.getInt("week"), w.optString("name"), (0 until f.length()).map { k ->
                val e = f.getJSONObject(k)
                Entry(e.getString("id"), e.getString("name"), false)
            })
        }
    }.getOrDefault(emptyList())

    /** Kataloğa, Drive'da olup katalogda olmayan dosyaları "Yeni gelenler" dersi olarak ekler. */
    fun merge(catalog: Catalog, weeks: List<WeekFolder>): Catalog {
        if (weeks.isEmpty()) return catalog
        val all = catalog.courses.flatMap { c -> c.topics.flatMap { it.resources } }
        val knownIds = all.mapNotNull { it.driveId }.toSet()
        // Sınıflandırılmış başlık + tür: aynı adlı PPTX'in PDF'i yeni gelirse "(PDF)" diye ayrı görünür
        val knownTitles = all.associate { normTitle(it.title) to it.kind }
        val topics = ArrayList<Topic>()
        for (w in weeks.sortedBy { if (it.week == 0) 999 else it.week }) {
            val fresh = w.files.filter { it.id !in knownIds && kindOf(it.name) != null }
            // Aynı dosyanın "(1)" kopyaları ve aynı adın PDF/PPTX ikilisi: PDF varsa o (uygulamada üstüne yazılır)
            val byTitle = fresh.groupBy { normTitle(baseName(it.name)) }
            val picked = byTitle.mapNotNull { (title, group) ->
                val best = group.minByOrNull { if (kindOf(it.name) == "pdf") 0 else 1 } ?: return@mapNotNull null
                val kind = kindOf(best.name)!!
                val known = knownTitles[title]
                // Aynı adla katalogdaysa atla; sadece katalogdakinin PDF'i yeni geldiyse "(PDF)" diye göster
                if (known != null && !(kind == "pdf" && known != "pdf")) return@mapNotNull null
                val display = cleanTitle(baseName(best.name)) + if (known != null && kind == "pdf") " (PDF)" else ""
                Triple(display, best, kind)
            }.sortedBy { it.first.lowercase() }
            if (picked.isEmpty()) continue
            topics += Topic(
                id = "auto-w${w.week}",
                name = if (w.week > 0) "Hafta ${w.week}" else w.name,
                resources = picked.mapIndexed { i, (title, e, kind) ->
                    Resource(title, "https://drive.google.com/file/d/${e.id}/view", kind, w.week, if (w.week > 0) 6 else 0, i)
                },
            )
        }
        if (topics.isEmpty()) return catalog
        val course = Course(COURSE_ID, "Yeni gelenler", "", 0xFF5B7FD6.toInt(), topics)
        return catalog.copy(courses = catalog.courses + course)
    }

    /** Uzantıya göre tür; listede gösterilmeyecekler (yedek zip, bilinmeyen) null. */
    fun kindOf(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "pdf"
        "pptx", "ppt" -> "pptx"
        "docx", "doc" -> "docx"
        "mp3", "m4a", "wav", "ogg", "mpeg", "mpga" -> "audio"
        "mp4", "mov", "webm", "mkv" -> "video"
        "html", "htm" -> "html"
        "png", "jpg", "jpeg" -> "image"
        else -> null
    }

    private fun baseName(name: String) = if ('.' in name) name.substringBeforeLast('.') else name

    /** "Ad (1)" kopya ekini ve alt çizgileri temizler. */
    private fun cleanTitle(s: String) = s.replace(Regex("""\s*\(\d+\)\s*$"""), "").replace('_', ' ').replace(Regex("\\s+"), " ").trim()

    private fun normTitle(s: String) = cleanTitle(s).lowercase().replace(Regex("[^a-z0-9ğüşıöç]+"), "")

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"")
        .replace("&lt;", "<").replace("&gt;", ">")

    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) SouNote")
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
