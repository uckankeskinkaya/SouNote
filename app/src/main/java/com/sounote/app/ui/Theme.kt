package com.sounote.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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

@Composable
fun SouNoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
