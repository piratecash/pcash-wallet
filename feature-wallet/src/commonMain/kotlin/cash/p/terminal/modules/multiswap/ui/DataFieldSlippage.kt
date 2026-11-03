package cash.p.terminal.modules.multiswap.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.modules.fee.QuoteInfoRow
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import java.math.BigDecimal
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Swap_Slippage
import cash.p.terminal.core.getKoinInstance
import io.horizontalsystems.core.IAppNumberFormatter

data class DataFieldSlippage(val slippage: BigDecimal) : DataField {
    @Composable
    override fun GetContent(navigation: HSNavigation, borderTop: Boolean) {
        QuoteInfoRow(
            borderTop = borderTop,
            title = {
                subhead2_grey(text = stringResource(Res.string.Swap_Slippage))
            },
            value = {
                subhead2_leah(text = getKoinInstance<IAppNumberFormatter>().format(slippage, 0, 2, suffix = "%"))
            }
        )
    }
}
