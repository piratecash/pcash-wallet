package cash.p.terminal.modules.xtransaction.sections

import androidx.compose.runtime.Composable
import cash.p.terminal.resources.TransactionInfo_To
import cash.p.terminal.resources.Send_Confirmation_YouSend
import cash.p.terminal.resources.Res
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.modules.xtransaction.cells.AmountColor
import cash.p.terminal.modules.xtransaction.cells.AmountSign
import cash.p.terminal.modules.xtransaction.helpers.TransactionInfoHelper
import cash.p.terminal.navigation.HSNavigation
import io.horizontalsystems.core.entities.BlockchainType

@Composable
fun SendCoinSection(
    transactionValue: TransactionValue,
    address: String,
    comment: String?,
    sentToSelf: Boolean,
    navigation: HSNavigation,
    transactionInfoHelper: TransactionInfoHelper,
    blockchainType: BlockchainType
) {
    TransferCoinSection(
        amountTitle = stringResource(Res.string.Send_Confirmation_YouSend),
        transactionValue = transactionValue,
        coinAmountColor = AmountColor.Negative,
        coinAmountSign = if (sentToSelf) AmountSign.None else AmountSign.Minus,
        addressTitle = stringResource(Res.string.TransactionInfo_To),
        address = address,
        comment = comment,
        navigation = navigation,
        transactionInfoHelper = transactionInfoHelper,
        blockchainType = blockchainType,
    )
}
