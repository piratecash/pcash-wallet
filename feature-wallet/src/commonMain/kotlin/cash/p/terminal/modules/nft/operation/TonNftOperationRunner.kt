package cash.p.terminal.modules.nft.operation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.HardwareWalletSupport
import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.TonFeeEstimate
import cash.p.terminal.core.nft.NftOwner
import cash.p.terminal.modules.nft.NftOwnerWalletResolver
import cash.p.terminal.modules.nft.transfer.approximateFeeText
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.modules.send.fee.insufficientRequiredBalanceText
import cash.p.terminal.modules.send.orHiddenBalanceText
import cash.p.terminal.modules.send.toSendCaution
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.tokenQueryId
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.CurrencyValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import kotlin.time.Duration.Companion.seconds

private val logger = Logger.withTag("TonNftOperationRunner")

internal val PREPARE_RETRY_INTERVAL = 5.seconds

sealed interface NftOperationOutcome {
    data object Idle : NftOperationOutcome
    data object Running : NftOperationOutcome
    data class Done(val sent: Int) : NftOperationOutcome

    /** [sent] items reached the node before the error; the outcome of the rest is unknown. */
    data class Ended(val sent: Int, val total: Int, val caution: HSCaution) : NftOperationOutcome
}

/** [token] is the owners' TON token, null until they resolve. */
data class NftOperationState(
    val token: Token?,
    val fee: String,
    val insufficientBalance: TranslatableString?,
    val balanceHidden: Boolean,
    val canRun: Boolean,
    val outcome: NftOperationOutcome,
)

// Not a data class: Wallet.toString() would print key material.
private class OwnerTarget(val wallet: Wallet, val adapter: ISendTonAdapter, val nftAddresses: List<String>)

/** Runs [operation] once over [targets] (NFT addresses per owner) in the owning view model's [scope]. */
class TonNftOperationRunner(
    private val scope: CoroutineScope,
    private val operation: TonNftOperation,
    private val targets: Map<NftOwner, List<String>>,
    private val walletResolver: NftOwnerWalletResolver,
    private val adapterManager: IAdapterManager,
    private val balanceHiddenManager: IBalanceHiddenManager,
    private val offlineOperationGate: OfflineOperationGate,
    private val xRateService: XRateService,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val total = targets.values.sumOf { it.size }
    private var owners: List<OwnerTarget>? = null
    private var estimates: List<TonFeeEstimate>? = null
    private var rate: CurrencyValue? = null
    private var outcome: NftOperationOutcome = NftOperationOutcome.Idle

    // Set before the first broadcast, so the captured targets are never submitted twice.
    private var started = false

    var state by mutableStateOf(buildState())
        private set

    init {
        scope.launch { balanceHiddenManager.anyWalletVisibilityChangedFlow.collect { refresh() } }
        if (total > 0) {
            scope.launch {
                val resolved = prepareUntilReady()
                val coinUid = resolved.first().wallet.coin.uid
                rate = xRateService.getRate(coinUid)
                refresh()
                launch { xRateService.getRateFlow(coinUid).collect { rate = it; refresh() } }
                launch { stateChanges(resolved).collect { refresh() } }
            }
        }
    }

    // The wallet and its adapter may publish after the NFT was found, and an idle synced wallet emits
    // nothing, so owners and fees are retried together on a timer while the sheet is open.
    private suspend fun prepareUntilReady(): List<OwnerTarget> {
        while (true) {
            val resolved = resolveOwners()
            if (resolved != null) {
                owners = resolved
                refresh()
                val networkPaused = resolved.any { offlineOperationGate.isBlocked(it.wallet) }
                (if (networkPaused) null else estimateFees(resolved))?.let {
                    estimates = it
                    return resolved
                }
            }
            delay(PREPARE_RETRY_INTERVAL)
        }
    }

    /** Call after the PIN gate. */
    fun run() {
        val owners = owners ?: return
        if (!canRun()) return
        started = true
        outcome = NftOperationOutcome.Running
        refresh()
        scope.launch {
            outcome = execute(owners)
            if (outcome == NftOperationOutcome.Idle) started = false
            refresh()
        }
    }

    private suspend fun execute(owners: List<OwnerTarget>): NftOperationOutcome {
        var sent = 0
        for (owner in owners) {
            val result = withContext(dispatcherProvider.io) { operation.execute(owner.adapter, owner.nftAddresses) }
            sent += result.broadcast.size
            result.error?.let { return stoppedOutcome(it, sent) }
        }
        return NftOperationOutcome.Done(sent)
    }

    private fun stoppedOutcome(error: Throwable, sent: Int): NftOperationOutcome {
        logger.w { "NFT operation stopped after $sent of $total: ${error::class.simpleName}" }
        // Nothing left the device, so this is the one case the same targets may be confirmed again.
        if (sent == 0 && HardwareWalletSupport.isUserCancellation(error)) return NftOperationOutcome.Idle
        return NftOperationOutcome.Ended(sent, total, error.toSendCaution())
    }

    private suspend fun resolveOwners(): List<OwnerTarget>? =
        targets.map { (owner, nftAddresses) ->
            val wallet = walletResolver.wallet(owner)
            val adapter = wallet?.let { adapterManager.getAdapterForWallet<ISendTonAdapter>(it) }
            if (wallet == null || adapter == null) {
                logger.w { "No active TON wallet for an NFT operation owner" }
                return null
            }
            OwnerTarget(wallet, adapter, nftAddresses)
        }

    private suspend fun estimateFees(owners: List<OwnerTarget>): List<TonFeeEstimate>? =
        withContext(dispatcherProvider.io) {
            try {
                owners.map { operation.estimate(it.adapter, it.nftAddresses) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w { "NFT operation fee estimate failed: ${e::class.simpleName}" }
                null
            }
        }

    private fun stateChanges(owners: List<OwnerTarget>): Flow<Unit> = merge(
        owners.flatMap { listOf(it.adapter.balanceUpdatedFlow, it.adapter.balanceStateUpdatedFlow) }.merge(),
        owners.map { offlineOperationGate.blockedFlow(it.wallet) }.merge().map { },
    )

    private fun refresh() {
        state = buildState()
    }

    private fun buildState(): NftOperationState {
        val token = owners?.first()?.wallet?.token
        val balanceHidden = isBalanceHidden()
        return NftOperationState(
            token = token,
            fee = approximateFeeText(token, estimates?.sumOf { it.fee }, rate, balanceHidden),
            insufficientBalance = insufficientBalanceText(balanceHidden),
            balanceHidden = balanceHidden,
            canRun = canRun(),
            outcome = outcome,
        )
    }

    private fun canRun(): Boolean {
        val owners = owners ?: return false
        return !started && estimates != null && shortfall() == null && owners.all(::isReady)
    }

    private fun isReady(owner: OwnerTarget): Boolean =
        owner.adapter.balanceState is AdapterState.Synced && !offlineOperationGate.isBlocked(owner.wallet)

    private fun insufficientBalanceText(balanceHidden: Boolean): TranslatableString? {
        val (owner, required) = shortfall() ?: return null
        return insufficientRequiredBalanceText(required, availableBalance(owner), owner.wallet.token)
            .orHiddenBalanceText(balanceHidden, owner.wallet.coin.code)
    }

    /** The first owner whose balance does not cover its part, with the TON it needs. */
    private fun shortfall(): Pair<OwnerTarget, BigDecimal>? {
        val estimates = estimates ?: return null
        return owners.orEmpty().zip(estimates)
            .firstOrNull { (owner, estimate) -> estimate.requiredTon > availableBalance(owner) }
            ?.let { (owner, estimate) -> owner to estimate.requiredTon }
    }

    private fun availableBalance(owner: OwnerTarget): BigDecimal =
        adapterManager.getAdjustedBalanceData(owner.wallet)?.available ?: owner.adapter.balanceData.available

    // Until the owners resolve, the global eye decides.
    private fun isBalanceHidden(): Boolean {
        val owners = owners ?: return balanceHiddenManager.balanceHidden
        return owners.any { balanceHiddenManager.isWalletBalanceHidden(it.wallet.tokenQueryId) }
    }
}
