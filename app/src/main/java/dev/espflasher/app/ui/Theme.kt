package dev.espflasher.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Build

private val Teal = Color(0xFF1EC8B0)
private val Ink = Color(0xFF0B1118)
private val SurfaceDark = Color(0xFF141C26)
private val SurfaceLight = Color(0xFFFFFFFF)
private val BgLight = Color(0xFFF3F5F7)

private val DarkColors = darkColorScheme(
    primary = Teal,
    onPrimary = Color(0xFF06241F),
    secondary = Color(0xFF1A2F2C),
    onSecondary = Color(0xFFC6F6EE),
    background = Ink,
    onBackground = Color(0xFFE8EEF4),
    surface = SurfaceDark,
    onSurface = Color(0xFFE8EEF4),
    error = Color(0xFFFF7468),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F7A6C),
    onPrimary = Color(0xFFF4FFFC),
    secondary = Color(0xFFD7EFE9),
    onSecondary = Color(0xFF0B3D36),
    background = BgLight,
    onBackground = Color(0xFF12181F),
    surface = SurfaceLight,
    onSurface = Color(0xFF12181F),
    error = Color(0xFFB42318),
)

@Composable
fun EspFlasherTheme(
    theme: String,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) DarkColors else LightColors

    MaterialTheme(colorScheme = colors, content = content)
}
