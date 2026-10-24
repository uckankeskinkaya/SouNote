package com.sounote.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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

/** 2.0 ile gelenler ("Yenilikler" penceresi; 1.0'dan güncelleyen bir kez görür). */
private val WHATS_NEW = listOf(
    "🎨" to "Yeni görünüm: Obsidian Purple ve Midnight Blue koyu temalar, SouNote Light, istediğin vurgu rengi.",
    "🏠" to "Kişiselleştirilebilir ana sayfa: modülleri sırala ya da gizle; çalışma serin ve hızlı erişim.",
    "⭐" to "Favoriler ve sabitleme: belge, klasör, ders ve notlara tek dokunuşla ulaş.",
    "🗓️" to "Planlayıcı: liste, hafta, ay ve zaman çizelgesi; ödevleri tamamlandı olarak işaretle, öncelik ver.",
    "✍️" to "Yazı stüdyosu: essay şablonu, başlık/kalın/italik, kelime ve karakter sayacı, odak modu, yazma koçu.",
    "📖" to "PDF'te okuma modu; lasso ile seçtiğin bölgeyi asistana açıklat ya da çevirt.",
    "🔍" to "Evrensel arama: dersler, klasörler, belgeler, notlar, sınavlar; filtreler ve son aramalar.",
    "⚙️" to "Kategorili ayarlar ve ayarlarda arama. Notların ve yedeklerin aynen korunur.",
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
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updates.APK_URL))) }
                    vm.skipUpdate()
                }) { Text("İndir") }
            },
            dismissButton = { TextButton(onClick = { vm.skipUpdate() }) { Text("Sonra") } },
        )
    } else if (vm.showWhatsNew) {
        AlertDialog(
            onDismissRequest = { vm.dismissWhatsNew() },
            title = { Text("SouNote 2.0 🎉") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                ) {
                    WHATS_NEW.forEach { (emoji, text) ->
                        Row {
                            Text(emoji, fontSize = 18.sp, modifier = Modifier.width(32.dp))
                            Text(text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { vm.dismissWhatsNew() }) { Text("Başla") } },
        )
    }
}
