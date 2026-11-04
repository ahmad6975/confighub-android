package com.confighub.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ConfigHub panel theme: dark blue + dark green, not too dark.
// Same palette as the simulator panel.
val DarkBlue = Color(0xFF1D4E66)
val DarkGreen = Color(0xFF14402F)
val PanelText = Color(0xFFEDF7F3)
val AccentTeal = Color(0xFF2DD4BF)
val AccentGreen = Color(0xFF22C55E)

private val ConfigHubScheme = darkColorScheme(
    primary = AccentTeal,
    secondary = AccentGreen,
    background = Color(0xFF0F2A33),
    surface = DarkBlue,
    onBackground = PanelText,
    onSurface = PanelText,
)

@Composable
fun ConfigHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ConfigHubScheme, content = content)
}
