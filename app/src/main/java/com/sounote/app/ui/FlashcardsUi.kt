package com.sounote.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sounote.app.data.Card
import java.io.File

/** Kart oluşturma: soru (isteğe bağlı) ve cevap. [fromRegion] ise ön yüze seçilen bölgenin görüntüsü konur. */
@Composable
fun MakeCardDialog(fromRegion: Boolean, onSave: (front: String, back: String) -> Unit, onDismiss: () -> Unit) {
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (fromRegion) "Seçimi karta çevir" else "Yeni kart") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (fromRegion) {
                    Text(
                        "Kartın ön yüzünde seçtiğin bölüm görünecek. İstersen bir soru ekle, arka yüze cevabı yaz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = front,
                    onValueChange = { front = it },
                    label = { Text(if (fromRegion) "Soru (isteğe bağlı)" else "Soru / ön yüz") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = back,
                    onValueChange = { back = it },
                    label = { Text("Cevap / arka yüz") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = fromRegion || front.isNotBlank(),
                onClick = { onSave(front, back) },
            ) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

/** Belgenin kartları: liste, sayılar, çalış ve yeni yazılı kart. */
@Composable
fun FlashcardsDialog(
    cards: List<Card>,
    image: (Card) -> File?,
    onStudy: (all: Boolean) -> Unit,
    onNewText: () -> Unit,
    onDelete: (Card) -> Unit,
    onDismiss: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val due = cards.count { it.due <= now }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bilgi kartları") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (cards.isEmpty()) "Henüz kart yok. Seç aracıyla bir bölümü seçip \"Karta çevir\" de ya da yazılı kart ekle."
                    else "${cards.size} kart · bugün tekrar: $due",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onStudy(false) }, enabled = due > 0) { Text("Çalış ($due)") }
                    if (cards.isNotEmpty() && due < cards.size) {
                        OutlinedButton(onClick = { onStudy(true) }) { Text("Hepsini çalış") }
                    }
                    OutlinedButton(onClick = onNewText) { Text("+ Yazılı kart") }
                }
                if (cards.isNotEmpty()) {
                    HorizontalDivider()
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(cards, key = { it.id }) { card ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                                CardImage(image(card), Modifier.size(56.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text(
                                        card.frontText.ifBlank { if (card.frontImage != null) "Görsel kart" else "—" },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "Kutu ${card.box}/5" + if (card.page >= 0) " · sayfa ${card.page + 1}" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { onDelete(card) }) { Icon(SouIcons.Delete, contentDescription = "Sil") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}

@Composable
private fun CardImage(file: File?, modifier: Modifier) {
    val bmp: Bitmap? = remember(file?.path) { file?.takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() } }
    Box(
        modifier
            .background(Color.White, RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(2.dp))
        } else {
            Text("Aa", color = Color.Gray)
        }
    }
}

/**
 * Çalışma: tam ekran, kartlar sırayla. Karta dokununca döner (ön yüz → cevap).
 * Sağa kaydır ya da "Bildim" → kart daha geç gelir; sola ya da "Bilemedim" → bu oturumun sonunda tekrar sorulur.
 */
@Composable
fun StudyDialog(
    cards: List<Card>,
    image: (Card) -> File?,
    onReview: (Card, Boolean) -> Unit,
    onGoToPage: (Card) -> Unit,
    onDismiss: () -> Unit,
) {
    var queue by remember { mutableStateOf(cards.shuffled()) }
    var revealed by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(0) }
    var known by remember { mutableStateOf(0) }
    var failed by remember { mutableStateOf(setOf<Long>()) }
    val total = remember { cards.size }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val offset = remember { androidx.compose.animation.core.Animatable(0f) }
    val flip by androidx.compose.animation.core.animateFloatAsState(if (revealed) 180f else 0f, androidx.compose.animation.core.tween(350), label = "flip")
    val sou = LocalSouColors.current

    fun answer(card: Card, ok: Boolean) {
        onReview(card, ok)
        if (ok) {
            if (card.id !in failed) known++
            queue = queue.drop(1)
        } else {
            failed = failed + card.id
            // Bilemediğin kart sona eklenir, oturumda tekrar gelir.
            queue = queue.drop(1) + card
        }
        revealed = false
        done++
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().souBackground()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val card = queue.firstOrNull()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onDismiss) { Icon(SouIcons.Close, contentDescription = "Kapat") }
                    ProgressBar(
                        if (total == 0) 1f else (total - queue.size).toFloat() / total,
                        Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text("${total - queue.size}/$total", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
                }
                if (card == null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        ProgressRing(if (total == 0) 0f else known.toFloat() / total, size = 120.dp, stroke = 10.dp, color = sou.success)
                        Text("Bitti", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "$total kartın $known tanesini ilk seferde bildin.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = onDismiss) { Text("Tamam") }
                    }
                    return@Column
                }
                // Kart: dokun → döner; yatay kaydır → bildim / bilemedim
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp),
                ) {
                    val drag = offset.value
                    Box(
                        Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth()
                            .fillMaxHeight(0.92f)
                            .graphicsLayer {
                                translationX = drag
                                rotationZ = drag / 40f
                                rotationY = flip
                                cameraDistance = 14f * density
                            }
                            .shadow(10.dp, RoundedCornerShape(28.dp))
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color(0xFFFFFDF8))
                            .pointerInput(card.id) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        val v = offset.value
                                        scope.launch {
                                            if (abs(v) > size.width * 0.28f) {
                                                offset.animateTo(if (v > 0) size.width * 1.5f else -size.width * 1.5f, androidx.compose.animation.core.tween(180))
                                                answer(card, v > 0)
                                                offset.snapTo(0f)
                                            } else {
                                                offset.animateTo(0f)
                                            }
                                        }
                                    },
                                ) { _, dx -> scope.launch { offset.snapTo(offset.value + dx) } }
                            }
                            .clickable { revealed = !revealed },
                    ) {
                        // Kaydırma yönüne göre yeşil / kırmızı ipucu
                        if (drag != 0f) {
                            Box(
                                Modifier.fillMaxSize().background(
                                    (if (drag > 0) sou.success else sou.danger).copy(alpha = (abs(drag) / 900f).coerceAtMost(0.25f)),
                                ),
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { if (flip > 90f) rotationY = 180f }
                                .verticalScroll(rememberScrollState())
                                .padding(28.dp),
                        ) {
                            if (flip <= 90f) {
                                Text("SORU", style = MaterialTheme.typography.labelMedium, color = Color(0xFF8A8DA3))
                                image(card)?.let { CardImage(it, Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 360.dp)) }
                                if (card.frontText.isNotBlank()) {
                                    Text(card.frontText, style = MaterialTheme.typography.headlineSmall, color = Color(0xFF14162B), textAlign = TextAlign.Center)
                                }
                                Text("Cevap için dokun", style = MaterialTheme.typography.labelMedium, color = Color(0xFF8A8DA3))
                            } else {
                                Text("CEVAP", style = MaterialTheme.typography.labelMedium, color = Color(0xFF8A8DA3))
                                Text(
                                    card.back.ifBlank { "(Cevap yazılmamış)" },
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color(0xFF3A4BE0),
                                    textAlign = TextAlign.Center,
                                )
                                if (card.page >= 0) {
                                    TextButton(onClick = { onGoToPage(card) }) { Text("Sayfa ${card.page + 1}'e git") }
                                }
                            }
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    RoundAction(SouIcons.Close, "Bilemedim", sou.danger) { answer(card, false) }
                    androidx.compose.material3.FilledTonalButton(onClick = { revealed = !revealed }) {
                        Text(if (revealed) "Soruya dön" else "Cevabı göster")
                    }
                    RoundAction(SouIcons.Check, "Bildim", sou.success) { answer(card, true) }
                }
            }
        }
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(60.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(color.copy(alpha = 0.15f))
                .clickable(onClick = onClick),
        ) { Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(28.dp)) }
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
    }
}
