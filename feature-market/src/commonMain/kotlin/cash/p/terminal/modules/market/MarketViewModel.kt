package cash.p.terminal.modules.market

import androidx.lifecycle.viewModelScope
import cash.p.terminal.entities.LaunchPage
import cash.p.terminal.modules.market.MarketModule.Tab
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.models.MarketGlobal
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.MarketSettingsStore
import io.horizontalsystems.core.ViewModelUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await

class MarketViewModel(
    private val marketSettings: MarketSettingsStore,
    private val marketKit: MarketKitWrapper,
    private val currencyManager: CurrencyManager,
    private val dispatcherProvider: DispatcherProvider
) : ViewModelUiState<MarketModule.UiState>() {

    private val logger = Logger.withTag("MarketViewModel")

    val tabs = Tab.entries.toTypedArray()
    private var currency = currencyManager.baseCurrency

    private var marketGlobal: MarketGlobal? = null
    private var selectedTab: Tab = getInitialTab()

    init {
        // One collector for the start request and for currency changes, so a reply to a superseded
        // request can never overwrite the newer one.
        viewModelScope.launch(dispatcherProvider.io) {
            currencyManager.baseCurrencyUpdatedFlow
                .onStart { emit(Unit) }
                .collectLatest { loadMarketGlobal() }
        }
    }

    override fun createState() = MarketModule.UiState(
        selectedTab = selectedTab,
        marketGlobal = marketGlobal,
        currency = currency
    )

    fun onSelect(tab: Tab) {
        selectedTab = tab
        marketSettings.currentMarketTab = tab.name
        emitState()
    }

    private suspend fun loadMarketGlobal() {
        currency = currencyManager.baseCurrency
        marketGlobal = null
        emitState()
        try {
            marketGlobal = marketKit.marketGlobalSingle(currency.code).await()
            emitState()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "updateMarketOverview" }
        }
    }

    private fun getInitialTab() = when (LaunchPage.fromString(marketSettings.launchPage)) {
        LaunchPage.Watchlist -> Tab.Watchlist
        else -> Tab.fromString(marketSettings.currentMarketTab) ?: Tab.Coins
    }
}
