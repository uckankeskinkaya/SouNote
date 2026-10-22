package com.sounote.app.ink

import android.content.SharedPreferences

enum class Tool { PEN, HIGHLIGHTER, ERASER }

/** Seçili araç ve her aracın kendi ayarları. Kalınlıklar mm (sayfa üzerinde). */
data class ToolState(
    val tool: Tool = Tool.PEN,
    val penColor: Int = PEN_COLORS[0],
    val penWidthMm: Float = 0.5f,
    val highlighterColor: Int = HIGHLIGHTER_COLORS[0],
    val highlighterWidthMm: Float = 5f,
    val eraserMode: EraserMode = EraserMode.STROKE,
    /** Silgi yarıçapı, ekranda dp; yakınlaştırmadan bağımsız aynı boyutta görünür. */
    val eraserSizeDp: Float = 14f,
    val eraserHighlighterOnly: Boolean = false,
    /** Açıkken tek parmak çizer, iki parmak kaydırır/yakınlaştırır. */
    val fingerDraw: Boolean = false,
) {
    fun save(prefs: SharedPreferences) {
        prefs.edit()
            .putString("tool", tool.name)
            .putInt("penColor", penColor)
            .putFloat("penWidthMm", penWidthMm)
            .putInt("highlighterColor", highlighterColor)
            .putFloat("highlighterWidthMm", highlighterWidthMm)
            .putString("eraserMode", eraserMode.name)
            .putFloat("eraserSizeDp", eraserSizeDp)
            .putBoolean("eraserHighlighterOnly", eraserHighlighterOnly)
            .putBoolean("fingerDraw", fingerDraw)
            .apply()
    }

    companion object {
        val PEN_COLORS = listOf(
            0xFF1B1B1F.toInt(), // siyah
            0xFF1F5FD6.toInt(), // mavi
            0xFFD93025.toInt(), // kırmızı
            0xFF188038.toInt(), // yeşil
            0xFF7B3FC4.toInt(), // mor
            0xFFE8710A.toInt(), // turuncu
            0xFF6E6E73.toInt(), // gri
        )
        val HIGHLIGHTER_COLORS = listOf(
            0xFFFFE94D.toInt(), // sarı
            0xFFA6E37A.toInt(), // yeşil
            0xFFFF9FCB.toInt(), // pembe
            0xFF8FD3FF.toInt(), // mavi
            0xFFFFC069.toInt(), // turuncu
        )

        fun load(prefs: SharedPreferences): ToolState {
            val d = ToolState()
            return ToolState(
                tool = enumOr(prefs.getString("tool", null), d.tool),
                penColor = prefs.getInt("penColor", d.penColor),
                penWidthMm = prefs.getFloat("penWidthMm", d.penWidthMm),
                highlighterColor = prefs.getInt("highlighterColor", d.highlighterColor),
                highlighterWidthMm = prefs.getFloat("highlighterWidthMm", d.highlighterWidthMm),
                eraserMode = enumOr(prefs.getString("eraserMode", null), d.eraserMode),
                eraserSizeDp = prefs.getFloat("eraserSizeDp", d.eraserSizeDp),
                eraserHighlighterOnly = prefs.getBoolean("eraserHighlighterOnly", d.eraserHighlighterOnly),
                fingerDraw = prefs.getBoolean("fingerDraw", d.fingerDraw),
            )
        }

        private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
            name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
    }
}
