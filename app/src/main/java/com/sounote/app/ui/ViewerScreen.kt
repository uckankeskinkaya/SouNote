package com.sounote.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.sounote.app.LibraryViewModel
import com.sounote.app.R
import com.sounote.app.data.DocRef
import com.sounote.app.ink.TextBox
import com.sounote.app.pdf.FormField
import com.sounote.app.pdf.PdfDocumentView
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(doc: DocRef, vm: LibraryViewModel) {
    val context = LocalContext.current
    val ink = vm.ink
    val tools = vm.tools
    var page by remember(doc.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    var error by remember(doc.id) { mutableStateOf<String?>(null) }
    var view by remember { mutableStateOf<PdfDocumentView?>(null) }
    var canUndo by remember(ink) { mutableStateOf(ink.canUndo) }
    var canRedo by remember(ink) { mutableStateOf(ink.canRedo) }
    var editingText by remember { mutableStateOf<Triple<Int, TextBox, Boolean>?>(null) }
    var editingField by remember { mutableStateOf<FormField?>(null) }
    var exportMenu by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<File?>(null) }
    val initialPage = remember(doc.id) { vm.lastPage(doc) }
    val currentVm by rememberUpdatedState(vm)
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    DisposableEffect(ink) {
        val listener = {
            canUndo = ink.canUndo
            canRedo = ink.canRedo
        }
        ink.addHistoryListener(listener)
        onDispose { ink.removeHistoryListener(listener) }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val file = pendingExport
        if (uri != null && file != null) vm.saveExportTo(file, uri)
    }

    fun share(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "PDF'i paylaş"))
    }

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
                    IconButton(onClick = vm::closeDoc) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    IconButton(onClick = { ink.undo() }, enabled = canUndo) {
                        Icon(painterResource(R.drawable.ic_undo), contentDescription = "Geri al")
                    }
                    IconButton(onClick = { ink.redo() }, enabled = canRedo) {
                        Icon(painterResource(R.drawable.ic_redo), contentDescription = "İleri al")
                    }
                    Box {
                        if (vm.exporting) {
                            CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.5.dp)
                        } else {
                            IconButton(onClick = { exportMenu = true }) {
                                Icon(Icons.Default.Share, contentDescription = "Dışa aktar")
                            }
                        }
                        DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Notlu PDF'i paylaş") },
                                onClick = {
                                    exportMenu = false
                                    vm.export(doc) { share(it) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Notlu PDF'i dosyaya kaydet") },
                                onClick = {
                                    exportMenu = false
                                    vm.export(doc) { file ->
                                        pendingExport = file
                                        saveLauncher.launch(file.name)
                                    }
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            InkToolbar(tools, vm::updateTools)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val message = error
                if (message != null) {
                    Text(message, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
                } else {
                    AndroidView(
                        factory = { ctx ->
                            PdfDocumentView(ctx).apply {
                                onPageChanged = { p, n ->
                                    page = p to n
                                    currentVm.saveLastPage(doc, p)
                                }
                                onError = { error = "PDF açılamadı. Dosya taşınmış, bozuk ya da şifreli olabilir." }
                                onTextBoxRequest = { p, box, isNew -> editingText = Triple(p, box, isNew) }
                                onFormFieldRequest = { editingField = it }
                                onUndoGesture = { this.ink.undo() }
                                onFormSaved = { currentVm.showMessage("Form kaydedildi") }
                                formSaveTarget = currentVm.formSaveTarget(doc)
                                this.ink = ink
                                this.tools = tools
                                open(doc.uri, initialPage)
                                view = this
                            }
                        },
                        update = {
                            it.tools = tools
                            if (it.ink !== ink) it.ink = ink
                        },
                        onRelease = {
                            it.release()
                            view = null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    editingText?.let { (p, box, isNew) ->
        TextBoxDialog(
            box = box,
            isNew = isNew,
            onSave = { edited ->
                val c = ink.content(p)
                val texts = when {
                    edited.text.isBlank() -> c.texts.filter { it.id != box.id }
                    isNew -> c.texts + edited
                    else -> c.texts.map { if (it.id == box.id) edited else it }
                }
                ink.commit(p, c.copy(texts = texts))
                if (edited.text.isNotBlank()) {
                    vm.updateTools(tools.copy(textSizePt = edited.fontSize, textColor = edited.color))
                }
                editingText = null
            },
            onDelete = {
                val c = ink.content(p)
                ink.commit(p, c.copy(texts = c.texts.filter { it.id != box.id }))
                editingText = null
            },
            onDismiss = { editingText = null },
        )
    }

    editingField?.let { field ->
        when (field.type) {
            FormField.Type.CHOICE -> FormChoiceDialog(
                field = field,
                onSave = { indices ->
                    view?.editFormField(field, indices = indices)
                    editingField = null
                },
                onDismiss = { editingField = null },
            )
            else -> FormTextDialog(
                field = field,
                onSave = { text ->
                    view?.editFormField(field, text = text)
                    editingField = null
                },
                onDismiss = { editingField = null },
            )
        }
    }
}
