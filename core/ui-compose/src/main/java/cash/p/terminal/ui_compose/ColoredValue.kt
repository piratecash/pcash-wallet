package cash.p.terminal.ui_compose

import androidx.compose.runtime.Composable
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

data class ColoredValue(val value: String, val color: ColorName)

enum class ColorName {
    Remus, Lucian, Grey, Leah, Jacob;

    @Composable
    fun compose() = when (this) {
        Remus -> ComposeAppTheme.colors.remus
        Lucian -> ComposeAppTheme.colors.lucian
        Leah -> ComposeAppTheme.colors.leah
        Grey -> ComposeAppTheme.colors.grey
        Jacob -> ComposeAppTheme.colors.jacob
    }
}

/**
 * Single source of truth for amount colours, shared by the transaction list and the
 * transaction details screen so one record can never be painted two ways.
 */
fun amountColor(incoming: Boolean?, sentToThirdParty: Boolean = false): ColorName = when {
    incoming == true && sentToThirdParty -> ColorName.Grey
    incoming == true -> ColorName.Remus
    incoming == false -> ColorName.Lucian
    else -> ColorName.Leah
}
