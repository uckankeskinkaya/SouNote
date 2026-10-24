package com.sounote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sounote.app.ink.InkLayer
import com.sounote.app.ink.LayerSet

/**
 * Katmanlar: listenin üstü en üstteki katman. Dokun = etkin katman (yeni çizgiler oraya, silgi ve lasso sadece orada),
 * göz = gizle/göster (gizli katman dışa aktarılmaz), oklar = sıra, uzun ad = yeniden adlandır.
 */
@Composable
fun LayersDialog(
    set: LayerSet,
    onChange: (LayerSet) -> Unit,
    onDeleteLayer: (InkLayer) -> Unit,
    onDismiss: () -> Unit,
) {
    var renaming by remember { mutableStateOf<InkLayer?>(null) }
    var deleting by remember { mutableStateOf<InkLayer?>(null) }

    fun update(l: InkLayer) = onChange(set.copy(layers = set.layers.map { if (it.id == l.id) l else it }))
    fun move(l: InkLayer, up: Boolean) {
        val list = set.layers.toMutableList()
        val i = list.indexOfFirst { it.id == l.id }
        val j = if (up) i + 1 else i - 1
        if (i < 0 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        onChange(set.copy(layers = list))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Katmanlar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Üstteki katman listede en üstte
                set.layers.asReversed().forEach { l ->
                    val active = l.id == set.active
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable { onChange(set.copy(active = l.id, layers = set.layers.map { if (it.id == l.id) it.copy(visible = true) else it })) },
                    ) {
                        RadioButton(selected = active, onClick = { onChange(set.copy(active = l.id, layers = set.layers.map { if (it.id == l.id) it.copy(visible = true) else it })) })
                        Text(
                            l.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (l.visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).clickable { renaming = l }.padding(vertical = 10.dp),
                        )
                        // Etkin katman gizlenemez (çizilen görünmez olurdu)
                        IconButton(enabled = !active, onClick = { update(l.copy(visible = !l.visible)) }) {
                            Icon(if (l.visible) SouIcons.Visibility else SouIcons.VisibilityOff, contentDescription = if (l.visible) "Gizle" else "Göster")
                        }
                        IconButton(enabled = l != set.layers.last(), onClick = { move(l, up = true) }) {
                            Icon(SouIcons.ArrowUpward, contentDescription = "Yukarı")
                        }
                        IconButton(enabled = l != set.layers.first(), onClick = { move(l, up = false) }) {
                            Icon(SouIcons.ArrowDownward, contentDescription = "Aşağı")
                        }
                        IconButton(enabled = set.layers.size > 1, onClick = { deleting = l }) {
                            Icon(SouIcons.Delete, contentDescription = "Sil")
                        }
                    }
                }
                Text(
                    "Yeni çizgiler seçili katmana gider; silgi ve lasso sadece onda çalışır. Gizli katmanlar dışa aktarılmaz. Ada dokun: yeniden adlandır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tamam") } },
        dismissButton = {
            TextButton(enabled = set.layers.size < LayerSet.MAX, onClick = {
                val id = (set.layers.maxOf { it.id } + 1).coerceAtMost(127)
                if (set.layers.none { it.id == id }) {
                    onChange(set.copy(layers = set.layers + InkLayer(id, "Katman ${set.layers.size + 1}"), active = id))
                }
            }) { Text("Katman ekle") }
        },
    )

    renaming?.let { l ->
        var name by remember(l.id) { mutableStateOf(l.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Katman adı") },
            text = { OutlinedTextField(name, { name = it.take(30) }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { update(l.copy(name = name.trim())); renaming = null }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Vazgeç") } },
        )
    }
    deleting?.let { l ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("\"${l.name}\" silinsin mi?") },
            text = { Text("Bu katmandaki bütün çizgiler silinir (geri alınabilir).") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteLayer(l)
                    deleting = null
                }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } },
        )
    }
}
