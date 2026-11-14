package com.sounote.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sounote.app.LibraryViewModel
import com.sounote.app.data.Course
import com.sounote.app.data.Resource
import com.sounote.app.data.Topic
import java.util.Calendar

const val TODAY_DOWNLOAD = -2

fun todayLesson(vm: LibraryViewModel): List<Triple<Course, Topic, Resource>> {
    val now = System.currentTimeMillis()
    val week = vm.catalog.weekOf(now)
    if (week == 0) return emptyList()
    val weekday = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK)
    val day = when (weekday) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        else -> return emptyList()
    }
    return vm.catalog.courses.flatMap { course ->
        course.topics.flatMap { topic ->
            topic.resources.filter { it.week == week && it.day == day }
                .map { Triple(course, topic, it) }
        }
    }.sortedBy { it.third.order }
}

@Composable
fun TodayLessonCard(
    vm: LibraryViewModel,
    items: List<Triple<Course, Topic, Resource>>,
    modifier: Modifier = Modifier,
) {
    val pdfs = items.filter { it.third.isPdf }
    val bulk = vm.weekDownload
    Column(
        modifier.fillMaxWidth().card().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(SouIcons.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Bugün derste", style = MaterialTheme.typography.titleSmall)
                Text(
                    items.map { it.second.name }.distinct().joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        pdfs.take(4).forEach { (course, topic, res) ->
            val local = vm.downloadedDoc(course, res)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(SouIcons.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text(
                    res.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                )
                when {
                    local != null -> TextButton(onClick = { vm.open(local) }) { Text("Aç") }
                    vm.downloading == res.url -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else -> TextButton(
                        onClick = { vm.downloadResource(course, topic, res) },
                        enabled = bulk == null && vm.downloading == null,
                    ) { Text("İndir") }
                }
            }
        }
        when {
            bulk != null && bulk.first == TODAY_DOWNLOAD -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("  ${bulk.second + 1}/${bulk.third}", style = MaterialTheme.typography.labelLarge)
                }
            }
            pdfs.size > 4 || pdfs.any { vm.downloadedDoc(it.first, it.third) == null } -> {
                TextButton(
                    onClick = { vm.downloadWeek(TODAY_DOWNLOAD, pdfs) },
                    enabled = bulk == null && vm.downloading == null,
                ) { Text("Hepsini indir") }
            }
        }
        items.filter { it.third.kind == "audio" }.forEach { (course, topic, res) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(SouIcons.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text(
                    res.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                )
                TextButton(onClick = {
                    // Dinleme ekranı Çalış sekmesinde açılır
                    vm.openListening(course, topic, res)
                    vm.tab = LibraryViewModel.Tab.COURSES
                }) { Text("Dinle") }
            }
        }
    }
}
