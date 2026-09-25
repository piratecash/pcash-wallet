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
            color = AppWidgetTheme.colors.brand,
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
            color = AppWidgetTheme.colors.brand,
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
        brand = ColorProvider(lightPalette.brand, darkPalette.brand),
        remus = ColorProvider(lightPalette.remus, darkPalette.remus),
        lucian = ColorProvider(lightPalette.lucian, darkPalette.lucian),
        tyler = ColorProvider(lightPalette.tyler, darkPalette.tyler),
        bran = ColorProvider(lightPalette.bran, darkPalette.bran),
        textPrimary = ColorProvider(lightPalette.textPrimary, darkPalette.textPrimary),
        textSecondary = ColorProvider(lightPalette.textSecondary, darkPalette.textSecondary),
    )
}

data class ColorProviders(
    val brand: ColorProvider,
    val remus: ColorProvider,
    val lucian: ColorProvider,
    val tyler: ColorProvider,
    val bran: ColorProvider,
    val textPrimary: ColorProvider,
    val textSecondary: ColorProvider,
)
