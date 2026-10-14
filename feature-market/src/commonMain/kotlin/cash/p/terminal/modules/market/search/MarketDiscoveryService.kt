package cash.p.terminal.modules.market.search

import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.FullCoin
import io.horizontalsystems.core.MarketSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MarketDiscoveryService(
    private val marketKit: MarketKitWrapper,
    private val settingsStore: MarketSettingsStore,
) {
    private var recentCoins: List<FullCoin> = listOf()
    private var popularCoins: List<FullCoin> = listOf()

    private val _stateFlow = MutableStateFlow(
        State(
            recent = recentCoins,
            popular = popularCoins,
        )
    )
    val stateFlow: StateFlow<State>
        get() = _stateFlow.asStateFlow()

    suspend fun start() {
        recentCoins = marketKit
            .fullCoins(settingsStore.marketSearchRecentCoinUids)
            .sortedBy {
                settingsStore.marketSearchRecentCoinUids.indexOf(it.coin.uid)
            }
        popularCoins = marketKit.fullCoins("")

        emitState()
    }

    private fun emitState() {
        _stateFlow.update {
            State(
                recent = recentCoins,
                popular = popularCoins
            )
        }
    }

    fun addCoinToRecent(coin: Coin) {
        settingsStore.marketSearchRecentCoinUids =
            (listOf(coin.uid) + settingsStore.marketSearchRecentCoinUids).distinct().take(5)
    }

    data class State(
        val recent: List<FullCoin>,
        val popular: List<FullCoin>,
    )
}
