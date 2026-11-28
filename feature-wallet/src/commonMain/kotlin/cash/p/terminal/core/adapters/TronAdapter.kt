package cash.p.terminal.core.adapters

import cash.p.terminal.core.tryOrNull
import cash.p.terminal.modules.send.tron.toTronFeeBreakdown
import cash.p.terminal.wallet.AdapterState
import io.horizontalsystems.databasenaming.DatabaseNameResolver
import io.horizontalsystems.tronkit.PlatformContext
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.core.managers.TronKitWrapper
import io.horizontalsystems.tronkit.TronKit
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.models.Contract
import io.horizontalsystems.tronkit.network.Network
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.math.BigDecimal

class TronAdapter(kitWrapper: TronKitWrapper) : BaseTronAdapter(kitWrapper, decimal) {

    override val maxSpendableBalance: BigDecimal
        get() = maxOf(balanceData.available - fee.value, BigDecimal.ZERO)

    private var networkJob: Job? = null

    // IAdapter

    override fun attachLocalData() = Unit

    override fun resumeNetwork() {
        networkJob = coroutineScope.launch {
            balanceUpdatedFlow.collect {
                estimateFeeForMax()
            }
        }
    }

    override fun pauseNetwork() {
        networkJob?.cancel()
    }

    // A failed estimate keeps the previous value.
    private suspend fun estimateFeeForMax() {
        tryOrNull {
            val amount = maxOf(balanceData.available, BigDecimal.ONE.movePointLeft(decimal))
            val breakdown = estimateFee(amount, tronKit.address).toTronFeeBreakdown(decimal)
            mutableFee.value = breakdown.totalFee.stripTrailingZeros()
        }
    }

    override suspend fun refresh() {
        // refreshed via TronKitManager
    }

    // IBalanceAdapter

    override val balanceState: AdapterState
        get() = convertToAdapterState(tronKit.syncState)

    override val balanceStateUpdatedFlow: Flow<Unit>
        get() = tronKit.syncStateFlow.map {}

    override val balanceData: BalanceData
        get() = BalanceData(balanceInBigDecimal(tronKit.trxBalance, decimal))

    override val balanceUpdatedFlow: Flow<Unit>
        get() = tronKit.trxBalanceFlow.map {}

    override val trxBalanceData: BalanceData
        get() = balanceData

    override fun transferContract(amount: BigDecimal, to: Address): Contract =
        tronKit.transferContract(scaleUp(amount), to)

    private fun convertToAdapterState(syncState: TronKit.SyncState): AdapterState =
        when (syncState) {
            is TronKit.SyncState.Synced -> AdapterState.Synced
            is TronKit.SyncState.NotSynced -> AdapterState.NotSynced(syncState.error)
            is TronKit.SyncState.Syncing -> AdapterState.Syncing()
        }

    companion object {
        const val decimal = 6

        fun clear(platformContext: PlatformContext, walletId: String, nameResolver: DatabaseNameResolver) {
            Network.values().forEach { network ->
                TronKit.clear(platformContext, network, walletId, nameResolver)
            }
        }
    }

}
