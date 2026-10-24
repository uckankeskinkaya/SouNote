package com.sounote.app.ink

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.pow

enum class Tool { PEN, HIGHLIGHTER, ERASER, TEXT, LASSO, TAPE, LASER, STAMP, SHAPE }

/** Ekrandaki ölçüm aracı (kaydedilmez). */
enum class GuideKind { NONE, RULER, PROTRACTOR }

/** Şekil aracının çizdiği şekil. */
enum class ShapeKind(val symbol: String, val label: String) {
    LINE("╱", "Çizgi"),
    ARROW("→", "Ok"),
    RECT("▭", "Dikdörtgen"),
    ELLIPSE("◯", "Elips"),
    TRIANGLE("△", "Üçgen"),
}

/** Kalem aracının türü: hangi çizgi türüyle kaydedileceği ve basınca tepki verip vermediği. */
enum class PenType(
    val label: String,
    val description: String,
    val short: String,
    val inkTool: InkTool,
    val usesPressure: Boolean,
    /** Bu kalem seçilince önerilen kalınlık (mm); null = kullanıcının kalınlığı korunur. */
    val defaultWidthMm: Float? = null,
) {
    FOUNTAIN("Dolma kalem", "Bastırdıkça kalınlaşır", "", InkTool.PEN, true),
    BALLPOINT("Tükenmez", "Sabit kalınlık", "T", InkTool.PEN, false),
    FINELINER("İnce uç", "Çok ince, sabit; şema ve küçük yazı", "İ", InkTool.PEN, false, 0.25f),
    MARKER("Keçeli kalem", "Kalın ve dolgun; başlıklar için", "K", InkTool.PEN, false, 1.6f),
    PENCIL("Kurşun kalem", "Yarı saydam", "Kş", InkTool.PENCIL, false),
    PASTEL("Pastel", "Geniş, yumuşak, yarı saydam", "P", InkTool.PENCIL, false, 2.4f),
    BRUSH("Fırça", "Basınca çok duyarlı", "F", InkTool.BRUSH, true),
    CALLIGRAPHY("Kaligrafi", "Yöne göre ince/kalın", "Kl", InkTool.CALLIGRAPHY, false),
    DASHED("Kesik çizgi", "Şekil ve şemalar için", "--", InkTool.DASHED, false),
    GEL("Jel kalem", "Akıcı, hafif basınca duyarlı", "J", InkTool.PEN, true, 0.4f),
    NEON("Neon", "Parlayan renkli çizgi", "N", InkTool.NEON, false, 0.8f),
    DOTTED("Noktalı", "Nokta nokta çizgi", "··", InkTool.DOTTED, false, 0.8f),
    BROAD("Geniş uç", "Kalın kaligrafi ucu; başlıklar için", "G", InkTool.CALLIGRAPHY, false, 2.2f),
    SUMI("Mürekkep fırçası", "Kalın, uçları incelen fırça", "M", InkTool.BRUSH, true, 2.6f),
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
    /** Yazma desteği: el titremesini süzüp çizgiyi yumuşatır (0 = kapalı, 1 = en güçlü). */
    val smoothing: Float = 0.35f,
    /** Kalemi çizginin sonunda bir an bekletince düz çizgi / şekil yap. */
    val shapeAssist: Boolean = true,
    /** Deneysel: çizilen çizgi ön tampona (daha az gecikme). */
    val lowLatency: Boolean = false,
    val highlighterColor: Int = HIGHLIGHTER_COLORS[0],
    val highlighterWidthMm: Float = 5f,
    /** Fosforlu her zaman düz çizgi çizer (altını çizmek için). */
    val highlighterStraight: Boolean = false,
    /** Fosforluyu altındaki metin satırına oturt (Android 15+). */
    val smartHighlighter: Boolean = true,
    val eraserMode: EraserMode = EraserMode.STROKE,
    /** Silgi yarıçapı, ekranda dp; yakınlaştırmadan bağımsız aynı boyutta görünür. */
    val eraserSizeDp: Float = 14f,
    val eraserHighlighterOnly: Boolean = false,
    /** Çizgi silgisi yazı kutularını ve resimleri de silsin. */
    val eraserObjects: Boolean = false,
    /** Ezber bandı rengi ve kalınlığı (mm). */
    val tapeColor: Int = TAPE_COLORS[0],
    val tapeWidthMm: Float = 6f,
    /** Açıkken tek parmak çizer, iki parmak kaydırır/yakınlaştırır. */
    val fingerDraw: Boolean = false,
    /** Yeni metin kutularının yazı boyutu (pt) ve rengi. */
    val textSizePt: Float = 14f,
    val textColor: Int = PEN_COLORS[0],
    val textBold: Boolean = false,
    val textBackground: Int = 0,
    /** Damga: işaret, rengi ve boyutu (pt). */
    val stampSymbol: String = STAMPS[0],
    val stampColor: Int = 0xFFD93025.toInt(),
    val stampSizePt: Float = 24f,
    val shapeKind: ShapeKind = ShapeKind.RECT,
    val favorites: List<FavoritePen> = emptyList(),
    /** Cetvel / açıölçer görünür mü (uygulama kapanınca kapanır). */
    val guide: GuideKind = GuideKind.NONE,
    /** Her kalem türünün son rengi ve kalınlığı: kalem değişince kaldığı yerden devam eder. */
    val penMemory: Map<PenType, Pair<Int, Float>> = emptyMap(),
    /** Son kullanılan renkler (en yenisi başta): paletten ya da renk seçiciden seçilenler. */
    val customColors: List<Int> = emptyList(),
) {
    /** Ham kalem basıncını hassasiyet ayarına göre eğer. */
    fun applyPressureCurve(p: Float): Float {
        val gamma = 2f.pow((0.5f - pressureSensitivity) * 2f)
        return p.coerceIn(0f, 1f).pow(gamma)
    }

    /** Kalem türünü değiştirir: şimdikinin ayarını saklar, yenisinin hatırlanan ayarını yükler. */
    fun switchPen(t: PenType): ToolState {
        if (t == penType) return copy(tool = Tool.PEN)
        val memory = penMemory + (penType to (penColor to penWidthMm))
        val (color, width) = memory[t] ?: (penColor to (t.defaultWidthMm ?: penWidthMm))
        return copy(tool = Tool.PEN, penType = t, penColor = color, penWidthMm = width, penMemory = memory)
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
            .putBoolean("lowLatency", lowLatency)
            .putInt("highlighterColor", highlighterColor)
            .putFloat("highlighterWidthMm", highlighterWidthMm)
            .putBoolean("highlighterStraight", highlighterStraight)
            .putBoolean("smartHighlighter", smartHighlighter)
            .putFloat("smoothing", smoothing)
            .putString("eraserMode", eraserMode.name)
            .putFloat("eraserSizeDp", eraserSizeDp)
            .putBoolean("eraserHighlighterOnly", eraserHighlighterOnly)
            .putBoolean("eraserObjects", eraserObjects)
            .putInt("tapeColor", tapeColor)
            .putFloat("tapeWidthMm", tapeWidthMm)
            .putBoolean("textBold", textBold)
            .putString("stampSymbol", stampSymbol)
            .putInt("stampColor", stampColor)
            .putFloat("stampSizePt", stampSizePt)
            .putString("shapeKind", shapeKind.name)
            .putInt("textBackground", textBackground)
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
            .putString("penMemory", JSONObject().apply {
                penMemory.forEach { (t, v) -> put(t.name, JSONObject().put("color", v.first).put("widthMm", v.second.toDouble())) }
            }.toString())
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
        /** Damga işaretleri (düzeltme, işaretleme, önem). */
        val STAMPS = listOf("✓", "✗", "★", "?", "!", "→", "←", "♥", "☆", "○", "①", "②", "③", "④", "⑤")

        /** Ezber bandı: altındaki yazıyı tamamen kapatacak kadar koyu, opak renkler. */
        val TAPE_COLORS = listOf(
            0xFF7FA7D9.toInt(), // mavi
            0xFF8CC27A.toInt(), // yeşil
            0xFFEE97B5.toInt(), // pembe
            0xFFF2C14E.toInt(), // sarı
            0xFFA9AEB8.toInt(), // gri
        )

        /** Yazı kutusu arka planları (yapışkan not); 0 = yok. */
        val NOTE_BACKGROUNDS = listOf(
            0,
            0xFFFFF3A6.toInt(), // sarı
            0xFFCDE7FF.toInt(), // mavi
            0xFFD6F2C8.toInt(), // yeşil
            0xFFFFD6E5.toInt(), // pembe
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
                lowLatency = prefs.getBoolean("lowLatency", d.lowLatency),
                highlighterColor = prefs.getInt("highlighterColor", d.highlighterColor),
                highlighterWidthMm = prefs.getFloat("highlighterWidthMm", d.highlighterWidthMm),
                highlighterStraight = prefs.getBoolean("highlighterStraight", d.highlighterStraight),
                smartHighlighter = prefs.getBoolean("smartHighlighter", d.smartHighlighter),
                smoothing = prefs.getFloat("smoothing", d.smoothing),
                eraserMode = enumOr(prefs.getString("eraserMode", null), d.eraserMode),
                eraserSizeDp = prefs.getFloat("eraserSizeDp", d.eraserSizeDp),
                eraserHighlighterOnly = prefs.getBoolean("eraserHighlighterOnly", d.eraserHighlighterOnly),
                eraserObjects = prefs.getBoolean("eraserObjects", d.eraserObjects),
                tapeColor = prefs.getInt("tapeColor", d.tapeColor),
                tapeWidthMm = prefs.getFloat("tapeWidthMm", d.tapeWidthMm),
                textBold = prefs.getBoolean("textBold", d.textBold),
                stampSymbol = prefs.getString("stampSymbol", null) ?: d.stampSymbol,
                stampColor = prefs.getInt("stampColor", d.stampColor),
                stampSizePt = prefs.getFloat("stampSizePt", d.stampSizePt),
                shapeKind = enumOr(prefs.getString("shapeKind", null), d.shapeKind),
                textBackground = prefs.getInt("textBackground", d.textBackground),
                fingerDraw = prefs.getBoolean("fingerDraw", d.fingerDraw),
                textSizePt = prefs.getFloat("textSizePt", d.textSizePt),
                textColor = prefs.getInt("textColor", d.textColor),
                favorites = loadFavorites(prefs.getString("favorites", null)),
                customColors = prefs.getString("customColors", null)
                    ?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList(),
                penMemory = loadPenMemory(prefs.getString("penMemory", null)),
            )
        }

        private fun loadPenMemory(json: String?): Map<PenType, Pair<Int, Float>> {
            if (json.isNullOrEmpty()) return emptyMap()
            return runCatching {
                val o = JSONObject(json)
                o.keys().asSequence().mapNotNull { k ->
                    val t = PenType.entries.firstOrNull { it.name == k } ?: return@mapNotNull null
                    val v = o.getJSONObject(k)
                    t to (v.getInt("color") to v.getDouble("widthMm").toFloat())
                }.toMap()
            }.getOrDefault(emptyMap())
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
