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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.LocalConfiguration
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
    var byWeek by remember { mutableStateOf(false) }
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
                course == null && byWeek -> WeekList(vm) { byWeek = false }
                course == null -> CourseGrid(vm, onWeeks = { byWeek = true }) { selected = it.id }
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
private fun CourseGrid(vm: LibraryViewModel, onWeeks: () -> Unit, onOpen: (Course) -> Unit) {
    val now = System.currentTimeMillis()
    val upcoming = vm.allExams.filter { it.time > now }
    val narrow = LocalConfiguration.current.screenWidthDp < 600
    LazyVerticalGrid(
        // Telefonda da iki sütun kalsın (tek sütunda kartlar dev oluyordu).
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(if (narrow) 12.dp else 20.dp),
        horizontalArrangement = Arrangement.spacedBy(if (narrow) 10.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (narrow) 10.dp else 16.dp),
        modifier = Modifier.widthIn(max = 1100.dp).fillMaxSize(),
    ) {
        if (vm.catalog.termStart != 0L) {
            item(span = { GridItemSpan(maxLineSpan) }) { ViewSwitch(byWeek = false, onTopics = {}, onWeeks = onWeeks) }
        }
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
                Text(c.name, color = Color.White, style = if (narrow) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    val icon = kindIcon(res.kind)
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

@Composable
private fun ViewSwitch(byWeek: Boolean, onTopics: () -> Unit, onWeeks: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        androidx.compose.material3.FilterChip(selected = !byWeek, onClick = onTopics, label = { Text("📚 Konular", maxLines = 1) })
        androidx.compose.material3.FilterChip(selected = byWeek, onClick = onWeeks, label = { Text("🗓️ Haftalar", maxLines = 1) })
    }
}

/** Haftalara göre: her haftanın tarihleri, o haftanın sınav/ödevleri ve kaynakları; bu hafta öne çıkar. */
@Composable
private fun WeekList(vm: LibraryViewModel, onTopics: () -> Unit) {
    val context = LocalContext.current
    val catalog = vm.catalog
    val exams = vm.allExams
    val resources = catalog.courses.flatMap { c -> c.topics.flatMap { t -> t.resources.map { Triple(c, t, it) } } }
    val lastWeek = maxOf(
        exams.maxOfOrNull { catalog.weekOf(it.time) } ?: 0,
        resources.maxOfOrNull { it.third.week } ?: 0,
        catalog.weekItems.maxOfOrNull { it.week } ?: 0,
    )
    val current = catalog.weekOf(System.currentTimeMillis())
    val fmt = remember { java.text.SimpleDateFormat("d MMM", java.util.Locale.forLanguageTag("tr")) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(Unit) { if (current in 1..lastWeek) listState.scrollToItem(current) }
    val undated = resources.filter { it.third.week == 0 }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        modifier = Modifier.widthIn(max = 900.dp).fillMaxSize(),
    ) {
        item { ViewSwitch(byWeek = true, onTopics = onTopics, onWeeks = {}) }
        items((1..lastWeek).toList(), key = { it }) { week ->
            val start = catalog.weekStart(week)
            val weekExams = exams.filter { catalog.weekOf(it.time) == week }
            val weekRes = resources.filter { it.third.week == week }
            val weekMissing = catalog.weekItems.filter { it.week == week }
            val isNow = week == current
            Column(
                Modifier
                    .fillMaxWidth()
                    .card()
                    .then(if (isNow) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)) else Modifier)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hafta $week", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "  ${fmt.format(java.util.Date(start))} – ${fmt.format(java.util.Date(start + 4L * 86_400_000))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (isNow) Text("Bu hafta", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
                weekExams.forEach { e ->
                    Text(
                        "${e.kindEmoji} ${e.title} · ${countdownText(e.time)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = countdownColor(e.time),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                // Gün gün: Pazartesi … Cuma, sonra kendi çalışman
                for (day in listOf(1, 2, 3, 4, 5, 0)) {
                    val rows = weekRes.filter { it.third.day == day }.map { it.third.order to it } +
                        weekMissing.filter { it.day == day }.map { it.order to it }
                    if (rows.isEmpty()) continue
                    Text(
                        DAY_NAMES.getValue(day),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    rows.sortedBy { it.first }.forEach { (_, item) ->
                        if (item is Triple<*, *, *>) {
                            @Suppress("UNCHECKED_CAST")
                            val tr = item as Triple<Course, Topic, Resource>
                            ResourceRow(vm, tr.first, tr.second, tr.third) { res ->
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(res.url))) }
                            }
                        } else if (item is com.sounote.app.data.WeekItem) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            ) {
                                Text(kindIcon(item.kind), fontSize = 20.sp, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                )
                                Text("Drive'da yok", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
                if (weekExams.isEmpty() && weekRes.isEmpty() && weekMissing.isEmpty()) {
                    Text("Bu hafta için kayıt yok", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (undated.isNotEmpty()) {
            item {
                Text(
                    "Haftası belirtilmemiş ${undated.size} kaynak \"Konulara göre\" görünümünde.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val DAY_NAMES = mapOf(
    1 to "Pazartesi", 2 to "Salı", 3 to "Çarşamba", 4 to "Perşembe", 5 to "Cuma", 0 to "Kendi çalışman (self-study)",
)

private fun kindIcon(kind: String) = when (kind) {
    "pdf" -> "📄"
    "pptx" -> "📊"
    "docx" -> "📝"
    "video" -> "🎬"
    "audio" -> "🎧"
    "html" -> "🧪"
    else -> "🔗"
}
