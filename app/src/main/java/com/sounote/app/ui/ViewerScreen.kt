package com.sounote.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sounote.app.data.DocRef
import com.sounote.app.pdf.PdfDocumentView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    doc: DocRef,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var page by remember(doc.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    var error by remember(doc.id) { mutableStateOf<String?>(null) }
    val pageCallback by rememberUpdatedState(onPageChanged)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        page?.let { (p, n) ->
                            Text("Sayfa ${p + 1} / $n", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            val message = error
            if (message != null) {
                Text(message, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
            } else {
                AndroidView(
                    factory = { context ->
                        PdfDocumentView(context).apply {
                            this.onPageChanged = { p, n ->
                                page = p to n
                                pageCallback(p)
                            }
                            onError = { error = "PDF açılamadı. Dosya taşınmış, bozuk ya da şifreli olabilir." }
                            open(doc.uri, initialPage)
                        }
                    },
                    onRelease = { it.release() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
