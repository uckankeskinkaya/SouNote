@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sounote.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sounote.app.R
import com.sounote.app.ink.EraserMode
import com.sounote.app.ink.FavoritePen
import com.sounote.app.ink.PenType
import com.sounote.app.ink.GuideKind
import com.sounote.app.ink.Tool
import com.sounote.app.ink.ToolState
import java.util.Locale
import kotlin.math.roundToInt

private val TR = Locale.forLanguageTag("tr")

private fun mm(v: Float) = String.format(TR, "%.1f", v)

/** Lasso seçimiyle ilgili durum ve işlemler. */
class SelectionUi(
    val count: Int,
    val hasClipboard: Boolean,
    val onRecolor: (Int) -> Unit,
    val onCopy: () -> Unit,
    val onPaste: () -> Unit,
    val onDelete: () -> Unit,
    val onDone: () -> Unit,
    val onMakeCard: () -> Unit = {},
    /** Lasso ile bir bölge çizildiyse (notsuz da olsa) o bölgenin metnini asistana sor. */
    val canAsk: Boolean = false,
    val onAsk: () -> Unit = {},
)

/**
 * Görüntüleyicinin üstündeki araç çubuğu.
 * Üst satır: araçlar, favori kalemler, parmakla çiz. Alt satır: seçili aracın ayarları.
 */
@Composable
fun InkToolbar(
    state: ToolState,
    onChange: (ToolState) -> Unit,
    selection: SelectionUi,
    onInsertImage: () -> Unit,
    /** Tam ekranda üst çubuk gizlenince gereken düğmeler (çık, geri al, ileri al) buraya gelir. */
    leading: (@Composable () -> Unit)? = null,
    /** Bütün ezber bantlarını aç (true) / kapat (false). */
    onRevealTapes: (Boolean) -> Unit = {},
) {
    // Renk seçici açıkken seçilen rengin hangi araca gideceği
    var picker by remember { mutableStateOf<Tool?>(null) }

    Surface(tonalElevation = 2.dp) {
        Column {
            ToolbarRow {
                if (leading != null) {
                    leading()
                    Separator()
                }
                // Kalemi olmayanlar için en başta, hemen görünür
                FingerToggle(state.fingerDraw) { onChange(state.copy(fingerDraw = !state.fingerDraw)) }
                Separator()
                ToolChip("Kalem", state.tool == Tool.PEN, vector = SouIcons.InkPen) {
                    onChange(state.copy(tool = Tool.PEN))
                }
                ToolChip("Fosforlu", state.tool == Tool.HIGHLIGHTER, vector = SouIcons.InkHighlighter) {
                    onChange(state.copy(tool = Tool.HIGHLIGHTER))
                }
                ToolChip("Silgi", state.tool == Tool.ERASER, vector = SouIcons.InkEraser) {
                    onChange(state.copy(tool = Tool.ERASER))
                }
                ToolChip("Yazı", state.tool == Tool.TEXT, vector = SouIcons.TextFields) {
                    onChange(state.copy(tool = Tool.TEXT))
                }
                ToolChip("Seç", state.tool == Tool.LASSO, vector = SouIcons.GestureSelect) {
                    onChange(state.copy(tool = Tool.LASSO))
                }
                ToolChip("Bant", state.tool == Tool.TAPE, vector = SouIcons.VisibilityOff) {
                    onChange(state.copy(tool = Tool.TAPE))
                }
                ToolChip("Lazer", state.tool == Tool.LASER, vector = SouIcons.StylusLaserPointer) {
                    onChange(state.copy(tool = Tool.LASER))
                }
                ToolChip("Şekil", state.tool == Tool.SHAPE, vector = SouIcons.Shapes) {
                    onChange(state.copy(tool = Tool.SHAPE))
                }
                ToolChip("Damga", state.tool == Tool.STAMP, vector = SouIcons.Approval) {
                    onChange(state.copy(tool = Tool.STAMP))
                }
                ToolChip("Resim ekle", false, vector = SouIcons.Image, onClick = onInsertImage)
                Separator()
                ToolChip("Cetvel", state.guide == GuideKind.RULER, vector = SouIcons.Straighten) {
                    onChange(state.copy(guide = if (state.guide == GuideKind.RULER) GuideKind.NONE else GuideKind.RULER))
                }
                ToolChip("Açıölçer", state.guide == GuideKind.PROTRACTOR, vector = SouIcons.Architecture) {
                    onChange(state.copy(guide = if (state.guide == GuideKind.PROTRACTOR) GuideKind.NONE else GuideKind.PROTRACTOR))
                }
                Separator()
                Favorites(state, onChange)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ToolbarRow {
                when (state.tool) {
                    Tool.PEN -> PenOptions(state, onChange) { picker = Tool.PEN }
                    Tool.HIGHLIGHTER -> HighlighterOptions(state, onChange) { picker = Tool.HIGHLIGHTER }
                    Tool.ERASER -> EraserOptions(state, onChange)
                    Tool.TEXT -> TextOptions(state, onChange) { picker = Tool.TEXT }
                    Tool.LASSO -> SelectionOptions(state, selection) { picker = Tool.LASSO }
                    Tool.TAPE -> TapeOptions(state, onChange, onRevealTapes) { picker = Tool.TAPE }
                    Tool.STAMP -> StampOptions(state, onChange) { picker = Tool.STAMP }
                    Tool.SHAPE -> ShapeOptions(state, onChange) { picker = Tool.PEN }
                    Tool.LASER -> Text(
                        "Çizgi birkaç saniyede kaybolur, kaydedilmez.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    picker?.let { target ->
        val initial = when (target) {
            Tool.HIGHLIGHTER -> state.highlighterColor
            Tool.TEXT -> state.textColor
            Tool.TAPE -> state.tapeColor
            Tool.STAMP -> state.stampColor
            else -> state.penColor
        }
        ColorPickerDialog(
            initial = initial,
            onPick = { c ->
                val s = state.withCustomColor(c)
                onChange(
                    when (target) {
                        Tool.HIGHLIGHTER -> s.copy(highlighterColor = c)
                        Tool.TEXT -> s.copy(textColor = c)
                        Tool.TAPE -> s.copy(tapeColor = c)
                        Tool.STAMP -> s.copy(stampColor = c)
                        Tool.LASSO -> s
                        else -> s.copy(penColor = c)
                    },
                )
                if (target == Tool.LASSO) selection.onRecolor(c)
                picker = null
            },
            onDismiss = { picker = null },
        )
    }
}

@Composable
private fun ToolbarRow(content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) { content() }
}

@Composable
private fun Separator() {
    VerticalDivider(Modifier.height(28.dp).padding(horizontal = 4.dp))
}

// ---- Favoriler ----

@Composable
private fun Favorites(state: ToolState, onChange: (ToolState) -> Unit) {
    state.favorites.forEach { f ->
        FavoriteChip(
            f,
            active = state.isActive(f),
            onClick = { onChange(state.apply(f)) },
            onLongClick = { onChange(state.copy(favorites = state.favorites - f)) },
        )
    }
    val current = state.currentFavorite()
    if (current != null && current !in state.favorites && state.favorites.size < ToolState.MAX_FAVORITES) {
        AssistChip(
            onClick = { onChange(state.copy(favorites = state.favorites + current)) },
            label = { Text(if (state.favorites.isEmpty()) "Favorilere ekle" else "Ekle") },
            leadingIcon = { Icon(SouIcons.StarFilled, null, Modifier.size(18.dp)) },
        )
    }
}

/** Favori kalem: renkli nokta (kalınlığa göre büyür) + kalınlık. Basılı tutunca silinir. */
@Composable
private fun FavoriteChip(f: FavoritePen, active: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(34.dp)
            .border(
                if (active) 2.dp else 1.dp,
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp),
    ) {
        val dot = if (f.tool == Tool.HIGHLIGHTER) 14.dp else (4f + f.widthMm * 4f).coerceIn(5f, 16f).dp
        // Beyaz zemin: koyu renkli kalem koyu temada da görünsün.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 28.dp, height = 24.dp)
                .background(Color.White, RoundedCornerShape(6.dp)),
        ) {
            Box(
                Modifier
                    .size(width = if (f.tool == Tool.HIGHLIGHTER) 20.dp else dot, height = dot)
                    .background(Color(f.color), if (f.tool == Tool.HIGHLIGHTER) RoundedCornerShape(3.dp) else CircleShape),
            )
        }
        Text(
            if (f.tool == Tool.PEN && f.penType.short.isNotEmpty()) "${mm(f.widthMm)} ${f.penType.short}" else mm(f.widthMm),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

// ---- Araç ayarları ----

@Composable
private fun PenOptions(state: ToolState, onChange: (ToolState) -> Unit, onCustomColor: () -> Unit) {
    var typeMenu by remember { mutableStateOf(false) }
    Box {
        // Seçili kalem: küçük çizimi ve adı; dokununca kalem kutusu açılır.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable { typeMenu = true }
                .padding(start = 6.dp, end = 8.dp),
        ) {
            // Kalem çizimi her temada açık zeminde (koyu kalemler koyu temada kaybolmasın).
            PenImage(
                state.penType,
                Modifier.size(width = 48.dp, height = 24.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF1F2F5)),
                horizontal = true,
            )
            Text(state.penType.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
            Icon(SouIcons.ArrowDropDown, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
            Text(
                "Kalemler",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
            )
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                maxItemsInEachRow = 5,
                modifier = Modifier.width(400.dp).padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                PenType.entries.forEach { t ->
                    val sel = t == state.penType
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(70.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF1F2F5))
                            .border(if (sel) 2.dp else 0.dp, if (sel) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickable {
                                typeMenu = false
                                onChange(state.switchPen(t))
                            }
                            .padding(vertical = 8.dp),
                    ) {
                        PenImage(t, Modifier.height(76.dp).width(30.dp))
                        Text(
                            t.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1A1C20),
                            maxLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
    Palette(ToolState.PEN_COLORS, state.customColors, state.penColor, { onChange(state.copy(penColor = it)) }, onCustomColor)
    WidthControl(state.penWidthMm, min = 0.1f, max = 5f, step = 0.1f) { onChange(state.copy(penWidthMm = it)) }
    PenSettings(state, onChange)
}

/** Kalem çizimi; [horizontal] ise uç sağa bakacak şekilde yatırılır. */
@Composable
private fun PenImage(type: PenType, modifier: Modifier, horizontal: Boolean = false) {
    Box(modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(
            painterResource(penArt(type)),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = if (horizontal) {
                Modifier.requiredSize(width = 18.dp, height = 52.dp).graphicsLayer { rotationZ = -90f }
            } else {
                Modifier.fillMaxSize()
            },
        )
    }
}

/** Kalem ayarları menüsü: yazma desteği, basınç hassasiyeti, şekil düzeltme. */
@Composable
private fun PenSettings(state: ToolState, onChange: (ToolState) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(SouIcons.Settings, contentDescription = "Kalem ayarları") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(Modifier.width(320.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Yazma desteği", style = MaterialTheme.typography.titleSmall)
                Text(
                    "El titremesini süzer, çizgileri yumuşak ve bağlantılı yapar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = state.smoothing,
                    onValueChange = { onChange(state.copy(smoothing = (it * 20).roundToInt() / 20f)) },
                    valueRange = 0f..1f,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kapalı", style = MaterialTheme.typography.labelSmall)
                    Text("Güçlü", style = MaterialTheme.typography.labelSmall)
                }
                Text("Basınç hassasiyeti", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                Text(
                    if (state.penType.usesPressure) "Bu kalemde etkili" else "Dolma, jel ya da fırça kalemde etkili",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = state.pressureSensitivity,
                    onValueChange = { onChange(state.copy(pressureSensitivity = (it * 20).roundToInt() / 20f)) },
                    valueRange = 0f..1f,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Sert", style = MaterialTheme.typography.labelSmall)
                    Text("Yumuşak", style = MaterialTheme.typography.labelSmall)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Şekil düzeltme", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Çizginin sonunda kalemi bir an beklet: düz çizgi, daire, dikdörtgen, üçgen olur",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.shapeAssist, onCheckedChange = { onChange(state.copy(shapeAssist = it)) })
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Düşük gecikme (deneysel)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Çizgi kalemin ucunu daha yakından izler. Dolma, tükenmez, fırça ve kaligrafide çalışır. Sorun olursa kapat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.lowLatency, onCheckedChange = { onChange(state.copy(lowLatency = it)) })
                }
            }
        }
    }
}

@Composable
private fun HighlighterOptions(state: ToolState, onChange: (ToolState) -> Unit, onCustomColor: () -> Unit) {
    Palette(
        ToolState.HIGHLIGHTER_COLORS, state.customColors, state.highlighterColor,
        { onChange(state.copy(highlighterColor = it)) }, onCustomColor,
    )
    WidthControl(state.highlighterWidthMm, min = 1f, max = 15f, step = 0.5f) {
        onChange(state.copy(highlighterWidthMm = it))
    }
    FilterChip(
        selected = state.highlighterStraight,
        onClick = { onChange(state.copy(highlighterStraight = !state.highlighterStraight)) },
        label = { Text("Düz çizgi") },
    )
    FilterChip(
        selected = state.smartHighlighter,
        onClick = { onChange(state.copy(smartHighlighter = !state.smartHighlighter)) },
        label = { Text("Satıra oturt") },
    )
}

@Composable
private fun EraserOptions(state: ToolState, onChange: (ToolState) -> Unit) {
    FilterChip(
        selected = state.eraserMode == EraserMode.STROKE,
        onClick = { onChange(state.copy(eraserMode = EraserMode.STROKE)) },
        label = { Text("Çizgiyi sil") },
    )
    FilterChip(
        selected = state.eraserMode == EraserMode.PRECISE,
        onClick = { onChange(state.copy(eraserMode = EraserMode.PRECISE)) },
        label = { Text("Hassas") },
    )
    Text("Boyut", style = MaterialTheme.typography.labelLarge)
    Slider(
        value = state.eraserSizeDp,
        onValueChange = { onChange(state.copy(eraserSizeDp = it.roundToInt().toFloat())) },
        valueRange = 4f..48f,
        modifier = Modifier.width(180.dp),
    )
    FilterChip(
        selected = state.eraserHighlighterOnly,
        onClick = { onChange(state.copy(eraserHighlighterOnly = !state.eraserHighlighterOnly)) },
        label = { Text("Sadece fosforlu") },
    )
    if (state.eraserMode == EraserMode.STROKE) {
        FilterChip(
            selected = state.eraserObjects,
            onClick = { onChange(state.copy(eraserObjects = !state.eraserObjects)) },
            label = { Text("Yazı ve resim de") },
        )
    }
}

@Composable
private fun ShapeOptions(state: ToolState, onChange: (ToolState) -> Unit, onCustomColor: () -> Unit) {
    com.sounote.app.ink.ShapeKind.entries.forEach { k ->
        FilterChip(
            selected = state.shapeKind == k,
            onClick = { onChange(state.copy(shapeKind = k)) },
            label = { Text(k.label) },
            leadingIcon = {
                Icon(
                    when (k) {
                        com.sounote.app.ink.ShapeKind.LINE -> SouIcons.DiagonalLine
                        com.sounote.app.ink.ShapeKind.ARROW -> SouIcons.NorthEast
                        com.sounote.app.ink.ShapeKind.RECT -> SouIcons.Rectangle
                        com.sounote.app.ink.ShapeKind.ELLIPSE -> SouIcons.RadioButtonUnchecked
                        com.sounote.app.ink.ShapeKind.TRIANGLE -> SouIcons.ChangeHistory
                    },
                    null,
                    Modifier.size(18.dp),
                )
            },
        )
    }
    Palette(ToolState.PEN_COLORS, state.customColors, state.penColor, { onChange(state.copy(penColor = it)) }, onCustomColor)
    WidthControl(state.penWidthMm, min = 0.1f, max = 5f, step = 0.1f) { onChange(state.copy(penWidthMm = it)) }
}

@Composable
private fun StampOptions(state: ToolState, onChange: (ToolState) -> Unit, onCustomColor: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        ToolState.STAMPS.forEach { s ->
            val selected = s == state.stampSymbol
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .border(
                        if (selected) 2.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { onChange(state.copy(stampSymbol = s)) },
            ) {
                Text(s, color = Color(state.stampColor), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    Palette(ToolState.PEN_COLORS, state.customColors, state.stampColor, { onChange(state.copy(stampColor = it)) }, onCustomColor)
    Text("Boyut", style = MaterialTheme.typography.labelLarge)
    Slider(
        value = state.stampSizePt,
        onValueChange = { onChange(state.copy(stampSizePt = it.roundToInt().toFloat())) },
        valueRange = 10f..72f,
        modifier = Modifier.width(160.dp),
    )
    Text(
        "Dokunduğun yere koyar · Yazı ya da Seç aracıyla taşı/sil",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TapeOptions(
    state: ToolState,
    onChange: (ToolState) -> Unit,
    onReveal: (Boolean) -> Unit,
    onCustomColor: () -> Unit,
) {
    Palette(ToolState.TAPE_COLORS, emptyList(), state.tapeColor, { onChange(state.copy(tapeColor = it)) }, onCustomColor)
    WidthControl(state.tapeWidthMm, min = 3f, max = 25f, step = 0.5f) { onChange(state.copy(tapeWidthMm = it)) }
    AssistChip(onClick = { onReveal(true) }, label = { Text("Hepsini aç") })
    AssistChip(onClick = { onReveal(false) }, label = { Text("Hepsini kapat") })
    Text(
        "Ezberlemek istediğin yerin üstünü çiz · banda parmakla dokun: aç/kapat",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TextOptions(state: ToolState, onChange: (ToolState) -> Unit, onCustomColor: () -> Unit) {
    Palette(ToolState.PEN_COLORS, state.customColors, state.textColor, { onChange(state.copy(textColor = it)) }, onCustomColor)
    Text("Boyut", style = MaterialTheme.typography.labelLarge)
    Slider(
        value = state.textSizePt,
        onValueChange = { onChange(state.copy(textSizePt = it.roundToInt().toFloat())) },
        valueRange = 6f..48f,
        modifier = Modifier.width(180.dp),
    )
    Text("${state.textSizePt.roundToInt()} pt", style = MaterialTheme.typography.labelLarge)
    FilterChip(
        selected = state.textBold,
        onClick = { onChange(state.copy(textBold = !state.textBold)) },
        label = { Text("Kalın", fontWeight = FontWeight.Bold) },
    )
    NoteBackgroundRow(state.textBackground) { onChange(state.copy(textBackground = it)) }
    Text(
        "Sayfaya dokun: yazı ekle · yazıya dokun: düzenle · sürükle: taşı",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SelectionOptions(state: ToolState, sel: SelectionUi, onCustomColor: () -> Unit) {
    if (sel.count == 0) {
        Text(
            "Seçmek istediğin yerin etrafını kalemle çiz · seçimi sürükle: taşı · köşedeki nokta ya da iki parmak: büyüt/küçült",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (sel.canAsk) AssistChip(onClick = sel.onAsk, label = { Text("Bölgeyi asistana sor") }, leadingIcon = { Icon(SouIcons.StarShine, null, Modifier.size(18.dp)) })
        if (sel.hasClipboard) AssistChip(onClick = sel.onPaste, label = { Text("Yapıştır") })
        return
    }
    Text("${sel.count} öğe seçili", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    Text("Renk:", style = MaterialTheme.typography.labelLarge)
    Palette(ToolState.PEN_COLORS, state.customColors, selected = 0, onSelect = sel.onRecolor, onCustom = onCustomColor)
    AssistChip(onClick = sel.onMakeCard, label = { Text("Karta çevir") })
    if (sel.canAsk) AssistChip(onClick = sel.onAsk, label = { Text("Asistana sor") }, leadingIcon = { Icon(SouIcons.StarShine, null, Modifier.size(18.dp)) })
    AssistChip(onClick = sel.onCopy, label = { Text("Kopyala") })
    if (sel.hasClipboard) AssistChip(onClick = sel.onPaste, label = { Text("Yapıştır") })
    AssistChip(
        onClick = sel.onDelete,
        label = { Text("Sil", color = MaterialTheme.colorScheme.error) },
    )
    AssistChip(onClick = sel.onDone, label = { Text("Bitti") })
}

// ---- Ortak parçalar ----

/** Parmakla yazma anahtarı: açıkken tek parmak çizer, iki parmak kaydırır. */
@Composable
private fun FingerToggle(on: Boolean, onClick: () -> Unit) {
    val bg by androidx.compose.animation.animateColorAsState(
        if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        label = "finger",
    )
    val fg = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressScale()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (on) "Parmakla yaz açık" else "Parmakla yaz kapalı" }
            .padding(9.dp),
    ) {
        Icon(SouIcons.TouchApp, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
    }
}

/** Araç düğmesi: sadece simge (adı erişilebilirlik için). */
@Composable
private fun ToolChip(
    label: String,
    selected: Boolean,
    vector: ImageVector? = null,
    @DrawableRes drawable: Int = 0,
    onClick: () -> Unit,
) {
    // Sadece simge: seçiliyken dolu zemin. Adı erişilebilirlik ve basılı tutma ipucu için.
    val bg by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "tool",
    )
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        val m = Modifier.size(22.dp)
        if (vector != null) Icon(vector, null, m, tint = fg) else Icon(painterResource(drawable), null, m, tint = fg)
    }
}

/** Hazır renkler + renk seçiciden seçilmiş son renkler + "yeni renk" düğmesi. */
@Composable
private fun Palette(
    presets: List<Int>,
    customs: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onCustom: () -> Unit,
) {
    val extra = customs.filter { it !in presets }
    val all = if (selected !in presets && selected !in extra) presets + extra + selected else presets + extra
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        all.forEach { c -> Swatch(c, c == selected) { onSelect(c) } }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .border(2.5.dp, Brush.sweepGradient(HUE_COLORS), CircleShape)
                .clickable(onClick = onCustom),
        ) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Swatch(c: Int, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .padding(3.dp)
            // Beyaz halka: siyah/koyu renkler koyu temada da görünsün (sayfa da beyaz).
            .background(Color.White, CircleShape)
            .padding(2.dp)
            .background(Color(c), CircleShape)
            .clickable(onClick = onClick),
    )
}

/** Yazı kutusu arka planı seçimi: yok + pastel renkler. */
@Composable
internal fun NoteBackgroundRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        ToolState.NOTE_BACKGROUNDS.forEach { c ->
            val isSelected = c == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .border(
                        if (isSelected) 2.5.dp else 1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(6.dp),
                    )
                    .padding(3.dp)
                    .background(if (c == 0) Color.White else Color(c), RoundedCornerShape(4.dp))
                    .clickable { onSelect(c) },
            ) {
                if (c == 0) Text("∅", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            }
        }
    }
}

/** Sadece hazır renkler (yazı düzenleme penceresi için). */
@Composable
internal fun ColorRow(colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        colors.forEach { c -> Swatch(c, c == selected) { onSelect(c) } }
    }
}

/** Kalınlık: kaydırıcı + ince ayar için −/+ düğmeleri. */
@Composable
private fun WidthControl(value: Float, min: Float, max: Float, step: Float, onChange: (Float) -> Unit) {
    fun snap(v: Float) = ((v / step).roundToInt() * step).coerceIn(min, max)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(snap(value - step)) }) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = { onChange(snap(it)) },
            valueRange = min..max,
            modifier = Modifier.width(200.dp),
        )
        IconButton(onClick = { onChange(snap(value + step)) }) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "${mm(value)} mm",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.widthIn(min = 56.dp),
        )
    }
}
