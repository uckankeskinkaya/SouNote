package com.sounote.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
 * Çalışma: kartlar sırayla; önce ön yüz, "Cevabı göster" ile arka yüz.
 * Bildim → kart daha geç gelir; Bilemedim → bu oturumun sonunda tekrar sorulur.
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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.9f).heightIn(min = 420.dp, max = 760.dp),
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val card = queue.firstOrNull()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (card == null) "Bitti!" else "Kart ${done + 1} · kalan ${queue.size}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text("Kapat") }
                }
                Spacer(Modifier.height(16.dp))
                if (card == null) {
                    Text(
                        "$total kartın $known tanesini ilk seferde bildin.",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onDismiss) { Text("Tamam") }
                    return@Column
                }
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    image(card)?.let { CardImage(it, Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 360.dp)) }
                    if (card.frontText.isNotBlank()) {
                        Text(card.frontText, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    }
                    if (revealed) {
                        HorizontalDivider()
                        Text(
                            card.back.ifBlank { "(Cevap yazılmamış)" },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                        if (card.page >= 0) {
                            TextButton(onClick = { onGoToPage(card) }) { Text("Sayfa ${card.page + 1}'e git") }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                if (!revealed) {
                    Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) { Text("Cevabı göster") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                onReview(card, false)
                                failed = failed + card.id
                                // Bilemediğin kart sona eklenir, oturumda tekrar gelir.
                                queue = queue.drop(1) + card
                                revealed = false
                                done++
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Bilemedim") }
                        Button(
                            onClick = {
                                onReview(card, true)
                                if (card.id !in failed) known++
                                queue = queue.drop(1)
                                revealed = false
                                done++
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Bildim") }
                    }
                }
            }
        }
    }
}
