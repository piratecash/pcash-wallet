package cash.p.terminal.modules.multiswap.exchanges

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.storage.PendingMultiSwapStorage
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.entities.PendingMultiSwap
import cash.p.terminal.modules.multiswap.exchange.LegStatus
import cash.p.terminal.modules.multiswap.exchange.MultiSwapExchangeViewModel.Companion.mapStatus
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.changenow.domain.entity.toStatus
import io.horizontalsystems.core.Translator
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.coinImageUrl
import io.horizontalsystems.core.IAppNumberFormatter
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Transactions_Failed
import cash.p.terminal.resources.multi_swap_completed
import cash.p.terminal.resources.multi_swap_continue
import cash.p.terminal.resources.transaction_swap_status_confirming
import cash.p.terminal.resources.transaction_swap_status_created_or_wait_user
import cash.p.terminal.resources.transaction_swap_status_exchanging
import cash.p.terminal.resources.transaction_swap_status_new
import cash.p.terminal.resources.transaction_swap_status_refunded
import cash.p.terminal.resources.transaction_swap_status_sending
import cash.p.terminal.resources.transaction_swap_status_unknown
import cash.p.terminal.resources.transaction_swap_status_verifying
import cash.p.terminal.resources.transaction_swap_status_waiting
import org.jetbrains.compose.resources.StringResource

class MultiSwapExchangesViewModel(
    private val pendingMultiSwapStorage: PendingMultiSwapStorage,
    private val marketKit: MarketKitWrapper,
    private val numberFormatter: IAppNumberFormatter,
    private val swapProviderTransactionsStorage: SwapProviderTransactionsStorage,
    private val accountManager: IAccountManager,
) : ViewModel() {

    var uiState by mutableStateOf(MultiSwapExchangesUiState())
        private set

    init {
        observeSwaps()
    }

    private fun observeSwaps() {
        viewModelScope.launch {
            combine(
                pendingMultiSwapStorage.observeForActiveAccount(accountManager.activeAccountStateFlow),
                // Refresh trigger only: the status text is read from this storage in mapToCardItem.
                swapProviderTransactionsStorage.observeForActiveAccount(accountManager.activeAccountStateFlow),
            ) { swaps, _ -> swaps.map { mapToCardItem(it) } }
                .collect { items ->
                    uiState = uiState.copy(items = items)
                }
        }
    }

    private fun mapToCardItem(swap: PendingMultiSwap): ExchangeCardItem {
        val leg1Status = mapStatus(swap.leg1Status)
        val leg2Status = mapStatus(swap.leg2Status)

        val coinCodeIn = coinCode(swap.coinUidIn)
        val coinCodeIntermediate = coinCode(swap.coinUidIntermediate)
        val coinCodeOut = coinCode(swap.coinUidOut)

        val activeLine = when {
            leg1Status != LegStatus.Completed -> 0
            leg2Status != LegStatus.Completed -> 1
            else -> 2
        }

        return ExchangeCardItem(
            id = swap.id,
            coinIconUrlIn = coinImageUrl(swap.coinUidIn),
            coinIconUrlIntermediate = coinImageUrl(swap.coinUidIntermediate),
            coinIconUrlOut = coinImageUrl(swap.coinUidOut),
            amountInFormatted = numberFormatter.formatCoinFull(swap.amountIn, coinCodeIn, 8),
            amountIntermediateFormatted = swap.leg1AmountOut?.let {
                numberFormatter.formatCoinFull(it, coinCodeIntermediate, 8)
            } ?: "--- $coinCodeIntermediate",
            amountOutFormatted = numberFormatter.formatCoinFull(
                swap.leg2AmountOut ?: swap.expectedAmountOut, coinCodeOut, 8
            ),
            activeLine = activeLine,
            statusText = resolveStatusText(swap, leg1Status, leg2Status),
        )
    }

    private fun resolveStatusText(
        swap: PendingMultiSwap,
        leg1Status: LegStatus,
        leg2Status: LegStatus,
    ): String {
        if (leg1Status == LegStatus.Completed && leg2Status == LegStatus.Completed) {
            return Translator.getString(Res.string.multi_swap_completed)
        }
        if (leg1Status == LegStatus.Completed && leg2Status == LegStatus.Pending) {
            return Translator.getString(Res.string.multi_swap_continue)
        }
        val (txId, isOffChain) = when {
            leg1Status == LegStatus.Executing -> swap.leg1TransactionId to swap.leg1IsOffChain
            leg2Status == LegStatus.Executing -> swap.leg2TransactionId to (swap.leg2IsOffChain ?: false)
            else -> null to false
        }
        if (isOffChain && txId != null) {
            val swapTx = swapProviderTransactionsStorage.getByOutgoingRecordUid(txId)
            if (swapTx != null) {
                return Translator.getString(statusToStringRes(swapTx.status.toStatus()))
            }
        }
        return Translator.getString(Res.string.transaction_swap_status_confirming)
    }

    private fun coinCode(uid: String) = marketKit.coin(uid)?.code ?: uid

    fun onDelete(item: ExchangeCardItem) {
        viewModelScope.launch {
            pendingMultiSwapStorage.delete(item.id)
        }
    }
}

data class MultiSwapExchangesUiState(
    val items: List<ExchangeCardItem> = emptyList(),
)

data class ExchangeCardItem(
    val id: String,
    val coinIconUrlIn: String?,
    val coinIconUrlIntermediate: String?,
    val coinIconUrlOut: String?,
    val amountInFormatted: String,
    val amountIntermediateFormatted: String?,
    val amountOutFormatted: String,
    val activeLine: Int,
    val statusText: String,
)

private fun statusToStringRes(status: TransactionStatusEnum): StringResource = when (status) {
    TransactionStatusEnum.NEW -> Res.string.transaction_swap_status_new
    TransactionStatusEnum.WAITING -> Res.string.transaction_swap_status_waiting
    TransactionStatusEnum.CONFIRMING -> Res.string.transaction_swap_status_confirming
    TransactionStatusEnum.EXCHANGING -> Res.string.transaction_swap_status_exchanging
    TransactionStatusEnum.SENDING -> Res.string.transaction_swap_status_sending
    TransactionStatusEnum.FINISHED -> Res.string.multi_swap_completed
    TransactionStatusEnum.FAILED -> Res.string.Transactions_Failed
    TransactionStatusEnum.REFUNDED -> Res.string.transaction_swap_status_refunded
    TransactionStatusEnum.VERIFYING -> Res.string.transaction_swap_status_verifying
    TransactionStatusEnum.CREATED_OR_WAIT_USER -> Res.string.transaction_swap_status_created_or_wait_user
    TransactionStatusEnum.UNKNOWN -> Res.string.transaction_swap_status_unknown
}
