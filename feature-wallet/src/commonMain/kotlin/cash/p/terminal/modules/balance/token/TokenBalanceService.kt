package cash.p.terminal.modules.balance.token

import cash.p.terminal.wallet.Clearable
import cash.p.terminal.modules.balance.BalanceAdapterRepository
import cash.p.terminal.wallet.balance.BalanceItem
import cash.p.terminal.modules.balance.DefaultBalanceXRateRepository
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.models.CoinPrice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.withContext

class TokenBalanceService(
    private val wallet: Wallet,
    private val xRateRepository: DefaultBalanceXRateRepository,
    private val balanceAdapterRepository: BalanceAdapterRepository
) : Clearable {

    private val _balanceItemFlow = MutableStateFlow<BalanceItem?>(null)
    val balanceItemFlow = _balanceItemFlow.asStateFlow()

    val balanceItem: BalanceItem? get() = _balanceItemFlow.value

    private val job = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.Default + job)
    val baseCurrency by xRateRepository::baseCurrency

    suspend fun start() = withContext(Dispatchers.IO) {
        balanceAdapterRepository.setWallet(listOf(wallet))
        xRateRepository.setCoinUids(listOf(wallet.coin.uid))

        val latestRates = xRateRepository.getLatestRates()
        // Without an adapter the request below answers nothing, right after a seed import above all.
        val adapterMissing = !balanceAdapterRepository.hasAdapter(wallet)

        _balanceItemFlow.value = BalanceItem(
            wallet = wallet,
            balanceData = balanceAdapterRepository.balanceData(wallet),
            state = balanceAdapterRepository.state(wallet),
            sendAllowed = balanceAdapterRepository.sendAllowed(wallet),
            coinPrice = latestRates[wallet.coin.uid],
            warning = balanceAdapterRepository.warning(wallet),
            transactionsSyncState = balanceAdapterRepository.transactionsSyncState(wallet),
        )

        if (adapterMissing) {
            coroutineScope.launch { loadWarningWhenAdapterAppears() }
        }
        coroutineScope.launch {
            xRateRepository.itemObservable.asFlow().collect {
                handleXRateUpdate(it)
            }
        }
        coroutineScope.launch {
            balanceAdapterRepository.readyObservable.asFlow().collect {
                handleAdapterUpdate()
            }
        }
        coroutineScope.launch {
            balanceAdapterRepository.updatesObservable.asFlow().collect {
                handleAdapterUpdate()
            }
        }
    }

    fun refreshRates() {
        xRateRepository.refresh()
    }

    private suspend fun loadWarningWhenAdapterAppears() {
        balanceAdapterRepository.awaitAdapter(wallet)
        if (balanceItem?.warning != null) return
        // A failed request answers null too: it must not erase anything.
        val warning = balanceAdapterRepository.warning(wallet) ?: return
        _balanceItemFlow.update { it?.copy(warning = warning) }
    }

    private fun handleXRateUpdate(latestRates: Map<String, CoinPrice?>) {
        _balanceItemFlow.update {
            it?.copy(coinPrice = latestRates[wallet.coin.uid])
        }
    }

    private fun handleAdapterUpdate() {
        _balanceItemFlow.update {
            it?.copy(
                balanceData = balanceAdapterRepository.balanceData(wallet),
                state = balanceAdapterRepository.state(wallet),
                sendAllowed = balanceAdapterRepository.sendAllowed(wallet),
                transactionsSyncState = balanceAdapterRepository.transactionsSyncState(wallet),
            )
        }
    }

    override fun clear() {
        balanceAdapterRepository.clear()
        coroutineScope.cancel()
    }
}
