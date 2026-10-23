package com.sounote.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * Sabit tema. Sistemin dinamik renkleri (HyperOS) bazı yüzeyleri renkli/düşük kontrastlı
 * yapıyordu; kalem renkleri ve yazılar seçilemiyordu. Nötr gri yüzeyler + mavi vurgu.
 */

private val Light = lightColorScheme(
    primary = Color(0xFF2F5BD3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF0A2470),
    secondary = Color(0xFF555F71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E7F0),
    onSecondaryContainer = Color(0xFF1B2333),
    tertiary = Color(0xFF8A5100),
    background = Color(0xFFF4F5F8),
    onBackground = Color(0xFF1A1C20),
    surface = Color(0xFFFAFBFD),
    onSurface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFFE6E8EE),
    onSurfaceVariant = Color(0xFF4A4E57),
    surfaceTint = Color(0xFF2F5BD3),
    outline = Color(0xFF7A7F89),
    outlineVariant = Color(0xFFC9CCD3),
    surfaceBright = Color(0xFFFAFBFD),
    surfaceDim = Color(0xFFDADCE1),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F5F8),
    surfaceContainer = Color(0xFFEEF0F4),
    surfaceContainerHigh = Color(0xFFE8EAEF),
    surfaceContainerHighest = Color(0xFFE2E4E9),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA9BEFF),
    onPrimary = Color(0xFF0B2A73),
    primaryContainer = Color(0xFF23439F),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFFBCC6DC),
    onSecondary = Color(0xFF263041),
    secondaryContainer = Color(0xFF3A4252),
    onSecondaryContainer = Color(0xFFDCE2F0),
    tertiary = Color(0xFFFFB86B),
    background = Color(0xFF111316),
    onBackground = Color(0xFFE3E5EA),
    surface = Color(0xFF16181C),
    onSurface = Color(0xFFE3E5EA),
    surfaceVariant = Color(0xFF2A2D34),
    onSurfaceVariant = Color(0xFFC2C6CF),
    surfaceTint = Color(0xFFA9BEFF),
    outline = Color(0xFF8D929C),
    outlineVariant = Color(0xFF50545E),
    surfaceBright = Color(0xFF3A3D44),
    surfaceDim = Color(0xFF111316),
    surfaceContainerLowest = Color(0xFF0D0F12),
    surfaceContainerLow = Color(0xFF1A1C20),
    surfaceContainer = Color(0xFF1F2125),
    surfaceContainerHigh = Color(0xFF292B30),
    surfaceContainerHighest = Color(0xFF34363B),
)

/** Vurgu rengi seçenekleri (yüzeyler nötr kalır, sadece vurgu değişir). */
enum class ColorTheme(
    val label: String,
    val lightPrimary: Long, val lightContainer: Long, val lightOnContainer: Long,
    val darkPrimary: Long, val darkOnPrimary: Long, val darkContainer: Long, val darkOnContainer: Long,
) {
    BLUE("Mavi", 0xFF2F5BD3, 0xFFDCE4FF, 0xFF0A2470, 0xFFA9BEFF, 0xFF0B2A73, 0xFF23439F, 0xFFDCE4FF),
    GREEN("Yeşil", 0xFF2E7D4F, 0xFFCDEFD8, 0xFF0B3A1F, 0xFF8FD9A8, 0xFF0A3A1E, 0xFF1E5C39, 0xFFCDEFD8),
    TEAL("Turkuaz", 0xFF00796B, 0xFFC4EEE8, 0xFF00332D, 0xFF7FD6CB, 0xFF00382F, 0xFF005048, 0xFFC4EEE8),
    PURPLE("Mor", 0xFF7048C8, 0xFFE8DDFF, 0xFF2A0F66, 0xFFCDB8FF, 0xFF3A1580, 0xFF5531A8, 0xFFE8DDFF),
    PINK("Pembe", 0xFFC2357A, 0xFFFFD8E8, 0xFF4D0A2C, 0xFFFFAFCF, 0xFF5E0F37, 0xFF8F2358, 0xFFFFD8E8),
    RED("Kırmızı", 0xFFC62828, 0xFFFFDAD6, 0xFF410002, 0xFFFFB4AB, 0xFF690005, 0xFF93000A, 0xFFFFDAD6),
    ORANGE("Turuncu", 0xFFC2570C, 0xFFFFDCC6, 0xFF4A1A00, 0xFFFFB68A, 0xFF5A2200, 0xFF8A3F08, 0xFFFFDCC6),
    GRAPHITE("Grafit", 0xFF3C4350, 0xFFDDE2EA, 0xFF141B26, 0xFFC3CAD6, 0xFF2A313D, 0xFF444C5A, 0xFFDDE2EA),
    ;

    /** Ana sayfa karşılama kartının degradesi. */
    val heroColors get() = listOf(Color(lightPrimary), Color(lightOnContainer))
    val swatch get() = Color(lightPrimary)
}

/** Seçili vurgu rengi (ana sayfa karşılama kartı gibi yerler için). */
val LocalColorTheme = staticCompositionLocalOf { ColorTheme.BLUE }

private fun ColorTheme.light(): ColorScheme = Light.copy(
    primary = Color(lightPrimary),
    primaryContainer = Color(lightContainer),
    onPrimaryContainer = Color(lightOnContainer),
    surfaceTint = Color(lightPrimary),
)

private fun ColorTheme.dark(): ColorScheme = Dark.copy(
    primary = Color(darkPrimary),
    onPrimary = Color(darkOnPrimary),
    primaryContainer = Color(darkContainer),
    onPrimaryContainer = Color(darkOnContainer),
    surfaceTint = Color(darkPrimary),
)

/** [dark] null ise sistem ayarı izlenir. */
@Composable
fun SouNoteTheme(dark: Boolean? = null, color: ColorTheme = ColorTheme.BLUE, content: @Composable () -> Unit) {
    val scheme = if (dark ?: isSystemInDarkTheme()) color.dark() else color.light()
    CompositionLocalProvider(LocalColorTheme provides color) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
