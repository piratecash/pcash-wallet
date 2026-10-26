package cash.p.terminal.core.managers

import cash.p.terminal.entities.transactionrecords.ton.TonTransactionRecord
import cash.p.terminal.modules.transactionInfo.TransactionInfoViewItem
import cash.p.terminal.modules.transactionInfo.TransactionInfoViewItem.Status
import cash.p.terminal.modules.transactionInfo.TransactionInfoViewItem.Value
import cash.p.terminal.modules.transactionInfo.TransactionViewItemFactoryHelper
import cash.p.terminal.modules.transactionInfo.TransactionViewItemFactoryHelper.getSwapEventSectionItems
import cash.p.terminal.modules.transactions.TransactionStatus
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.CurrencyValue
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.TransactionInfo_Memo

object TonHelper {
    fun getViewItemsForAction(
        action: TonTransactionRecord.Action,
        rates: Map<String, CurrencyValue>,
        blockchainType: BlockchainType,
        hideAmount: Boolean,
        showCopyWarning: Boolean = false,
    ): List<TransactionInfoViewItem> {

        val itemsForAction = mutableListOf<TransactionInfoViewItem>()

        when (val actionType = action.type) {
            is TonTransactionRecord.Action.Type.Send -> {
                itemsForAction.addAll(
                    TransactionViewItemFactoryHelper.getSendSectionItems(
                        value = actionType.value,
                        toAddress = listOf(actionType.to),
                        coinPrice = rates[actionType.value.coinUid],
                        hideAmount = hideAmount,
                        sentToSelf = actionType.sentToSelf,
                        blockchainType = blockchainType,
                        showCopyWarning = showCopyWarning,
                    )
                )
                actionType.comment?.let {
                    itemsForAction.add(
                        Value(
                            Translator.getString(Res.string.TransactionInfo_Memo),
                            it
                        )
                    )
                }
            }

            is TonTransactionRecord.Action.Type.Receive -> {
                itemsForAction.addAll(
                    TransactionViewItemFactoryHelper.getReceiveSectionItems(
                        showCopyWarning = showCopyWarning,
                        value = actionType.value,
                        fromAddress = actionType.from,
                        toAddress = actionType.to?.let(::listOf),
                        coinPrice = rates[actionType.value.coinUid],
                        hideAmount = hideAmount,
                        blockchainType = blockchainType,
                    )
                )
                actionType.comment?.let {
                    itemsForAction.add(
                        Value(
                            Translator.getString(Res.string.TransactionInfo_Memo),
                            it
                        )
                    )
                }
            }

            is TonTransactionRecord.Action.Type.Burn -> {
                itemsForAction.addAll(
                    TransactionViewItemFactoryHelper.getSendSectionItems(
                        value = actionType.value,
                        toAddress = null,
                        coinPrice = rates[actionType.value.coinUid],
                        hideAmount = hideAmount,
                        blockchainType = blockchainType,
                    )
                )
            }

            is TonTransactionRecord.Action.Type.Mint -> {
                itemsForAction.addAll(
                    TransactionViewItemFactoryHelper.getReceiveSectionItems(
                        value = actionType.value,
                        fromAddress = TransactionViewItemFactoryHelper.zeroAddress,
                        toAddress = actionType.to?.let(::listOf),
                        coinPrice = rates[actionType.value.coinUid],
                        hideAmount = hideAmount,
                        blockchainType = blockchainType,
                    )
                )
            }

            is TonTransactionRecord.Action.Type.Swap -> {
                itemsForAction.addAll(
                    getSwapEventSectionItems(
                        valueIn = actionType.valueIn,
                        valueOut = actionType.valueOut,
                        rates = rates,
                        amount = null,
                        hideAmount = hideAmount,
                        hasRecipient = false
                    )
                )
            }

            is TonTransactionRecord.Action.Type.ContractDeploy -> {
//                        case let .contractDeploy(interfaces):
//                            viewItems = [
//                                .actionTitle(iconName: nil, iconDimmed: false, title: "transactions.contract_deploy".localized, subTitle: interfaces.joined(separator: ", ")),
//                            ]
            }

            is TonTransactionRecord.Action.Type.ContractCall -> {
//                        case let .contractCall(address, value, operation):
//                            viewItems = [
//                                .actionTitle(iconName: record.source.blockchainType.iconPlain32, iconDimmed: false, title: "transactions.contract_call".localized, subTitle: operation),
//                                .to(value: address, valueTitle: nil, contactAddress: nil)
//                            ]
//
//                            viewItems.append(contentsOf: sendSection(source: record.source, transactionValue: value, to: nil, rates: item.rates, balanceHidden: balanceHidden))
            }

            is TonTransactionRecord.Action.Type.Unsupported -> {
                itemsForAction.add(Value("Action", actionType.type))
            }
        }

        if (action.status == TransactionStatus.Failed) {
            itemsForAction.add(Status(action.status))
        }

        return itemsForAction


    }
}
