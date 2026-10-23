@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sounote.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sounote.app.LibraryViewModel
import com.sounote.app.data.Course
import com.sounote.app.data.Resource
import com.sounote.app.data.Topic

/** Ortak ders kataloğu: alanlar → konular → kaynaklar; sınavlarla bağlantılı konular işaretli. */
@Composable
fun CoursesScreen(vm: LibraryViewModel) {
    var selected by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    val catalog = vm.catalog
    val course = catalog.courses.find { it.id == selected }

    // Sınavlar ekranından bir konuya gelindi: o alanı aç, konuyu genişlet.
    LaunchedEffect(vm.focusTopic) {
        val t = vm.focusTopic ?: return@LaunchedEffect
        catalog.topic(t)?.let { (c, topic) ->
            selected = c.id
            expanded = expanded + topic.id
        }
        vm.focusTopic = null
    }
    BackHandler(enabled = course != null) { selected = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(course?.let { "${it.emoji} ${it.name}".trim() } ?: "Dersler") },
                navigationIcon = {
                    if (course != null) {
                        IconButton(onClick = { selected = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri") }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refreshCatalog() }) { Icon(Icons.Default.Refresh, contentDescription = "Yenile") }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (vm.catalogLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                catalog.courses.isEmpty() -> EmptyCatalog(vm)
                course == null -> CourseGrid(vm) { selected = it.id }
                else -> CourseDetail(vm, course, expanded) { id ->
                    expanded = if (id in expanded) expanded - id else expanded + id
                }
            }
        }
    }
}

@Composable
private fun EmptyCatalog(vm: LibraryViewModel) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.widthIn(max = 520.dp).padding(32.dp),
    ) {
        Text("📚", fontSize = 48.sp)
        Text("Ders kaynakları henüz yüklenmedi", style = MaterialTheme.typography.titleMedium)
        Text(
            vm.catalogError ?: "Ortak liste internetten indirilir. İnternete bağlıyken yenile.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { vm.refreshCatalog() }) { Text("Yenile") }
    }
}

@Composable
private fun CourseGrid(vm: LibraryViewModel, onOpen: (Course) -> Unit) {
    val now = System.currentTimeMillis()
    val upcoming = vm.allExams.filter { it.time > now }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(170.dp),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.widthIn(max = 1100.dp).fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Bütün ders kaynakları konulara göre ayrıldı. Bu liste herkes için ortak; yeni kaynak eklenince burada görünür." +
                    (if (vm.catalog.updated.isNotEmpty()) " Son güncelleme: ${vm.catalog.updated}" else ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(vm.catalog.courses, key = { it.id }) { c ->
            val topicIds = c.topics.map { it.id }.toSet()
            val next = upcoming.firstOrNull { e -> e.topics.any { it in topicIds } }
            val color = Color(c.color)
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .aspectRatio(0.95f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.7f).compositeOver(Color.Black))))
                    .clickable { onOpen(c) }
                    .padding(16.dp),
            ) {
                Text(c.emoji, fontSize = 36.sp)
                Text(c.name, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${c.topics.size} konu · ${c.topics.sumOf { it.resources.size }} kaynak",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelLarge,
                )
                if (next != null) {
                    Text(
                        "${next.kindEmoji} ${next.title} · ${countdownText(next.time)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.18f)).padding(6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseDetail(vm: LibraryViewModel, course: Course, expanded: Set<String>, onToggle: (String) -> Unit) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val exams = vm.allExams.filter { it.time > now - 3 * 3_600_000L }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        modifier = Modifier.widthIn(max = 900.dp).fillMaxSize(),
    ) {
        items(course.topics, key = { it.id }) { topic ->
            val related = exams.filter { topic.id in it.topics }
            TopicCard(
                vm, course, topic, related.map { "${it.kindEmoji} ${it.title} · ${countdownText(it.time)}" },
                open = topic.id in expanded,
                onToggle = { onToggle(topic.id) },
                onOpenLink = { res ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(res.url))) }
                        .onFailure { vm.showMessage("Bağlantı açılamadı") }
                },
            )
        }
        item {
            Text(
                "PDF'ler \"İndir\" ile kütüphanende \"${course.name}\" klasörüne gelir; üstüne kalemle çalışabilirsin. " +
                    "Sunum, Word ve videolar Drive'da açılır.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun TopicCard(
    vm: LibraryViewModel,
    course: Course,
    topic: Topic,
    exams: List<String>,
    open: Boolean,
    onToggle: () -> Unit,
    onOpenLink: (Resource) -> Unit,
) {
    Column(Modifier.fillMaxWidth().card()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(topic.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${topic.resources.size} kaynak",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                exams.forEach { e ->
                    Text(
                        "Sınavda: $e",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(if (open) "▲" else "▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) {
            topic.resources.forEach { res ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ResourceRow(vm, course, topic, res, onOpenLink)
            }
        }
    }
}

@Composable
private fun ResourceRow(vm: LibraryViewModel, course: Course, topic: Topic, res: Resource, onOpenLink: (Resource) -> Unit) {
    val icon = when (res.kind) {
        "pdf" -> "📄"
        "pptx" -> "📊"
        "docx" -> "📝"
        "video" -> "🎬"
        else -> "🔗"
    }
    val local = vm.downloadedDoc(course, res)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(icon, fontSize = 20.sp)
        Text(
            res.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        when {
            vm.downloading == res.url -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
            local != null -> TextButton(onClick = { vm.open(local) }) { Text("Aç") }
            res.kind == "pdf" -> {
                TextButton(onClick = { vm.downloadResource(course, topic, res) }) { Text("İndir") }
            }
            else -> TextButton(onClick = { onOpenLink(res) }) { Text("Drive'da aç") }
        }
    }
}
