package dev.espflasher.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.espflasher.app.domain.prefs.Accent
import dev.espflasher.app.domain.prefs.ThemeMode

@Composable
fun EspFlasherTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: Accent = Accent.TEAL,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val dynamicOk = accent == Accent.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        dynamicOk && darkTheme -> dynamicDarkColorScheme(context)
        dynamicOk && !darkTheme -> dynamicLightColorScheme(context)
        else -> accentScheme(if (accent == Accent.DYNAMIC) Accent.TEAL else accent, darkTheme)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
