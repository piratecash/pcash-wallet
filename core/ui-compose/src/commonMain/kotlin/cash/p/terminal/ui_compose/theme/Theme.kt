package cash.p.terminal.ui_compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density


val lightPalette = Colors(
    backgroundBase = BaseL,
    backgroundNavigation = NavigationL,
    backgroundOverlay = OverlayL,
    surfacePrimary = Color.White,
    surfaceElevated = Color.White,
    surfacePlaceholder = Steel10,
    controlActionBackground = ActionBackgroundL,
    controlActionBorder = ActionBorderL,
    controlTrack = Steel20,
    brandDefault = BrandL,
    statusSuccess = StatusSuccessL,
    statusWarning = StatusWarningL,
    statusError = StatusErrorL,
    statusSuccess20 = StatusSuccessL.copy(alpha = 0.2f),
    statusWarning20 = StatusWarningL.copy(alpha = 0.2f),
    statusError20 = StatusErrorL.copy(alpha = 0.2f),
    statusSuccess50 = StatusSuccessL.copy(alpha = 0.5f),
    statusWarning50 = StatusWarningL.copy(alpha = 0.5f),
    statusError50 = StatusErrorL.copy(alpha = 0.5f),
    borderDefault = BorderDefaultL,
    borderDivider = BorderDividerL,
    borderAccentSubtle = BrandL.copy(alpha = 0.32f),
    textPrimary = TextPrimaryL,
    textSecondary = TextSecondaryL,
    textSecondaryDimmed = TextSecondaryL.copy(alpha = 0.5f),
    textDisabled = TextDisabledL,
    iconPrimary = TextPrimaryL,
    iconSecondary = TextSecondaryL,
    iconDisabled = TextDisabledL,
    buttonPrimaryBrandContent = TextPrimaryL,
    buttonPrimaryNeutralBackground = TextPrimaryL,
    buttonPrimaryNeutralContent = TextPrimaryD,
    buttonPrimaryDestructiveBackground = StatusErrorL,
    buttonPrimaryDestructiveContent = TextPrimaryD,
    buttonPrimaryDisabledBackground = ActionBorderL,
    buttonPrimaryOutlineContent = TextPrimaryL,
    buttonPrimaryOutlineBorder = OutlineBorderL,
    buttonSecondaryFilledBackground = NavigationL,
    buttonSecondaryFilledBorder = SecondaryFilledBorderL,
    badgeBackground = SteelLight,
    contentInverse = Color.White,
    switchThumbChecked = Color.White,
    switchThumbUnchecked = LightGrey,
    switchTrackUnchecked = SwitchTrackUnchecked,
    qrBackground = Color.White,
    scannerBackground = Dark,
    snackbarNeutralBackground = TextSecondaryL,
    contentOnColor = Color.White,
)

val darkPalette = Colors(
    backgroundBase = BaseD,
    backgroundNavigation = NavigationD,
    backgroundOverlay = OverlayD,
    surfacePrimary = SurfaceD,
    surfaceElevated = SurfaceElevatedD,
    surfacePlaceholder = Steel10,
    controlActionBackground = ActionBackgroundD,
    controlActionBorder = ActionBorderD,
    controlTrack = Steel20,
    brandDefault = BrandD,
    statusSuccess = StatusSuccessD,
    statusWarning = StatusWarningD,
    statusError = StatusErrorD,
    statusSuccess20 = StatusSuccessD.copy(alpha = 0.2f),
    statusWarning20 = StatusWarningD.copy(alpha = 0.2f),
    statusError20 = StatusErrorD.copy(alpha = 0.2f),
    statusSuccess50 = StatusSuccessD.copy(alpha = 0.5f),
    statusWarning50 = StatusWarningD.copy(alpha = 0.5f),
    statusError50 = StatusErrorD.copy(alpha = 0.5f),
    borderDefault = BorderDefaultD,
    borderDivider = BorderDividerD,
    borderAccentSubtle = BrandD.copy(alpha = 0.32f),
    textPrimary = TextPrimaryD,
    textSecondary = TextSecondaryD,
    textSecondaryDimmed = TextSecondaryD.copy(alpha = 0.5f),
    textDisabled = TextDisabledD,
    iconPrimary = TextPrimaryD,
    iconSecondary = TextSecondaryD,
    iconDisabled = TextDisabledD,
    buttonPrimaryBrandContent = TextPrimaryL,
    buttonPrimaryNeutralBackground = TextPrimaryD,
    buttonPrimaryNeutralContent = TextPrimaryL,
    buttonPrimaryDestructiveBackground = StatusErrorD,
    buttonPrimaryDestructiveContent = TextPrimaryL,
    buttonPrimaryDisabledBackground = ActionBackgroundD,
    buttonPrimaryOutlineContent = TextPrimaryD,
    buttonPrimaryOutlineBorder = TextDisabledD,
    buttonSecondaryFilledBackground = SecondaryFilledBackgroundD,
    buttonSecondaryFilledBorder = SecondaryFilledBorderD,
    badgeBackground = Steel20,
    contentInverse = Dark,
    switchThumbChecked = Color.White,
    switchThumbUnchecked = LightGrey,
    switchTrackUnchecked = SwitchTrackUnchecked,
    qrBackground = Color.White,
    scannerBackground = Dark,
    snackbarNeutralBackground = TextSecondaryD,
    contentOnColor = Color.White,
)

@Composable
fun ComposeAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable() () -> Unit
) {

    val colors = if (darkTheme) {
        darkPalette
    } else {
        lightPalette
    }

    ProvideLocalAssets(colors = colors, typography = Typography()) {
        PlatformMaterialTheme(content)
    }
}

object ComposeAppTheme {
    val colors: Colors
        @Composable
        get() = LocalColors.current

    val typography: Typography
        @Composable
        get() = LocalTypography.current
}

@Composable
fun ProvideLocalAssets(
    colors: Colors,
    typography: Typography,
    content: @Composable () -> Unit
) {

    val currentDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalTypography provides typography,
        LocalDensity provides Density(currentDensity.density, fontScale = 1f),
        LocalContentColor provides colors.textPrimary,
        content = content
    )
}

val LocalColors = staticCompositionLocalOf<Colors> {
    error("No Colors provided")
}

@Composable
internal expect fun PlatformMaterialTheme(content: @Composable () -> Unit)
