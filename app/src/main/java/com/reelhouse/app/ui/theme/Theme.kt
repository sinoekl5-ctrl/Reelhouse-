package com.reelhouse.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val CinemaRed = Color(0xFFC62828)

private val LightColors = lightColorScheme(
    primary = CinemaRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF775652),
    background = Color(0xFFFAF6F5),
    onBackground = Color(0xFF201A19),
    surface = Color(0xFFFAF6F5),
    onSurface = Color(0xFF201A19),
    surfaceVariant = Color(0xFFF3E4E1),
    onSurfaceVariant = Color(0xFF534341),
    surfaceContainer = Color(0xFFF6EBE9),
    surfaceContainerHigh = Color(0xFFF0E4E2),
    outline = Color(0xFF857370)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFEF5350),
    onPrimary = Color(0xFF2B0000),
    primaryContainer = Color(0xFF7F1D1D),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFE7BDB8),
    background = Color(0xFF141212),
    onBackground = Color(0xFFEDE0DE),
    surface = Color(0xFF141212),
    onSurface = Color(0xFFEDE0DE),
    surfaceVariant = Color(0xFF3A2F2E),
    onSurfaceVariant = Color(0xFFD8C2BF),
    surfaceContainer = Color(0xFF211D1C),
    surfaceContainerHigh = Color(0xFF2B2726),
    outline = Color(0xFFA08C8A)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** [mode] is "system", "light" or "dark". */
@Composable
fun ReelhouseTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = AppShapes,
        content = content
    )
}
