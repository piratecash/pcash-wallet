package cash.p.terminal.modules.multiswap

import cash.p.terminal.core.ISendTronAdapter
import cash.p.terminal.core.ISendZcashAdapter
import cash.p.terminal.core.ServiceState
import cash.p.terminal.core.getFeeTokenBalance
import cash.p.terminal.core.isNative
import cash.p.terminal.modules.send.hasInsufficientFeeTokenBalance
import cash.p.terminal.wallet.AdapterState
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
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.math.BigDecimal

class TokenBalanceService(
    private val adapterManager: IAdapterManager,
    private val marketKit: MarketKitWrapper,
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
    private var insufficientFeeBalance: Boolean = false
    private var tronResources: AccountResources? = null
    private var coroutineScope: CoroutineScope? = null
    private var balanceUpdateJob: Job? = null
    private var feeUpdateJob: Job? = null

    override fun createState() = State(
        balance = balance,
        displayBalance = displayBalance,
        error = error,
        fee = fee,
        feeToken = feeToken,
        feeCoinBalance = feeCoinBalance,
        insufficientFeeBalance = insufficientFeeBalance,
        tronResources = tronResources,
    )

    fun setToken(token: Token?) {
        this.token = token

        refreshAvailableBalance()
        refreshSubscriptions()
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
        refreshSubscriptions()
    }

    private fun refreshSubscriptions() {
        balanceUpdateJob?.cancel()
        feeUpdateJob?.cancel()

        val currentAdapter = adapter ?: return
        val scope = coroutineScope ?: return

        balanceUpdateJob = scope.launch {
            currentAdapter.balanceUpdatedFlow.collect {
                refreshBalanceState()
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

    // TRON tokens estimate their own transfer cost in TRX; elsewhere a token's fee is the native coin's.
    private fun feeAdapter(token: Token, tokenAdapter: IBalanceAdapter?): IBalanceAdapter? =
        if (token.type.isNative || token.blockchainType == BlockchainType.Tron) tokenAdapter else nativeAdapter()

    private fun nativeAdapter(): IBalanceAdapter? =
        feeToken?.let { adapterManager.getAdapterForToken(it) as? IBalanceAdapter }

    private fun refreshBalanceState() {
        refreshAvailableBalance()
        validate()
        emitState()
    }

    private fun validate() {
        error = null
        insufficientFeeBalance = false

        val amount = amount ?: return

        error = when (adapter?.balanceState) {
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

        // Fee balance validation (only when no other error)
        if (error == null) {
            val currentFee = fee ?: return
            val currentToken = token ?: return

            insufficientFeeBalance = hasInsufficientFeeTokenBalance(
                token = currentToken,
                fee = currentFee,
                feeTokenBalance = feeCoinBalance,
            )
        }
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
            fee = feeAdapter(currentToken, currentAdapter)?.fee?.value

            feeCoinBalance = if (currentToken.type.isNative) {
                null
            } else {
                feeToken?.let {
                    adapterManager.getFeeTokenBalance(it, currentToken)
                } ?: nativeAdapter()?.balanceData?.available
            }
        } else {
            feeToken = null
            fee = null
            feeCoinBalance = null
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
        val feeToken: Token?,
        val feeCoinBalance: BigDecimal?,
        val insufficientFeeBalance: Boolean,
        val tronResources: AccountResources? = null,
    )
}

class WalletSyncing : Exception()
class WalletNotSynced : Exception()
