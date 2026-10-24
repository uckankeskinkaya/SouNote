package com.sounote.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sounote.app.R

/** Uygulamanın marka renkleri (simge ve açılış ekranıyla aynı). */
object Brand {
    val Blue = Color(0xFF4A7BEA)
    val Deep = Color(0xFF1E3A8A)
    val Yellow = Color(0xFFF7B928)
    val gradient get() = Brush.linearGradient(listOf(Blue, Deep))
}

/** Uygulama logosu ("A. Dengeli"): gece mavisi kare içinde kâğıt ve dolma kalem (site ve simgeyle aynı). */
@Composable
fun AppLogo(size: Dp, modifier: Modifier = Modifier, elevated: Boolean = true) {
    val shape = RoundedCornerShape(size * 0.22f)
    Image(
        painterResource(R.drawable.logo_tile),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .then(if (elevated) Modifier.shadow(size * 0.12f, shape) else Modifier)
            .clip(shape),
    )
}
