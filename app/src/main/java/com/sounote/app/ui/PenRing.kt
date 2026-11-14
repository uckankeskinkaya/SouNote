package com.sounote.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sounote.app.ink.FavoritePen
import com.sounote.app.ink.Tool
import com.sounote.app.ink.ToolState
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Sayfa üzerindeki basış konumuna yerleştirilen, kaydırılabilir hızlı araç menüsü. */
@Composable
fun PenRingOverlay(at: Offset, tools: ToolState, onPick: (ToolState) -> Unit, onDismiss: () -> Unit) {
    var state by remember { mutableStateOf(tools) }
    val latestOnPick by rememberUpdatedState(onPick)
    fun update(next: ToolState) {
        state = next
        // Caller must not close on pick: ViewerScreen should use onPick = { vm.updateTools(it) }.
        // Only outside taps, Close and Tamam should clear penRing through onDismiss.
        latestOnPick(next)
    }
    fun applyColor(rgb: Int, rememberColor: Boolean) {
        val color = if (state.tool == Tool.HIGHLIGHTER) {
            (rgb and 0x00FFFFFF) or (state.highlighterColor and 0xFF000000.toInt())
        } else {
            rgb or 0xFF000000.toInt()
        }
        val next = when (state.tool) {
            Tool.HIGHLIGHTER -> state.copy(highlighterColor = color)
            Tool.TEXT -> state.copy(textColor = color)
            else -> state.copy(penColor = color)
        }
        update(if (rememberColor) next.withCustomColor(color) else next)
    }
    val currentColor = when (state.tool) {
        Tool.HIGHLIGHTER -> state.highlighterColor
        Tool.TEXT -> state.textColor
        else -> state.penColor
    }
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(
        Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onDismiss,
        ),
    ) {
        val density = LocalDensity.current
        val panelWidth = minOf(360.dp, maxWidth)
        val availableHeight = (maxHeight - 16.dp).coerceAtLeast(0.dp)
        var panelHeightPx by remember { mutableStateOf(0) }
        val boxWidthPx = with(density) { maxWidth.toPx() }
        val boxHeightPx = with(density) { maxHeight.toPx() }
        val panelWidthPx = with(density) { panelWidth.toPx() }
        val gapPx = with(density) { 8.dp.toPx() }
        val panelShape = RoundedCornerShape(20.dp)
        Column(
            modifier = Modifier
                .absoluteOffset {
                    IntOffset(
                        (at.x + gapPx).coerceIn(0f, (boxWidthPx - panelWidthPx).coerceAtLeast(0f)).roundToInt(),
                        (at.y + gapPx).coerceIn(0f, (boxHeightPx - panelHeightPx).coerceAtLeast(0f)).roundToInt(),
                    )
                }
                .width(panelWidth)
                .heightIn(max = availableHeight)
                .onSizeChanged { panelHeightPx = it.height }
                .clip(panelShape)
                .background(scheme.surface)
                .border(1.dp, scheme.outlineVariant, panelShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hızlı menü", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(SouIcons.Close, contentDescription = "Kapat", tint = scheme.onSurfaceVariant)
                }
            }
            val toolItems = listOf(
                Triple(Tool.PEN, SouIcons.InkPen, "Kalem"),
                Triple(Tool.HIGHLIGHTER, SouIcons.InkHighlighter, "Fosforlu"),
                Triple(Tool.ERASER, SouIcons.InkEraser, "Silgi"),
                Triple(Tool.LASSO, SouIcons.GestureSelect, "Seç"),
                Triple(Tool.TEXT, SouIcons.TextFields, "Yazı"),
                Triple(Tool.SHAPE, SouIcons.Shapes, "Şekil"),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                toolItems.forEach { (tool, icon, label) ->
                    val active = state.tool == tool
                    Box(
                        Modifier.weight(1f).height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) scheme.primaryContainer else scheme.surfaceContainer)
                            .semantics { selected = active }
                            .clickable { update(state.copy(tool = tool)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp),
                            tint = if (active) scheme.primary else scheme.onSurfaceVariant)
                    }
                }
            }
            PenRingLabel("Favori kalemler")
            if (state.favorites.isEmpty()) {
                Text("Araç çubuğunda ★ ile kalem ekleyebilirsin", style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant)
            } else {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.favorites.forEach { favorite ->
                        PenRingFavorite(favorite, state.isActive(favorite)) { update(state.apply(favorite)) }
                    }
                }
            }
            val current = state.currentFavorite()
            if (current != null && current !in state.favorites && state.favorites.size < ToolState.MAX_FAVORITES) {
                AssistChip(
                    onClick = { update(state.copy(favorites = state.favorites + current)) },
                    label = { Text("Favorilere ekle") },
                    leadingIcon = { Icon(SouIcons.Star, null, Modifier.size(18.dp)) },
                )
            }
            PenRingLabel("Renk")
            PenRingColorWheel(
                color = currentColor,
                onChange = { applyColor(it, false) },
                onCommit = { update(state.withCustomColor(when (state.tool) {
                    Tool.HIGHLIGHTER -> state.highlighterColor
                    Tool.TEXT -> state.textColor
                    else -> state.penColor
                })) },
            )
            PenRingSwatches("Son kullanılanlar", state.customColors.take(8), currentColor) { applyColor(it, true) }
            PenRingSwatches("Hazır",
                if (state.tool == Tool.HIGHLIGHTER) ToolState.HIGHLIGHTER_COLORS else ToolState.PEN_COLORS,
                currentColor) { applyColor(it, true) }
            if (state.tool != Tool.ERASER) {
                val highlighter = state.tool == Tool.HIGHLIGHTER
                val width = if (highlighter) state.highlighterWidthMm else state.penWidthMm
                val presets = if (highlighter) listOf(2f, 4f, 6f, 8f, 12f) else listOf(0.3f, 0.5f, 0.8f, 1.2f, 2f)
                PenRingLabel("Kalınlık")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { preset ->
                        val active = width == preset
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (active) scheme.primaryContainer else scheme.surfaceContainer)
                                .semantics { contentDescription = "${penRingMm(preset)} mm"; selected = active }
                                .clickable {
                                    update(if (highlighter) state.copy(highlighterWidthMm = preset) else state.copy(penWidthMm = preset))
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size((4f + preset * 4f).coerceIn(5f, 28f).dp)
                                .background(Color.White, CircleShape).padding(1.dp)
                                .background(Color(currentColor), CircleShape))
                        }
                    }
                    Text("${penRingMm(width)} mm", style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurface, maxLines = 1)
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Tamam") }
        }
    }
}

@Composable
private fun PenRingLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun penRingMm(value: Float): String = String.format(Locale.US, "%.1f", value)

@Composable
private fun PenRingFavorite(f: FavoritePen, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.height(36.dp).clip(RoundedCornerShape(12.dp))
            .background(if (active) scheme.primaryContainer else scheme.surfaceContainer)
            .semantics { selected = active }
            .clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dot = if (f.tool == Tool.HIGHLIGHTER) 14.dp else (4f + f.widthMm * 4f).coerceIn(5f, 16f).dp
        Box(Modifier.size(24.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(dot).background(Color(f.color), CircleShape))
        }
        Text(
            if (f.tool == Tool.PEN && f.penType.short.isNotEmpty()) "${penRingMm(f.widthMm)} ${f.penType.short}" else penRingMm(f.widthMm),
            modifier = Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium,
            color = if (active) scheme.onPrimaryContainer else scheme.onSurface,
        )
    }
}

@Composable
private fun PenRingSwatches(label: String, colors: List<Int>, current: Int, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PenRingLabel(label)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            colors.forEach { color ->
                // Compare RGB because a highlighter retains its own alpha when choosing a swatch.
                val active = (color and 0xFFFFFF) == (current and 0xFFFFFF)
                Box(
                    Modifier.size(30.dp).clip(CircleShape)
                        .semantics {
                            contentDescription = String.format(Locale.US, "Renk #%06X", color and 0xFFFFFF)
                            selected = active
                        }
                        .clickable { onPick(color) }
                        .border(if (active) 2.dp else 1.dp,
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .padding(if (active) 5.dp else 4.dp)
                        .background(Color.White, CircleShape).padding(2.dp)
                        .background(Color(color), CircleShape),
                )
            }
        }
    }
}

@Composable
private fun PenRingColorWheel(color: Int, onChange: (Int) -> Unit, onCommit: () -> Unit) {
    val hsv = remember(color) { FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) } }
    val latestHsv by rememberUpdatedState(hsv)
    val latestChange by rememberUpdatedState(onChange)
    val latestCommit by rememberUpdatedState(onCommit)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier.size(180.dp)
                .semantics { contentDescription = "Renk çemberi" }
                .pointerInput(Unit) {
                    // Reserve space for the selection ring so it stays visible at full saturation.
                    val inset = 8.dp.toPx()
                    fun pick(position: Offset) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = (minOf(size.width, size.height) / 2f - inset).coerceAtLeast(1f)
                        val delta = position - center
                        val hue = ((Math.toDegrees(atan2(delta.y.toDouble(), delta.x.toDouble())) + 360.0) % 360.0).toFloat()
                        val saturation = (delta.getDistance() / radius).coerceIn(0f, 1f)
                        latestChange(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, latestHsv[2].coerceIn(0.15f, 1f))))
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = (minOf(size.width, size.height) / 2f - inset).coerceAtLeast(1f)
                        if ((down.position - center).getDistance() <= radius) {
                            down.consume()
                            pick(down.position)
                            val completed = drag(down.id) { change ->
                                change.consume()
                                pick(change.position)
                            }
                            // A tap or completed drag adds one recent colour; cancelled gestures do not.
                            if (completed) latestCommit()
                        }
                    }
                },
        ) {
            val radius = (size.minDimension / 2f - 8.dp.toPx()).coerceAtLeast(1f)
            drawCircle(Brush.sweepGradient(HUE_COLORS, center = center), radius = radius)
            drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center = center, radius = radius), radius = radius)
            drawCircle(Color.Black.copy(alpha = 1f - hsv[2]), radius = radius)
            val angle = Math.toRadians(hsv[0].toDouble())
            val indicator = center + Offset(
                (cos(angle) * hsv[1] * radius).toFloat(),
                (sin(angle) * hsv[1] * radius).toFloat(),
            )
            drawCircle(Color.White, 6.dp.toPx(), indicator, style = Stroke(3.dp.toPx()))
            drawCircle(Color.Black, 7.dp.toPx(), indicator, style = Stroke(1.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PenRingLabel("Parlaklık")
            Slider(
                value = hsv[2].coerceIn(0.15f, 1f),
                onValueChange = { value ->
                    latestChange(android.graphics.Color.HSVToColor(floatArrayOf(latestHsv[0], latestHsv[1], value)))
                },
                onValueChangeFinished = { latestCommit() },
                valueRange = 0.15f..1f,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
