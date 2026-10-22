package com.sounote.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.core.content.IntentCompat
import com.sounote.app.ui.LibraryScreen
import com.sounote.app.ui.SouNoteTheme
import com.sounote.app.ui.ViewerScreen

class MainActivity : ComponentActivity() {

    private val vm: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = when (vm.themeMode) {
                LibraryViewModel.ThemeMode.LIGHT -> false
                LibraryViewModel.ThemeMode.DARK -> true
                LibraryViewModel.ThemeMode.SYSTEM -> null
            }
            SouNoteTheme(dark) {
                SouNoteApp(vm)
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
        vm.studyResume()
    }

    override fun onPause() {
        super.onPause()
        vm.studyPause()
        vm.flush()
    }

    /** Dosya yöneticisinden "SouNote ile aç" / "Paylaş" ile gelen PDF'i içe aktarıp aç. */
    private fun handleIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        if (uri != null) vm.importPdfs(listOf(uri), openAfter = true)
    }
}

@Composable
private fun SouNoteApp(vm: LibraryViewModel) {
    val doc = vm.openDoc
    if (doc != null) {
        BackHandler { vm.closeDoc() }
        // Sayfa eklenip silinince PDF değişir: görüntüleyici baştan kurulur.
        key(doc.id, vm.docVersion) {
            ViewerScreen(doc, vm)
        }
    } else {
        BackHandler(enabled = vm.canNavigateUp) { vm.navigateUp() }
        LibraryScreen(vm)
    }
}
