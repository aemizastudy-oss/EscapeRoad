package com.example.roadescape.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.roadescape.R

object CaseFonts {
    val Serif = FontFamily(Font(R.font.nanum_myeongjo))
}

object CaseColors {
    val Burgundy = Color(0xFF3B0D13)
    val DeepBurgundy = Color(0xFF23080C)
    val Gold = Color(0xFFC8A05C)
    val Cream = Color(0xFFEFE5CC)
    val Ink = Color(0xFF382A1A)
    val Red = Color(0xFF962F37)
    val MutedGold = Color(0xFFBDA988)
}

private val EscapeColors = darkColorScheme(
    primary = CaseColors.Gold, onPrimary = CaseColors.Ink,
    secondary = CaseColors.Cream, onSecondary = CaseColors.Ink,
    background = CaseColors.Burgundy, onBackground = CaseColors.Cream,
    surface = CaseColors.DeepBurgundy, onSurface = CaseColors.Cream,
    surfaceVariant = Color(0xFF59252A), onSurfaceVariant = CaseColors.MutedGold,
    outline = CaseColors.Gold, error = Color(0xFFFFB4A8),
)

@Composable
fun RoadEscapeTheme(content: @Composable () -> Unit) {
    val base = Typography()
    fun serif(style: androidx.compose.ui.text.TextStyle) = style.copy(fontFamily = CaseFonts.Serif)
    val type = Typography(
        displayLarge = serif(base.displayLarge).copy(fontWeight = FontWeight.Medium),
        displayMedium = serif(base.displayMedium), displaySmall = serif(base.displaySmall),
        headlineLarge = serif(base.headlineLarge), headlineMedium = serif(base.headlineMedium), headlineSmall = serif(base.headlineSmall),
        titleLarge = serif(base.titleLarge), titleMedium = serif(base.titleMedium), titleSmall = serif(base.titleSmall),
        bodyLarge = serif(base.bodyLarge).copy(lineHeight = 28.sp), bodyMedium = serif(base.bodyMedium).copy(lineHeight = 24.sp), bodySmall = serif(base.bodySmall),
        labelLarge = serif(base.labelLarge), labelMedium = serif(base.labelMedium), labelSmall = serif(base.labelSmall),
    )
    MaterialTheme(colorScheme = EscapeColors, typography = type, shapes = Shapes(
        small = RoundedCornerShape(1.dp), medium = RoundedCornerShape(2.dp), large = RoundedCornerShape(3.dp),
    ), content = content)
}
