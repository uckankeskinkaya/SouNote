package com.sounote.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sounote.app.data.Sketch
import com.sounote.app.data.SketchStroke
import kotlin.math.hypot

private val SKETCH_COLORS = listOf(
    Color(0xFF1A1C20), Color(0xFF2F5BD3), Color(0xFFD64545), Color(0xFF2E7D4F),
    Color(0xFF7048C8), Color(0xFFE0893A), Color(0xFF8A8F99),
)
private val SKETCH_WIDTHS = listOf("İnce" to 3f, "Orta" to 6f, "Kalın" to 12f)

/** Çizimi Compose tuvaline çizer (kağıt beyazı zemin üstüne). */
internal fun DrawScope.drawSketch(strokes: List<SketchStroke>, sketchWidth: Float) {
    val scale = size.width / sketchWidth
    strokes.forEach { s ->
        if (s.points.size < 2) return@forEach
        val path = Path().apply {
            moveTo(s.points[0] * scale, s.points[1] * scale)
            var i = 2
            while (i + 1 < s.points.size) {
                lineTo(s.points[i] * scale, s.points[i + 1] * scale)
                i += 2
            }
            if (s.points.size == 2) lineTo(s.points[0] * scale + 0.5f, s.points[1] * scale)
        }
        drawPath(path, Color(s.color), style = Stroke(s.width * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Notun içindeki çizim: dokununca düzenlenir. */
@Composable
internal fun SketchView(sketch: Sketch, onEdit: () -> Unit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(sketch.width / sketch.height)
                .clip(shape)
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .clickable(onClick = onEdit),
        ) { drawSketch(sketch.strokes, sketch.width) }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onEdit) { Text("✏️ Düzenle") }
            TextButton(onClick = onDelete) { Text("Sil", color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** Tam ekran çizim alanı: kalem ya da parmakla çiz, renk, kalınlık, silgi, geri al. */
@Composable
internal fun SketchEditor(initial: Sketch, onSave: (Sketch) -> Unit, onDismiss: () -> Unit) {
    val strokes = remember { mutableStateListOf<SketchStroke>().apply { addAll(initial.strokes) } }
    val current = remember { mutableStateListOf<Offset>() }
    var color by remember { mutableStateOf(SKETCH_COLORS[0]) }
    var width by remember { mutableStateOf(SKETCH_WIDTHS[1].second) }
    var eraser by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Vazgeç") }
                    Text("Çizim", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }, enabled = strokes.isNotEmpty()) { Text("↶ Geri al") }
                    TextButton(onClick = { strokes.clear() }, enabled = strokes.isNotEmpty()) { Text("Temizle") }
                    TextButton(onClick = { onSave(initial.copy(strokes = strokes.toList())) }) { Text("Kaydet", fontWeight = FontWeight.Bold) }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    SKETCH_COLORS.forEach { c ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(if (!eraser && color == c) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable {
                                    color = c
                                    eraser = false
                                },
                        )
                    }
                    SKETCH_WIDTHS.forEach { (label, w) ->
                        FilterChip(selected = !eraser && width == w, onClick = { width = w; eraser = false }, label = { Text(label) })
                    }
                    FilterChip(selected = eraser, onClick = { eraser = !eraser }, label = { Text("🧽 Silgi") })
                }
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Canvas(
                        Modifier
                            .widthIn(max = 1100.dp)
                            .fillMaxWidth()
                            .aspectRatio(initial.width / initial.height)
                            .clip(shape)
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                            .pointerInput(eraser, color, width) {
                                val unit = initial.width / size.width
                                fun eraseAt(p: Offset) {
                                    val x = p.x * unit
                                    val y = p.y * unit
                                    strokes.removeAll { s ->
                                        var hit = false
                                        var i = 0
                                        while (i + 1 < s.points.size && !hit) {
                                            hit = hypot(s.points[i] - x, s.points[i + 1] - y) < 18f + s.width
                                            i += 2
                                        }
                                        hit
                                    }
                                }
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    down.consume()
                                    if (eraser) eraseAt(down.position) else current.add(down.position)
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        if (eraser) eraseAt(change.position) else current.add(change.position)
                                        change.consume()
                                    }
                                    if (!eraser && current.isNotEmpty()) {
                                        val pts = FloatArray(current.size * 2)
                                        current.forEachIndexed { i, o ->
                                            pts[i * 2] = o.x * unit
                                            pts[i * 2 + 1] = o.y * unit
                                        }
                                        strokes.add(SketchStroke(color.toArgb(), width, pts))
                                        current.clear()
                                    }
                                }
                            },
                    ) {
                        drawSketch(strokes, initial.width)
                        if (current.size > 1) {
                            val path = Path().apply {
                                moveTo(current[0].x, current[0].y)
                                for (i in 1 until current.size) lineTo(current[i].x, current[i].y)
                            }
                            drawPath(path, color, style = Stroke(width * size.width / initial.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
                Text(
                    "Kalemle ya da parmakla çiz. Silgi seçiliyken dokunduğun çizgi silinir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
