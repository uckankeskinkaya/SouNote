package com.sounote.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sounote.app.R
import com.sounote.app.ink.EraserMode
import com.sounote.app.ink.Tool
import com.sounote.app.ink.ToolState
import java.util.Locale
import kotlin.math.roundToInt

private val TR = Locale.forLanguageTag("tr")

/** Görüntüleyicinin üstündeki araç çubuğu: araç seçimi + seçili aracın ayarları. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkToolbar(state: ToolState, onChange: (ToolState) -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            ToolChip("Kalem", state.tool == Tool.PEN, vector = Icons.Default.Edit) {
                onChange(state.copy(tool = Tool.PEN))
            }
            ToolChip("Fosforlu", state.tool == Tool.HIGHLIGHTER, drawable = R.drawable.ic_highlighter) {
                onChange(state.copy(tool = Tool.HIGHLIGHTER))
            }
            ToolChip("Silgi", state.tool == Tool.ERASER, drawable = R.drawable.ic_eraser) {
                onChange(state.copy(tool = Tool.ERASER))
            }
            ToolChip("Yazı", state.tool == Tool.TEXT, drawable = R.drawable.ic_text) {
                onChange(state.copy(tool = Tool.TEXT))
            }
            VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp))

            when (state.tool) {
                Tool.PEN -> {
                    ColorRow(ToolState.PEN_COLORS, state.penColor) { onChange(state.copy(penColor = it)) }
                    WidthControl(state.penWidthMm, min = 0.1f, max = 5f, step = 0.1f) {
                        onChange(state.copy(penWidthMm = it))
                    }
                }
                Tool.HIGHLIGHTER -> {
                    ColorRow(ToolState.HIGHLIGHTER_COLORS, state.highlighterColor) {
                        onChange(state.copy(highlighterColor = it))
                    }
                    WidthControl(state.highlighterWidthMm, min = 1f, max = 15f, step = 0.5f) {
                        onChange(state.copy(highlighterWidthMm = it))
                    }
                }
                Tool.TEXT -> {
                    ColorRow(ToolState.PEN_COLORS, state.textColor) { onChange(state.copy(textColor = it)) }
                    Text("Boyut", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = state.textSizePt,
                        onValueChange = { onChange(state.copy(textSizePt = it.roundToInt().toFloat())) },
                        valueRange = 6f..48f,
                        modifier = Modifier.width(180.dp),
                    )
                    Text("${state.textSizePt.roundToInt()} pt", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Sayfaya dokun: yazı ekle · yazıya dokun: düzenle · sürükle: taşı",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Tool.ERASER -> {
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
                }
            }

            VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp))
            FilterChip(
                selected = state.fingerDraw,
                onClick = { onChange(state.copy(fingerDraw = !state.fingerDraw)) },
                label = { Text("Parmakla çiz") },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolChip(
    label: String,
    selected: Boolean,
    vector: ImageVector? = null,
    @DrawableRes drawable: Int = 0,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            val m = Modifier.size(18.dp)
            if (vector != null) Icon(vector, null, m) else Icon(painterResource(drawable), null, m)
        },
    )
}

@Composable
internal fun ColorRow(colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        colors.forEach { c ->
            val isSelected = c == selected
            Box(
                Modifier
                    .size(30.dp)
                    .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .padding(4.dp)
                    .background(Color(c), CircleShape)
                    .clickable { onSelect(c) },
            )
        }
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
            String.format(TR, "%.1f mm", value),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.widthIn(min = 56.dp),
        )
    }
}
