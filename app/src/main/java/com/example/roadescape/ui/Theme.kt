package com.example.roadescape.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val EscapeColors = darkColorScheme(
    primary = Color(0xFF71E2CD), onPrimary = Color(0xFF073C35),
    secondary = Color(0xFFFFCB7A), onSecondary = Color(0xFF422C09),
    background = Color(0xFF0D1723), onBackground = Color(0xFFEAF0F7),
    surface = Color(0xFF152331), onSurface = Color(0xFFEAF0F7),
    surfaceVariant = Color(0xFF213342), onSurfaceVariant = Color(0xFFB2C2CF),
    outline = Color(0xFF476071), error = Color(0xFFFFB4AB),
)

@Composable
fun RoadEscapeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EscapeColors, shapes = Shapes(
        small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
    ), content = content)
}
