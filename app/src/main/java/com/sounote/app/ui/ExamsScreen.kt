@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sounote.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.sounote.app.ExamReminders
import com.sounote.app.LibraryViewModel
import com.sounote.app.data.Exam
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

private val TR = Locale.forLanguageTag("tr")

/** Kalan gün yazısı ve rengi. */
internal fun countdownText(time: Long): String {
    val d = ExamReminders.daysUntil(time)
    return when {
        d < 0 -> "Geçti"
        d == 0 -> "Bugün"
        d == 1 -> "Yarın"
        else -> "$d gün"
    }
}

@Composable
internal fun countdownColor(time: Long): Color {
    val d = ExamReminders.daysUntil(time)
    return when {
        d < 0 -> MaterialTheme.colorScheme.outline
        d <= 3 -> Color(0xFFD64545)
        d <= 7 -> Color(0xFFE0893A)
        else -> MaterialTheme.colorScheme.primary
    }
}

/**
 * Akademik planlayıcı: ortak katalogdaki sınavlar + kendi eklediklerim. Liste, hafta, ay ve zaman çizelgesi
 * görünümleri; türe/derse göre filtre; ödev tamamlama; mevcut hatırlatıcılarla uyumlu.
 */
@Composable
fun ExamsScreen(vm: LibraryViewModel) {
    var editing by remember { mutableStateOf<Exam?>(null) }
    var deleting by remember { mutableStateOf<Exam?>(null) }
    var gradesOpen by remember { mutableStateOf(false) }
    var kindFilter by rememberSaveable { mutableStateOf("") }
    var courseFilter by rememberSaveable { mutableStateOf("") }
    var showDone by rememberSaveable { mutableStateOf(true) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.showMessage("Bildirim izni verilmedi; hatırlatıcılar görünmez")
    }
    fun enableReminders(on: Boolean) {
        vm.updateReminders(on)
        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val all = vm.allExams
    val courses = all.map { it.course }.filter { it.isNotBlank() }.distinct()
    val filtered = all.filter { e ->
        (kindFilter.isEmpty() || e.kind == kindFilter) &&
            (courseFilter.isEmpty() || e.course == courseFilter) &&
            (showDone || !vm.isDone(e))
    }
    val newExam = { Exam("m:" + UUID.randomUUID(), courseFilter, "", defaultExamTime(), kind = kindFilter.ifEmpty { "exam" }) }
    val onItem = { e: Exam -> if (!e.global) editing = e }
    val onLong = { e: Exam -> if (!e.global) deleting = e }
    val motion = LocalMotionLevel.current

    Scaffold(
        topBar = {
            SouTopBar(
                "Planlayıcı",
                subtitle = "Sınavlar, ödevler ve sunumlar",
                actions = {
                    IconButton(onClick = {
                        vm.openAssistant("Yaklaşan sınav ve ödevlerime göre bana önümüzdeki 7 gün için günlük, gerçekçi bir çalışma planı öner.")
                    }) { Text("🤖", fontSize = 20.sp) }
                    IconButton(onClick = { vm.refreshCatalog() }) { Icon(Icons.Default.Refresh, contentDescription = "Yenile") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = newExam() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Ekle") },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (vm.catalogLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(Modifier.widthIn(max = 1100.dp).fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    LibraryViewModel.PlannerView.entries.forEach { v ->
                        FilterChip(
                            selected = vm.plannerView == v,
                            onClick = { vm.updatePlannerView(v) },
                            label = { Text(v.label, maxLines = 1) },
                            leadingIcon = {
                                Text(
                                    when (v) {
                                        LibraryViewModel.PlannerView.LIST -> "☰"
                                        LibraryViewModel.PlannerView.WEEK -> "🗓️"
                                        LibraryViewModel.PlannerView.MONTH -> "📆"
                                        LibraryViewModel.PlannerView.TIMELINE -> "⏳"
                                    },
                                )
                            },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("" to "Tümü", "exam" to "📅 Sınav", "task" to "📝 Ödev", "presentation" to "🎤 Sunum").forEach { (k, label) ->
                        FilterChip(selected = kindFilter == k, onClick = { kindFilter = k }, label = { Text(label, maxLines = 1) })
                    }
                    FilterChip(selected = !showDone, onClick = { showDone = !showDone }, label = { Text("Tamamlananları gizle", maxLines = 1) })
                    courses.forEach { c ->
                        FilterChip(
                            selected = courseFilter == c,
                            onClick = { courseFilter = if (courseFilter == c) "" else c },
                            label = { Text(c, maxLines = 1) },
                            leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(vm.courseColor(c)))) },
                        )
                    }
                }
            }
            AnimatedContent(
                targetState = vm.plannerView,
                transitionSpec = Motion.fade<LibraryViewModel.PlannerView>(motion),
                label = "planner",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { view ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    when (view) {
                        LibraryViewModel.PlannerView.LIST -> PlannerList(
                            vm, filtered,
                            header = {
                                PlannerTools(vm, onGrades = { gradesOpen = true }, onReminders = ::enableReminders)
                            },
                            onItem = onItem, onLong = onLong,
                        )
                        LibraryViewModel.PlannerView.WEEK -> PlannerWeek(vm, filtered, onItem, onLong)
                        LibraryViewModel.PlannerView.MONTH -> PlannerMonth(vm, filtered, onItem, onLong)
                        LibraryViewModel.PlannerView.TIMELINE -> PlannerTimeline(vm, filtered, onItem, onLong)
                    }
                }
            }
        }
    }

    editing?.let { e ->
        ExamDialog(
            exam = e,
            courseNames = (vm.catalog.courses.map { it.name } + vm.myExams.map { it.course }).filter { it.isNotBlank() }.distinct(),
            isNew = vm.myExams.none { it.id == e.id },
            onSave = {
                vm.saveExam(it)
                editing = null
                if (vm.remindersOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onDelete = {
                vm.deleteExam(e)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
    if (gradesOpen) GradesDialog(vm) { gradesOpen = false }
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Silinsin mi?") },
            text = { Text("\"${e.title.ifBlank { e.course }}\" takvimden kaldırılacak.") },
            confirmButton = { TextButton(onClick = { vm.deleteExam(e); deleting = null }) { Text("Sil") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } },
        )
    }
}

/** Not ortalaması, kur ve hatırlatıcı kartları (liste görünümünün başında). */
@Composable
private fun PlannerTools(vm: LibraryViewModel, onGrades: () -> Unit, onReminders: (Boolean) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val g = vm.grades
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .widthIn(min = 280.dp, max = 520.dp)
                .pressScale(0.98f)
                .card()
                .clickable(onClick = onGrades)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text("📊", fontSize = 24.sp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Not ortalamam", style = MaterialTheme.typography.bodyLarge)
                val avg = g.average
                val needed = g.needed
                Text(
                    when {
                        g.items.isEmpty() -> "Sınav ağırlıklarını ve puanlarını gir, ortalamanı gör"
                        avg == null -> "Henüz puan girilmedi"
                        needed != null && needed in 0.0..100.0 ->
                            "Ortalama ${"%.1f".format(avg)} · geçmek için kalanlardan ${"%.1f".format(needed)}"
                        else -> "Ortalama ${"%.1f".format(avg)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.widthIn(min = 280.dp, max = 520.dp).card().padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("🔔", fontSize = 22.sp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Hatırlatıcılar", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "3 gün ve 1 gün önce 19:00'da, 2 saat önce bildirim",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = vm.remindersOn, onCheckedChange = onReminders)
        }
        if (vm.catalog.levels.isNotEmpty()) {
            Column(Modifier.widthIn(min = 280.dp, max = 520.dp).card().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("Kurun", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Sadece kendi kurunun sınav ve ödevleri görünür",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()),
                ) {
                    FilterChip(selected = vm.userLevel.isEmpty(), onClick = { vm.updateUserLevel("") }, label = { Text("Hepsi") })
                    vm.catalog.levels.forEach { l ->
                        FilterChip(selected = vm.userLevel == l, onClick = { vm.updateUserLevel(l) }, label = { Text(l) })
                    }
                }
            }
        }
    }
}

private fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Haftanın pazartesisi (00:00). */
private fun mondayOf(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(t)
    val dow = get(Calendar.DAY_OF_WEEK)
    add(Calendar.DAY_OF_YEAR, -((dow + 5) % 7))
}.timeInMillis

private fun addDays(t: Long, days: Int): Long = Calendar.getInstance().apply {
    timeInMillis = t
    add(Calendar.DAY_OF_YEAR, days)
}.timeInMillis

/** Liste: bu hafta, gelecek hafta, daha sonra; geçmiş açılır kapanır. */
@Composable
private fun PlannerList(
    vm: LibraryViewModel,
    exams: List<Exam>,
    header: @Composable () -> Unit,
    onItem: (Exam) -> Unit,
    onLong: (Exam) -> Unit,
) {
    var showPast by rememberSaveable { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val cutoff = now - 3 * 60 * 60 * 1000L
    val upcoming = exams.filter { it.time > cutoff }
    val past = exams.filter { it.time <= cutoff }.reversed()
    val nextMonday = addDays(mondayOf(now), 7)
    val groups = listOf(
        "Bu hafta" to upcoming.filter { it.time < nextMonday },
        "Gelecek hafta" to upcoming.filter { it.time >= nextMonday && it.time < addDays(nextMonday, 7) },
        "Daha sonra" to upcoming.filter { it.time >= addDays(nextMonday, 7) },
    ).filter { it.second.isNotEmpty() }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
        modifier = Modifier.widthIn(max = 1100.dp).fillMaxSize(),
    ) {
        item { header() }
        if (upcoming.isEmpty()) {
            item {
                EmptyState(
                    "🗓️", "Yaklaşan bir şey yok",
                    "\"Ekle\" ile sınav, ödev ya da sunum ekleyebilirsin; ortak takvim yüklenince burada görünür.",
                )
            }
        }
        groups.forEach { (label, list) ->
            item(key = "h:$label") { SectionHeader(label, subtitle = "${list.size} kayıt", modifier = Modifier.padding(top = 6.dp)) }
            items(list, key = { it.id }) { exam ->
                Box(Modifier.animateItem(), propagateMinConstraints = true) {
                    ExamCard(exam, vm, onClick = { onItem(exam) }, onLongClick = { onLong(exam) })
                }
            }
        }
        if (past.isNotEmpty()) {
            item {
                TextButton(onClick = { showPast = !showPast }) {
                    Text(if (showPast) "Geçmişi gizle" else "Geçmiş (${past.size})")
                }
            }
            if (showPast) {
                items(past, key = { "p" + it.id }) { exam ->
                    Box(Modifier.animateItem(), propagateMinConstraints = true) {
                        ExamCard(exam, vm, onClick = { onItem(exam) }, onLongClick = { onLong(exam) })
                    }
                }
            }
        }
        vm.catalogError?.let { err ->
            item { Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** Takvim görünümlerindeki küçük kayıt: renk şeridi, tür, başlık, saat; ödevlerde onay kutusu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MiniExam(vm: LibraryViewModel, e: Exam, onItem: (Exam) -> Unit, onLong: (Exam) -> Unit, showDate: Boolean = false) {
    val done = vm.isDone(e)
    val color = Color(vm.courseColor(e.course))
    val time = remember(e.time, e.slot, showDate) {
        (if (showDate) SimpleDateFormat("d MMM EEE", TR).format(Date(e.time)) + " · " else "") +
            e.slot.ifBlank { SimpleDateFormat("HH:mm", TR).format(Date(e.time)) }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .combinedClickable(onClick = { onItem(e) }, onLongClick = { onLong(e) })
            .graphicsLayer { alpha = if (done) 0.55f else 1f }
            .padding(end = 6.dp),
    ) {
        Box(Modifier.width(4.dp).height(40.dp).background(color))
        if (e.kind != "exam") {
            Checkbox(checked = done, onCheckedChange = { vm.toggleExamDone(e) }, modifier = Modifier.size(36.dp))
        } else {
            Text(e.kindEmoji, modifier = Modifier.padding(horizontal = 8.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(
                e.title.ifBlank { e.course },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Haftalık takvim: geniş ekranda 7 sütun, dar ekranda gün gün liste. */
@Composable
private fun PlannerWeek(vm: LibraryViewModel, exams: List<Exam>, onItem: (Exam) -> Unit, onLong: (Exam) -> Unit) {
    var offset by rememberSaveable { mutableStateOf(0) }
    val monday = addDays(mondayOf(System.currentTimeMillis()), offset * 7)
    val today = startOfDay(System.currentTimeMillis())
    val fmt = remember { SimpleDateFormat("d MMM", TR) }
    val dayFmt = remember { SimpleDateFormat("EEE", TR) }
    val days = (0 until 7).map { addDays(monday, it) }
    BoxWithConstraints(Modifier.widthIn(max = 1100.dp).fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            PeriodHeader(
                "${fmt.format(Date(monday))} – ${fmt.format(Date(days.last()))}" + if (offset == 0) " · Bu hafta" else "",
                onPrev = { offset-- }, onNext = { offset++ }, onToday = if (offset != 0) ({ offset = 0 }) else null,
            )
            if (wide) {
                Row(Modifier.fillMaxSize().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    days.forEach { d ->
                        val list = exams.filter { startOfDay(it.time) == d }
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .card(16.dp)
                                .then(if (d == today) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) else Modifier)
                                .verticalScroll(rememberScrollState())
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                dayFmt.format(Date(d)),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(SimpleDateFormat("d", TR).format(Date(d)), style = MaterialTheme.typography.titleLarge)
                            list.forEach { MiniExam(vm, it, onItem, onLong) }
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(days, key = { it }) { d ->
                        val list = exams.filter { startOfDay(it.time) == d }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .card(16.dp)
                                .then(if (d == today) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) else Modifier)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                SimpleDateFormat("EEEE, d MMMM", TR).format(Date(d)),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (list.isEmpty()) {
                                Text("Boş", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            list.forEach { MiniExam(vm, it, onItem, onLong) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodHeader(title: String, onPrev: () -> Unit, onNext: () -> Unit, onToday: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Önceki") }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        if (onToday != null) TextButton(onClick = onToday) { Text("Bugün") }
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Sonraki") }
    }
}

/** Aylık takvim: gün kutularında renkli noktalar; seçilen günün kayıtları altta. */
@Composable
private fun PlannerMonth(vm: LibraryViewModel, exams: List<Exam>, onItem: (Exam) -> Unit, onLong: (Exam) -> Unit) {
    var offset by rememberSaveable { mutableStateOf(0) }
    val today = startOfDay(System.currentTimeMillis())
    var selected by rememberSaveable { mutableStateOf(today) }
    val first = Calendar.getInstance().apply {
        timeInMillis = today
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, offset)
    }
    val month = first.get(Calendar.MONTH)
    val gridStart = mondayOf(first.timeInMillis)
    val cells = (0 until 42).map { addDays(gridStart, it) }
    val byDay = exams.groupBy { startOfDay(it.time) }
    val title = remember(first.timeInMillis) { SimpleDateFormat("LLLL yyyy", TR).format(first.time).replaceFirstChar { it.titlecase(TR) } }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
        modifier = Modifier.widthIn(max = 900.dp).fillMaxSize(),
    ) {
        item {
            PeriodHeader(title, onPrev = { offset-- }, onNext = { offset++ }, onToday = if (offset != 0) ({ offset = 0; selected = today }) else null)
        }
        item {
            Column(Modifier.fillMaxWidth().card().padding(8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz").forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                        )
                    }
                }
                cells.chunked(7).filter { week -> week.any { Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.MONTH) == month } }.forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { d ->
                            val inMonth = Calendar.getInstance().apply { timeInMillis = d }.get(Calendar.MONTH) == month
                            val list = byDay[d].orEmpty()
                            val isSel = d == selected
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when {
                                            isSel -> MaterialTheme.colorScheme.primaryContainer
                                            d == today -> MaterialTheme.colorScheme.surfaceContainerHigh
                                            else -> Color.Transparent
                                        },
                                    )
                                    .clickable { selected = d }
                                    .padding(top = 6.dp),
                            ) {
                                Text(
                                    SimpleDateFormat("d", TR).format(Date(d)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (d == today) FontWeight.Bold else null,
                                    color = when {
                                        isSel -> MaterialTheme.colorScheme.onPrimaryContainer
                                        !inMonth -> MaterialTheme.colorScheme.outline
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    list.take(3).forEach { e ->
                                        Box(Modifier.size(6.dp).clip(CircleShape).background(Color(vm.courseColor(e.course)).copy(alpha = if (vm.isDone(e)) 0.4f else 1f)))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val list = byDay[selected].orEmpty()
        item {
            SectionHeader(
                SimpleDateFormat("d MMMM EEEE", TR).format(Date(selected)),
                subtitle = if (list.isEmpty()) "Bu gün için kayıt yok" else "${list.size} kayıt",
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        items(list, key = { "m" + it.id }) { e ->
            Box(Modifier.animateItem(), propagateMinConstraints = true) {
                ExamCard(e, vm, onClick = { onItem(e) }, onLongClick = { onLong(e) })
            }
        }
    }
}

/** Zaman çizelgesi: dikey çizgi üzerinde tarih düğümleri; geçmiş soluk, "bugün" işaretli. */
@Composable
private fun PlannerTimeline(vm: LibraryViewModel, exams: List<Exam>, onItem: (Exam) -> Unit, onLong: (Exam) -> Unit) {
    val now = System.currentTimeMillis()
    val today = startOfDay(now)
    val shown = exams.filter { it.time >= addDays(today, -14) }
    val days = shown.groupBy { startOfDay(it.time) }.toSortedMap()
    val listState = rememberLazyListState()
    val firstUpcoming = days.keys.indexOfFirst { it >= today }
    LaunchedEffect(Unit) { if (firstUpcoming > 0) listState.scrollToItem(firstUpcoming) }
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    if (days.isEmpty()) {
        EmptyState("⏳", "Zaman çizelgesi boş", "Son iki haftadan bu yana ve ileride kayıt yok.")
        return
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
        modifier = Modifier.widthIn(max = 900.dp).fillMaxSize(),
    ) {
        days.entries.forEach { (day, list) ->
            item(key = day) {
                val past = day < today
                val isToday = day == today
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).graphicsLayer { alpha = if (past) 0.55f else 1f }) {
                    Box(Modifier.width(28.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.width(2.dp).fillMaxHeight().background(lineColor))
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .size(if (isToday) 16.dp else 12.dp)
                                .clip(CircleShape)
                                .background(if (isToday || !past) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        )
                    }
                    Column(Modifier.weight(1f).padding(start = 8.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                SimpleDateFormat("d MMMM EEEE", TR).format(Date(day)),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(countdownText(day + 12 * 3_600_000L), style = MaterialTheme.typography.labelMedium, color = countdownColor(day + 12 * 3_600_000L))
                        }
                        list.forEach { MiniExam(vm, it, onItem, onLong) }
                    }
                }
            }
        }
    }
}

private fun defaultExamTime(): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, 7)
    set(Calendar.HOUR_OF_DAY, 10)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ExamCard(exam: Exam, vm: LibraryViewModel, onClick: () -> Unit, onLongClick: () -> Unit) {
    val accent = if (exam.course.isBlank()) MaterialTheme.colorScheme.primary else Color(vm.courseColor(exam.course))
    val done = vm.isDone(exam)
    val day = remember(exam.time) { SimpleDateFormat("d", TR).format(Date(exam.time)) }
    val month = remember(exam.time) { SimpleDateFormat("MMM", TR).format(Date(exam.time)) }
    val detail = remember(exam.time, exam.slot) {
        SimpleDateFormat("EEEE", TR).format(Date(exam.time)) + " " +
            exam.slot.ifBlank { SimpleDateFormat("HH:mm", TR).format(Date(exam.time)) }
    }
    // Dar ekranda (telefon) geri sayım sağda yer kaplamasın, başlığın altına geçer.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val compact = maxWidth < 440.dp
    val c = countdownColor(exam.time)
    // Sınav 2 gün içindeyse geri sayım hafifçe nabız gibi atar.
    val urgent = !done && LocalMotionLevel.current == MotionLevel.FULL && exam.time - System.currentTimeMillis() in 0..(2 * 86_400_000L)
    val countdown = @Composable {
        val pulse = if (urgent) {
            rememberInfiniteTransition(label = "urgent").animateFloat(
                1f, 1.08f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse",
            ).value
        } else 1f
        Text(
            if (done) "Tamam ✓" else countdownText(exam.time),
            color = if (done) LocalSouColors.current.success else c,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier
                .graphicsLayer { scaleX = pulse; scaleY = pulse }
                .clip(RoundedCornerShape(50))
                .background(c.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .card()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .graphicsLayer { alpha = if (done) 0.65f else 1f }
            .padding(if (compact) 12.dp else 14.dp),
    ) {
        if (exam.kind != "exam") {
            Checkbox(checked = done, onCheckedChange = {
                vm.toggleExamDone(exam)
                if (!done) vm.showMessage("\"${exam.title.ifBlank { exam.course }}\" tamamlandı ✓")
            })
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(if (compact) 54.dp else 64.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.15f)).padding(vertical = 8.dp),
        ) {
            Text(day, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = accent)
            Text(month, style = MaterialTheme.typography.labelMedium, color = accent)
        }
        Column(Modifier.weight(1f).padding(start = if (compact) 12.dp else 14.dp, end = if (compact) 0.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(exam.kindEmoji + " ")
                Text(
                    exam.title.ifBlank { exam.course },
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    maxLines = if (compact) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (exam.priority >= 2 && !exam.global) {
                    Text(
                        "  Öncelikli",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalSouColors.current.danger,
                    )
                }
                if (exam.global) Text("  🌐", style = MaterialTheme.typography.labelSmall)
                if (exam.levels.isNotEmpty() && exam.levels.size < 4) {
                    Text("  " + exam.levels.joinToString("·"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                listOf(exam.course.takeIf { exam.title.isNotBlank() }, detail, exam.place.ifBlank { null }).filterNotNull().joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (exam.notes.isNotBlank()) {
                Text(exam.notes, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val topics = exam.topics.mapNotNull { vm.catalog.topic(it) }
            if (topics.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    topics.forEach { (_, t) ->
                        AssistChip(
                            onClick = {
                                vm.focusTopic = t.id
                                vm.tab = LibraryViewModel.Tab.COURSES
                            },
                            label = { Text(t.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.widthIn(max = 240.dp),
                        )
                    }
                }
            }
            if (compact) Box(Modifier.padding(top = 4.dp)) { countdown() }
        }
        if (!compact) countdown()
    }
    }
}

@Composable
private fun ExamDialog(
    exam: Exam,
    courseNames: List<String>,
    isNew: Boolean,
    onSave: (Exam) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var course by remember { mutableStateOf(exam.course) }
    var title by remember { mutableStateOf(exam.title) }
    var place by remember { mutableStateOf(exam.place) }
    var notes by remember { mutableStateOf(exam.notes) }
    var time by remember { mutableStateOf(exam.time) }
    var kind by remember { mutableStateOf(exam.kind) }
    var priority by remember { mutableStateOf(exam.priority) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val cal = Calendar.getInstance().apply { timeInMillis = time }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Ekle" else "Düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("exam" to "📅 Sınav", "task" to "📝 Ödev", "presentation" to "🎤 Sunum").forEach { (k, label) ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(label) })
                    }
                }
                OutlinedTextField(value = course, onValueChange = { course = it }, singleLine = true, label = { Text("Ders") })
                if (courseNames.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        courseNames.forEach { n -> FilterChip(selected = course == n, onClick = { course = n }, label = { Text(n) }) }
                    }
                }
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, singleLine = true,
                    label = { Text("Başlık") },
                    placeholder = { Text(if (kind == "exam") "ör. Quiz, Progress Test" else "ör. Opinion essay teslimi") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate = true }) { Text("📅 " + SimpleDateFormat("d MMMM yyyy", TR).format(Date(time))) }
                    OutlinedButton(onClick = { pickTime = true }) { Text("🕘 " + SimpleDateFormat("HH:mm", TR).format(Date(time))) }
                }
                OutlinedTextField(value = place, onValueChange = { place = it }, singleLine = true, label = { Text("Yer (isteğe bağlı)") })
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Not / konular (isteğe bağlı)") })
                Text("Öncelik", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Düşük", 1 to "Normal", 2 to "Yüksek").forEach { (p, label) ->
                        FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = course.isNotBlank() || title.isNotBlank(),
                onClick = {
                    onSave(
                        exam.copy(
                            course = course.trim(), title = title.trim(), time = time, place = place.trim(), notes = notes.trim(),
                            kind = kind, priority = priority,
                        ),
                    )
                },
            ) { Text("Kaydet") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Sil", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Vazgeç") }
            }
        },
    )

    if (pickDate) {
        // DatePicker UTC gece yarısı ile çalışır: yerel tarihe çevrilir.
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = utc.timeInMillis)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
                        time = Calendar.getInstance().apply {
                            timeInMillis = time
                            set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
                        }.timeInMillis
                    }
                    pickDate = false
                }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Vazgeç") } },
        ) {
            DatePicker(state = state)
        }
    }
    if (pickTime) {
        val state = rememberTimePickerState(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Saat") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    time = Calendar.getInstance().apply {
                        timeInMillis = time
                        set(Calendar.HOUR_OF_DAY, state.hour)
                        set(Calendar.MINUTE, state.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    pickTime = false
                }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Vazgeç") } },
        )
    }
}
