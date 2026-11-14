package cash.p.terminal.modules.xtransaction.sections

import androidx.compose.runtime.Composable
import cash.p.terminal.resources.Send_Confirmation_Mint
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
fun MintSection(
    transactionValue: TransactionValue,
    transactionInfoHelper: TransactionInfoHelper,
    navigation: HSNavigation,
) {
    SectionUniversalLawrence {
        AmountCellTV(
            title = stringResource(Res.string.Send_Confirmation_Mint),
            transactionValue = transactionValue,
            coinAmountColor = AmountColor.Positive,
            coinAmountSign = AmountSign.Plus,
            transactionInfoHelper = transactionInfoHelper,
            navigation = navigation,
            borderTop = false,
        )
    }
}
