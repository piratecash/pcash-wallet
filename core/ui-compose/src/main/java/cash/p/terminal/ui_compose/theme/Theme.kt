package cash.p.terminal.ui_compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RippleConfiguration
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density


val lightPalette = Colors(
    yellow = YellowL,
    remus = GreenL,
    lucian = RedL,
    tyler = BaseL,
    bran = Dark,
    claude = Color.White,
    lawrence = Color.White,
    navigation = NavigationL,
    actionBackground = ActionBackgroundL,
    actionBorder = ActionBorderL,
    divider = DividerL,
    buttonSecondaryFilledBackground = NavigationL,
    buttonSecondaryFilledBorder = SecondaryFilledBorderL,
    textPrimary = TextPrimaryL,
    textSecondary = TextSecondaryL,
    textSecondaryDimmed = TextSecondaryDimmedL,
    textDisabled = TextDisabledL,
    iconPrimary = TextPrimaryL,
    iconSecondary = TextSecondaryL,
    iconDisabled = TextDisabledL,
    brand = BrandL,
    borderAccentSubtle = BorderAccentSubtleL,
    buttonPrimaryBrandContent = TextPrimaryL,
    buttonPrimaryNeutralBackground = TextPrimaryL,
    buttonPrimaryNeutralContent = TextPrimaryD,
    buttonPrimaryDestructiveBackground = DestructiveL,
    buttonPrimaryDestructiveContent = TextPrimaryD,
    buttonPrimaryDisabledBackground = ActionBorderL,
    buttonPrimaryOutlineContent = TextPrimaryL,
    buttonPrimaryOutlineBorder = OutlineBorderL,
    jeremy = SteelLight,
    purple = PurpleL,
    raina = White50,
    blade = Light,
    midnight = Dark,
    modalOverlay = Grey70
)

val darkPalette = Colors(
    yellow = YellowD,
    remus = GreenD,
    lucian = RedD,
    tyler = BaseD,
    bran = LightGrey,
    claude = Dark,
    lawrence = SurfaceD,
    navigation = NavigationD,
    actionBackground = ActionBackgroundD,
    actionBorder = ActionBorderD,
    divider = DividerD,
    buttonSecondaryFilledBackground = SecondaryFilledBackgroundD,
    buttonSecondaryFilledBorder = SecondaryFilledBorderD,
    textPrimary = TextPrimaryD,
    textSecondary = TextSecondaryD,
    textSecondaryDimmed = TextSecondaryDimmedD,
    textDisabled = TextDisabledD,
    iconPrimary = TextPrimaryD,
    iconSecondary = TextSecondaryD,
    iconDisabled = TextDisabledD,
    brand = BrandD,
    borderAccentSubtle = BorderAccentSubtleD,
    buttonPrimaryBrandContent = TextPrimaryL,
    buttonPrimaryNeutralBackground = TextPrimaryD,
    buttonPrimaryNeutralContent = TextPrimaryL,
    buttonPrimaryDestructiveBackground = DestructiveD,
    buttonPrimaryDestructiveContent = TextPrimaryL,
    buttonPrimaryDisabledBackground = ActionBackgroundD,
    buttonPrimaryOutlineContent = TextPrimaryD,
    buttonPrimaryOutlineBorder = TextDisabledD,
    jeremy = Steel20,
    purple = PurpleD,
    raina = Steel10,
    blade = Carbon,
    midnight = DarkGray,
    modalOverlay = Black50
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

    //custom styles
    ProvideLocalAssets(colors = colors, typography = Typography()) {
        //material styles
        MaterialTheme(
            content = content
        )
    }

}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun getRippleConfiguration(): RippleConfiguration =
    if(isSystemInDarkTheme()) {
        RippleConfiguration(
            color = Color.White,
        )
    } else {
        RippleConfiguration(
            color = Color.Black,
        )
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

    val colorPalette = remember {
        // Explicitly creating a new object here so we don't mutate the initial [colors]
        // provided, and overwrite the values set in it.
        colors.copy()
    }
    colorPalette.update(colors)
    val currentDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalColors provides colorPalette,
        LocalTypography provides typography,
        LocalDensity provides Density(currentDensity.density, fontScale = 1f),
        LocalContentColor provides colorPalette.textPrimary,
        content = content
    )
}

val LocalColors = compositionLocalOf<Colors> {
    error("No Colors provided")
}
