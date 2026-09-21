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
    primary = jacob,
    onPrimary = dark,
    primaryContainer = lawrence,
    onPrimaryContainer = leah,
    inversePrimary = jacob,
    secondary = remus,
    onSecondary = dark,
    secondaryContainer = lawrence,
    onSecondaryContainer = leah,
    tertiary = laguna,
    onTertiary = claude,
    tertiaryContainer = lawrence,
    onTertiaryContainer = leah,
    background = tyler,
    onBackground = leah,
    surface = lawrence,
    onSurface = leah,
    surfaceVariant = jeremy,
    onSurfaceVariant = bran,
    surfaceTint = jacob,
    inverseSurface = bran,
    inverseOnSurface = claude,
    error = lucian,
    onError = claude,
    errorContainer = lawrence,
    onErrorContainer = leah,
    outline = andy,
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
    primaryFixedDim = jacob,
    onPrimaryFixed = dark,
    onPrimaryFixedVariant = midnight,
    secondaryFixed = greenD,
    secondaryFixedDim = remus,
    onSecondaryFixed = dark,
    onSecondaryFixedVariant = midnight,
    tertiaryFixed = laguna,
    tertiaryFixedDim = laguna,
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
