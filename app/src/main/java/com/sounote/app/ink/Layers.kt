package com.sounote.app.ink

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Belgenin bir çizim katmanı. [id] çizgilere yazılır (Stroke.layer); sıra listedeki yeridir (ilk = en alt). */
data class InkLayer(val id: Int, val name: String, val visible: Boolean = true)

/**
 * Belgenin katmanları ve etkin katman: filesDir/ink/<belge>/layers.json. Dosya yoksa tek katman (0).
 * Katmanlar sadece kalem/fosforlu çizgileri içindir; yazı kutuları ve resimler katmansızdır.
 * Silgi ve lasso sadece etkin katmandaki çizgilere dokunur.
 */
data class LayerSet(val layers: List<InkLayer> = listOf(InkLayer(0, "Katman 1")), val active: Int = 0) {
    val single get() = layers.size <= 1

    /** Bilinmeyen katmandaki (silinmiş katman ya da eski dosya) çizgi ilk katmana sayılır. */
    fun layerOf(s: Stroke): Int = if (layers.any { it.id == s.layer }) s.layer else layers.first().id

    fun visible(s: Stroke) = layers.firstOrNull { it.id == layerOf(s) }?.visible ?: true

    /** Silgi ve lassonun dokunabileceği çizgi: etkin katmanda. */
    fun editable(s: Stroke) = single || layerOf(s) == active

    /** Çizilecek çizgiler: gizli katmanlar çıkar, katman sırasına dizilir (katman içinde çizim sırası korunur). */
    fun arrange(strokes: List<Stroke>): List<Stroke> {
        if (single) return strokes
        val order = layers.withIndex().associate { (i, l) -> l.id to i }
        return strokes.filter { visible(it) }.sortedBy { order[layerOf(it)] ?: 0 }
    }

    fun save(dir: File) {
        dir.mkdirs()
        val arr = JSONArray()
        layers.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("visible", it.visible)) }
        File(dir, FILE).writeText(JSONObject().put("layers", arr).put("active", active).toString())
    }

    companion object {
        const val FILE = "layers.json"
        const val MAX = 6
        val DEFAULT = LayerSet()

        fun load(dir: File?): LayerSet {
            val f = File(dir ?: return DEFAULT, FILE)
            if (!f.exists()) return DEFAULT
            return runCatching {
                val o = JSONObject(f.readText())
                val a = o.getJSONArray("layers")
                val list = List(a.length()) { i ->
                    val l = a.getJSONObject(i)
                    InkLayer(l.getInt("id"), l.optString("name", "Katman ${i + 1}"), l.optBoolean("visible", true))
                }
                if (list.isEmpty()) DEFAULT
                else LayerSet(list, o.optInt("active", list.first().id).takeIf { id -> list.any { it.id == id } } ?: list.first().id)
            }.getOrDefault(DEFAULT)
        }
    }
}
