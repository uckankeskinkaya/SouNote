package com.sounote.app.ink

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.pow

enum class Tool { PEN, HIGHLIGHTER, ERASER, TEXT }

/** Kalem aracının türü: hangi çizgi türüyle kaydedileceği ve basınca tepki verip vermediği. */
enum class PenType(
    val label: String,
    val description: String,
    val short: String,
    val inkTool: InkTool,
    val usesPressure: Boolean,
) {
    FOUNTAIN("Dolma kalem", "Bastırdıkça kalınlaşır", "", InkTool.PEN, true),
    BALLPOINT("Tükenmez", "Her zaman aynı kalınlık", "T", InkTool.PEN, false),
    PENCIL("Kurşun kalem", "Yarı saydam, sabit kalınlık", "Kş", InkTool.PENCIL, false),
    BRUSH("Fırça", "Basınca çok duyarlı, uçları incelir", "F", InkTool.BRUSH, true),
    CALLIGRAPHY("Kaligrafi", "Yöne göre ince/kalın, güzel yazı için", "Kl", InkTool.CALLIGRAPHY, false),
    DASHED("Kesik çizgi", "Şekil ve şemalar için", "--", InkTool.DASHED, false),
}

/** Araç çubuğunda tek dokunuşla seçilen kayıtlı kalem. */
data class FavoritePen(
    val tool: Tool,
    val penType: PenType,
    val color: Int,
    val widthMm: Float,
)

/** Seçili araç ve her aracın kendi ayarları. Kalınlıklar mm (sayfa üzerinde). */
data class ToolState(
    val tool: Tool = Tool.PEN,
    val penType: PenType = PenType.FOUNTAIN,
    val penColor: Int = PEN_COLORS[0],
    val penWidthMm: Float = 0.5f,
    /** 0 = sert (kalınlaşmak için çok bastır), 1 = yumuşak (hafif dokunuş da kalın). */
    val pressureSensitivity: Float = 0.5f,
    /** Kalemi çizginin sonunda bir an bekletince düz çizgi / şekil yap. */
    val shapeAssist: Boolean = true,
    val highlighterColor: Int = HIGHLIGHTER_COLORS[0],
    val highlighterWidthMm: Float = 5f,
    /** Fosforlu her zaman düz çizgi çizer (altını çizmek için). */
    val highlighterStraight: Boolean = false,
    val eraserMode: EraserMode = EraserMode.STROKE,
    /** Silgi yarıçapı, ekranda dp; yakınlaştırmadan bağımsız aynı boyutta görünür. */
    val eraserSizeDp: Float = 14f,
    val eraserHighlighterOnly: Boolean = false,
    /** Açıkken tek parmak çizer, iki parmak kaydırır/yakınlaştırır. */
    val fingerDraw: Boolean = false,
    /** Yeni metin kutularının yazı boyutu (pt) ve rengi. */
    val textSizePt: Float = 14f,
    val textColor: Int = PEN_COLORS[0],
    val favorites: List<FavoritePen> = emptyList(),
    /** Renk seçiciden seçilmiş son renkler (en yenisi başta). */
    val customColors: List<Int> = emptyList(),
) {
    /** Ham kalem basıncını hassasiyet ayarına göre eğer. */
    fun applyPressureCurve(p: Float): Float {
        val gamma = 2f.pow((0.5f - pressureSensitivity) * 2f)
        return p.coerceIn(0f, 1f).pow(gamma)
    }

    fun withCustomColor(color: Int) =
        copy(customColors = (listOf(color) + customColors.filter { it != color }).take(MAX_CUSTOM_COLORS))

    fun currentFavorite(): FavoritePen? = when (tool) {
        Tool.PEN -> FavoritePen(Tool.PEN, penType, penColor, penWidthMm)
        Tool.HIGHLIGHTER -> FavoritePen(Tool.HIGHLIGHTER, PenType.FOUNTAIN, highlighterColor, highlighterWidthMm)
        else -> null
    }

    fun apply(f: FavoritePen) = when (f.tool) {
        Tool.HIGHLIGHTER -> copy(tool = Tool.HIGHLIGHTER, highlighterColor = f.color, highlighterWidthMm = f.widthMm)
        else -> copy(tool = Tool.PEN, penType = f.penType, penColor = f.color, penWidthMm = f.widthMm)
    }

    fun isActive(f: FavoritePen) = currentFavorite() == f

    fun save(prefs: SharedPreferences) {
        prefs.edit()
            .putString("tool", tool.name)
            .putString("penType", penType.name)
            .putInt("penColor", penColor)
            .putFloat("penWidthMm", penWidthMm)
            .putFloat("pressureSensitivity", pressureSensitivity)
            .putBoolean("shapeAssist", shapeAssist)
            .putInt("highlighterColor", highlighterColor)
            .putFloat("highlighterWidthMm", highlighterWidthMm)
            .putBoolean("highlighterStraight", highlighterStraight)
            .putString("eraserMode", eraserMode.name)
            .putFloat("eraserSizeDp", eraserSizeDp)
            .putBoolean("eraserHighlighterOnly", eraserHighlighterOnly)
            .putBoolean("fingerDraw", fingerDraw)
            .putFloat("textSizePt", textSizePt)
            .putInt("textColor", textColor)
            .putString("favorites", JSONArray().apply {
                favorites.forEach {
                    put(
                        JSONObject()
                            .put("tool", it.tool.name)
                            .put("penType", it.penType.name)
                            .put("color", it.color)
                            .put("widthMm", it.widthMm.toDouble()),
                    )
                }
            }.toString())
            .putString("customColors", customColors.joinToString(","))
            .apply()
    }

    companion object {
        const val MAX_FAVORITES = 10
        private const val MAX_CUSTOM_COLORS = 8

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
                penType = enumOr(prefs.getString("penType", null), d.penType),
                penColor = prefs.getInt("penColor", d.penColor),
                penWidthMm = prefs.getFloat("penWidthMm", d.penWidthMm),
                pressureSensitivity = prefs.getFloat("pressureSensitivity", d.pressureSensitivity),
                shapeAssist = prefs.getBoolean("shapeAssist", d.shapeAssist),
                highlighterColor = prefs.getInt("highlighterColor", d.highlighterColor),
                highlighterWidthMm = prefs.getFloat("highlighterWidthMm", d.highlighterWidthMm),
                highlighterStraight = prefs.getBoolean("highlighterStraight", d.highlighterStraight),
                eraserMode = enumOr(prefs.getString("eraserMode", null), d.eraserMode),
                eraserSizeDp = prefs.getFloat("eraserSizeDp", d.eraserSizeDp),
                eraserHighlighterOnly = prefs.getBoolean("eraserHighlighterOnly", d.eraserHighlighterOnly),
                fingerDraw = prefs.getBoolean("fingerDraw", d.fingerDraw),
                textSizePt = prefs.getFloat("textSizePt", d.textSizePt),
                textColor = prefs.getInt("textColor", d.textColor),
                favorites = loadFavorites(prefs.getString("favorites", null)),
                customColors = prefs.getString("customColors", null)
                    ?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList(),
            )
        }

        private fun loadFavorites(json: String?): List<FavoritePen> {
            if (json.isNullOrEmpty()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    FavoritePen(
                        tool = enumOr(o.optString("tool"), Tool.PEN),
                        penType = enumOr(o.optString("penType"), PenType.FOUNTAIN),
                        color = o.getInt("color"),
                        widthMm = o.getDouble("widthMm").toFloat(),
                    )
                }
            }.getOrDefault(emptyList())
        }

        private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
            name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
    }
}
