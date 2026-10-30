package cash.p.terminal.modules.multiswap.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.modules.fee.QuoteInfoRow
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.NotAvailable
import cash.p.terminal.resources.Swap_Slippage

data object DataFieldSlippageNotAvailable : DataField {
    @Composable
    override fun GetContent(navigation: HSNavigation, borderTop: Boolean) {
        QuoteInfoRow(
            borderTop = borderTop,
            title = {
                subhead2_grey(text = stringResource(Res.string.Swap_Slippage))
            },
            value = {
                subhead2_leah(text = stringResource(Res.string.NotAvailable))
            }
        )
    }
}
