package com.sounote.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sounote.app.ErrorLog
import com.sounote.app.LibraryViewModel
import com.sounote.app.LibraryViewModel.Tab
import com.sounote.app.R

private val TABS = listOf(
    Tab.HOME to "Ana sayfa",
    Tab.LIBRARY to "Kütüphane",
    Tab.COURSES to "Dersler",
    Tab.EXAMS to "Sınavlar",
    Tab.SETTINGS to "Ayarlar",
)

@Composable
private fun TabIcon(tab: Tab) {
    when (tab) {
        Tab.HOME -> Icon(Icons.Default.Home, contentDescription = null)
        Tab.LIBRARY -> Icon(painterResource(R.drawable.ic_folder), contentDescription = null)
        Tab.COURSES -> Icon(painterResource(R.drawable.ic_pages), contentDescription = null)
        Tab.EXAMS -> Icon(Icons.Default.DateRange, contentDescription = null)
        Tab.SETTINGS -> Icon(Icons.Default.Settings, contentDescription = null)
    }
}

/** Ana menü: tablette solda dikey çubuk, telefonda altta sekmeler. */
@Composable
fun MainShell(vm: LibraryViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    val content: @Composable () -> Unit = {
        Crossfade(targetState = vm.tab, label = "tab") { tab ->
            when (tab) {
                Tab.HOME -> HomeScreen(vm)
                Tab.LIBRARY -> LibraryScreen(vm)
                Tab.COURSES -> CoursesScreen(vm)
                Tab.EXAMS -> ExamsScreen(vm)
                Tab.SETTINGS -> SettingsScreen(vm)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    header = { AppLogo(44.dp, Modifier.padding(vertical = 16.dp), elevated = false) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TABS.forEach { (tab, label) ->
                            NavigationRailItem(
                                selected = vm.tab == tab,
                                onClick = { vm.tab = tab },
                                icon = { TabIcon(tab) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
            }
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        TABS.forEach { (tab, label) ->
                            NavigationBarItem(
                                selected = vm.tab == tab,
                                onClick = { vm.tab = tab },
                                icon = { TabIcon(tab) },
                                label = { Text(label) },
                            )
                        }
                    }
                },
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { inner ->
                Box(Modifier.padding(inner).consumeWindowInsets(inner).fillMaxSize()) { content() }
            }
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = if (wide) 8.dp else 88.dp),
        )
    }

    CrashDialog()
}

/** Önceki açılışta çöktüyse raporu paylaşma penceresi. */
@Composable
private fun CrashDialog() {
    val context = LocalContext.current
    var crash by remember { mutableStateOf(ErrorLog.pendingCrash()) }
    crash?.let { report ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Uygulama beklenmedik şekilde kapandı") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Raporu paylaşırsan (ya da kopyalayıp yapıştırırsan) sorun bulunup düzeltilebilir. Notların bundan etkilenmez.")
                    Text(
                        report.lineSequence().take(6).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    shareErrorReport(context)
                    ErrorLog.clearCrash()
                    crash = null
                }) { Text("Paylaş") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        cm?.setPrimaryClip(ClipData.newPlainText("SouNote hata raporu", ErrorLog.fullReport(context)))
                        ErrorLog.clearCrash()
                        crash = null
                    }) { Text("Kopyala") }
                    TextButton(onClick = {
                        ErrorLog.clearCrash()
                        crash = null
                    }) { Text("Kapat") }
                }
            },
        )
    }
}
