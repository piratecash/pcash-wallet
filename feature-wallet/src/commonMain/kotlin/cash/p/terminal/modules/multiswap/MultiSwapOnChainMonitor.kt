package cash.p.terminal.modules.multiswap

import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicBoolean

class MultiSwapOnChainMonitor(
    private val walletManager: IWalletManager,
    private val adapterManager: IAdapterManager,
    private val dispatcherProvider: DispatcherProvider,
) {

    /**
     * Returns true if monitoring started, false if wallet/balance not found.
     */
    fun observeBalanceIncrease(
        side: SwapSide,
        scope: CoroutineScope,
        onBalanceIncreased: () -> Unit,
    ): Boolean {
        val wallet = walletManager.activeWallets.findBySwapSide(side) ?: return false
        val initialBalance = currentBalance(wallet) ?: return false
        val completed = AtomicBoolean(false)

        var flowJob: Job? = null
        var pollJob: Job? = null

        val guardedCallback: () -> Unit = {
            if (completed.compareAndSet(false, true)) {
                onBalanceIncreased()
                flowJob?.cancel()
                pollJob?.cancel()
            }
        }

        flowJob = scope.launch(dispatcherProvider.io) {
            observeViaFlow(wallet, initialBalance, guardedCallback)
        }
        pollJob = scope.launch(dispatcherProvider.io) {
            pollBalance(wallet, initialBalance, guardedCallback)
        }
        return true
    }

    private suspend fun observeViaFlow(
        wallet: Wallet,
        initialBalance: BigDecimal,
        onBalanceIncreased: () -> Unit,
    ) {
        adapterManager.walletBalanceUpdatedFlow
            .filter { it == wallet }
            .onStart { emit(wallet) }
            .collect {
                checkBalanceIncrease(wallet, initialBalance, onBalanceIncreased)
            }
    }

    private suspend fun pollBalance(
        wallet: Wallet,
        initialBalance: BigDecimal,
        onBalanceIncreased: () -> Unit,
    ) {
        while (currentCoroutineContext().isActive) {
            delay(POLL_INTERVAL_MS)
            if (checkBalanceIncrease(wallet, initialBalance, onBalanceIncreased)) return
        }
    }

    private fun checkBalanceIncrease(
        wallet: Wallet,
        initialBalance: BigDecimal,
        onBalanceIncreased: () -> Unit,
    ): Boolean {
        val current = currentBalance(wallet) ?: return false
        if (current > initialBalance) {
            onBalanceIncreased()
            return true
        }
        return false
    }

    private fun currentBalance(wallet: Wallet): BigDecimal? =
        adapterManager.getBalanceAdapterForWallet(wallet)?.balanceData?.total

    companion object {
        private const val POLL_INTERVAL_MS = 5000L
    }
}
