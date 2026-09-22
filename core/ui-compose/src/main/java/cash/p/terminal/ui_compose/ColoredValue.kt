package cash.p.terminal.ui_compose

import androidx.compose.runtime.Composable
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

data class ColoredValue(val value: String, val color: ColorName)

enum class ColorName {
    Remus, Lucian, Secondary, Primary, Brand;

    @Composable
    fun compose() = when (this) {
        Remus -> ComposeAppTheme.colors.remus
        Lucian -> ComposeAppTheme.colors.lucian
        Primary -> ComposeAppTheme.colors.textPrimary
        Secondary -> ComposeAppTheme.colors.textSecondary
        Brand -> ComposeAppTheme.colors.brand
    }
}

/**
 * Single source of truth for amount colours, shared by the transaction list and the
 * transaction details screen so one record can never be painted two ways.
 */
fun amountColor(incoming: Boolean?, sentToThirdParty: Boolean = false): ColorName = when {
    incoming == true && sentToThirdParty -> ColorName.Secondary
    incoming == true -> ColorName.Remus
    incoming == false -> ColorName.Lucian
    else -> ColorName.Primary
}
