package cash.p.terminal.modules.xtransaction.sections

import androidx.compose.runtime.Composable
import cash.p.terminal.resources.TransactionInfo_From
import cash.p.terminal.resources.Send_Confirmation_YouReceive
import cash.p.terminal.resources.Res
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.modules.xtransaction.cells.AmountColor
import cash.p.terminal.modules.xtransaction.cells.AmountSign
import cash.p.terminal.modules.xtransaction.helpers.TransactionInfoHelper
import cash.p.terminal.navigation.HSNavigation
import io.horizontalsystems.core.entities.BlockchainType

@Composable
fun ReceiveCoinSection(
    transactionValue: TransactionValue,
    address: String,
    comment: String?,
    navigation: HSNavigation,
    transactionInfoHelper: TransactionInfoHelper,
    blockchainType: BlockchainType,
) {
    TransferCoinSection(
        amountTitle = stringResource(Res.string.Send_Confirmation_YouReceive),
        transactionValue = transactionValue,
        coinAmountColor = AmountColor.Positive,
        coinAmountSign = AmountSign.Plus,
        addressTitle = stringResource(Res.string.TransactionInfo_From),
        address = address,
        comment = comment,
        navigation = navigation,
        transactionInfoHelper = transactionInfoHelper,
        blockchainType = blockchainType,
    )
}
