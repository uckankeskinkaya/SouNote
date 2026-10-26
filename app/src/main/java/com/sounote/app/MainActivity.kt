package com.sounote.app

import android.app.KeyguardManager
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.key
import androidx.core.content.IntentCompat
import com.sounote.app.data.DocRef
import com.sounote.app.data.TextNote
import com.sounote.app.ui.LocalMotionLevel
import com.sounote.app.ui.LockScreen
import com.sounote.app.ui.Motion
import com.sounote.app.ui.MainShell
import com.sounote.app.ui.OnboardingScreen
import com.sounote.app.ui.TextNoteScreen
import com.sounote.app.ui.SouNoteTheme
import com.sounote.app.ui.ViewerScreen

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_QUICK_NOTE = "com.sounote.app.QUICK_NOTE"
        const val ACTION_OPEN_DOC = "com.sounote.app.OPEN_DOC"
        const val ACTION_OPEN_EXAMS = "com.sounote.app.OPEN_EXAMS"
        // Widget'lardan
        const val ACTION_OPEN_HOME = "com.sounote.app.OPEN_HOME"
        const val ACTION_OPEN_WEEK = "com.sounote.app.OPEN_WEEK"
        const val ACTION_OPEN_LIBRARY = "com.sounote.app.OPEN_LIBRARY"
        const val ACTION_NEW_CANVAS = "com.sounote.app.NEW_CANVAS"
        const val ACTION_OPEN_ASSISTANT = "com.sounote.app.OPEN_ASSISTANT"
        const val EXTRA_DOC_ID = "docId"
    }

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
            val isDark = dark ?: androidx.compose.foundation.isSystemInDarkTheme()
            // Durum ve gezinme çubuğu simgeleri uygulamanın temasına uysun (sistem temasına değil).
            androidx.compose.runtime.SideEffect {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !isDark
                    isAppearanceLightNavigationBars = !isDark
                }
            }
            SouNoteTheme(isDark, vm.appTheme, vm.accent, vm.uiDensity, vm.motionLevel) {
                Box(Modifier.fillMaxSize()) {
                    SouNoteApp(vm)
                    // Kilit ekranı üstte durur; altındaki belge açık kalır, kilit açılınca kaldığı yerden devam.
                    if (vm.locked) LockScreen(onUnlock = ::unlock)
                }
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    /** Biyometrik doğrulama sürerken (PIN ekranı başka etkinlik olarak açılabilir) arka plana geçiş sayılmaz. */
    private var authenticating = false

    private fun unlock() {
        if (authenticating) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isDeviceSecure != true) {
            vm.lockUnavailable()
            return
        }
        val builder = BiometricPrompt.Builder(this)
            .setTitle("SouNote kilitli")
            .setSubtitle("Notlarını açmak için doğrula")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        authenticating = true
        runCatching {
            builder.build().authenticate(
                CancellationSignal(),
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        authenticating = false
                        vm.unlocked()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        authenticating = false
                    }
                },
            )
        }.onFailure {
            authenticating = false
            ErrorLog.record("Kilit açılamadı", it)
        }
    }

    override fun onStart() {
        super.onStart()
        if (!authenticating) vm.onAppForeground()
    }

    override fun onStop() {
        super.onStop()
        if (!authenticating) vm.onAppBackground()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && !vm.locked && vm.keyHandler?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Kilit açıkken "son uygulamalar" önizlemesinde notlar görünmesin.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!vm.appLock)
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
        when (intent?.action) {
            ACTION_QUICK_NOTE -> {
                vm.quickNote()
                return
            }
            ACTION_OPEN_EXAMS -> {
                vm.closeDoc()
                vm.tab = LibraryViewModel.Tab.EXAMS
                return
            }
            ACTION_OPEN_DOC -> {
                intent.getStringExtra(EXTRA_DOC_ID)?.let(vm::openById)
                return
            }
            ACTION_OPEN_HOME, ACTION_OPEN_LIBRARY -> {
                vm.closeDoc()
                vm.tab = if (intent.action == ACTION_OPEN_HOME) LibraryViewModel.Tab.HOME else LibraryViewModel.Tab.LIBRARY
                return
            }
            ACTION_OPEN_WEEK -> {
                vm.closeDoc()
                vm.coursesByWeek = true
                vm.tab = LibraryViewModel.Tab.COURSES
                return
            }
            ACTION_NEW_CANVAS -> {
                vm.newCanvas()
                return
            }
            ACTION_OPEN_ASSISTANT -> {
                vm.closeDoc()
                vm.openAssistant()
                return
            }
        }
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        if (uri != null) vm.importShared(uri)
    }
}

/** Hangi ekranın gösterildiği (geçiş animasyonu için). */
private sealed interface Screen {
    val key: String
    val depth: Int

    data class Doc(val doc: DocRef, val version: Int) : Screen {
        override val key get() = "doc:${doc.id}:$version"
        override val depth get() = 1
    }
    data class Text(val note: TextNote) : Screen {
        override val key get() = "text:${note.id}"
        override val depth get() = 1
    }
    data object Onboarding : Screen {
        override val key get() = "onboarding"
        override val depth get() = 0
    }
    data object Main : Screen {
        override val key get() = "main"
        override val depth get() = 0
    }
}

@Composable
private fun SouNoteApp(vm: LibraryViewModel) {
    val doc = vm.openDoc
    val text = vm.openText
    val screen: Screen = when {
        doc != null -> Screen.Doc(doc, vm.docVersion)
        text != null -> Screen.Text(text)
        !vm.onboarded -> Screen.Onboarding
        else -> Screen.Main
    }
    when (screen) {
        is Screen.Doc -> BackHandler { vm.closeDoc() }
        is Screen.Text -> BackHandler { vm.closeTextNote() }
        else -> Unit
    }
    val motion = LocalMotionLevel.current
    AnimatedContent(
        targetState = screen,
        contentKey = { it.key },
        transitionSpec = {
            // Sayfa ekle/sil sonrası aynı belge yeniden kurulurken animasyon yok.
            val sameDoc = initialState is Screen.Doc && targetState is Screen.Doc &&
                (initialState as Screen.Doc).doc.id == (targetState as Screen.Doc).doc.id
            if (sameDoc) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                Motion.screenTransition<Screen>(targetState.depth >= initialState.depth, motion).invoke(this)
            }
        },
        label = "screen",
    ) { s ->
        when (s) {
            // Sayfa eklenip silinince PDF değişir: görüntüleyici baştan kurulur (key içinde version var).
            is Screen.Doc -> ViewerScreen(s.doc, vm)
            is Screen.Text -> TextNoteScreen(s.note, vm)
            Screen.Onboarding -> OnboardingScreen(vm)
            Screen.Main -> {
                val inLibrary = vm.tab == LibraryViewModel.Tab.LIBRARY
                // Geri: kütüphanede önce üst klasöre, sonra ana sayfaya; ana sayfada uygulamadan çıkar.
                BackHandler(enabled = inLibrary && vm.canNavigateUp) { vm.navigateUp() }
                BackHandler(enabled = vm.tab != LibraryViewModel.Tab.HOME && !(inLibrary && vm.canNavigateUp)) {
                    vm.tab = LibraryViewModel.Tab.HOME
                }
                MainShell(vm)
            }
        }
    }
}
