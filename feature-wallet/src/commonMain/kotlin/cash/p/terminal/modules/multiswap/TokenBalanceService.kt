package cash.p.terminal.modules.multiswap

import cash.p.terminal.core.INativeBalanceProvider
import cash.p.terminal.core.ISendTronAdapter
import cash.p.terminal.core.ISendZcashAdapter
import cash.p.terminal.core.ITokenTransferCostEstimator
import cash.p.terminal.core.ServiceState
import cash.p.terminal.core.TokenTransferCost
import cash.p.terminal.core.feeTokenBalance
import cash.p.terminal.core.isNative
import cash.p.terminal.core.managers.TokenTransferCostCache
import cash.p.terminal.core.managers.TokenTransferCostEstimate
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.modules.send.TokenFeeStatus
import cash.p.terminal.modules.send.hasInsufficientFeeTokenBalance
import cash.p.terminal.modules.send.tokenFeeStatus
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.nativeTokenQuery
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tronkit.models.AccountResources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import java.math.BigDecimal

class TokenBalanceService(
    private val adapterManager: IAdapterManager,
    private val marketKit: MarketKitWrapper,
    private val transferCostCache: TokenTransferCostCache,
    private val accountManager: IAccountManager,
    private val offlineOperationGate: OfflineOperationGate,
) : ServiceState<TokenBalanceService.State>() {
    private var token: Token? = null
    private var amount: BigDecimal? = null
    private var adapter: IBalanceAdapter? = null
    private var balance: BigDecimal? = null
    private var displayBalance: BigDecimal? = null
    private var error: Throwable? = null

    private var fee: BigDecimal? = null
    private var feeToken: Token? = null
    private var feeCoinBalance: BigDecimal? = null
    private var feeCoinBalanceLoaded: Boolean = false
    private var insufficientFeeBalance: Boolean = false
    private var feeBalanceRequired: BigDecimal? = null
    private var tronResources: AccountResources? = null
    private var coroutineScope: CoroutineScope? = null
    private var balanceUpdateJob: Job? = null
    private var feeUpdateJob: Job? = null
    private var nativeBalanceUpdateJob: Job? = null
    private var subscribedSources: Triple<Token?, IBalanceAdapter?, IBalanceAdapter?>? = null

    private var transferCostEstimate: TokenTransferCostEstimate? = null
    private val transferCost: TokenTransferCost?
        get() = transferCostEstimate?.cost
    private var transferCostLoading: Boolean = false
    private var transferCostAdapter: IBalanceAdapter? = null
    private var transferCostJob: Job? = null

    override fun createState() = State(
        balance = balance,
        displayBalance = displayBalance,
        error = error,
        fee = fee,
        feeLoading = transferCostLoading,
        feeToken = feeToken,
        feeCoinBalance = feeCoinBalance,
        insufficientFeeBalance = insufficientFeeBalance,
        feeBalanceRequired = feeBalanceRequired,
        tronResources = tronResources,
    )

    fun setToken(token: Token?) {
        this.token = token

        refreshAvailableBalance()
        refreshSubscriptions()
        refreshTransferCost()
        validate()

        emitState()
    }

    fun setAmount(amount: BigDecimal?) {
        this.amount = amount

        validate()

        emitState()
    }

    fun start(coroutineScope: CoroutineScope) {
        this.coroutineScope = coroutineScope
        subscribedSources = null
        refreshSubscriptions()
        refreshTransferCost()
        // An adapter published after the token was picked has no subscriptions and no estimate yet.
        coroutineScope.launch {
            adapterManager.adaptersReadyObservable.asFlow().collect { setToken(token) }
        }
    }

    private fun refreshSubscriptions() {
        // Kit subjects do not replay: an update arriving while collectors are recreated is lost.
        val sources = Triple(token, adapter, nativeAdapter())
        if (sources == subscribedSources) return

        balanceUpdateJob?.cancel()
        feeUpdateJob?.cancel()
        nativeBalanceUpdateJob?.cancel()
        subscribedSources = null

        val currentAdapter = adapter ?: return
        val scope = coroutineScope ?: return
        subscribedSources = sources

        balanceUpdateJob = scope.launch {
            currentAdapter.balanceUpdatedFlow.collect {
                onBalancesChanged()
            }
        }

        nativeBalanceUpdates(currentAdapter)?.let { updates ->
            nativeBalanceUpdateJob = scope.launch {
                updates.collect { onBalancesChanged() }
            }
        }

        val feeUpdates = token?.let { feeUpdates(it, currentAdapter) } ?: return
        feeUpdateJob = scope.launch {
            feeUpdates.collect {
                refreshBalanceState()
            }
        }
    }

    // Fees that are estimated after the balance arrives change what "100%" may spend.
    private fun feeUpdates(token: Token, tokenAdapter: IBalanceAdapter): Flow<Any?>? {
        val fee = feeAdapter(token, tokenAdapter)?.fee ?: return null
        return when (token.blockchainType) {
            BlockchainType.Zcash -> fee
            BlockchainType.Tron -> (tokenAdapter as? ISendTronAdapter)
                ?.let { merge(fee, it.accountResourcesFlow) }
                ?: fee
            else -> null
        }
    }

    // A sync that confirms the same zero changes only the native adapter's state, not its balance.
    private fun nativeBalanceUpdates(tokenAdapter: IBalanceAdapter): Flow<Unit>? {
        if (token?.type?.isNative != false) return null
        val native = nativeAdapter()
        return merge(
            native?.balanceUpdatedFlow ?: emptyFlow(),
            native?.balanceStateUpdatedFlow ?: emptyFlow(),
            (tokenAdapter as? INativeBalanceProvider)?.nativeBalanceUpdatedFlow ?: emptyFlow(),
        )
    }

    // TRON tokens estimate their own transfer cost in TRX; elsewhere a token's fee is the native coin's.
    private fun feeAdapter(token: Token, tokenAdapter: IBalanceAdapter?): IBalanceAdapter? =
        if (token.type.isNative || token.blockchainType == BlockchainType.Tron) tokenAdapter else nativeAdapter()

    // Quote emissions call setToken with the same token: re-estimate only a missing or stale cost,
    // because a stale fee can block the swap and a missing one cannot.
    private fun refreshTransferCost() {
        val currentAdapter = adapter
        if (currentAdapter === transferCostAdapter && (transferCostLoading || transferCostEstimate?.isFresh == true)) return
        val scope = coroutineScope ?: return

        transferCostJob?.cancel()
        transferCostAdapter = currentAdapter
        transferCostEstimate = null
        transferCostLoading = false

        val estimator = currentAdapter as? ITokenTransferCostEstimator ?: return
        val currentToken = token ?: return
        val account = onlineAccount(currentToken) ?: return

        // TRON re-estimates every time: the estimate also refreshes the resources shown under the fee.
        // SPL costs a constant, so it must not wait behind an RPC estimate holding the cache.
        val cacheKey = when (currentToken.blockchainType) {
            BlockchainType.Tron, BlockchainType.Solana -> null
            else -> "${account.id}|${currentToken.tokenQuery.id}"
        }
        transferCostLoading = true
        transferCostJob = scope.launch {
            transferCostEstimate = transferCostCache.get(cacheKey, estimator)
            transferCostLoading = false
            refreshBalanceState()
        }
    }

    private fun onlineAccount(token: Token): Account? =
        accountManager.activeAccount?.takeUnless { offlineOperationGate.isBlocked(it, token.blockchainType) }

    private fun nativeAdapter(): IBalanceAdapter? =
        feeToken?.let { adapterManager.getAdapterForToken(it) as? IBalanceAdapter }

    // A jetton estimate fails until the jetton balance arrives, so a balance change retries a missing one.
    private fun onBalancesChanged() {
        refreshAvailableBalance()
        refreshTransferCost()
        refreshBalanceState()
    }

    private fun refreshBalanceState() {
        refreshAvailableBalance()
        validate()
        emitState()
    }

    private fun validate() {
        error = null
        insufficientFeeBalance = false
        feeBalanceRequired = null

        val amount = amount ?: return

        error = balanceError(amount)

        // Route details read only the fee flag, so a balance error must not hide a certain fee shortage.
        val estimator = adapter as? ITokenTransferCostEstimator
        if (estimator != null) {
            validateTransferCost(estimator)
            return
        }
        if (error != null) return
        val currentFee = fee ?: return
        val currentToken = token ?: return

        insufficientFeeBalance = hasInsufficientFeeTokenBalance(
            token = currentToken,
            fee = currentFee,
            feeTokenBalance = feeCoinBalance,
        )
    }

    private fun balanceError(amount: BigDecimal): Throwable? = when (adapter?.balanceState) {
        null -> null
        is AdapterState.Connecting -> WalletSyncing()
        is AdapterState.SearchingTxs -> WalletSyncing()
        is AdapterState.Syncing -> WalletSyncing()
        is AdapterState.NotSynced -> WalletNotSynced()
        is AdapterState.Paused -> WalletNotSynced()
        AdapterState.Synced -> {
            if (amount > balance) {
                SwapError.InsufficientBalanceFrom
            } else {
                null
            }
        }
    }

    private fun validateTransferCost(estimator: ITokenTransferCostEstimator) {
        val status = tokenFeeStatus(
            transferAlwaysCostsNative = estimator.transferAlwaysCostsNative,
            nativeBalance = feeCoinBalance,
            nativeBalanceLoaded = feeCoinBalanceLoaded,
            fee = transferCost?.fee,
            requiredBalance = transferCost?.requiredBalance,
        )
        insufficientFeeBalance = status == TokenFeeStatus.CertainlyShort
        feeBalanceRequired = transferCost?.requiredBalance?.takeIf { status == TokenFeeStatus.MaybeShort && error == null }
    }

    private fun refreshAvailableBalance() {
        val currentAdapter =
            token?.let { adapterManager.getAdapterForToken<IBalanceAdapter>(it) }
        adapter = currentAdapter
        val currentToken = token
        val adjusted = token?.let { adapterManager.getAdjustedBalanceDataForToken(it)?.available }
        balance = getAvailableBalance(currentToken, currentAdapter, adjusted)

        displayBalance = adjusted
        tronResources = (currentAdapter as? ISendTronAdapter)?.accountResourcesFlow?.value

        if (currentToken != null) {
            feeToken = marketKit.token(currentToken.nativeTokenQuery)
                ?: currentToken
            fee = if (currentAdapter is ITokenTransferCostEstimator) {
                transferCost?.takeIf { currentAdapter === transferCostAdapter }?.fee
            } else {
                feeAdapter(currentToken, currentAdapter)?.fee?.value
            }

            if (currentToken.type.isNative) {
                feeCoinBalance = null
                feeCoinBalanceLoaded = false
            } else {
                val feeBalance = feeToken?.let { adapterManager.feeTokenBalance(it, currentToken) }
                feeCoinBalance = feeBalance?.amount ?: nativeAdapter()?.balanceData?.available
                feeCoinBalanceLoaded = feeBalance?.fetched == true
            }
        } else {
            feeToken = null
            fee = null
            feeCoinBalance = null
            feeCoinBalanceLoaded = false
        }
    }

    private fun getAvailableBalance(
        token: Token?,
        adapter: IBalanceAdapter?,
        adjustedAvailable: BigDecimal?,
    ): BigDecimal? {
        val zcashAdapter = adapter as? ISendZcashAdapter
        if (token?.blockchainType == BlockchainType.Zcash && zcashAdapter != null) {
            return zcashAdapter.maxSpendableBalance
        }

        val adapterAvailableBalance = adapter?.maxSpendableBalance
        return if (adjustedAvailable != null && adapterAvailableBalance != null) {
            minOf(adjustedAvailable, adapterAvailableBalance)
        } else {
            adjustedAvailable ?: adapterAvailableBalance
        }
    }

    data class State(
        val balance: BigDecimal?,
        val displayBalance: BigDecimal?,
        val error: Throwable?,
        val fee: BigDecimal?,
        val feeLoading: Boolean,
        val feeToken: Token?,
        val feeCoinBalance: BigDecimal?,
        val insufficientFeeBalance: Boolean,
        /** Native balance an estimated transfer needs, set when the balance may fall short of it. */
        val feeBalanceRequired: BigDecimal?,
        val tronResources: AccountResources? = null,
    )
}

class WalletSyncing : Exception()
class WalletNotSynced : Exception()
