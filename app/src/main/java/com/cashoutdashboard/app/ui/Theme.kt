package com.cashoutdashboard.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// One fixed brand palette (money green on calm neutrals) so the app looks the same on every phone,
// instead of taking on whatever colors the wallpaper suggests. Cards are the lightest surface.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0F7B55),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F1E2),
    onPrimaryContainer = Color(0xFF00391F),
    inversePrimary = Color(0xFF7FD9AE),
    secondary = Color(0xFF4A5A52),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5ECE8),
    onSecondaryContainer = Color(0xFF1B2620),
    tertiary = Color(0xFFA65A12),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFCE8D4),
    onTertiaryContainer = Color(0xFF4A2500),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFE2DE),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF3F5F4),
    onBackground = Color(0xFF151917),
    surface = Color(0xFFF3F5F4),
    onSurface = Color(0xFF151917),
    surfaceVariant = Color(0xFFE2E7E4),
    onSurfaceVariant = Color(0xFF5A635E),
    surfaceTint = Color(0xFF0F7B55),
    inverseSurface = Color(0xFF2B302D),
    inverseOnSurface = Color(0xFFEFF2EF),
    outline = Color(0xFF89928D),
    outlineVariant = Color(0xFFDDE3DF),
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFD9DEDB),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEDF0EE),
    surfaceContainerHigh = Color(0xFFE8ECEA),
    surfaceContainerHighest = Color(0xFFE1E6E3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FD6A5),
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF0B5237),
    onPrimaryContainer = Color(0xFFC8F2DC),
    inversePrimary = Color(0xFF0F7B55),
    secondary = Color(0xFFB3C3BA),
    onSecondary = Color(0xFF1E2A24),
    secondaryContainer = Color(0xFF29322D),
    onSecondaryContainer = Color(0xFFD5E0D9),
    tertiary = Color(0xFFF2B27A),
    onTertiary = Color(0xFF4A2500),
    tertiaryContainer = Color(0xFF5A3510),
    onTertiaryContainer = Color(0xFFFFDDBF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0D100F),
    onBackground = Color(0xFFE1E5E2),
    surface = Color(0xFF0D100F),
    onSurface = Color(0xFFE1E5E2),
    surfaceVariant = Color(0xFF39413D),
    onSurfaceVariant = Color(0xFFA8B2AC),
    surfaceTint = Color(0xFF6FD6A5),
    inverseSurface = Color(0xFFE1E5E2),
    inverseOnSurface = Color(0xFF2B302D),
    outline = Color(0xFF7A847F),
    outlineVariant = Color(0xFF2B3230),
    scrim = Color.Black,
    surfaceBright = Color(0xFF333936),
    surfaceDim = Color(0xFF0D100F),
    surfaceContainerLowest = Color(0xFF090B0A),
    surfaceContainerLow = Color(0xFF171B19),
    surfaceContainer = Color(0xFF1B201D),
    surfaceContainerHigh = Color(0xFF232926),
    surfaceContainerHighest = Color(0xFF2C322F),
)

/** Tabular figures keep money columns and changing numbers from jittering sideways. */
private fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

private fun TextStyle.heading(tracking: Double) =
    tabular().copy(fontWeight = FontWeight.SemiBold, letterSpacing = tracking.sp)

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.heading(-1.0),
        displayMedium = displayMedium.heading(-0.75),
        displaySmall = displaySmall.heading(-0.5),
        headlineLarge = headlineLarge.heading(-0.5),
        headlineMedium = headlineMedium.heading(-0.5),
        headlineSmall = headlineSmall.heading(-0.25),
        titleLarge = titleLarge.heading(-0.2),
        titleMedium = titleMedium.heading(0.0),
        titleSmall = titleSmall.heading(0.0),
        bodyLarge = bodyLarge.tabular(),
        bodyMedium = bodyMedium.tabular(),
        bodySmall = bodySmall.tabular(),
        labelLarge = labelLarge.tabular(),
        labelMedium = labelMedium.tabular(),
        labelSmall = labelSmall.tabular(),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun CashoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

private val ColorScheme.isLight get() = background.luminance() > 0.5f

/** Colors for the one bold, brand-colored surface on a screen (the dashboard hero, a shift's header). */
object HeroColors {
    val container: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.run { if (isLight) primary else primaryContainer }
    val content: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.run { if (isLight) onPrimary else onPrimaryContainer }
}
