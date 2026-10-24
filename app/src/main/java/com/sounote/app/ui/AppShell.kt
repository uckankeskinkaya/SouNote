package com.sounote.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sounote.app.ErrorLog
import com.sounote.app.LibraryViewModel
import com.sounote.app.LibraryViewModel.NavStyle
import com.sounote.app.LibraryViewModel.Tab
import com.sounote.app.R

/** Ana bölümler. Telefonun alt çubuğunda Ayarlar yok (ana sayfadaki ⚙ düğmesinden açılır). */
private val TABS = listOf(
    Tab.HOME to "Ana sayfa",
    Tab.LIBRARY to "Kütüphane",
    Tab.COURSES to "Dersler",
    Tab.EXAMS to "Planlayıcı",
    Tab.WRITING to "Yazı",
    Tab.SETTINGS to "Ayarlar",
)

@Composable
private fun TabIcon(tab: Tab) {
    when (tab) {
        Tab.HOME -> Icon(Icons.Default.Home, contentDescription = null)
        Tab.LIBRARY -> Icon(painterResource(R.drawable.ic_folder), contentDescription = null)
        Tab.COURSES -> Icon(painterResource(R.drawable.ic_pages), contentDescription = null)
        Tab.EXAMS -> Icon(Icons.Default.DateRange, contentDescription = null)
        Tab.WRITING -> Icon(Icons.Default.Create, contentDescription = null)
        Tab.SETTINGS -> Icon(Icons.Default.Settings, contentDescription = null)
    }
}

/** Sekme adı tek satır kalsın: büyük yazı boyutunda "Kütüpha-ne" diye bölünmesin. */
@Composable
private fun TabLabel(label: String) {
    val scale = LocalDensity.current.fontScale
    Text(
        label,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        // Büyük yazı ayarında da 5 sekme sığsın: en fazla ~12 birim
        style = MaterialTheme.typography.labelMedium.copy(fontSize = (if (scale > 1.05f) 12f / scale else 12f).sp),
    )
}

/** Ekran genişliğine göre gezinme biçimi. */
private enum class NavKind { BAR, RAIL, DRAWER }

/**
 * Uyarlanır ana menü: dar ekranda (telefon) alt çubuk, orta genişlikte navigasyon rayı,
 * geniş ekranda (büyük tablet, yatay) etiketli geniş menü. Kullanıcı Ayarlar → Arayüz'den ya da
 * menüdeki ☰ düğmesinden ray ↔ geniş menü arasında geçer.
 */
@Composable
fun MainShell(vm: LibraryViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    val motion = LocalMotionLevel.current
    val content: @Composable () -> Unit = {
        AnimatedContent(
            targetState = vm.tab,
            transitionSpec = { Motion.tabTransition<Tab>(targetState.ordinal > initialState.ordinal, motion).invoke(this) },
            label = "tab",
        ) { tab ->
            when (tab) {
                Tab.HOME -> HomeScreen(vm)
                Tab.LIBRARY -> LibraryScreen(vm)
                Tab.COURSES -> CoursesScreen(vm)
                Tab.EXAMS -> ExamsScreen(vm)
                Tab.WRITING -> WritingScreen(vm)
                Tab.SETTINGS -> SettingsScreen(vm)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val kind = when {
            maxWidth < 600.dp -> NavKind.BAR
            vm.navStyle == NavStyle.RAIL -> NavKind.RAIL
            vm.navStyle == NavStyle.EXPANDED -> NavKind.DRAWER
            maxWidth >= 1000.dp -> NavKind.DRAWER
            else -> NavKind.RAIL
        }
        // Yatay telefonda yükseklik az: logo gizlenir, sekmeler kaydırılabilir.
        val tall = maxHeight >= 520.dp
        val toggleExpanded = { vm.updateNavStyle(if (kind == NavKind.DRAWER) NavStyle.RAIL else NavStyle.EXPANDED) }
        when (kind) {
            NavKind.DRAWER -> Row(Modifier.fillMaxSize()) {
                NavDrawer(vm, tall, onCollapse = toggleExpanded)
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
            }
            NavKind.RAIL -> Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    header = {
                        IconButton(onClick = toggleExpanded) { Icon(Icons.Default.Menu, contentDescription = "Menüyü genişlet") }
                        if (tall) AppLogo(40.dp, Modifier.padding(vertical = 8.dp), elevated = false)
                    },
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(if (tall) 6.dp else 0.dp),
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    ) {
                        TABS.forEach { (tab, label) ->
                            NavigationRailItem(
                                selected = vm.tab == tab,
                                onClick = { vm.tab = tab },
                                icon = { TabIcon(tab) },
                                label = { TabLabel(label) },
                            )
                        }
                        NavigationRailItem(
                            selected = false,
                            onClick = { vm.searchOpen = true },
                            icon = { Icon(Icons.Default.Search, contentDescription = null) },
                            label = { TabLabel("Ara") },
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
            }
            NavKind.BAR -> Scaffold(
                bottomBar = {
                    NavigationBar {
                        TABS.filter { it.first != Tab.SETTINGS }.forEach { (tab, label) ->
                            NavigationBarItem(
                                selected = vm.tab == tab,
                                onClick = { vm.tab = tab },
                                icon = { TabIcon(tab) },
                                label = { TabLabel(label) },
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
                .padding(bottom = if (kind == NavKind.BAR) 88.dp else 8.dp),
        )
    }

    if (vm.searchOpen) SearchDialog(vm) { vm.searchOpen = false }
    vm.assistantPrefill?.let { prefill ->
        AssistantDialog(vm, AssistantSource.General, prefill = prefill) { vm.assistantPrefill = null }
    }
    CrashDialog()
    UpdateDialogs(vm)
}

/** Geniş ekran menüsü: logo, bölümler ve araçlar (arama, asistan, hızlı not). */
@Composable
private fun NavDrawer(vm: LibraryViewModel, tall: Boolean, onCollapse: () -> Unit) {
    val itemColors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    Column(
        Modifier
            .width(248.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            if (tall) AppLogo(36.dp, Modifier.padding(start = 8.dp, end = 10.dp), elevated = false)
            Text("SouNote", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onCollapse) { Icon(Icons.Default.Menu, contentDescription = "Menüyü daralt") }
        }
        TABS.forEach { (tab, label) ->
            NavigationDrawerItem(
                label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                selected = vm.tab == tab,
                onClick = { vm.tab = tab },
                icon = { TabIcon(tab) },
                colors = itemColors,
                modifier = Modifier.height(52.dp),
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp, horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            "Araçlar",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
        )
        NavigationDrawerItem(
            label = { Text("Her yerde ara", style = MaterialTheme.typography.labelLarge) },
            selected = false,
            onClick = { vm.searchOpen = true },
            icon = { Icon(Icons.Default.Search, contentDescription = null) },
            colors = itemColors,
            modifier = Modifier.height(48.dp),
        )
        NavigationDrawerItem(
            label = { Text("Asistan", style = MaterialTheme.typography.labelLarge) },
            selected = false,
            onClick = { vm.openAssistant() },
            icon = { Icon(Icons.Default.Face, contentDescription = null) },
            colors = itemColors,
            modifier = Modifier.height(48.dp),
        )
        NavigationDrawerItem(
            label = { Text("Hızlı not", style = MaterialTheme.typography.labelLarge) },
            selected = false,
            onClick = { vm.quickNote() },
            icon = { Icon(painterResource(R.drawable.ic_shortcut_note), contentDescription = null) },
            colors = itemColors,
            modifier = Modifier.height(48.dp),
        )
    }
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
