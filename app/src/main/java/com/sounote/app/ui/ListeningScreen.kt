@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sounote.app.ui

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.sounote.app.ErrorLog
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
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    var speed by remember { mutableFloatStateOf(1f) }
    var loop by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf(store.note(res)) }
    var exerciseOpen by remember { mutableStateOf(false) }
    var counted by remember { mutableStateOf(false) }

    DisposableEffect(res.url) {
        val mp = MediaPlayer()
        try {
            mp.setDataSource(store.file(res).absolutePath)
            mp.prepare()
            duration = mp.duration.coerceAtLeast(0)
            val saved = store.position(res)
            // Sona çok yakınsa baştan başla
            if (saved in 1 until duration - 3_000) mp.seekTo(saved)
            position = mp.currentPosition
            mp.setOnCompletionListener {
                playing = false
                position = duration
                store.savePosition(res, 0)
                vm.markActive()
            }
            player = mp
        } catch (e: Exception) {
            ErrorLog.record("Ses açılamadı", e)
            vm.showMessage("Ses açılamadı; dosya bozuk olabilir")
            store.file(res).delete()
            mp.release()
            onDismiss()
        }
        onDispose {
            player?.let { p ->
                runCatching { store.savePosition(res, p.currentPosition) }
                p.release()
            }
            player = null
            store.saveNote(res, note)
        }
    }

    // Çalarken konumu güncelle
    LaunchedEffect(playing) {
        while (playing) {
            player?.let { p ->
                position = p.currentPosition
                if (!counted && position > 120_000) {
                    counted = true
                    vm.markActive()
                }
            }
            delay(200)
        }
    }
    // Not yazılırken kısa aralıkla kaydet
    LaunchedEffect(note) {
        delay(800)
        store.saveNote(res, note)
    }

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
            playing = false
            store.savePosition(res, p.currentPosition)
        } else {
            if (p.currentPosition >= duration - 200) p.seekTo(0)
            p.start()
            playing = true
        }
    }

    fun seekBy(ms: Int) {
        val p = player ?: return
        val to = (p.currentPosition + ms).coerceIn(0, duration)
        p.seekTo(to)
        position = to
    }

    fun setSpeed(s: Float) {
        val p = player ?: return
        val wasPlaying = p.isPlaying
        runCatching { p.playbackParams = p.playbackParams.setSpeed(s) }
            .onSuccess { speed = s }
            .onFailure { vm.showMessage("Bu cihazda hız değiştirilemiyor") }
        // Hız ayarı duraklamışken çalmayı başlatabiliyor
        if (!wasPlaying && p.isPlaying) p.pause()
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
                    val shown = dragging?.toInt() ?: position
                    Slider(
                        value = shown.toFloat(),
                        valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                        onValueChange = { dragging = it },
                        onValueChangeFinished = {
                            dragging?.let { v ->
                                player?.seekTo(v.toInt())
                                position = v.toInt()
                            }
                            dragging = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(clock(shown), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.weight(1f))
                        Text(clock(duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(onClick = { seekBy(-5_000) }) { Text("−5 sn") }
                        FilledIconButton(onClick = { toggle() }, shape = CircleShape, modifier = Modifier.size(68.dp)) {
                            Icon(
                                if (playing) SouIcons.Pause else SouIcons.PlayArrow,
                                contentDescription = if (playing) "Duraklat" else "Çal",
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        OutlinedButton(onClick = { seekBy(5_000) }) { Text("+5 sn") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SPEEDS.forEach { s ->
                            FilterChip(selected = speed == s, onClick = { setSpeed(s) }, label = { Text(speedText(s)) })
                        }
                        FilterChip(
                            selected = loop,
                            onClick = {
                                loop = !loop
                                player?.isLooping = loop
                            },
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
                            val t = clock(position)
                            note = if (note.isBlank()) "[$t] " else note.trimEnd() + "\n[$t] "
                        },
                    ) { Text("Zamanı ekle (${clock(position)})") }
                }

                // Aynı günün ve konunun çalışma kâğıtları
                val related = remember(res.url, vm.catalog) { relatedMaterials(vm, topic, res) }
                if (related.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().card().padding(vertical = 8.dp)) {
                        Text(
                            "Bu dinlemeyle ilgili",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                                    local != null -> TextButton(onClick = {
                                        onDismiss()
                                        vm.open(local)
                                    }) { Text("Aç") }
                                    r.isPdf -> TextButton(onClick = { vm.downloadResource(c, t, r) }) { Text("İndir") }
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
