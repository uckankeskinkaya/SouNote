@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sounote.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sounote.app.LibraryViewModel
import com.sounote.app.data.Course
import com.sounote.app.data.Exam
import com.sounote.app.data.Resource
import com.sounote.app.data.Topic

/** Ana sayfada sınavdan önceki gün ve sınav günü gösterilecek sınav: 36 saat kala, başladıktan 2 saat sonrasına kadar. */
fun examDayTarget(vm: LibraryViewModel, now: Long = System.currentTimeMillis()): Exam? =
    vm.allExams.filter { it.kind != "task" && !vm.isDone(it) && it.time - now in -2 * HOUR..36 * HOUR }.minByOrNull { it.time }

private const val HOUR = 3_600_000L

/**
 * Sınav günü kartı: sınava giren konular, o konuların PDF'leri (kütüphanede olanlar açılır, olmayanlar toplu indirilir),
 * deneme sınavı ve kart tekrarı tek yerde.
 */
@Composable
fun ExamDayCard(vm: LibraryViewModel, exam: Exam, onReview: () -> Unit, modifier: Modifier = Modifier) {
    val topics: List<Pair<Course, Topic>> = remember(exam.id, vm.catalog) { exam.topics.mapNotNull { vm.catalog.topic(it) } }
    val pdfs: List<Triple<Course, Topic, Resource>> = remember(topics) {
        topics.flatMap { (c, t) -> t.resources.filter { it.isPdf }.map { Triple(c, t, it) } }.distinctBy { it.third.url }
    }
    // Deneme sınavı için: konuların çoğunun bulunduğu ders
    val mainCourse = remember(topics) { topics.groupBy { it.first.id }.maxByOrNull { it.value.size }?.value?.first()?.first }
    var practice by remember { mutableStateOf(false) }
    if (practice && mainCourse != null) {
        val only = topics.filter { it.first.id == mainCourse.id }.map { it.second }.singleOrNull()
        PracticeSetupDialog(vm, mainCourse, only) { practice = false }
    }
    val now = System.currentTimeMillis()
    val todayEnd = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 23); set(java.util.Calendar.MINUTE, 59)
    }.timeInMillis
    val whenText = when {
        exam.time <= now -> "Şimdi"
        exam.time <= todayEnd -> "Bugün"
        else -> "Yarın"
    }

    Column(
        modifier.fillMaxWidth().card().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(SouIcons.Flag, contentDescription = null, tint = countdownColor(exam.time), modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    "$whenText · ${exam.slot.ifBlank { countdownText(exam.time) }}",
                    style = MaterialTheme.typography.labelLarge,
                    color = countdownColor(exam.time),
                )
                Text(exam.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (exam.place.isNotBlank()) {
                    Text(exam.place, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (topics.isNotEmpty()) {
            Text("Sınava girenler", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                topics.forEach { (_, t) ->
                    AssistChip(
                        onClick = {
                            vm.focusTopic = t.id
                            vm.coursesByWeek = false
                            vm.tab = LibraryViewModel.Tab.COURSES
                        },
                        label = { Text(t.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }

        if (pdfs.isNotEmpty()) {
            val local = pdfs.mapNotNull { (c, _, r) -> vm.downloadedDoc(c, r) }
            val bulk = vm.weekDownload
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(SouIcons.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(
                    if (local.size == pdfs.size) "  ${pdfs.size} PDF'in hepsi kütüphanende" else "  ${local.size}/${pdfs.size} PDF kütüphanende",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (local.size == pdfs.size) LocalSouColors.current.success else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                when {
                    bulk != null && bulk.first == EXAM_DOWNLOAD -> {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("  ${bulk.second + 1}/${bulk.third}", style = MaterialTheme.typography.labelLarge)
                    }
                    local.size < pdfs.size -> TextButton(
                        onClick = { vm.downloadWeek(EXAM_DOWNLOAD, pdfs) },
                        enabled = bulk == null && vm.downloading == null,
                    ) { Text("Hepsini indir") }
                }
            }
            // Kütüphanedeki ilk birkaç PDF tek dokunuşla açılır
            local.take(3).forEach { d ->
                TextButton(onClick = { vm.open(d) }, modifier = Modifier.fillMaxWidth()) {
                    Text(d.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("Aç", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (mainCourse != null) {
                Button(onClick = {
                    if (!vm.assistantConfig.ready) vm.showMessage("Deneme sınavı için önce asistanı kur: Ayarlar → Asistan") else practice = true
                }) { Text("Deneme sınavı") }
            }
            if (vm.dueCount > 0) OutlinedButton(onClick = onReview) { Text("${vm.dueCount} kartı tekrar et") }
        }
    }
}

/** Toplu indirmede sınav kartının anahtarı (haftalar 1'den başlar). */
const val EXAM_DOWNLOAD = -1
