package com.sounote.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Bilgi kartı. Ön yüz: yazı ve/veya sayfadan alınmış görüntü; arka yüz: cevap.
 * [box] Leitner kutusu (1–5): bildikçe artar, kart daha seyrek gelir. [due]: bir sonraki tekrar zamanı.
 */
data class Card(
    val id: Long,
    val frontText: String,
    val frontImage: String?,
    val back: String,
    val page: Int,
    val box: Int = 1,
    val due: Long = 0L,
)

/** Kartlar belgeyle birlikte: filesDir/ink/<belge>/cards.json + cards/ klasöründe görüntüler. */
object Flashcards {
    private val INTERVAL_DAYS = intArrayOf(1, 2, 4, 8, 16)
    private const val DAY = 24L * 60 * 60 * 1000

    fun dir(context: Context, docId: String) = File(context.filesDir, "ink/$docId")
    fun imagesDir(context: Context, docId: String) = File(dir(context, docId), "cards")
    private fun file(context: Context, docId: String) = File(dir(context, docId), "cards.json")

    fun load(context: Context, docId: String): List<Card> {
        val f = file(context, docId)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Card(
                    id = o.getLong("id"),
                    frontText = o.optString("front"),
                    frontImage = o.optString("image").ifEmpty { null },
                    back = o.optString("back"),
                    page = o.optInt("page", -1),
                    box = o.optInt("box", 1),
                    due = o.optLong("due", 0L),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, docId: String, cards: List<Card>) {
        val arr = JSONArray()
        cards.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("front", it.frontText).put("image", it.frontImage ?: "")
                    .put("back", it.back).put("page", it.page).put("box", it.box).put("due", it.due),
            )
        }
        val f = file(context, docId)
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(f)) throw IOException("Kartlar kaydedilemedi")
    }

    /** Bildim: bir üst kutuya, daha uzun aralıkla. Bilemedim: ilk kutuya, hemen tekrar. */
    fun review(card: Card, known: Boolean, now: Long = System.currentTimeMillis()): Card =
        if (known) {
            val box = (card.box + 1).coerceAtMost(5)
            card.copy(box = box, due = now + INTERVAL_DAYS[box - 1] * DAY)
        } else {
            card.copy(box = 1, due = now)
        }
}
