package cash.p.terminal.modules.xtransaction.sections

import androidx.compose.runtime.Composable
import cash.p.terminal.resources.Swap_YouGet
import cash.p.terminal.resources.Send_Confirmation_YouSend
import cash.p.terminal.resources.Res
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.modules.xtransaction.cells.AmountCellTV
import cash.p.terminal.modules.xtransaction.cells.AmountColor
import cash.p.terminal.modules.xtransaction.cells.AmountSign
import cash.p.terminal.modules.xtransaction.helpers.TransactionInfoHelper
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence

@Composable
fun SwapSection(
    transactionInfoHelper: TransactionInfoHelper,
    navigation: HSNavigation,
    transactionValueIn: TransactionValue,
    transactionValueOut: TransactionValue,
) {
    SectionUniversalLawrence {
        AmountCellTV(
            title = stringResource(Res.string.Send_Confirmation_YouSend),
            transactionValue = transactionValueIn,
            coinAmountColor = AmountColor.Negative,
            coinAmountSign = AmountSign.Minus,
            transactionInfoHelper = transactionInfoHelper,
            navigation = navigation,
            borderTop = false,
        )

        AmountCellTV(
            title = stringResource(Res.string.Swap_YouGet),
            transactionValue = transactionValueOut,
            coinAmountColor = AmountColor.Positive,
            coinAmountSign = AmountSign.Plus,
            transactionInfoHelper = transactionInfoHelper,
            navigation = navigation,
            borderTop = true,
        )
    }
}
