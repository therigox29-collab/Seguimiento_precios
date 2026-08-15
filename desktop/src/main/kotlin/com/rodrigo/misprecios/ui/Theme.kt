package com.rodrigo.misprecios.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Primary = Color(0xFF6750A4)
val PrimaryContainer = Color(0xFFEADDFF)
val OnPrimaryContainer = Color(0xFF21005D)
val SurfaceVariant = Color(0xFFF3EDF7)
val Background = Color(0xFFF4F1F8)
val SuccessGreen = Color(0xFF0F9D58)
val ErrorRed = Color(0xFFD93025)

private val LightColors = lightColorScheme(
    primary = Primary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    surfaceVariant = SurfaceVariant,
    background = Background
)

@Composable
fun MisPreciosTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
