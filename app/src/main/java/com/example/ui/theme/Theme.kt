package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = PolishedOnBackground,
    surface = PolishedOnBackground,
    onBackground = PolishedBackground,
    onSurface = PolishedBackground
)

private val LightColorScheme = lightColorScheme(
    primary = PolishedPrimary,
    onPrimary = PolishedOnPrimary,
    primaryContainer = PolishedPrimaryContainer,
    onPrimaryContainer = PolishedOnPrimaryContainer,
    secondary = PolishedSecondary,
    onSecondary = PolishedOnSecondary,
    secondaryContainer = PolishedSecondaryContainer,
    onSecondaryContainer = PolishedOnSecondaryContainer,
    tertiary = PolishedTertiary,
    onTertiary = PolishedOnTertiary,
    background = PolishedBackground,
    onBackground = PolishedOnBackground,
    surface = PolishedSurface,
    onSurface = PolishedOnSurface,
    surfaceVariant = PolishedSurfaceVariant,
    onSurfaceVariant = PolishedOnSurfaceVariant,
    outline = PolishedOutline,
    outlineVariant = PolishedOutlineVariant,
    errorContainer = PolishedErrorContainer,
    onErrorContainer = PolishedOnErrorContainer
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Set to false by default to showcase our gorgeous custom palette!
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
