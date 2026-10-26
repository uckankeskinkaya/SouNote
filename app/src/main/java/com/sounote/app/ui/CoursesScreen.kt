@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sounote.app.ui

import java.io.File
import androidx.core.content.FileProvider
import android.content.Context
import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Checkbox
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.sounote.app.data.QuickItem
import com.sounote.app.data.Resource
import com.sounote.app.data.Topic

/** Ortak ders kataloğu: alanlar → konular → kaynaklar; sınavlarla bağlantılı konular işaretli. */
@Composable
fun CoursesScreen(vm: LibraryViewModel) {
    var selected by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var byWeek by vm::coursesByWeek
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
    // Arama sonucundan ya da hızlı erişimden gelindi: dersi aç.
    LaunchedEffect(vm.focusCourse) {
        val id = vm.focusCourse ?: return@LaunchedEffect
        if (catalog.courses.any { it.id == id }) {
            selected = id
            byWeek = false
        }
        vm.focusCourse = null
    }
    LaunchedEffect(selected) { selected?.let(vm::courseOpened) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(course?.name ?: "Dersler") },
                navigationIcon = {
                    if (course != null) {
                        IconButton(onClick = { selected = null }) { Icon(SouIcons.ArrowBack, contentDescription = "Geri") }
                    }
                },
                actions = {
                    if (course != null) {
                        FavoriteButton(vm.isFavorite(QuickItem.COURSE, course.id), onToggle = { vm.toggleFavorite(QuickItem.COURSE, course.id) })
                    }
                    IconButton(onClick = { vm.refreshCatalog() }) { Icon(SouIcons.Refresh, contentDescription = "Yenile") }
                },
            )
        },
        containerColor = Color.Transparent,
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
        Icon(SouIcons.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
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
        items(vm.catalog.courses, key = { it.id }) { c ->
            Box(Modifier.animateItem().appear(vm.catalog.courses.indexOf(c)).pressScale(), propagateMinConstraints = true) {
                val topicIds = c.topics.map { it.id }.toSet()
                val next = upcoming.firstOrNull { e -> e.topics.any { it in topicIds } }
                val color = Color(c.color)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .card()
                        .clickable { onOpen(c) }
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        CourseArt(c, if (narrow) 52.dp else 60.dp)
                        Box(Modifier.weight(1f))
                        if (vm.isFavorite(QuickItem.COURSE, c.id)) {
                            Icon(SouIcons.StarFilled, contentDescription = "Favori", tint = LocalSouColors.current.star, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(
                        c.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "${c.topics.size} konu · ${c.topics.sumOf { it.resources.size }} kaynak",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val (done, total) = vm.progressOf(c.topics.flatMap { it.resources })
                    if (total > 0) {
                        ProgressBar(done / total.toFloat(), Modifier.fillMaxWidth().padding(top = 8.dp), color = color, height = 4.dp)
                    }
                    if (next != null) {
                        Text(
                            "${next.title} · ${countdownText(next.time)}",
                            color = countdownColor(next.time),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseDetail(vm: LibraryViewModel, course: Course, expanded: Set<String>, onToggle: (String) -> Unit) {
    val context = LocalContext.current
    var summaryOpen by remember { mutableStateOf(false) }
    var testOpen by remember { mutableStateOf(false) }
    var mapOpen by remember { mutableStateOf(false) }
    var speakingOpen by remember { mutableStateOf(false) }
    var exerciseOpen by remember { mutableStateOf(false) }
    if (exerciseOpen) ExerciseScreen(vm, course = course) { exerciseOpen = false }
    if (speakingOpen) SpeakingScreen(vm) { speakingOpen = false }
    if (testOpen) PracticeSetupDialog(vm, course) { testOpen = false }
    if (mapOpen) {
        TopicMapScreen(vm, course, onOpenTopic = { t ->
            mapOpen = false
            if (t.id !in expanded) onToggle(t.id)
        }) { mapOpen = false }
    }
    if (summaryOpen) {
        AssistantDialog(
            vm,
            AssistantSource.Course(course.name, vm.courseDocs(course)),
            prefill = "Bu dersin konularını tek sayfada başlıklar ve maddelerle özetle",
        ) { summaryOpen = false }
    }
    val now = System.currentTimeMillis()
    val exams = vm.allExams.filter { it.time > now - 3 * 3_600_000L }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        modifier = Modifier.widthIn(max = 900.dp).fillMaxSize(),
    ) {
        item { CourseHeader(vm, course) }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().card().clickable { mapOpen = true }.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                IconBadge(SouIcons.AccountTree, Color(course.color))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Konu haritası", style = MaterialTheme.typography.titleSmall)
                    Text("Hangi konuda ne kadar hazırsın", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(SouIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val docs = vm.courseDocs(course)
        if ("speaking" in (course.id + course.name).lowercase()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().card().clickable { speakingOpen = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    IconBadge(SouIcons.Mic, Color(course.color))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Speaking pratiği", style = MaterialTheme.typography.titleSmall)
                        Text("Konuş, yazıya dökülsün, asistan puanlasın", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(SouIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            // Asistan karışık türde alıştırma hazırlar; cevaplar gösterilir ya da asistan değerlendirir.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().card().clickable { exerciseOpen = true }.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                IconBadge(SouIcons.EditNote, MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Alıştırma", style = MaterialTheme.typography.titleSmall)
                    Text("Boşluk doldurma, yeniden yazma, açık uçlu; asistan kontrol eder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(SouIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            // Asistan konudan çoktan seçmeli deneme sınavı hazırlar (indirilmiş PDF'ler varsa onlardan).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().card().clickable { testOpen = true }.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                IconBadge(SouIcons.Quiz, LocalSouColors.current.success)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Deneme sınavı", style = MaterialTheme.typography.titleSmall)
                    Text("Konudan çoktan seçmeli test, anında puan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(SouIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (docs.isNotEmpty()) {
            item {
                // Asistan bu dersin indirilmiş bütün PDF'lerini okuyup özet çıkarır.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().card().clickable { summaryOpen = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    IconBadge(SouIcons.StarShine)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Asistanla ders özeti", style = MaterialTheme.typography.titleSmall)
                        Text("${docs.size} belgeden özet, kelimeler ve sorular", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(SouIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (docs.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Son açılan belgeler")
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(docs.take(10), key = { "cd" + it.id }) { d ->
                            Box(Modifier.width(120.dp).pressScale()) {
                                DocCard(d, onClick = { vm.open(d) }, favorite = vm.isFavorite(QuickItem.DOC, d.id))
                            }
                        }
                    }
                }
            }
        }
        item { SectionHeader("Konular", modifier = Modifier.padding(top = 6.dp)) }
        items(course.topics, key = { it.id }) { topic ->
            Box(Modifier.animateItem(), propagateMinConstraints = true) {
                val related = exams.filter { topic.id in it.topics }
                TopicCard(
                    vm, course, topic, related.map { "${it.title} · ${countdownText(it.time)}" },
                    open = topic.id in expanded,
                    onToggle = { onToggle(topic.id) },
                    onOpenLink = { res ->
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(res.url))) }
                            .onFailure { vm.showMessage("Bağlantı açılamadı") }
                    },
                )
            }
        }
    }
}

/** Dersin üst kartı: emoji, renk, ilerleme ve sayılar. */
@Composable
private fun CourseHeader(vm: LibraryViewModel, course: Course) {
    val color = Color(course.color)
    val resources = course.topics.flatMap { it.resources }
    val (done, total) = vm.progressOf(resources)
    val now = System.currentTimeMillis()
    val topicIds = course.topics.map { it.id }.toSet()
    val next = vm.allExams.firstOrNull { e -> e.time > now && e.topics.any { it in topicIds } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().card().padding(16.dp),
    ) {
        CourseArt(course, 64.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${course.topics.size} konu · ${resources.size} kaynak",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (next != null) {
                Text(
                    "${next.title} · ${countdownText(next.time)}",
                    color = countdownColor(next.time),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (total > 0) {
            ProgressRing(
                done / total.toFloat(),
                size = 56.dp,
                stroke = 5.dp,
                color = color,
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
                val (done, total) = vm.progressOf(topic.resources)
                Text(
                    if (total > 0 && done > 0) "$done / $total kaynak tamamlandı" else "${topic.resources.size} kaynak",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (total > 0 && done == total) LocalSouColors.current.success else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (total > 0 && done > 0) ProgressBar(done / total.toFloat(), Modifier.fillMaxWidth(0.6f).padding(top = 2.dp), height = 4.dp)
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
                val key = vm.doneKey(res)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                    Checkbox(checked = key in vm.doneItems, onCheckedChange = { vm.toggleDone(key) })
                    Box(Modifier.weight(1f).graphicsLayer { alpha = if (key in vm.doneItems) 0.6f else 1f }) {
                        ResourceRow(vm, course, topic, res, onOpenLink)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResourceRow(vm: LibraryViewModel, course: Course, topic: Topic, res: Resource, onOpenLink: (Resource) -> Unit) {
    val context = LocalContext.current
    val icon = kindIcon(res.kind)
    val local = vm.downloadedDoc(course, res)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
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
            // Word/PPT: WPS/Office'te açılır; oradan PDF olarak SouNote'a paylaşılır
            res.isOffice -> Row {
                TextButton(onClick = { onOpenLink(res) }) { Text("Drive", maxLines = 1) }
                TextButton(onClick = { vm.downloadForOtherApp(res) { openInOtherApp(context, vm, it, res) } }) {
                    Text("Aç", maxLines = 1)
                }
            }
            else -> TextButton(onClick = { onOpenLink(res) }) { Text("Drive'da aç") }
        }
    }
}

/** Word/PPT'yi WPS, Office gibi bir uygulamada açar; PDF'e çevirip SouNote'a geri paylaşmayı hatırlatır. */
private fun openInOtherApp(context: Context, vm: LibraryViewModel, file: File, res: Resource) {
    val mime = if (res.kind == "pptx") {
        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    } else {
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
        vm.showMessage("Üstüne yazmak için WPS'te PDF olarak dışa aktar → Paylaş → SouNote; PDF kütüphanene gelir")
    } catch (e: ActivityNotFoundException) {
        vm.showMessage("Word/PowerPoint açabilen uygulama yok. Play Store'dan WPS Office ya da Microsoft 365'i kurabilirsin")
    }
}

@Composable
private fun ViewSwitch(byWeek: Boolean, onTopics: () -> Unit, onWeeks: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        androidx.compose.material3.FilterChip(selected = !byWeek, onClick = onTopics, label = { Text("Konular", maxLines = 1) })
        androidx.compose.material3.FilterChip(selected = byWeek, onClick = onWeeks, label = { Text("Haftalar", maxLines = 1) })
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
            Box(Modifier.animateItem(), propagateMinConstraints = true) {
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
                    val tasks = vm.weekTasks(week)
                    if (tasks.isNotEmpty()) {
                        val done = tasks.count { it.first in vm.doneItems }
                        val progress by animateFloatAsState(done / tasks.size.toFloat(), label = "week")
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                            )
                            Text(
                                if (done == tasks.size) "  Tamam" else "  $done/${tasks.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    weekExams.forEach { e ->
                        Text(
                            "${e.title} · ${countdownText(e.time)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = countdownColor(e.time),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    // Gün gün: Pazartesi … Cuma, günü belli olmayanlar (6), sonra kendi çalışman
                    for (day in listOf(1, 2, 3, 4, 5, 6, 0)) {
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
                                val key = vm.doneKey(tr.third)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = key in vm.doneItems, onCheckedChange = { vm.toggleDone(key) })
                                    Box(Modifier.weight(1f).graphicsLayer { alpha = if (key in vm.doneItems) 0.55f else 1f }) {
                                        ResourceRow(vm, tr.first, tr.second, tr.third) { res ->
                                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(res.url))) }
                                        }
                                    }
                                }
                            } else if (item is com.sounote.app.data.WeekItem) {
                                val key = vm.doneKey(item)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(end = 16.dp, top = 2.dp, bottom = 2.dp),
                                ) {
                                    Checkbox(checked = key in vm.doneItems, onCheckedChange = { vm.toggleDone(key) })
                                    Icon(kindIcon(item.kind), contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
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
    1 to "Pazartesi", 2 to "Salı", 3 to "Çarşamba", 4 to "Perşembe", 5 to "Cuma", 6 to "Bu hafta", 0 to "Kendi çalışman (self-study)",
)

private fun kindIcon(kind: String) = when (kind) {
    "pdf" -> SouIcons.PictureAsPdf
    "pptx" -> SouIcons.Slideshow
    "docx" -> SouIcons.Description
    "video" -> SouIcons.PlayArrow
    "audio" -> SouIcons.Headphones
    "html" -> SouIcons.Quiz
    else -> SouIcons.Link
}
