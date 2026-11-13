package com.sounote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sounote.app.ink.Tool
import com.sounote.app.ink.ToolState
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Hızlı kalem halkası: kalemle sayfada basılı tutunca kalemin ucunda açılır. Çevresinde kalem, fosforlu, silgi ve son
 * kullanılan renkler; dokununca seçilir, boşluğa dokununca kapanır. Ekran kenarında halka içeri kaydırılır.
 */
@Composable
fun PenRingOverlay(at: Offset, tools: ToolState, onPick: (ToolState) -> Unit, onDismiss: () -> Unit) {
    val colors = remember(tools.customColors, tools.penColor) {
        (listOf(tools.penColor) + tools.customColors.take(3) + ToolState.PEN_COLORS).distinct().take(5)
    }
    data class Item(val icon: ImageVector?, val color: Int?, val label: String, val selected: Boolean, val apply: () -> ToolState)
    val items = buildList {
        add(Item(SouIcons.InkPen, null, "Kalem", tools.tool == Tool.PEN) { tools.copy(tool = Tool.PEN) })
        add(Item(SouIcons.InkHighlighter, null, "Fosforlu", tools.tool == Tool.HIGHLIGHTER) { tools.copy(tool = Tool.HIGHLIGHTER) })
        add(Item(SouIcons.InkEraser, null, "Silgi", tools.tool == Tool.ERASER) { tools.copy(tool = Tool.ERASER) })
        colors.forEach { c ->
            add(Item(null, c, "Renk", tools.tool == Tool.PEN && tools.penColor == c) { tools.copy(tool = Tool.PEN, penColor = c) })
        }
    }
    val radiusDp = 84.dp
    val itemDp = 46.dp
    val ringDp = radiusDp * 2 + itemDp
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        val density = LocalDensity.current
        val ringPx = with(density) { ringDp.toPx() }
        val maxX = with(density) { maxWidth.toPx() } - ringPx
        val maxY = with(density) { maxHeight.toPx() } - ringPx
        val left = (at.x - ringPx / 2).coerceIn(0f, maxX.coerceAtLeast(0f))
        val top = (at.y - ringPx / 2).coerceIn(0f, maxY.coerceAtLeast(0f))
        Box(
            Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(ringDp),
        ) {
            // Merkez: kapat
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Icon(SouIcons.Close, contentDescription = "Kapat", modifier = Modifier.size(20.dp)) }
            items.forEachIndexed { i, item ->
                val angle = -Math.PI / 2 + 2 * Math.PI * i / items.size
                val dx = (cos(angle) * radiusDp.value).toFloat().dp
                val dy = (sin(angle) * radiusDp.value).toFloat().dp
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .offset(dx, dy)
                        .size(itemDp)
                        .clip(CircleShape)
                        .background(if (item.selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .border(
                            if (item.selected) 2.dp else 1.dp,
                            if (item.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        )
                        .clickable { onPick(item.apply()) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.icon != null) {
                        Icon(item.icon, contentDescription = item.label, tint = if (item.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    } else if (item.color != null) {
                        // Beyaz halka: koyu renkler koyu temada da görünsün
                        Box(Modifier.size(30.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(26.dp).background(Color(item.color), CircleShape))
                        }
                    }
                }
            }
        }
    }
}
