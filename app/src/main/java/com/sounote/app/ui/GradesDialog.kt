package com.sounote.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sounote.app.LibraryViewModel
import com.sounote.app.data.GradeItem
import com.sounote.app.data.Grades
import java.util.Locale

private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else String.format(Locale.forLanguageTag("tr"), "%.1f", d)
private fun parse(s: String) = s.trim().replace(',', '.').toDoubleOrNull()

/**
 * Not ortalaması: her sınav/ödev için ağırlık (%) ve puan. Girilenlerin ortalaması, şu ana kadar toplanan
 * ve geçmek için kalanlardan alınması gereken ortalama.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GradesDialog(vm: LibraryViewModel, onDismiss: () -> Unit) {
    val g = vm.grades
    fun update(items: List<GradeItem> = g.items, pass: Double = g.passMark) = vm.updateGrades(Grades(pass, items))

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kapat") }
                    Text("Not ortalamam", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().weight(1f),
                ) {
                    item { Summary(g) }
                    item {
                        var passText by remember { mutableStateOf(fmt(g.passMark)) }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().card().padding(14.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Geçme notu", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Okulunun belirlediği baraj (100 üzerinden)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedTextField(
                                value = passText,
                                onValueChange = { t ->
                                    passText = t.take(5)
                                    parse(t)?.takeIf { it in 0.0..100.0 }?.let { update(pass = it) }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(90.dp),
                            )
                        }
                    }
                    if (g.items.isEmpty()) {
                        item {
                            Text(
                                "Hazırlık notun hangi sınavlardan oluşuyorsa ekle (ör. Progress Test %20, Writing %10, Yeterlik %50) " +
                                    "ve aldığın puanları gir. Ağırlıkları okulunun yönergesinden ya da hocandan öğrenebilirsin.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(g.items, key = { it.id }) { item ->
                        GradeRow(
                            item,
                            onChange = { changed -> update(items = g.items.map { if (it.id == item.id) changed else it }) },
                            onDelete = { update(items = g.items.filter { it.id != item.id }) },
                        )
                    }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                update(items = g.items + GradeItem(System.currentTimeMillis(), "", 0.0, null))
                            }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(" Sınav ekle")
                            }
                            // Takvimdeki sınav adlarını (henüz listede olmayanları) öner
                            val names = g.items.map { it.name.lowercase() }.toSet()
                            vm.allExams.map { it.title.ifBlank { it.course } }.distinct()
                                .filter { it.isNotBlank() && it.lowercase() !in names }.take(6).forEach { title ->
                                    AssistChip(
                                        onClick = { update(items = g.items + GradeItem(System.currentTimeMillis(), title, 0.0, null)) },
                                        label = { Text("+ $title", maxLines = 1) },
                                    )
                                }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(g: Grades) {
    val avg = g.average
    val needed = g.needed
    val weightOk = kotlin.math.abs(g.totalWeight - 100.0) < 0.01
    val target by animateFloatAsState(((avg ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f), tween(700), label = "avg")
    val passColor = if (avg != null && avg >= g.passMark) Color(0xFF2E7D4F) else MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().card().padding(18.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(104.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 12.dp.toPx()
                drawArc(track, -90f, 360f, false, style = Stroke(w, cap = StrokeCap.Round))
                drawArc(passColor, -90f, 360f * target, false, style = Stroke(w, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(avg?.let(::fmt) ?: "–", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = passColor)
                Text("ortalama", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                g.items.isEmpty() -> Text("Henüz sınav eklemedin", style = MaterialTheme.typography.titleSmall)
                needed == null -> Text(
                    if ((avg ?: 0.0) >= g.passMark) "Tebrikler, geçtin 🎉" else "Bütün puanlar girildi; geçme notunun altında",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                needed <= 0 -> Text("Kalanlardan 0 alsan bile geçiyorsun 🎉", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                needed > 100 -> Text(
                    "Kalan sınavlardan ${fmt(needed)} ortalama gerekiyor; 100'ü aşıyor 😕",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> Text(
                    "Geçmek için kalan sınavlardan ortalama ${fmt(needed)} almalısın",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "Şu ana kadar toplanan: ${fmt(g.secured)} / 100 · kalan ağırlık %${fmt(g.remainingWeight)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (g.items.isNotEmpty() && !weightOk) {
                Text(
                    "Ağırlıkların toplamı %${fmt(g.totalWeight)}; %100 olmalı",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun GradeRow(item: GradeItem, onChange: (GradeItem) -> Unit, onDelete: () -> Unit) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    var weight by remember(item.id) { mutableStateOf(if (item.weight > 0) fmt(item.weight) else "") }
    var score by remember(item.id) { mutableStateOf(item.score?.let(::fmt) ?: "") }
    Column(Modifier.fillMaxWidth().card().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(60)
                    onChange(item.copy(name = name))
                },
                singleLine = true,
                label = { Text("Sınav / ödev") },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Sil") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = weight,
                onValueChange = { t ->
                    weight = t.take(5)
                    onChange(item.copy(weight = parse(t)?.coerceIn(0.0, 100.0) ?: 0.0))
                },
                singleLine = true,
                label = { Text("Ağırlık %") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = score,
                onValueChange = { t ->
                    score = t.take(5)
                    onChange(item.copy(score = parse(t)?.coerceIn(0.0, 100.0)))
                },
                singleLine = true,
                label = { Text("Puan (boş = olmadı)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
