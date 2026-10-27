@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sounote.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sounote.app.LibraryViewModel
import com.sounote.app.data.Course
import com.sounote.app.data.Resource
import com.sounote.app.data.Topic
import kotlinx.coroutines.delay

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f)

/**
 * Listening pratiği: katalogdaki ses kaydını çalar (hız, 5 sn geri/ileri, tekrar, kaldığın yer), dinlerken not alınır;
 * aynı günün çalışma kâğıtları ve konudan asistan alıştırması buradan açılır.
 */
@Composable
fun ListeningScreen(vm: LibraryViewModel, course: Course, topic: Topic, res: Resource, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = vm.listeningStore
    val player = vm.listeningPlayer
    var dragging by remember { mutableStateOf<Float?>(null) }
    var note by remember { mutableStateOf(store.note(res)) }
    var exerciseOpen by remember { mutableStateOf(false) }

    LaunchedEffect(res.url) {
        if (!player.load(res)) {
            vm.showMessage("Ses açılamadı; dosya bozuk olabilir, yeniden indir")
            onDismiss()
        }
    }
    DisposableEffect(res.url) { onDispose { store.saveNote(res, note) } }
    PlayerTicker(vm)
    // Not yazılırken kısa aralıkla kaydet
    LaunchedEffect(note) {
        delay(800)
        store.saveNote(res, note)
    }

    if (exerciseOpen) ExerciseScreen(vm, course = course, initialTopic = topic) { exerciseOpen = false }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().souBackground(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(SouIcons.ArrowBack, contentDescription = "Kapat") }
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text("Listening", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(res.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(topic.name, if (res.week > 0) "Hafta ${res.week}" else null).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Oynatıcı
                Column(
                    Modifier.fillMaxWidth().card().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(SouIcons.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                    val shown = dragging?.toInt() ?: player.position
                    Slider(
                        value = shown.toFloat(),
                        valueRange = 0f..player.duration.coerceAtLeast(1).toFloat(),
                        onValueChange = { dragging = it },
                        onValueChangeFinished = {
                            dragging?.let { v -> player.seekTo(v.toInt()) }
                            dragging = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(clock(shown), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.weight(1f))
                        Text(clock(player.duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(onClick = { player.seekBy(-5_000) }) { Text("−5 sn") }
                        FilledIconButton(onClick = { player.toggle() }, shape = CircleShape, modifier = Modifier.size(68.dp)) {
                            Icon(
                                if (player.playing) SouIcons.Pause else SouIcons.PlayArrow,
                                contentDescription = if (player.playing) "Duraklat" else "Çal",
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        OutlinedButton(onClick = { player.seekBy(5_000) }) { Text("+5 sn") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SPEEDS.forEach { s ->
                            FilterChip(
                                selected = player.speed == s,
                                onClick = { if (!player.changeSpeed(s)) vm.showMessage("Bu cihazda hız değiştirilemiyor") },
                                label = { Text(speedText(s)) },
                            )
                        }
                        FilterChip(
                            selected = player.loop,
                            onClick = { player.toggleLoop() },
                            label = { Text("Tekrarla") },
                            leadingIcon = { Icon(SouIcons.Replay, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }

                // Not
                Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Dinlerken not al", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = { Text("Duyduğun anahtar kelimeler, cevaplar…") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    )
                    TextButton(
                        onClick = {
                            val t = clock(player.position)
                            note = if (note.isBlank()) "[$t] " else note.trimEnd() + "\n[$t] "
                        },
                    ) { Text("Zamanı ekle (${clock(player.position)})") }
                }

                // Aynı günün ve konunun çalışma kâğıtları: dinlerken açılır, ses belge ekranında küçük çubukta sürer
                val related = remember(res.url, vm.catalog) { relatedMaterials(vm, topic, res) }
                if (related.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().card().padding(vertical = 8.dp)) {
                        Text(
                            "Bu dinlemeyle ilgili",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        Text(
                            "PDF'i açınca dinleme sürer; kâğıdın üstüne yazarken üstteki çubuktan durdurup geri sarabilirsin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp),
                        )
                        related.forEach { (c, t, r) ->
                            val local = vm.downloadedDoc(c, r)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                                Icon(
                                    if (r.isPdf) SouIcons.PictureAsPdf else SouIcons.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(r.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                                when {
                                    vm.downloading == r.url -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                    local != null || r.isPdf -> TextButton(onClick = {
                                        store.saveNote(res, note)
                                        vm.listenAlongside(c, t, r)
                                    }) { Text(if (local != null) "Dinlerken aç" else "İndir ve aç") }
                                    else -> TextButton(onClick = {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url))) }
                                    }) { Text("Drive") }
                                }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().card().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    val key = vm.doneKey(res)
                    Checkbox(checked = key in vm.doneItems, onCheckedChange = { vm.toggleDone(key) })
                    Text("Dinledim", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        if (!vm.assistantConfig.ready) vm.showMessage("Önce asistanı kur: Ayarlar → Asistan") else exerciseOpen = true
                    }) { Text("Asistanla alıştırma") }
                }
            }
        }
    }
}

/** Çalarken oynatıcının konumunu ekrana yansıtır; iki dakikadan uzun dinleme çalışma serisine sayılır. */
@Composable
private fun PlayerTicker(vm: LibraryViewModel) {
    val player = vm.listeningPlayer
    LaunchedEffect(player.playing) {
        while (player.playing) {
            player.tick()
            if (player.position > 120_000) vm.markActive()
            delay(200)
        }
    }
}

/** Belge ekranında, çalışma kâğıdının üstünde: dinleme sürerken küçük oynatıcı çubuğu. */
@Composable
fun ListeningMiniBar(vm: LibraryViewModel) {
    val player = vm.listeningPlayer
    val res = player.res ?: return
    PlayerTicker(vm)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Icon(SouIcons.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp).size(20.dp))
        Text(res.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(
            "${clock(player.position)} / ${clock(player.duration)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        TextButton(onClick = { player.seekBy(-5_000) }) { Text("−5") }
        IconButton(onClick = { player.toggle() }) {
            Icon(if (player.playing) SouIcons.Pause else SouIcons.PlayArrow, contentDescription = if (player.playing) "Duraklat" else "Çal")
        }
        TextButton(onClick = { player.seekBy(5_000) }) { Text("+5") }
        TextButton(onClick = {
            val i = SPEEDS.indexOf(player.speed).let { if (it < 0) 1 else it }
            if (!player.changeSpeed(SPEEDS[(i + 1) % SPEEDS.size])) vm.showMessage("Bu cihazda hız değiştirilemiyor")
        }) { Text(speedText(player.speed)) }
        IconButton(onClick = { vm.closeListeningMini() }) { Icon(SouIcons.Close, contentDescription = "Dinlemeyi kapat") }
    }
}

/** Aynı hafta ve gündeki (yoksa aynı konudaki) ses dışı kaynaklar: çalışma kâğıdı, sunum. */
private fun relatedMaterials(vm: LibraryViewModel, topic: Topic, res: Resource): List<Triple<Course, Topic, Resource>> {
    val all = vm.catalog.courses.flatMap { c -> c.topics.flatMap { t -> t.resources.map { Triple(c, t, it) } } }
    val sameDay = if (res.week > 0 && res.day in 1..6) {
        all.filter { it.third.week == res.week && it.third.day == res.day && it.third.kind != "audio" }
    } else emptyList()
    val sameTopic = all.filter { it.second.id == topic.id && it.third.kind != "audio" }
    return (sameDay + sameTopic).distinctBy { it.third.url }.take(8)
}

private fun clock(ms: Int): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

private fun speedText(s: Float) = when (s) {
    1f -> "1×"
    0.75f -> "0,75×"
    1.25f -> "1,25×"
    else -> "1,5×"
}
