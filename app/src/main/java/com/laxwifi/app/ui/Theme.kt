package com.laxwifi.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Magenta = Color(0xFFFF00D4)
val MagentaSoft = Color(0xFFFF7AE8)
val MagentaDeep = Color(0xFF7A0066)

private val LaxColors = darkColorScheme(
    primary = Magenta,
    onPrimary = Color.Black,
    primaryContainer = MagentaDeep,
    onPrimaryContainer = Color(0xFFFFD6F7),
    secondary = MagentaSoft,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF4A0B40),
    onSecondaryContainer = Color(0xFFFFD6F7),
    tertiary = MagentaSoft,
    onTertiary = Color.Black,
    background = Color.Black,
    onBackground = Color(0xFFF5E6F2),
    surface = Color.Black,
    onSurface = Color(0xFFF5E6F2),
    surfaceVariant = Color(0xFF1F0F1C),
    onSurfaceVariant = Color(0xFFC9A6C2),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0E070D),
    surfaceContainer = Color(0xFF140A12),
    surfaceContainerHigh = Color(0xFF1C0E19),
    surfaceContainerHighest = Color(0xFF261322),
    outline = Color(0xFF7A3A6E),
    outlineVariant = Color(0xFF3D1C37),
    error = Color(0xFFFF6B8B),
    onError = Color.Black,
)

@Composable
fun LaxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LaxColors, content = content)
}
