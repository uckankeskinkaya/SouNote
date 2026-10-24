package com.sounote.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Hızlı erişim (favoriler): belge, klasör, ders ya da yazılı not. Sadece kimlik tutulur;
 * belge başka klasöre taşınsa da kimliği değişmediği için favori bozulmaz. Favoriden çıkarmak içeriği silmez.
 * Sıra = kullanıcının elle verdiği sıra; sabitlenenler ana sayfada önce gelir.
 */
data class QuickItem(val type: String, val id: String, val pinned: Boolean = false) {
    val key get() = "$type:$id"

    companion object {
        const val DOC = "doc"
        const val FOLDER = "folder"
        const val COURSE = "course"
        const val NOTE = "note"
    }
}

/** Tercihlerde JSON olarak saklanır (prefs "quickAccess"). */
object QuickAccess {
    fun parse(json: String?): List<QuickItem> = runCatching {
        val arr = JSONArray(json ?: return emptyList())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val type = o.optString("type")
            val id = o.optString("id")
            if (type.isEmpty() || id.isEmpty()) null else QuickItem(type, id, o.optBoolean("pinned"))
        }.distinctBy { it.key }
    }.getOrDefault(emptyList())

    fun toJson(items: List<QuickItem>): String = JSONArray().apply {
        items.forEach { put(JSONObject().put("type", it.type).put("id", it.id).put("pinned", it.pinned)) }
    }.toString()
}

/** Ana sayfa modülleri: sıralanabilir ve gizlenebilir. Ad tercihlerde saklanır, değiştirme. */
enum class HomeModule(val label: String) {
    HERO("Karşılama"),
    CONTINUE("Kaldığın yer"),
    REVIEW("Bugünkü tekrar"),
    QUICK_ACCESS("Favoriler"),
    UPCOMING("Yaklaşanlar"),
    WEEK_TASKS("Haftalık görevler"),
    ACTIONS("Hızlı başla"),
    RECENT("Son kullanılanlar"),
    NOTES("Yazılı notlar"),
    STATS("Bu hafta"),
    TIP("İpucu"),
}

data class HomeLayout(val order: List<HomeModule>, val hidden: Set<HomeModule>) {
    val visible get() = order.filter { it !in hidden }

    fun move(m: HomeModule, delta: Int): HomeLayout {
        val i = order.indexOf(m)
        val j = (i + delta).coerceIn(0, order.size - 1)
        if (i < 0 || i == j) return this
        return copy(order = order.toMutableList().apply { add(j, removeAt(i)) })
    }

    fun setVisible(m: HomeModule, on: Boolean) = copy(hidden = if (on) hidden - m else hidden + m)

    companion object {
        /** Sade başlangıç: ipucu ve yazılı notlar (Yazı sekmesinde de var) gizli; Ana sayfayı düzenle'den açılır. */
        private val DEFAULT_HIDDEN = setOf(HomeModule.TIP, HomeModule.NOTES)

        val DEFAULT = HomeLayout(HomeModule.entries.toList(), DEFAULT_HIDDEN)

        /** Kayıtlı sıra; sonradan eklenen modüller sona eklenir, bilinmeyen adlar atlanır. */
        fun parse(order: String?, hidden: String?): HomeLayout {
            fun names(s: String?) = s.orEmpty().split(',').mapNotNull { n -> HomeModule.entries.find { it.name == n.trim() } }
            val saved = names(order).distinct()
            val all = saved + HomeModule.entries.filter { it !in saved }
            return HomeLayout(all, if (hidden == null) DEFAULT_HIDDEN else names(hidden).toSet())
        }
    }
}
