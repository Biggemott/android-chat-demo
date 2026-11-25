// Palette entries are defined directly by their ARGB values.
@file:Suppress("MagicNumber")

package com.biggemott.chatdemo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = Color(0xFFC01D5D),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD91C65),
        onPrimaryContainer = Color.White,
        secondaryContainer = Color(0xFFEDF0F4),
        onSecondaryContainer = Color(0xFF28323E),
        tertiaryContainer = Color(0xFFF5DCE5),
        onTertiaryContainer = Color(0xFF692341),
        surface = Color(0xFFFFFCFD),
        surfaceVariant = Color(0xFFF3E9ED),
        onSurface = Color(0xFF20202A),
        onSurfaceVariant = Color(0xFF61616D),
        outline = Color(0xFF77747E),
        outlineVariant = Color(0xFFE7E0E4),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFFF9CC1),
        onPrimary = Color(0xFF57002A),
        primaryContainer = Color(0xFF94234E),
        onPrimaryContainer = Color(0xFFFFF0F5),
        secondaryContainer = Color(0xFF30313C),
        onSecondaryContainer = Color(0xFFEEEFF6),
        tertiaryContainer = Color(0xFF51273A),
        onTertiaryContainer = Color(0xFFFFD9E7),
        surface = Color(0xFF17161D),
        surfaceVariant = Color(0xFF342A31),
        onSurface = Color(0xFFF3EDF1),
        onSurfaceVariant = Color(0xFFC3B7C0),
        outline = Color(0xFFAAA0AA),
        outlineVariant = Color(0xFF423742),
    )

@Composable
fun ChatDemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
