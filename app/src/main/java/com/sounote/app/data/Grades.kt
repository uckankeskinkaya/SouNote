package com.sounote.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Not hesabındaki bir kalem: ad, toplam nottaki yüzde ağırlık, puan (girilmediyse null = henüz yapılmadı).
 * [group]: aynı gruptakilerin kendi içinde ortalaması da gösterilir (ör. "Kur içi", "Kur Atlama Sınavı").
 */
data class GradeItem(val id: Long, val name: String, val weight: Double, val score: Double?, val group: String = "")

data class Grades(val passMark: Double, val items: List<GradeItem>) {
    val totalWeight get() = items.sumOf { it.weight }
    private val done get() = items.filter { it.score != null }
    val doneWeight get() = done.sumOf { it.weight }
    val remainingWeight get() = totalWeight - doneWeight
    private val earned get() = done.sumOf { it.weight * it.score!! }

    /** Girilen sınavların ağırlıklı ortalaması (hiç yoksa null). */
    val average: Double? get() = if (doneWeight > 0) earned / doneWeight else null

    /** Şu ana kadar toplanan puan (100 üzerinden; kalanlar 0 sayılır). */
    val secured: Double get() = if (totalWeight > 0) earned / totalWeight else 0.0

    /** Geçmek için kalan sınavlardan alınması gereken ortalama (kalan yoksa null). */
    val needed: Double? get() = if (remainingWeight > 0) (passMark * totalWeight - earned) / remainingWeight else null

    /** Grubun kendi içindeki ağırlıklı ortalaması (100 üzerinden; puan girilmemişse null). */
    fun groupAverage(group: String): Double? {
        val done = items.filter { it.group == group && it.score != null }
        val w = done.sumOf { it.weight }
        return if (w > 0) done.sumOf { it.weight * it.score!! } / w else null
    }

    val groups get() = items.map { it.group }.filter { it.isNotEmpty() }.distinct()

    companion object {
        val EMPTY = Grades(70.0, emptyList())

        const val INTERNAL = "Kur içi (%40)"
        const val LEVEL_EXAM = "Kur Atlama Sınavı (%60)"

        /**
         * Ankara Üniversitesi YDYO İngilizce Hazırlık (Öğrenci El Kitabı 2026-2027): kur içi değerlendirme %40,
         * Kur Atlama Sınavı %60; sonuç en az 70 olmalı. Ağırlıklar toplam nota göre (kur içi %10 → toplamda %4).
         */
        fun ankaraTemplate(now: Long = System.currentTimeMillis()): Grades {
            val internal = listOf(
                "Kısa Sınav 1 (Quiz)" to 10.0, "Ara Sınav 1 (Progress Test)" to 20.0,
                "Kısa Sınav 2 (Quiz)" to 10.0, "Ara Sınav 2 (Progress Test)" to 20.0,
                "Sözlü Performans 1 (Video)" to 10.0, "Sözlü Performans 2 (Sunum)" to 10.0,
                "Yazma Sınavı 1" to 10.0, "Yazma Sınavı 2" to 10.0,
            )
            val level = listOf(
                "KAS: Dinleme, Okuma, Dil Kullanımı" to 60.0,
                "KAS: Yazma Sınavı" to 20.0,
                "KAS: Sözlü Sınav" to 20.0,
            )
            var id = now
            return Grades(
                70.0,
                internal.map { (n, w) -> GradeItem(id++, n, w * 0.4, null, INTERNAL) } +
                    level.map { (n, w) -> GradeItem(id++, n, w * 0.6, null, LEVEL_EXAM) },
            )
        }
    }
}

/** filesDir/grades.json (yedeğe girer). */
class GradeStore(context: Context) {
    private val file = File(context.filesDir, FILE)

    fun load(): Grades = runCatching {
        val o = JSONObject(file.readText())
        val arr = o.optJSONArray("items") ?: JSONArray()
        Grades(
            o.optDouble("pass", 70.0),
            List(arr.length()) { i ->
                val it = arr.getJSONObject(i)
                GradeItem(
                    it.getLong("id"),
                    it.optString("name"),
                    it.optDouble("weight", 0.0),
                    if (it.has("score") && !it.isNull("score")) it.getDouble("score") else null,
                    it.optString("group"),
                )
            },
        )
    }.getOrDefault(Grades.EMPTY)

    fun save(g: Grades) {
        val arr = JSONArray()
        g.items.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("weight", it.weight).put("score", it.score ?: JSONObject.NULL).put("group", it.group))
        }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(JSONObject().put("pass", g.passMark).put("items", arr).toString())
        tmp.renameTo(file)
    }

    companion object {
        const val FILE = "grades.json"
    }
}
