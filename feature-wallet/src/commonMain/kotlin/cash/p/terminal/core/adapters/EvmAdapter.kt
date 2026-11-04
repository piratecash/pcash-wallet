package cash.p.terminal.core.adapters

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.data.repository.EvmTransactionRepository
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.entities.BalanceData
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.TransactionData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import java.math.BigDecimal

class EvmAdapter(evmTransactionRepository: EvmTransactionRepository, coinManager: ICoinManager) :
    BaseEvmAdapter(evmTransactionRepository, decimal, coinManager) {

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val _fee = MutableStateFlow(BigDecimal.ZERO)
    override val fee: StateFlow<BigDecimal> = _fee.asStateFlow()
    private var accountStateJob: Job? = null
    private var initialFeeJob: Job? = null

    override val maxSpendableBalance: BigDecimal
        get() = maxOf(balanceData.available - fee.value, BigDecimal.ZERO)

    // IAdapter

    override fun attachLocalData() = Unit

    override fun resumeNetwork() {
        accountStateJob = coroutineScope.launch {
            evmTransactionRepository.accountStateFlowable.asFlow().collect {
                estimateFeeForMax()
            }
        }
        // Initial fee estimation
        initialFeeJob = coroutineScope.launch {
            estimateFeeForMax()
        }
    }

    override fun pauseNetwork() {
        accountStateJob?.cancel()
        initialFeeJob?.cancel()
    }

    override fun stop() {
        coroutineScope.cancel()
    }

    override suspend fun refresh() {
        // refreshed via EthereumKitManager
    }

    private suspend fun estimateFeeForMax() {
        tryOrNull {
            val feeInWei = evmTransactionRepository.estimateNativeTransferFee()
            _fee.value = feeInWei.toBigDecimal().movePointLeft(decimal).stripTrailingZeros()
        }
    }

    // IBalanceAdapter

    override val balanceState: AdapterState
        get() = evmTransactionRepository.syncState.toAdapterState()

    override val balanceStateUpdatedFlow: Flow<Unit>
        get() = evmTransactionRepository.syncStateFlowable.map { }.asFlow()

    override val balanceData: BalanceData
        get() = BalanceData(balanceInBigDecimal(evmTransactionRepository.accountState?.balance, decimal))

    override val balanceUpdatedFlow: Flow<Unit>
        get() = evmTransactionRepository.accountStateFlowable.map { }.asFlow()

    // ISendEthereumAdapter

    override fun getTransactionData(amount: BigDecimal, address: Address): TransactionData {
        val amountBigInt = amount.movePointRight(decimal).toBigInteger()
        return TransactionData(address, amountBigInt, byteArrayOf())
    }

    companion object {
        const val decimal = 18
    }

}

// A kit stopped on purpose (background, offline pause) is not a sync failure.
internal fun EthereumKit.SyncState.toAdapterState(): AdapterState = when (this) {
    is EthereumKit.SyncState.Synced -> AdapterState.Synced
    is EthereumKit.SyncState.Syncing -> AdapterState.Syncing()
    is EthereumKit.SyncState.NotSynced ->
        if (error is EthereumKit.SyncError.NotStarted) AdapterState.Paused
        else AdapterState.NotSynced(error)
}
