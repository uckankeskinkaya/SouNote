package com.sounote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

internal val HUE_COLORS = listOf(
    Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
)

/** Tam renk seçici: ton çubuğu, doygunluk/parlaklık alanı ve hex kodu. */
@Composable
fun ColorPickerDialog(initial: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    var hex by remember { mutableStateOf(toHex(initial)) }
    val color = Color.hsv(hue, sat, value)

    fun syncHex() {
        hex = toHex(color.toArgb())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renk seç") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Doygunluk (sağa doğru) ve parlaklık (yukarı doğru)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            fun update(o: Offset) {
                                sat = (o.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (o.y / size.height).coerceIn(0f, 1f)
                                hex = toHex(Color.hsv(hue, sat, value).toArgb())
                            }
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                update(down.position)
                                drag(down.id) { change ->
                                    update(change.position)
                                    change.consume()
                                }
                            }
                        }
                        .drawBehind {
                            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
                            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                            val c = Offset(sat * size.width, (1f - value) * size.height)
                            drawCircle(Color.White, radius = 11.dp.toPx(), center = c, style = Stroke(3.dp.toPx()))
                            drawCircle(Color.Black.copy(alpha = 0.4f), radius = 13.dp.toPx(), center = c, style = Stroke(1.dp.toPx()))
                        },
                )
                // Ton
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .pointerInput(Unit) {
                            fun update(o: Offset) {
                                hue = (o.x / size.width).coerceIn(0f, 1f) * 360f
                                hex = toHex(Color.hsv(hue, sat, value).toArgb())
                            }
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                update(down.position)
                                drag(down.id) { change ->
                                    update(change.position)
                                    change.consume()
                                }
                            }
                        }
                        .drawBehind {
                            drawRect(Brush.horizontalGradient(HUE_COLORS))
                            val x = hue / 360f * size.width
                            drawCircle(Color.White, radius = 11.dp.toPx(), center = Offset(x, size.height / 2), style = Stroke(3.dp.toPx()))
                        },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(color)
                            .border(1.dp, Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                    )
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { text ->
                            hex = text
                            parseHex(text)?.let { c ->
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(c, hsv)
                                hue = hsv[0]
                                sat = hsv[1]
                                value = hsv[2]
                            }
                        },
                        singleLine = true,
                        label = { Text("Hex") },
                        modifier = Modifier.width(160.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                syncHex()
                onPick(color.toArgb())
            }) { Text("Seç") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

private fun toHex(c: Int) = "#%06X".format(c and 0xFFFFFF)

private fun parseHex(text: String): Int? {
    val t = text.trim().removePrefix("#")
    if (t.length != 6) return null
    return t.toIntOrNull(16)?.let { it or 0xFF000000.toInt() }
}
