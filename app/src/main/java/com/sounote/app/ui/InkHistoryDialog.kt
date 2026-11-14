package com.sounote.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sounote.app.LibraryViewModel
import com.sounote.app.data.DocRef
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InkHistoryDialog(vm: LibraryViewModel, doc: DocRef, page: Int, onDismiss: () -> Unit) {
    val stamps = remember(vm, doc.id, page) { vm.pageHistory(doc, page) }
    val formatter = remember { SimpleDateFormat("d MMMM HH:mm", Locale("tr")) }
    val now = remember(doc.id, page) { System.currentTimeMillis() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Not geçmişi · Sayfa ${page + 1}") },
        text = {
            if (stamps.isEmpty()) {
                Text("Bu sayfa için kayıtlı eski sürüm yok. Notlar değiştikçe birkaç dakikada bir sürüm saklanır.")
            } else {
                Column(
                    modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    stamps.forEach { stamp ->
                        val minutes = ((now - stamp) / 60_000L).coerceAtLeast(0L)
                        val relative = when {
                            minutes < 60 -> "$minutes dk önce"
                            minutes < 1440 -> "${minutes / 60} sa önce"
                            else -> "${minutes / 1440} gün önce"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().card().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(formatter.format(Date(stamp)), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    relative,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = {
                                vm.restorePageVersion(doc, page, stamp)
                                onDismiss()
                            }) { Text("Geri yükle") }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}
