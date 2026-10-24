package com.sounote.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sounote.app.LibraryViewModel
import com.sounote.app.data.Course
import com.sounote.app.data.Topic
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Konu haritası: ders ortada, konuları çevresinde. Her konu kaynak ilerlemesi ve deneme sınavı puanına göre
 * renklenir (başlanmadı / çalışılıyor / hazır). Konuya dokununca ayrıntı ve kısayollar.
 */
@Composable
fun TopicMapScreen(vm: LibraryViewModel, course: Course, onOpenTopic: (Topic) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf<Topic?>(null) }
    var practice by remember { mutableStateOf<Topic?>(null) }
    var practiceOpen by remember { mutableStateOf(false) }
    if (practiceOpen) PracticeSetupDialog(vm, course, practice) { practiceOpen = false }

    val sou = LocalSouColors.current
    val idle = MaterialTheme.colorScheme.outline
    val working = MaterialTheme.colorScheme.primary
    fun colorOf(m: Float) = when {
        m >= 0.8f -> sou.success
        m > 0f -> working
        else -> idle
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().souBackground()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onDismiss) { Icon(SouIcons.Close, contentDescription = "Kapat") }
                    Column(Modifier.weight(1f)) {
                        Text("Konu haritası", style = MaterialTheme.typography.titleMedium)
                        Text(course.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Legend(idle, "Başlanmadı")
                    Legend(working, "Çalışılıyor")
                    Legend(sou.success, "Hazır")
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val density = LocalDensity.current
                    val w = with(density) { maxWidth.toPx() }
                    val h = with(density) { maxHeight.toPx() }
                    val center = Offset(w / 2, h / 2)
                    val nodeW = if (maxWidth < 500.dp) 112.dp else 140.dp
                    val nodeWpx = with(density) { nodeW.toPx() }
                    val nodeHpx = with(density) { 96.dp.toPx() }
                    // Konular elips üstünde; düğümler kenardan taşmasın.
                    val rx = (w / 2 - nodeWpx / 2 - 8f).coerceAtLeast(60f)
                    val ry = (h / 2 - nodeHpx / 2 - 8f).coerceAtLeast(60f)
                    val n = course.topics.size
                    val points = course.topics.indices.map { i ->
                        val a = -Math.PI / 2 + 2 * Math.PI * i / n.coerceAtLeast(1)
                        Offset(center.x + (rx * cos(a)).toFloat(), center.y + (ry * sin(a)).toFloat())
                    }
                    val masteries = course.topics.map { vm.mastery(it) }

                    Canvas(Modifier.fillMaxSize()) {
                        points.forEachIndexed { i, p ->
                            val c = colorOf(masteries[i])
                            val path = Path().apply {
                                moveTo(center.x, center.y)
                                // Hafif kavisli bağ
                                val mx = (center.x + p.x) / 2 + (p.y - center.y) * 0.12f
                                val my = (center.y + p.y) / 2 - (p.x - center.x) * 0.12f
                                quadraticTo(mx, my, p.x, p.y)
                            }
                            drawPath(path, c.copy(alpha = 0.55f), style = Stroke(width = 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                        }
                    }

                    // Ders düğümü
                    val (done, total) = vm.progressOf(course.topics.flatMap { it.resources })
                    val avg = if (masteries.isEmpty()) 0f else masteries.average().toFloat()
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .offset { IntOffset((center.x - with(density) { 52.dp.toPx() }).roundToInt(), (center.y - with(density) { 52.dp.toPx() }).roundToInt()) }
                            .size(104.dp),
                    ) {
                        ProgressRing(avg, size = 104.dp, stroke = 6.dp, color = Color(course.color), showLabel = false)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CourseArt(course, 52.dp)
                            Text("%${(avg * 100).roundToInt()}", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    course.topics.forEachIndexed { i, t ->
                        val p = points[i]
                        val m = masteries[i]
                        val c = colorOf(m)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .offset { IntOffset((p.x - nodeWpx / 2).roundToInt(), (p.y - nodeHpx / 2).roundToInt()) }
                                .width(nodeW)
                                .appear(i)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .border(if (selected == t) 2.dp else 1.dp, if (selected == t) c else c.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                .clickable { selected = if (selected == t) null else t }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                ProgressRing(m, size = 34.dp, stroke = 4.dp, color = c, showLabel = false)
                                Text("${(m * 100).roundToInt()}", style = MaterialTheme.typography.labelSmall)
                            }
                            Text(
                                t.name,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    if (total == 0 && n == 0) {
                        Text("Bu derste konu yok", modifier = Modifier.align(Alignment.Center))
                    } else if (done == 0 && vm.testScores.keys.none { k -> course.topics.any { it.id == k } }) {
                        Text(
                            "Kaynakları işaretledikçe ve deneme sınavı çözdükçe harita dolar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                        )
                    }
                }
                selected?.let { t ->
                    val (d, tot) = vm.progressOf(t.resources)
                    val score = vm.testScores[t.id]
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(max = 640.dp).fillMaxWidth().card().padding(16.dp),
                    ) {
                        Text(t.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            buildString {
                                append("$d / $tot kaynak tamamlandı")
                                append(" · ")
                                append(if (score != null) "son deneme %$score" else "deneme çözülmedi")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = {
                                practice = t
                                practiceOpen = true
                            }) {
                                Icon(SouIcons.Quiz, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Deneme sınavı", modifier = Modifier.padding(start = 6.dp))
                            }
                            OutlinedButton(onClick = { onOpenTopic(t) }) { Text("Kaynaklar") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 5.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
