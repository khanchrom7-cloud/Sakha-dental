package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val SakhaLightColorScheme = lightColorScheme(
    primary = RoyalBlue,
    onPrimary = SoftWhite,
    primaryContainer = RoyalBlueLight,
    onPrimaryContainer = SoftWhite,
    secondary = GoldAccent,
    onSecondary = RoyalBlueDark,
    secondaryContainer = GoldAccentSoft,
    onSecondaryContainer = RoyalBlueDark,
    tertiary = GoldDark,
    onTertiary = SoftWhite,
    background = SoftWhite,
    onBackground = RoyalBlueDark,
    surface = PureWhite,
    onSurface = RoyalBlueDark,
    surfaceVariant = SurfaceAlt,
    onSurfaceVariant = SlateMuted,
    outline = LightGray,
    error = ErrorCrimson,
    onError = PureWhite,
    errorContainer = ErrorContainer,
    onErrorContainer = ErrorCrimson
)

private val SakhaDarkColorScheme = darkColorScheme(
    primary = GoldAccent,
    onPrimary = RoyalBlueDark,
    primaryContainer = RoyalBlueLight,
    onPrimaryContainer = SoftWhite,
    secondary = RoyalBlueLight,
    onSecondary = SoftWhite,
    secondaryContainer = RoyalBlue,
    onSecondaryContainer = GoldAccentSoft,
    background = RoyalBlueDark,
    onBackground = SoftWhite,
    surface = RoyalBlue,
    onSurface = SoftWhite,
    surfaceVariant = RoyalBlueLight,
    onSurfaceVariant = LightGray,
    outline = SlateMuted,
    error = ErrorCrimson,
    onError = PureWhite
)

private val SakhaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SakhaDentalTheme(
    darkTheme: Boolean = false,
    isRtlLanguage: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SakhaDarkColorScheme else SakhaLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = buildSakhaTypography(isRtlLanguage = isRtlLanguage),
        shapes = SakhaShapes,
        content = content
    )
}
