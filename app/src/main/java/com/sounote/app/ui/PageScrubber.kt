package com.sounote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Sağ kenarda sayfa kaydırıcısı: tutup sürükleyince sayfalar arasında hızlıca gezilir.
 * Sadece küçük tutamaç dokunmaya açık; sayfanın geri kalanında çizim engellenmez.
 */
@Composable
fun PageScrubber(page: Int, pageCount: Int, onScrub: (Int) -> Unit, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf(false) }
    var dragPage by remember { mutableStateOf(page) }
    val shown = if (dragging) dragPage else page
    BoxWithConstraints(modifier.fillMaxHeight().width(120.dp)) {
        val density = LocalDensity.current
        val handleH = 48.dp
        val track = with(density) { (maxHeight - handleH - 16.dp).toPx() }.coerceAtLeast(1f)
        val y = track * shown / (pageCount - 1).coerceAtLeast(1)
        var startY by remember { mutableStateOf(0f) }
        var accumulated by remember { mutableStateOf(0f) }
        // Sürükleme dinleyicisi bir kez kurulur; güncel değerleri buradan okur.
        val currentY by rememberUpdatedState(y)
        val currentPage by rememberUpdatedState(page)
        val currentTrack by rememberUpdatedState(track)
        val scrub by rememberUpdatedState(onScrub)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (y + with(density) { 8.dp.toPx() }).roundToInt()) }
                .padding(end = 4.dp)
                .size(width = 22.dp, height = handleH)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (dragging) 0.95f else 0.55f), RoundedCornerShape(11.dp))
                .pointerInput(pageCount) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragging = true
                            dragPage = currentPage
                            startY = currentY
                            accumulated = 0f
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, dy ->
                        change.consume()
                        accumulated += dy
                        val p = (((startY + accumulated) / currentTrack) * (pageCount - 1)).roundToInt().coerceIn(0, pageCount - 1)
                        if (p != dragPage) {
                            dragPage = p
                            scrub(p)
                        }
                    }
                },
        )
        if (dragging) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 6.dp,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(-with(density) { 34.dp.toPx() }.roundToInt(), (y + with(density) { 16.dp.toPx() }).roundToInt()) },
            ) {
                Text(
                    "${shown + 1} / $pageCount",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}
