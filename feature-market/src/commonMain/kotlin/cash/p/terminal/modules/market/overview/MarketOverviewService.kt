package cash.p.terminal.modules.market.overview

import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.modules.market.TimeDuration
import cash.p.terminal.modules.market.TopMarket
import cash.p.terminal.modules.market.topcoins.MarketTopMoversRepository
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.AppForegroundState
import io.horizontalsystems.core.DispatcherProvider
import cash.p.terminal.wallet.models.MarketOverview
import cash.p.terminal.wallet.models.TopMovers
import io.reactivex.subjects.BehaviorSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import kotlinx.coroutines.rx2.await

class MarketOverviewService(
    private val marketTopMoversRepository: MarketTopMoversRepository,
    private val marketKit: MarketKitWrapper,
    private val appForegroundState: AppForegroundState,
    private val currencyManager: CurrencyManager,
    dispatcherProvider: DispatcherProvider,
) {
    private val coroutineScope = CoroutineScope(dispatcherProvider.io)
    private var topMoversJob: Job? = null
    private var marketOverviewJob: Job? = null

    val topMarketOptions: List<TopMarket> = TopMarket.values().toList()
    val timeDurationOptions: List<TimeDuration> = listOf(
        TimeDuration.SevenDay,
        TimeDuration.ThirtyDay,
    )
    val topMoversObservable: BehaviorSubject<Result<TopMovers>> = BehaviorSubject.create()
    val marketOverviewObservable: BehaviorSubject<Result<MarketOverview>> = BehaviorSubject.create()

    private fun updateTopMovers() {
        topMoversJob?.cancel()
        topMoversJob = coroutineScope.launch {
            try {
                val topMovers = marketTopMoversRepository.getTopMovers(currencyManager.baseCurrency).await()
                topMoversObservable.onNext(Result.success(topMovers))
            } catch (e: Throwable) {
                topMoversObservable.onNext(Result.failure(e))
            }
        }
    }

    private fun updateMarketOverview() {
        marketOverviewJob?.cancel()
        marketOverviewJob = coroutineScope.launch {
            try {
                val marketOverview =
                    marketKit.marketOverviewSingle(currencyManager.baseCurrency.code).await()
                marketOverviewObservable.onNext(Result.success(marketOverview))
            } catch (e: Throwable) {
                marketOverviewObservable.onNext(Result.failure(e))
            }
        }
    }

    private fun forceRefresh() {
        updateTopMovers()
        updateMarketOverview()
    }

    fun start() {
        coroutineScope.launch {
            appForegroundState.inForeground.collect { inForeground ->
                if (inForeground) {
                    forceRefresh()
                }
            }
        }

        coroutineScope.launch {
            currencyManager.baseCurrencyUpdatedSignal.asFlow().collect {
                forceRefresh()
            }
        }

        forceRefresh()
    }

    fun stop() {
        coroutineScope.cancel()
    }

    fun refresh() {
        forceRefresh()
    }

}
