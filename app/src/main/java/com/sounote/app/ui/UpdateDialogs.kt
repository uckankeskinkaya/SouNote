package com.sounote.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sounote.app.LibraryViewModel
import com.sounote.app.data.Updates

/** Son ana sürümle gelenler ("Yenilikler" penceresi; eski sürümden güncelleyen bir kez görür). */
private val WHATS_NEW = listOf(
    SouIcons.Palette to "Yeni sade arayüz ve yeni logo: dört sekme (Bugün, Kütüphane, Çalış, Takvim), açık ve koyu tema.",
    SouIcons.Event to "Takvim: her sınavın türü simgesiyle (quiz, writing, speaking, sunum, listening, ödev).",
    SouIcons.InkPen to "Yeni kalem menüsü ve hızlı menü: sayfaya basılı tut; araçlar, favoriler, gökkuşağından renk, kalınlık.",
    SouIcons.History to "Not geçmişi: sayfanın önceki hâline dön.",
    SouIcons.Quiz to "Daha iyi deneme sınavı: denetlenmiş sorular, konu konu analiz, yanlış soruda kaynakta göster.",
    SouIcons.Headphones to "Listening A–B tekrarı: bir bölümü işaretle, arada beklemeyle tekrar tekrar çalsın.",
    SouIcons.School to "Bugün derste kartı: günün konuları ve PDF'leri tek yerde.",
    SouIcons.Bookmarks to "Çalışma düzenini kaydet: belge, yan belge ve dinleme kaydı tek dokunuşla açılır.",
    SouIcons.Widgets to "Widget'lar da yeni sade görünümde.",
)

/** Yeni sürüm ve "Yenilikler" pencereleri (ana ekranda). */
@Composable
fun UpdateDialogs(vm: LibraryViewModel) {
    val context = LocalContext.current
    val update = vm.update
    if (update != null) {
        AlertDialog(
            onDismissRequest = { vm.skipUpdate() },
            title = { Text("Yeni sürüm: ${update.name}") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text("Şu an ${vm.installedVersion} kurulu.", style = MaterialTheme.typography.bodySmall)
                    if (update.notes.isNotBlank()) {
                        Text("Bu sürümde:", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        Text(update.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "İndirdikten sonra dosyaya dokunup \"Güncelle\" de. Notların silinmez.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.apkUrl))) }
                    vm.skipUpdate()
                }) { Text("İndir") }
            },
            dismissButton = { TextButton(onClick = { vm.skipUpdate() }) { Text("Sonra") } },
        )
    } else if (vm.showWhatsNew) {
        AlertDialog(
            onDismissRequest = { vm.dismissWhatsNew() },
            title = { Text("Yenilikler") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                ) {
                    WHATS_NEW.forEach { (emoji, text) ->
                        Row {
                            Icon(emoji, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp).size(20.dp))
                            Text(text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { vm.dismissWhatsNew() }) { Text("Başla") } },
        )
    }
}
