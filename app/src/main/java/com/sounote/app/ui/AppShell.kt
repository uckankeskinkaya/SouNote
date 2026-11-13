package com.sounote.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
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

/**
 * Ana bölümler (4.0): Bugün, Kütüphane, Çalış, Takvim. Yazı (Writing) Çalış'ın içinden açılır, Ayarlar menünün altında
 * (telefonda ana sayfadaki ⚙ düğmesinden).
 */
private val TABS = listOf(
    Tab.HOME to "Bugün",
    Tab.LIBRARY to "Kütüphane",
    Tab.COURSES to "Çalış",
    Tab.EXAMS to "Takvim",
)

/** Menüde hangi bölüm seçili görünsün: Writing ekranı Çalış'ın altında. */
private fun railTab(t: Tab) = if (t == Tab.WRITING) Tab.COURSES else t

@Composable
private fun TabIcon(tab: Tab, selected: Boolean) {
    val icon = when (tab) {
        Tab.HOME -> if (selected) SouIcons.HomeFilled else SouIcons.Home
        Tab.LIBRARY -> if (selected) SouIcons.FolderFilled else SouIcons.Folder
        Tab.COURSES -> if (selected) SouIcons.SchoolFilled else SouIcons.School
        Tab.EXAMS -> if (selected) SouIcons.CalendarMonthFilled else SouIcons.CalendarMonth
        Tab.WRITING -> if (selected) SouIcons.EditNoteFilled else SouIcons.EditNote
        Tab.SETTINGS -> if (selected) SouIcons.SettingsFilled else SouIcons.Settings
    }
    Icon(icon, contentDescription = null)
}

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

    BoxWithConstraints(Modifier.fillMaxSize().souBackground()) {
        val kind = if (maxWidth < 600.dp) NavKind.BAR else NavKind.RAIL
        when (kind) {
            NavKind.BAR -> Scaffold(
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        TABS.forEach { (tab, label) ->
                            val sel = railTab(vm.tab) == tab
                            NavigationBarItem(
                                selected = sel,
                                onClick = { vm.tab = tab },
                                icon = { TabIcon(tab, sel) },
                                label = { TabLabel(label) },
                            )
                        }
                    }
                },
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { inner ->
                Box(Modifier.padding(inner).consumeWindowInsets(inner).fillMaxSize()) { content() }
            }
            else -> Row(Modifier.fillMaxSize()) {
                SouRail(vm, tall = maxHeight >= 520.dp)
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
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

/**
 * Tablet menüsü (4.0): solda dar, düz bir sütun. Üstte logo, ortada dört bölüm (seçili olanın simgesi dolu ve
 * yumuşak bir hap içinde), en altta Asistan ve Ayarlar.
 */
@Composable
private fun SouRail(vm: LibraryViewModel, tall: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxHeight()) {
        Column(
            Modifier
                .width(84.dp)
                .fillMaxHeight()
                .background(scheme.surface)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (tall) AppLogo(40.dp, Modifier.padding(bottom = 18.dp), elevated = false)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (tall) 8.dp else 2.dp),
            ) {
                TABS.forEach { (tab, label) ->
                    val sel = railTab(vm.tab) == tab
                    RailItem(label, sel, onClick = { vm.tab = tab }) { TabIcon(tab, sel) }
                }
            }
            RailItem("Asistan", false, onClick = { vm.openAssistant() }) { Icon(SouIcons.StarShine, contentDescription = null) }
            RailItem("Ayarlar", vm.tab == Tab.SETTINGS, onClick = { vm.tab = Tab.SETTINGS }) {
                Icon(if (vm.tab == Tab.SETTINGS) SouIcons.SettingsFilled else SouIcons.Settings, contentDescription = null)
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.width(1.dp).fillMaxHeight().background(scheme.outlineVariant))
    }
}

@Composable
private fun RailItem(label: String, selected: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Box(
            Modifier
                .size(width = 56.dp, height = 32.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .background(if (selected) scheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides if (selected) scheme.primary else scheme.onSurfaceVariant,
            ) { icon() }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(top = 4.dp),
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
