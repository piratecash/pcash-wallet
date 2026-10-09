package cash.p.terminal.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.sp
import androidx.glance.color.ColorProvider
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import cash.p.terminal.ui_compose.theme.darkPalette
import cash.p.terminal.ui_compose.theme.lightPalette

object AppWidgetTheme {
    val colors: ColorProviders
        @Composable
        @ReadOnlyComposable
        get() = LocalColorProviders.current

    val textStyles: TextStyles = TextStyles()
}

class TextStyles {
    @Composable
    fun c3(textAlign: TextAlign = TextAlign.Start) =
        TextStyle(
            color = AppWidgetTheme.colors.brandDefault,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = textAlign
        )

    @Composable
    fun d1(textAlign: TextAlign = TextAlign.Start) =
        TextStyle(
            color = AppWidgetTheme.colors.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            textAlign = textAlign
        )

    @Composable
    fun d3(textAlign: TextAlign = TextAlign.Start) =
        TextStyle(
            color = AppWidgetTheme.colors.brandDefault,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            textAlign = textAlign
        )

    @Composable
    fun micro(textAlign: TextAlign = TextAlign.Start) =
        TextStyle(
            color = AppWidgetTheme.colors.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
            textAlign = textAlign
        )
}

@Composable
fun AppWidgetTheme(colors: ColorProviders = AppWidgetTheme.colors, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalColorProviders provides colors) {
        content()
    }
}

internal val LocalColorProviders = staticCompositionLocalOf {
    ColorProviders(
        brandDefault = ColorProvider(lightPalette.brandDefault, darkPalette.brandDefault),
        statusSuccess = ColorProvider(lightPalette.statusSuccess, darkPalette.statusSuccess),
        statusError = ColorProvider(lightPalette.statusError, darkPalette.statusError),
        backgroundBase = ColorProvider(lightPalette.backgroundBase, darkPalette.backgroundBase),
        surfacePrimary = ColorProvider(lightPalette.surfacePrimary, darkPalette.surfacePrimary),
        surfacePlaceholder = ColorProvider(lightPalette.surfacePlaceholder, darkPalette.surfacePlaceholder),
        borderDefault = ColorProvider(lightPalette.borderDefault, darkPalette.borderDefault),
        borderDivider = ColorProvider(lightPalette.borderDivider, darkPalette.borderDivider),
        badgeBackground = ColorProvider(lightPalette.badgeBackground, darkPalette.badgeBackground),
        textPrimary = ColorProvider(lightPalette.textPrimary, darkPalette.textPrimary),
        textSecondary = ColorProvider(lightPalette.textSecondary, darkPalette.textSecondary),
        iconSecondary = ColorProvider(lightPalette.iconSecondary, darkPalette.iconSecondary),
    )
}

data class ColorProviders(
    val brandDefault: ColorProvider,
    val statusSuccess: ColorProvider,
    val statusError: ColorProvider,
    val backgroundBase: ColorProvider,
    val surfacePrimary: ColorProvider,
    val surfacePlaceholder: ColorProvider,
    val borderDefault: ColorProvider,
    val borderDivider: ColorProvider,
    val badgeBackground: ColorProvider,
    val textPrimary: ColorProvider,
    val textSecondary: ColorProvider,
    val iconSecondary: ColorProvider,
)
