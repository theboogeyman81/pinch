package com.pinch.gary.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val GaryDarkColorScheme = darkColorScheme(
    background = GaryBackground,
    surface = GarySurface,
    onSurface = GaryOnSurface,
    primary = GaryAccentListening
)

private val GaryLightColorScheme = lightColorScheme(
    primary = GaryAccentListening
)

/**
 * Gary is designed dark-first ("a presence, not an app"), but still respects
 * system theme rather than forcing dark mode on the user.
 */
@Composable
fun GaryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) GaryDarkColorScheme else GaryLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GaryTypography,
        shapes = GaryShapes,
        content = content
    )
}
