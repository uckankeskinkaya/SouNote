package com.sounote.app.ui

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sounote.app.R
import kotlinx.coroutines.delay
import java.io.File

/**
 * Kayda bağlı çizgiye dokununca açılan küçük oynatıcı: kaydı o andan biraz önce başlatır.
 * [key] değişince (başka çizgiye dokunulunca) yeni konumdan çalar.
 */
@Composable
fun AudioPlayerBar(file: File, startMs: Long, key: Any, onClose: () -> Unit) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(1f) }

    DisposableEffect(file.path) {
        val p = runCatching { MediaPlayer().apply { setDataSource(file.path); prepare() } }.getOrNull()
        player = p
        duration = (p?.duration ?: 1).toFloat().coerceAtLeast(1f)
        p?.setOnCompletionListener { playing = false }
        onDispose {
            p?.release()
            player = null
        }
    }
    // Dokunulan çizginin anından 1,5 sn önce başla: cümlenin başı kaçmasın.
    LaunchedEffect(key, player) {
        val p = player ?: return@LaunchedEffect
        p.seekTo((startMs - 1500).coerceAtLeast(0).toInt())
        p.start()
        playing = true
    }
    LaunchedEffect(playing) {
        while (playing) {
            player?.let { runCatching { position = it.currentPosition.toFloat() } }
            delay(250)
        }
    }

    Surface(tonalElevation = 4.dp, shadowElevation = 4.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = {
                val p = player ?: return@IconButton
                if (p.isPlaying) {
                    p.pause()
                    playing = false
                } else {
                    p.start()
                    playing = true
                }
            }) {
                if (playing) Icon(painterResource(R.drawable.ic_pause), contentDescription = "Duraklat")
                else Icon(Icons.Default.PlayArrow, contentDescription = "Çal")
            }
            Text(
                formatDuration(position.toLong()) + " / " + formatDuration(duration.toLong()),
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = position.coerceIn(0f, duration),
                onValueChange = { v ->
                    position = v
                    player?.seekTo(v.toInt())
                },
                valueRange = 0f..duration,
                modifier = Modifier.weight(1f),
            )
            Text("Notla eşleşen kayıt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Kapat") }
        }
    }
}
