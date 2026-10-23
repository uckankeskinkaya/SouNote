package com.sounote.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Not hesabındaki bir kalem: ad, yüzde ağırlık, puan (girilmediyse null = henüz yapılmadı). */
data class GradeItem(val id: Long, val name: String, val weight: Double, val score: Double?)

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

    companion object {
        val EMPTY = Grades(60.0, emptyList())
    }
}

/** filesDir/grades.json (yedeğe girer). */
class GradeStore(context: Context) {
    private val file = File(context.filesDir, FILE)

    fun load(): Grades = runCatching {
        val o = JSONObject(file.readText())
        val arr = o.optJSONArray("items") ?: JSONArray()
        Grades(
            o.optDouble("pass", 60.0),
            List(arr.length()) { i ->
                val it = arr.getJSONObject(i)
                GradeItem(
                    it.getLong("id"),
                    it.optString("name"),
                    it.optDouble("weight", 0.0),
                    if (it.has("score") && !it.isNull("score")) it.getDouble("score") else null,
                )
            },
        )
    }.getOrDefault(Grades.EMPTY)

    fun save(g: Grades) {
        val arr = JSONArray()
        g.items.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("weight", it.weight).put("score", it.score ?: JSONObject.NULL))
        }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(JSONObject().put("pass", g.passMark).put("items", arr).toString())
        tmp.renameTo(file)
    }

    companion object {
        const val FILE = "grades.json"
    }
}
