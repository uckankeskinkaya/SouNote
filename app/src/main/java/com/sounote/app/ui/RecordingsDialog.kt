package com.sounote.app.ui

import android.content.Intent
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.sounote.app.R
import com.sounote.app.data.Recording
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Belgenin ses kayıtları: dinle (ileri/geri sar), paylaş, sil. */
@Composable
fun RecordingsDialog(recordings: List<Recording>, onDelete: (Recording) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf<File?>(null) }
    var paused by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(0f) }
    var deleting by remember { mutableStateOf<Recording?>(null) }
    val dateFormat = remember { SimpleDateFormat("d MMMM, HH:mm", Locale.forLanguageTag("tr")) }

    fun stop() {
        player?.release()
        player = null
        playing = null
        paused = false
        position = 0f
    }

    fun play(rec: Recording) {
        stop()
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(rec.file.path)
                prepare()
                setOnCompletionListener { stop() }
                start()
            }
            playing = rec.file
        }
    }

    DisposableEffect(Unit) { onDispose { player?.release() } }
    // Konum çubuğunu güncelle
    LaunchedEffect(playing) {
        while (playing != null) {
            player?.let { p -> runCatching { position = p.currentPosition.toFloat() } }
            delay(300)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ses kayıtları") },
        text = {
            if (recordings.isEmpty()) {
                Text("Henüz kayıt yok. Üst çubuktaki mikrofonla ders kaydı başlatabilirsin.")
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(recordings, key = { it.file.path }) { rec ->
                        val isPlaying = playing == rec.file
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    val p = player
                                    when {
                                        !isPlaying -> play(rec)
                                        p != null && p.isPlaying -> { p.pause(); paused = true }
                                        p != null -> { p.start(); paused = false }
                                    }
                                }) {
                                    if (isPlaying && !paused) {
                                        Icon(painterResource(R.drawable.ic_pause), contentDescription = "Duraklat")
                                    } else {
                                        Icon(SouIcons.PlayArrow, contentDescription = "Dinle")
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(dateFormat.format(Date(rec.createdAt)), style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        formatDuration(rec.durationMs),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", rec.file)
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "audio/mp4"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(send, "Ses kaydını paylaş"))
                                }) {
                                    Icon(SouIcons.Share, contentDescription = "Paylaş")
                                }
                                IconButton(onClick = { deleting = rec }) {
                                    Icon(SouIcons.Delete, contentDescription = "Sil")
                                }
                            }
                            if (isPlaying && rec.durationMs > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(formatDuration(position.toLong()), style = MaterialTheme.typography.labelSmall)
                                    Slider(
                                        value = position.coerceIn(0f, rec.durationMs.toFloat()),
                                        onValueChange = { v ->
                                            position = v
                                            player?.seekTo(v.toInt())
                                        },
                                        valueRange = 0f..rec.durationMs.toFloat(),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )

    deleting?.let { rec ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Kayıt silinsin mi?") },
            text = { Text("${dateFormat.format(Date(rec.createdAt))} · ${formatDuration(rec.durationMs)}") },
            confirmButton = {
                TextButton(onClick = {
                    if (playing == rec.file) stop()
                    onDelete(rec)
                    deleting = null
                }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } },
        )
    }
}
