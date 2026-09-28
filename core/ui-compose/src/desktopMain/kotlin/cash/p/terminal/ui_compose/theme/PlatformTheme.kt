package cash.p.terminal.ui_compose.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography as MaterialTypography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
internal actual fun PlatformMaterialTheme(content: @Composable () -> Unit) {
    val typography = ComposeAppTheme.typography
    MaterialTheme(
        colorScheme = ComposeAppTheme.colors.toMaterialColorScheme(),
        typography = typography.toMaterialTypography(),
    ) {
        CompositionLocalProvider(LocalTextStyle provides typography.body, content = content)
    }
}

private fun Colors.toMaterialColorScheme() = ColorScheme(
    primary = brand,
    onPrimary = dark,
    primaryContainer = lawrence,
    onPrimaryContainer = textPrimary,
    inversePrimary = brand,
    secondary = remus,
    onSecondary = dark,
    secondaryContainer = lawrence,
    onSecondaryContainer = textPrimary,
    tertiary = brand,
    onTertiary = claude,
    tertiaryContainer = lawrence,
    onTertiaryContainer = textPrimary,
    background = tyler,
    onBackground = textPrimary,
    surface = lawrence,
    onSurface = textPrimary,
    surfaceVariant = jeremy,
    onSurfaceVariant = bran,
    surfaceTint = brand,
    inverseSurface = bran,
    inverseOnSurface = claude,
    error = lucian,
    onError = claude,
    errorContainer = lawrence,
    onErrorContainer = textPrimary,
    outline = divider,
    outlineVariant = blade,
    scrim = modalOverlay,
    surfaceBright = lawrence,
    surfaceDim = tyler,
    surfaceContainer = lawrence,
    surfaceContainerHigh = jeremy,
    surfaceContainerHighest = blade,
    surfaceContainerLow = lawrence,
    surfaceContainerLowest = tyler,
    primaryFixed = yellowD,
    primaryFixedDim = brand,
    onPrimaryFixed = dark,
    onPrimaryFixedVariant = midnight,
    secondaryFixed = greenD,
    secondaryFixedDim = remus,
    onSecondaryFixed = dark,
    onSecondaryFixedVariant = midnight,
    tertiaryFixed = brand,
    tertiaryFixedDim = brand,
    onTertiaryFixed = claude,
    onTertiaryFixedVariant = bran,
)

private fun Typography.toMaterialTypography() = MaterialTypography(
    displayLarge = title1,
    displayMedium = title2,
    displaySmall = title2R,
    headlineLarge = title2,
    headlineMedium = title3,
    headlineSmall = headline1,
    titleLarge = title3,
    titleMedium = headline2,
    titleSmall = subhead1,
    bodyLarge = body,
    bodyMedium = subhead2,
    bodySmall = caption,
    labelLarge = subhead1,
    labelMedium = captionSB,
    labelSmall = microSB,
)
