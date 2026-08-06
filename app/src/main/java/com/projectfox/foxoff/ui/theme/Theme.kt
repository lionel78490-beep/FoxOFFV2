package com.projectfox.foxoff.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = FoxElectricBlue,
    secondary = FoxElectricViolet,
    tertiary = FoxElectricBlue,
    background = FoxBlack,
    surface = FoxBlackSurface,
    onPrimary = FoxWhite,
    onBackground = FoxWhite,
    onSurface = FoxWhite,
    error = FoxHeartRed
)

@Composable
fun FoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
