package cash.p.terminal.modules.xtransaction.sections.ton

import org.jetbrains.compose.resources.painterResource
import cash.p.terminal.resources.Transactions_ContractCall
import cash.p.terminal.resources.TransactionInfo_To
import cash.p.terminal.resources.Send_Confirmation_YouSend
import cash.p.terminal.resources.Res
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.modules.transactions.TransactionViewItem
import cash.p.terminal.modules.xtransaction.cells.AddressCell
import cash.p.terminal.modules.xtransaction.cells.AmountCellTV
import cash.p.terminal.modules.xtransaction.cells.AmountColor
import cash.p.terminal.modules.xtransaction.cells.AmountSign
import cash.p.terminal.modules.xtransaction.cells.HeaderCell
import cash.p.terminal.modules.xtransaction.helpers.TransactionInfoHelper
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import io.horizontalsystems.core.entities.BlockchainType

@Composable
fun ContractCallSection(
    navigation: HSNavigation,
    operation: String,
    address: String,
    transactionValue: TransactionValue,
    transactionInfoHelper: TransactionInfoHelper,
    blockchainType: BlockchainType,
) {
    SectionUniversalLawrence {
        HeaderCell(
            title = stringResource(Res.string.Transactions_ContractCall),
            value = operation,
            painter = TransactionViewItem.Icon.Platform.fromBlockchainType(blockchainType).iconRes?.let {
                painterResource(it)
            }
        )
        val contact = transactionInfoHelper.getContact(address, blockchainType)
        AddressCell(
            title = stringResource(Res.string.TransactionInfo_To),
            value = address,
            showAddContactButton = contact == null,
            blockchainType = blockchainType,
            navigation = navigation
        )

        AmountCellTV(
            title = stringResource(Res.string.Send_Confirmation_YouSend),
            transactionValue = transactionValue,
            coinAmountColor = AmountColor.Negative,
            coinAmountSign = AmountSign.Minus,
            transactionInfoHelper = transactionInfoHelper,
            navigation = navigation,
        )
    }
}
