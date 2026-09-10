package com.hospital.supply.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Bg = Color(0xFFE9ECF0)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF12181F)
    val Ink2 = Color(0xFF5A6672)
    val Ink3 = Color(0xFF93A0AC)
    val Line = Color(0x1412181F)
    val StatTop = Color(0xFF1C2530)
    val StatBottom = Color(0xFF101720)
    val FieldBg = Color(0xFFF1F4F7)
    val Danger = Color(0xFFD93A3A)
}

private val Scheme = lightColorScheme(
    primary = Color(0xFF2E86C8),
    onPrimary = Color.White,
    background = Palette.Bg,
    onBackground = Palette.Ink,
    surface = Palette.Card,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.FieldBg,
    onSurfaceVariant = Palette.Ink2,
    outline = Color(0x1F12181F)
)

@Composable
fun SupplyCounterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
