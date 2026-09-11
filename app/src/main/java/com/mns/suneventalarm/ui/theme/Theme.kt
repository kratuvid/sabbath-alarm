package com.mns.suneventalarm.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    background = androidx.compose.ui.graphics.Color(0xFF000000),
    surface = androidx.compose.ui.graphics.Color(0xFF121212),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
    primary = androidx.compose.ui.graphics.Color(0xFFA8C7FA),
    onBackground = androidx.compose.ui.graphics.Color(0xFFE3E3E3),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE3E3E3)
)

private val LightColorScheme = lightColorScheme(
    background = androidx.compose.ui.graphics.Color(0xFFFDFDFD),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFF0F0F0),
    primary = androidx.compose.ui.graphics.Color(0xFF0B57D0),
    onBackground = androidx.compose.ui.graphics.Color(0xFF1F1F1F),
    onSurface = androidx.compose.ui.graphics.Color(0xFF1F1F1F)
)

@Composable
fun SabbathAlarmTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
