package com.school.hub.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val Indigo = Color(0xFF5B4CF0)
val Pink = Color(0xFFFF4D8D)
val Teal = Color(0xFF00BFA6)
val Gold = Color(0xFFFFB800)

object AppGradients {
    val Violet = listOf(Color(0xFF5B4CF0), Color(0xFFA855F7))
    val Sunset = listOf(Color(0xFFFF7A59), Color(0xFFFF3D77))
    val Ocean = listOf(Color(0xFF00B4DB), Color(0xFF0072FF))
    val Mint = listOf(Color(0xFF11998E), Color(0xFF38EF7D))
    val Candy = listOf(Color(0xFFF953C6), Color(0xFFB91D73))
    val Night = listOf(Color(0xFF232526), Color(0xFF5B4CF0))
}

private val LightColors = lightColorScheme(
    primary = Indigo, onPrimary = Color.White,
    primaryContainer = Color(0xFFE3DFFF), onPrimaryContainer = Color(0xFF16006E),
    secondary = Pink, onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E3), onSecondaryContainer = Color(0xFF3E001D),
    tertiary = Teal, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB8F5EA), onTertiaryContainer = Color(0xFF00201B),
    background = Color(0xFFF7F5FF), onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFF7F5FF), onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE5E1EC), onSurfaceVariant = Color(0xFF5B5966),
    outline = Color(0xFF8A8894), outlineVariant = Color(0xFFD6D3E0),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0EDFC),
    surfaceContainer = Color(0xFFEAE7F7),
    surfaceContainerHigh = Color(0xFFE4E1F2),
    surfaceContainerHighest = Color(0xFFDEDBEC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC5BFFF), onPrimary = Color(0xFF2A1A9E),
    primaryContainer = Color(0xFF4133C9), onPrimaryContainer = Color(0xFFE3DFFF),
    secondary = Color(0xFFFFB1C8), onSecondary = Color(0xFF5E1133),
    secondaryContainer = Color(0xFF7B2949), onSecondaryContainer = Color(0xFFFFD9E3),
    tertiary = Color(0xFF6FF7DE), onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF005045), onTertiaryContainer = Color(0xFFB8F5EA),
    background = Color(0xFF121218), onBackground = Color(0xFFE5E1EA),
    surface = Color(0xFF121218), onSurface = Color(0xFFE5E1EA),
    surfaceVariant = Color(0xFF47464F), onSurfaceVariant = Color(0xFFC8C5D0),
    outline = Color(0xFF928F9A), outlineVariant = Color(0xFF47464F),
    surfaceContainerLowest = Color(0xFF0C0C12),
    surfaceContainerLow = Color(0xFF1B1A22),
    surfaceContainer = Color(0xFF1F1E27),
    surfaceContainerHigh = Color(0xFF2A2931),
    surfaceContainerHighest = Color(0xFF35343C),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun SchoolHubTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
