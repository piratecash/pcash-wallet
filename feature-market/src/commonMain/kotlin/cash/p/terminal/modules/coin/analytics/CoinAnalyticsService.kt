package cash.p.terminal.modules.coin.analytics

import cash.p.terminal.ui_compose.entities.DataState
import io.horizontalsystems.core.CurrencyManager
import cash.p.terminal.wallet.MarketKitWrapper
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.Currency
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.models.Analytics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.rx2.await

class CoinAnalyticsService(
    val fullCoin: FullCoin,
    private val marketKit: MarketKitWrapper,
    private val currencyManager: CurrencyManager,
) {

    private val _stateFlow = MutableStateFlow<DataState<Analytics>>(DataState.Loading)
    val stateFlow: Flow<DataState<Analytics>> = _stateFlow

    val currency: Currency
        get() = currencyManager.baseCurrency

    fun blockchain(uid: String): Blockchain? {
        return marketKit.blockchain(uid)
    }

    fun blockchains(uids: List<String>): List<Blockchain> {
        return marketKit.blockchains(uids)
    }

    suspend fun start() {
        fetch()
    }

    suspend fun refresh() {
        fetch()
    }

    private suspend fun fetch() {
        _stateFlow.emit(DataState.Loading)

        try {
            marketKit.analyticsSingle(fullCoin.coin.uid, currency.code).await()
                .let {
                    _stateFlow.emit(DataState.Success(it))
                }
        } catch (error: Throwable) {
            _stateFlow.emit(DataState.Error(error))
        }
    }

}
