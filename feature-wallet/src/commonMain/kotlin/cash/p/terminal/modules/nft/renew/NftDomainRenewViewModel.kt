package cash.p.terminal.modules.nft.renew

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.HardwareWalletSupport
import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.TonFeeEstimate
import cash.p.terminal.core.adapters.DOMAIN_RENEW_AMOUNT
import cash.p.terminal.core.orHide
import cash.p.terminal.core.nft.NftDomainExpiry
import cash.p.terminal.modules.nft.NftOwnerWalletResolver
import cash.p.terminal.modules.nft.ui.HIDDEN_NAME
import cash.p.terminal.modules.nft.transfer.approximateFeeText
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.modules.send.fee.feePrimaryText
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
import io.horizontalsystems.core.helpers.DateHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.Calendar
import kotlin.time.Duration.Companion.seconds

private val logger = Logger.withTag("NftDomainRenewViewModel")

private const val RENEW_UNTIL_PATTERN = "d MMMM yyyy"

internal val PREPARE_RETRY_INTERVAL = 5.seconds

sealed interface RenewOutcome {
    data object Idle : RenewOutcome
    data object Renewing : RenewOutcome
    data class Done(val sent: Int) : RenewOutcome

    /** [sent] renewals reached the node before the error; the outcome of the rest is unknown. */
    data class Ended(val sent: Int, val total: Int, val caution: HSCaution) : RenewOutcome
}

data class NftDomainRenewUiState(
    val domainNames: List<String>,
    val renewUntil: String,
    val cost: String,
    val fee: String,
    val insufficientBalance: TranslatableString?,
    val canRenew: Boolean,
    val outcome: RenewOutcome,
)

// Not a data class: Wallet.toString() would print key material.
private class OwnerRenewal(val wallet: Wallet, val adapter: ISendTonAdapter, val nftAddresses: List<String>)

/** [namesRevealed]: the user revealed these names on the page that opened the sheet, so hiding does not mask them. */
class NftDomainRenewViewModel(
    domains: List<NftDomainExpiry>,
    private val namesRevealed: Boolean,
    private val walletResolver: NftOwnerWalletResolver,
    private val adapterManager: IAdapterManager,
    private val balanceHiddenManager: IBalanceHiddenManager,
    private val offlineOperationGate: OfflineOperationGate,
    private val xRateService: XRateService,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val renewable = domains.filterNot { it.isOnSale }
    private val renewUntil = renewUntilText()
    private var owners: List<OwnerRenewal>? = null
    private var estimates: List<TonFeeEstimate>? = null
    private var rate: CurrencyValue? = null
    private var outcome: RenewOutcome = RenewOutcome.Idle

    // Set before the first broadcast, so the captured list is never submitted twice.
    private var started = false

    var uiState by mutableStateOf(buildState())
        private set

    init {
        viewModelScope.launch { balanceHiddenManager.anyWalletVisibilityChangedFlow.collect { refresh() } }
        if (renewable.isNotEmpty()) {
            viewModelScope.launch {
                val resolved = prepareUntilReady()
                val coinUid = resolved.first().wallet.coin.uid
                rate = xRateService.getRate(coinUid)
                refresh()
                launch { xRateService.getRateFlow(coinUid).collect { rate = it; refresh() } }
                launch { stateChanges(resolved).collect { refresh() } }
            }
        }
    }

    // The wallet and its adapter may publish after the expiry was found, and an idle synced wallet emits
    // nothing, so owners and fees are retried together on a timer while the sheet is open.
    private suspend fun prepareUntilReady(): List<OwnerRenewal> {
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
    fun onRenew() {
        val owners = owners ?: return
        if (!canRenew()) return
        started = true
        outcome = RenewOutcome.Renewing
        refresh()
        viewModelScope.launch {
            outcome = renew(owners)
            if (outcome == RenewOutcome.Idle) started = false
            refresh()
        }
    }

    private suspend fun renew(owners: List<OwnerRenewal>): RenewOutcome {
        var sent = 0
        for (owner in owners) {
            val result = withContext(dispatcherProvider.io) { owner.adapter.renewDomains(owner.nftAddresses) }
            sent += result.broadcast.size
            result.error?.let { return stoppedOutcome(it, sent) }
        }
        return RenewOutcome.Done(sent)
    }

    private fun stoppedOutcome(error: Throwable, sent: Int): RenewOutcome {
        logger.w { "Domain renewal stopped after $sent of ${renewable.size}: ${error::class.simpleName}" }
        // Nothing left the device, so this is the one case the same selection may be confirmed again.
        if (sent == 0 && HardwareWalletSupport.isUserCancellation(error)) return RenewOutcome.Idle
        return RenewOutcome.Ended(sent, renewable.size, error.toSendCaution())
    }

    private suspend fun resolveOwners(): List<OwnerRenewal>? =
        renewable.groupBy { it.owner }.map { (owner, ownerDomains) ->
            val wallet = walletResolver.wallet(owner)
            val adapter = wallet?.let { adapterManager.getAdapterForWallet<ISendTonAdapter>(it) }
            if (wallet == null || adapter == null) {
                logger.w { "No active TON wallet for a renewal owner" }
                return null
            }
            OwnerRenewal(wallet, adapter, ownerDomains.map { it.nftAddress })
        }

    private suspend fun estimateFees(owners: List<OwnerRenewal>): List<TonFeeEstimate>? =
        withContext(dispatcherProvider.io) {
            try {
                owners.map { it.adapter.estimateDomainRenewalFee(it.nftAddresses) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w { "Domain renewal fee estimate failed: ${e::class.simpleName}" }
                null
            }
        }

    private fun stateChanges(owners: List<OwnerRenewal>): Flow<Unit> = merge(
        owners.flatMap { listOf(it.adapter.balanceUpdatedFlow, it.adapter.balanceStateUpdatedFlow) }.merge(),
        owners.map { offlineOperationGate.blockedFlow(it.wallet) }.merge().map { },
    )

    private fun refresh() {
        uiState = buildState()
    }

    private fun buildState(): NftDomainRenewUiState {
        val token = owners?.first()?.wallet?.token
        val balanceHidden = isBalanceHidden()
        return NftDomainRenewUiState(
            domainNames = renewable.map { if (balanceHidden && !namesRevealed) HIDDEN_NAME else it.name },
            renewUntil = renewUntil,
            cost = feePrimaryText(token, token?.let(::renewalCost)).orHide(balanceHidden),
            fee = approximateFeeText(token, estimates?.sumOf { it.fee }, rate, balanceHidden),
            insufficientBalance = insufficientBalanceText(balanceHidden),
            canRenew = canRenew(),
            outcome = outcome,
        )
    }

    private fun canRenew(): Boolean {
        val owners = owners ?: return false
        return !started && estimates != null && shortfall() == null && owners.all(::isReady)
    }

    private fun isReady(owner: OwnerRenewal): Boolean =
        owner.adapter.balanceState is AdapterState.Synced && !offlineOperationGate.isBlocked(owner.wallet)

    private fun renewalCost(token: Token): BigDecimal =
        DOMAIN_RENEW_AMOUNT.toBigDecimal(token.decimals) * renewable.size.toBigDecimal()

    private fun insufficientBalanceText(balanceHidden: Boolean): TranslatableString? {
        val (owner, required) = shortfall() ?: return null
        return insufficientRequiredBalanceText(required, availableBalance(owner), owner.wallet.token)
            .orHiddenBalanceText(balanceHidden, owner.wallet.coin.code)
    }

    /** The first owner whose balance does not cover its renewals, with the TON it needs. */
    private fun shortfall(): Pair<OwnerRenewal, BigDecimal>? {
        val estimates = estimates ?: return null
        return owners.orEmpty().zip(estimates)
            .firstOrNull { (owner, estimate) -> estimate.requiredTon > availableBalance(owner) }
            ?.let { (owner, estimate) -> owner to estimate.requiredTon }
    }

    private fun availableBalance(owner: OwnerRenewal): BigDecimal =
        adapterManager.getAdjustedBalanceData(owner.wallet)?.available ?: owner.adapter.balanceData.available

    // Until the owners resolve, the global eye decides.
    private fun isBalanceHidden(): Boolean {
        val owners = owners ?: return balanceHiddenManager.balanceHidden
        return owners.any { balanceHiddenManager.isWalletBalanceHidden(it.wallet.tokenQueryId) }
    }
}

/** The date a renewal started now runs until. */
internal fun renewUntilText(): String =
    DateHelper.formatDate(Calendar.getInstance().apply { add(Calendar.YEAR, 1) }.time, RENEW_UNTIL_PATTERN)
