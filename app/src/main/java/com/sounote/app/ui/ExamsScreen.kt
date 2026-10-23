@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sounote.app.ui

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

/** Sınav takvimi: ortak katalogdaki sınavlar + kendi eklediklerim, geri sayım ve hatırlatıcı. */
@Composable
fun ExamsScreen(vm: LibraryViewModel) {
    var editing by remember { mutableStateOf<Exam?>(null) }
    var deleting by remember { mutableStateOf<Exam?>(null) }
    var showPast by remember { mutableStateOf(false) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.showMessage("Bildirim izni verilmedi; hatırlatıcılar görünmez")
    }
    fun enableReminders(on: Boolean) {
        vm.updateReminders(on)
        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val now = System.currentTimeMillis()
    val exams = vm.allExams
    val upcoming = exams.filter { it.time > now - 3 * 60 * 60 * 1000L }
    val past = exams.filter { it.time <= now - 3 * 60 * 60 * 1000L }.reversed()
    val newExam = { Exam("m:" + UUID.randomUUID(), "", "", defaultExamTime()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sınavlar") },
                actions = {
                    IconButton(onClick = { vm.refreshCatalog() }) { Icon(Icons.Default.Refresh, contentDescription = "Yenile") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = newExam() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Sınav ekle") },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (vm.catalogLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
                modifier = Modifier.widthIn(max = 860.dp).fillMaxSize(),
            ) {
                if (vm.catalog.levels.isNotEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text("Kurun", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Sadece kendi kurunun sınav ve ödevleri görünür",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                            ) {
                                FilterChip(selected = vm.userLevel.isEmpty(), onClick = { vm.updateUserLevel("") }, label = { Text("Hepsi") })
                                vm.catalog.levels.forEach { l ->
                                    FilterChip(selected = vm.userLevel == l, onClick = { vm.updateUserLevel(l) }, label = { Text(l) })
                                }
                            }
                        }
                    }
                }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text("🔔", fontSize = 22.sp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("Hatırlatıcılar", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "3 gün ve 1 gün önce akşam 19:00'da, sınavdan 2 saat önce bildirim",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = vm.remindersOn, onCheckedChange = ::enableReminders)
                    }
                }
                if (upcoming.isEmpty()) {
                    item {
                        Text(
                            "Yaklaşan sınav yok. \"Sınav ekle\" ile kendin ekleyebilirsin; ortak takvim yüklenince burada görünür.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(upcoming, key = { it.id }) { exam ->
                    Box(Modifier.animateItem(), propagateMinConstraints = true) {
                        ExamCard(
                            exam, vm,
                            onClick = { if (!exam.global) editing = exam },
                            onLongClick = { if (!exam.global) deleting = exam },
                        )
                    }
                }
                if (past.isNotEmpty()) {
                    item {
                        TextButton(onClick = { showPast = !showPast }) {
                            Text(if (showPast) "Geçmiş sınavları gizle" else "Geçmiş sınavlar (${past.size})")
                        }
                    }
                    if (showPast) {
                        items(past, key = { "p" + it.id }) { exam ->
                            Box(Modifier.animateItem(), propagateMinConstraints = true) {
                                ExamCard(exam, vm, onClick = { if (!exam.global) editing = exam }, onLongClick = { if (!exam.global) deleting = exam })
                            }
                        }
                    }
                }
                vm.catalogError?.let { err ->
                    item {
                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Sınav silinsin mi?") },
            text = { Text("\"${e.title.ifBlank { e.course }}\" takvimden kaldırılacak.") },
            confirmButton = { TextButton(onClick = { vm.deleteExam(e); deleting = null }) { Text("Sil") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } },
        )
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
    val course = vm.catalog.courses.find { it.name.equals(exam.course, ignoreCase = true) }
    val accent = course?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary
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
    val urgent = exam.time - System.currentTimeMillis() in 0..(2 * 86_400_000L)
    val countdown = @Composable {
        val pulse = if (urgent) {
            rememberInfiniteTransition(label = "urgent").animateFloat(
                1f, 1.08f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse",
            ).value
        } else 1f
        Text(
            countdownText(exam.time),
            color = c,
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
        modifier = Modifier.fillMaxWidth().card().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(if (compact) 12.dp else 14.dp),
    ) {
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
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (compact) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
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
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val cal = Calendar.getInstance().apply { timeInMillis = time }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Sınav ekle" else "Sınavı düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = course, onValueChange = { course = it }, singleLine = true, label = { Text("Ders") })
                if (courseNames.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        courseNames.forEach { n -> FilterChip(selected = course == n, onClick = { course = n }, label = { Text(n) }) }
                    }
                }
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, singleLine = true,
                    label = { Text("Sınav") }, placeholder = { Text("ör. Vize, Final, Quiz") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate = true }) { Text("📅 " + SimpleDateFormat("d MMMM yyyy", TR).format(Date(time))) }
                    OutlinedButton(onClick = { pickTime = true }) { Text("🕘 " + SimpleDateFormat("HH:mm", TR).format(Date(time))) }
                }
                OutlinedTextField(value = place, onValueChange = { place = it }, singleLine = true, label = { Text("Yer (isteğe bağlı)") })
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Not / konular (isteğe bağlı)") })
            }
        },
        confirmButton = {
            TextButton(
                enabled = course.isNotBlank() || title.isNotBlank(),
                onClick = { onSave(exam.copy(course = course.trim(), title = title.trim(), time = time, place = place.trim(), notes = notes.trim())) },
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
