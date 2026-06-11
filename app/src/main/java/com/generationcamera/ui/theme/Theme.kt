package com.generationcamera.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Retro-hardware palette: near-black body, brass/amber accents.
val Amber = Color(0xFFE8A33D)
val AmberDim = Color(0xFF8A6324)
val Charcoal = Color(0xFF141210)
val PanelGray = Color(0xFF201D19)
val OffWhite = Color(0xFFEDE6DA)

private val RetroDarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Charcoal,
    secondary = AmberDim,
    background = Charcoal,
    onBackground = OffWhite,
    surface = PanelGray,
    onSurface = OffWhite,
)

@Composable
fun GenerationCameraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RetroDarkColors, content = content)
}
