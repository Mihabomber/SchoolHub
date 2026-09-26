package com.school.hub.core.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import com.school.hub.R

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.unit.sp

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
    primaryContainer = Color(0xFFE6E2FF), onPrimaryContainer = Color(0xFF16006E),
    secondary = Pink, onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDE7), onSecondaryContainer = Color(0xFF3E001D),
    tertiary = Teal, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC2F7EC), onTertiaryContainer = Color(0xFF00201B),
    background = Color(0xFFF8F7FC), onBackground = Color(0xFF17161D),
    surface = Color(0xFFF8F7FC), onSurface = Color(0xFF17161D),
    surfaceVariant = Color(0xFFE7E4EF), onSurfaceVariant = Color(0xFF5A5866),
    outline = Color(0xFF8C8A96), outlineVariant = Color(0xFFDCD9E5),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF1EFF8),
    surfaceContainerHigh = Color(0xFFEBE9F4),
    surfaceContainerHighest = Color(0xFFE4E2EE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBDB5FF), onPrimary = Color(0xFF24168F),
    primaryContainer = Color(0xFF3F31C4), onPrimaryContainer = Color(0xFFE6E2FF),
    secondary = Color(0xFFFFB1C8), onSecondary = Color(0xFF5E1133),
    secondaryContainer = Color(0xFF7B2949), onSecondaryContainer = Color(0xFFFFD9E3),
    tertiary = Color(0xFF6FF7DE), onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF005045), onTertiaryContainer = Color(0xFFB8F5EA),
    background = Color(0xFF0F0E14), onBackground = Color(0xFFE8E5EE),
    surface = Color(0xFF0F0E14), onSurface = Color(0xFFE8E5EE),
    surfaceVariant = Color(0xFF3F3D49), onSurfaceVariant = Color(0xFFC9C6D2),
    outline = Color(0xFF8F8C99), outlineVariant = Color(0xFF34323D),
    surfaceContainerLowest = Color(0xFF0A0910),
    surfaceContainerLow = Color(0xFF17161E),
    surfaceContainer = Color(0xFF1C1B24),
    surfaceContainerHigh = Color(0xFF25242E),
    surfaceContainerHighest = Color(0xFF302F39),
)

val AppSans = FontFamily(
    Font(R.font.app_sans_regular, FontWeight.Normal),
    Font(R.font.app_sans_medium, FontWeight.Medium),
    Font(R.font.app_sans_semibold, FontWeight.SemiBold),
    Font(R.font.app_sans_bold, FontWeight.Bold),
    Font(R.font.app_sans_extrabold, FontWeight.ExtraBold),
)
val AppSerif = FontFamily(Font(R.font.app_serif_regular, FontWeight.Normal), Font(R.font.app_serif_bold, FontWeight.Bold))
val AppMono = FontFamily(Font(R.font.app_mono_regular, FontWeight.Normal), Font(R.font.app_mono_bold, FontWeight.Bold))

/** Короткие алиасы семейств (используются экранами: AppFonts.mono и т.д.). */
object AppFonts {
    val sans = AppSans
    val serif = AppSerif
    val mono = AppMono
}

/** 0 — Noto Sans (фирменный), 1 — с засечками, 2 — моноширинный, 3 — системный. */
val FontChoices = listOf("Фирменный", "С засечками", "Моно", "Системный")

private fun typography(family: FontFamily?): Typography {
    val t = Typography()
    fun TextStyle.f(w: FontWeight? = null, ls: Float? = null) = copy(
        fontFamily = family ?: fontFamily,
        fontWeight = w ?: fontWeight,
        letterSpacing = ls?.sp ?: letterSpacing,
    )
    return t.copy(
        displayLarge = t.displayLarge.f(FontWeight.ExtraBold, -1.0f), displayMedium = t.displayMedium.f(FontWeight.ExtraBold, -0.8f), displaySmall = t.displaySmall.f(FontWeight.Bold, -0.5f),
        headlineLarge = t.headlineLarge.f(FontWeight.ExtraBold, -0.6f), headlineMedium = t.headlineMedium.f(FontWeight.ExtraBold, -0.4f), headlineSmall = t.headlineSmall.f(FontWeight.Bold, -0.2f),
        titleLarge = t.titleLarge.f(FontWeight.Bold, -0.2f), titleMedium = t.titleMedium.f(FontWeight.SemiBold, 0f), titleSmall = t.titleSmall.f(FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.f().copy(lineHeight = 25.sp), bodyMedium = t.bodyMedium.f().copy(lineHeight = 21.sp), bodySmall = t.bodySmall.f(),
        labelLarge = t.labelLarge.f(FontWeight.SemiBold), labelMedium = t.labelMedium.f(FontWeight.Medium), labelSmall = t.labelSmall.f(FontWeight.Medium),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/** Включён ли режим «жидкое стекло». */
val LocalGlass = staticCompositionLocalOf { false }
val LocalDark = staticCompositionLocalOf { false }

/** Полупрозрачные поверхности — сквозь них видно живой цветной фон. */
private fun ColorScheme.glassy(dark: Boolean): ColorScheme {
    val base = if (dark) Color(0xFF15131F) else Color(0xFFFFFFFF)
    return copy(
        background = Color.Transparent,
        surface = base.copy(alpha = .35f),
        surfaceContainerLowest = base.copy(alpha = .30f),
        surfaceContainerLow = base.copy(alpha = .40f),
        surfaceContainer = base.copy(alpha = .50f),
        surfaceContainerHigh = base.copy(alpha = .80f),
        surfaceContainerHighest = base.copy(alpha = .55f),
        surfaceVariant = base.copy(alpha = .45f),
    )
}

@Composable
fun SchoolHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    glass: Boolean = false,
    font: Int = 0,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val base = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val scheme = if (glass) base.glassy(darkTheme) else base
    val family = when (font) { 1 -> AppSerif; 2 -> AppMono; 3 -> null; else -> AppSans }
    CompositionLocalProvider(LocalGlass provides glass, LocalDark provides darkTheme) {
        MaterialTheme(colorScheme = scheme, typography = typography(family), shapes = AppShapes) {
            if (glass) LiquidBackground(darkTheme) { content() } else content()
        }
    }
}

/** Живой фон из плавающих цветных «капель» — основа эффекта жидкого стекла. */
@Composable
fun LiquidBackground(dark: Boolean, content: @Composable () -> Unit) {
    val t = rememberInfiniteTransition(label = "liquid")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(23_000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val bg = if (dark) listOf(Color(0xFF0B0A14), Color(0xFF1A1233)) else listOf(Color(0xFFEDEBFF), Color(0xFFFFE9F2))
    val blobs = if (dark) listOf(Color(0xFF5B4CF0), Color(0xFFFF3D77), Color(0xFF00BFA6), Color(0xFFA855F7))
    else listOf(Color(0xFF8C7CFF), Color(0xFFFF8DB4), Color(0xFF5CE1CF), Color(0xFFFFC85C))
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(bg))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height; val r = maxOf(w, h) * .55f
            val pts = listOf(
                Offset(w * (.1f + .6f * a), h * (.15f + .2f * b)),
                Offset(w * (.9f - .5f * b), h * (.45f + .2f * a)),
                Offset(w * (.2f + .5f * b), h * (.85f - .25f * a)),
                Offset(w * (.8f - .3f * a), h * (.05f + .3f * b)),
            )
            pts.forEachIndexed { i, p ->
                drawCircle(Brush.radialGradient(listOf(blobs[i].copy(alpha = if (dark) .55f else .6f), Color.Transparent), p, r), r, p)
            }
        }
        content()
    }
}

/** Карточка: в режиме стекла — матовая полупрозрачная с бликом по краю, иначе аккуратная плоская карточка с тонкой рамкой. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    forceGlass: Boolean = false,
    content: @Composable () -> Unit,
) {
    val glass = LocalGlass.current || forceGlass
    if (!glass) {
        Card(
            modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f)),
        ) { content() }
        return
    }
    val dark = LocalDark.current
    val fill = if (dark) listOf(Color.White.copy(alpha = .14f), Color.White.copy(alpha = .05f))
    else listOf(Color.White.copy(alpha = .72f), Color.White.copy(alpha = .45f))
    Box(
        modifier.clip(shape)
            .background(Brush.linearGradient(fill))
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .75f), Color.White.copy(alpha = .08f))), shape),
    ) { content() }
}
