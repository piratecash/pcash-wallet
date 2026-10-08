package cash.p.terminal.wallet

import cash.p.terminal.network.pirate.domain.repository.CoinsListRepository
import cash.p.terminal.wallet.chart.HsChartRequestHelper
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.managers.CoinHistoricalPriceManager
import cash.p.terminal.wallet.managers.CoinPriceManager
import cash.p.terminal.wallet.managers.CoinPriceSyncManager
import cash.p.terminal.wallet.managers.GlobalMarketInfoManager
import cash.p.terminal.wallet.managers.MarketOverviewManager
import cash.p.terminal.wallet.managers.NftManager
import cash.p.terminal.wallet.managers.PostManager
import cash.p.terminal.wallet.managers.VirtualCoinMapper
import cash.p.terminal.wallet.models.Analytics
import cash.p.terminal.wallet.models.AnalyticsPreview
import cash.p.terminal.wallet.models.ChartPoint
import cash.p.terminal.wallet.models.CoinCategory
import cash.p.terminal.wallet.models.CoinInvestment
import cash.p.terminal.wallet.models.CoinPrice
import cash.p.terminal.wallet.models.CoinReport
import cash.p.terminal.wallet.models.CoinTreasury
import cash.p.terminal.wallet.models.DefiMarketInfo
import cash.p.terminal.wallet.models.Etf
import cash.p.terminal.wallet.models.EtfPoint
import cash.p.terminal.wallet.models.EtfPointResponse
import cash.p.terminal.wallet.models.EtfResponse
import cash.p.terminal.wallet.models.GlobalMarketPoint
import cash.p.terminal.wallet.models.IntervalData
import cash.p.terminal.wallet.models.MarketGlobal
import cash.p.terminal.wallet.models.MarketInfo
import cash.p.terminal.wallet.models.MarketInfoOverview
import cash.p.terminal.wallet.models.MarketOverview
import cash.p.terminal.wallet.models.MarketTicker
import cash.p.terminal.wallet.models.NftTopCollection
import cash.p.terminal.wallet.models.Post
import cash.p.terminal.wallet.models.RankMultiValue
import cash.p.terminal.wallet.models.RankValue
import cash.p.terminal.wallet.models.SubscriptionResponse
import cash.p.terminal.wallet.models.TokenHolders
import cash.p.terminal.wallet.models.TopMovers
import cash.p.terminal.wallet.models.TopPair
import cash.p.terminal.wallet.models.TopPlatform
import cash.p.terminal.wallet.models.TopPlatformMarketCapPoint
import cash.p.terminal.wallet.providers.CoinPriceSchedulerFactory
import cash.p.terminal.wallet.providers.CryptoCompareProvider
import cash.p.terminal.wallet.providers.HsNftProvider
import cash.p.terminal.wallet.providers.HsProvider
import cash.p.terminal.wallet.storage.CoinHistoricalPriceStorage
import cash.p.terminal.wallet.storage.CoinPriceStorage
import cash.p.terminal.wallet.storage.CoinStorage
import cash.p.terminal.wallet.storage.GlobalMarketInfoStorage
import cash.p.terminal.wallet.storage.MarketDatabase
import cash.p.terminal.wallet.syncers.CoinSyncer
import cash.p.terminal.wallet.syncers.CoinsListMapper
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DefaultDispatcherProvider
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.models.HsPeriodType
import io.horizontalsystems.core.models.HsTimePeriod
import io.reactivex.Observable
import kotlinx.coroutines.CancellationException
import io.reactivex.Single
import cash.p.terminal.wallet.managers.CoinManager
import org.koin.java.KoinJavaComponent.get
import org.koin.java.KoinJavaComponent.inject
import retrofit2.Response
import java.math.BigDecimal
import java.util.Date

class MarketKit(
    private val nftManager: NftManager,
    private val marketOverviewManager: MarketOverviewManager,
    private val coinManager: CoinManager,
    private val coinSyncer: CoinSyncer,
    private val coinPriceManager: CoinPriceManager,
    private val coinHistoricalPriceManager: CoinHistoricalPriceManager,
    private val coinPriceSyncManager: CoinPriceSyncManager,
    private val postManager: PostManager,
    private val globalMarketInfoManager: GlobalMarketInfoManager,
    private val hsProvider: HsProvider,
) {
    private val logger = Logger.withTag("MarketKit")

    private val coinsMap by lazy { coinManager.allCoins().associateBy { it.uid } }

    // Coins
    suspend fun fullCoins(filter: String, limit: Int = 20): List<FullCoin> {
        return coinManager.fullCoins(filter, limit)
    }

    fun fullCoins(coinUids: List<String>): List<FullCoin> {
        return coinManager.fullCoins(coinUids)
    }

    fun coin(coinUid: String): Coin? = coinManager.coin(coinUid)

    fun allCoins(): List<Coin> = coinManager.allCoins()

    fun token(query: TokenQuery): Token? =
        coinManager.token(query)

    fun tokens(queries: List<TokenQuery>): List<Token> =
        coinManager.tokens(queries)

    fun tokens(reference: String): List<Token> =
        coinManager.tokens(reference)

    fun tokens(blockchainType: BlockchainType, filter: String, limit: Int = 20): List<Token> =
        coinManager.tokens(blockchainType, filter, limit)

    fun blockchains(uids: List<String>): List<Blockchain> =
        coinManager.blockchains(uids)

    fun allBlockchains(): List<Blockchain> =
        coinManager.allBlockchains()

    fun blockchain(uid: String): Blockchain? =
        coinManager.blockchain(uid)

    fun marketInfosSingle(
        top: Int,
        currencyCode: String,
        defi: Boolean,
    ): Single<List<MarketInfo>> {
        return hsProvider.marketInfosSingle(top, currencyCode, defi).map {
            coinManager.getMarketInfos(it)
        }
    }

    fun topCoinsMarketInfosSingle(top: Int, currencyCode: String): Single<List<MarketInfo>> {
        return hsProvider.topCoinsMarketInfosSingle(top, currencyCode).map {
            coinManager.getMarketInfos(it)
        }
    }

    fun advancedMarketInfosSingle(
        top: Int = 250,
        currencyCode: String,
    ): Single<List<MarketInfo>> {
        return hsProvider.advancedMarketInfosSingle(top, currencyCode).map {
            coinManager.getMarketInfos(it)
        }
    }

    fun marketInfosSingle(
        coinUids: List<String>,
        currencyCode: String,
    ): Single<List<MarketInfo>> {
        return hsProvider.marketInfosSingle(coinUids, currencyCode).map {
            coinManager.getMarketInfos(it)
        }
    }

    fun marketInfosSingle(
        categoryUid: String,
        currencyCode: String,
    ): Single<List<MarketInfo>> {
        return hsProvider.marketInfosSingle(categoryUid, currencyCode).map {
            coinManager.getMarketInfos(it)
        }
    }

    suspend fun marketInfoOverviewSingle(
        coinUid: String,
        currencyCode: String,
        language: String,
    ): MarketInfoOverview {
        return hsProvider.getMarketInfoOverview(
            coinGeckoUid = coinUid,
            currencyCode = currencyCode,
            language = language,
        ).let { rawOverview ->
            val fullCoin = coinManager.fullCoin(coinUid) ?: throw Exception("No Full Coin")

            rawOverview.marketInfoOverview(fullCoin)
        }
    }

    fun marketInfoTvlSingle(
        coinUid: String,
        currencyCode: String,
        timePeriod: HsTimePeriod
    ): Single<List<ChartPoint>> {
        return hsProvider.marketInfoTvlSingle(coinUid, currencyCode, timePeriod)
    }

    fun marketInfoGlobalTvlSingle(
        chain: String,
        currencyCode: String,
        timePeriod: HsTimePeriod
    ): Single<List<ChartPoint>> {
        return hsProvider.marketInfoGlobalTvlSingle(chain, currencyCode, timePeriod)
    }

    fun defiMarketInfosSingle(currencyCode: String): Single<List<DefiMarketInfo>> {
        return hsProvider.defiMarketInfosSingle(currencyCode).map {
            coinManager.getDefiMarketInfos(it)
        }
    }

    //Signals

    fun coinsSignalsSingle(coinUids: List<String>): Single<Map<String, Analytics.TechnicalAdvice.Advice>> {
        return hsProvider.coinsSignalsSingle(coinUids).map { list ->
            list.mapNotNull { coinSignal ->
                coinSignal.signal?.let { coinSignal.uid to it }
            }.toMap()
        }
    }


    // Categories

    fun coinCategoriesSingle(currencyCode: String): Single<List<CoinCategory>> =
        hsProvider.getCoinCategories(currencyCode)

    fun coinCategoryMarketPointsSingle(
        categoryUid: String,
        interval: HsTimePeriod,
        currencyCode: String
    ) =
        hsProvider.coinCategoryMarketPointsSingle(categoryUid, interval, currencyCode)

    suspend fun sync(forceUpdate: Boolean) {
        // CoinSyncer's own failure write can fail too (e.g. disk full); nothing may escape into the app-wide collector.
        try {
            coinSyncer.sync(forceUpdate)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "sync() error" }
        }
    }

    // Coin Prices

    fun refreshCoinPrices(currencyCode: String) {
        coinPriceSyncManager.refresh(currencyCode)
    }

    fun coinPrice(coinUid: String, currencyCode: String): CoinPrice? {
        return coinPriceManager.coinPrice(coinUid, currencyCode)
    }

    fun coinPriceMap(coinUids: List<String>, currencyCode: String): Map<String, CoinPrice> {
        return coinPriceManager.coinPriceMap(coinUids, currencyCode)
    }

    fun coinPriceObservable(
        tag: String,
        coinUid: String,
        currencyCode: String
    ): Observable<CoinPrice> {
        return coinPriceSyncManager.coinPriceObservable(tag, coinUid, currencyCode)
    }

    fun coinPriceMapObservable(
        tag: String,
        coinUids: List<String>,
        currencyCode: String
    ): Observable<Map<String, CoinPrice>> {
        return coinPriceSyncManager.coinPriceMapObservable(tag, coinUids, currencyCode)
    }

    // Coin Historical Price

    suspend fun coinHistoricalPriceSingle(
        coinUid: String,
        currencyCode: String,
        timestamp: Long
    ): BigDecimal {
        return coinHistoricalPriceManager.coinHistoricalPriceSingle(
            coinUid,
            currencyCode,
            timestamp
        )
    }

    fun coinHistoricalPrice(coinUid: String, currencyCode: String, timestamp: Long): BigDecimal? =
        coinHistoricalPriceManager.coinHistoricalPrice(coinUid, currencyCode, timestamp)

    // Posts

    fun postsSingle(): Single<List<Post>> {
        return postManager.postsSingle()
    }

    // Market Tickers

    suspend fun marketTickersSingle(coinUid: String, currencyCode: String): List<MarketTicker> =
        hsProvider.marketTickers(coinUid, currencyCode)

    // Details

    fun tokenHoldersSingle(
        authToken: String,
        coinUid: String,
        blockchainUid: String
    ): Single<TokenHolders> =
        hsProvider.tokenHoldersSingle(authToken, coinUid, blockchainUid)

    fun treasuriesSingle(coinUid: String, currencyCode: String): Single<List<CoinTreasury>> =
        hsProvider.coinTreasuriesSingle(coinUid, currencyCode)

    fun investmentsSingle(coinUid: String): Single<List<CoinInvestment>> =
        hsProvider.investmentsSingle(coinUid)

    fun coinReportsSingle(coinUid: String): Single<List<CoinReport>> =
        hsProvider.coinReportsSingle(coinUid)

    // Pro Data

    suspend fun cexVolumesSingle(
        coinUid: String,
        currencyCode: String,
        timePeriod: HsTimePeriod
    ): List<ChartPoint> {
        val periodType = HsPeriodType.ByPeriod(timePeriod)
        val currentTime = Date().time / 1000
        val fromTimestamp = HsChartRequestHelper.fromTimestamp(currentTime, periodType)
        return hsProvider.coinPriceChartSingle(
            coinGeckoUid = coinUid,
            currencyCode = currencyCode,
            periodType = timePeriod,
            fromTimestamp = fromTimestamp
        )
            .let { response ->
                response.mapNotNull { chartCoinPrice ->
                    chartCoinPrice.totalVolume?.let { volume ->
                        ChartPoint(volume, chartCoinPrice.timestamp, null)
                    }
                }
            }
    }

    fun dexLiquiditySingle(
        authToken: String,
        coinUid: String,
        currencyCode: String,
        timePeriod: HsTimePeriod
    ): Single<List<Analytics.VolumePoint>> =
        hsProvider.dexLiquiditySingle(authToken, coinUid, currencyCode, timePeriod)

    fun dexVolumesSingle(
        authToken: String,
        coinUid: String,
        currencyCode: String,
        timePeriod: HsTimePeriod
    ): Single<List<Analytics.VolumePoint>> =
        hsProvider.dexVolumesSingle(authToken, coinUid, currencyCode, timePeriod)

    fun transactionDataSingle(
        authToken: String,
        coinUid: String,
        timePeriod: HsTimePeriod,
        platform: String?
    ): Single<List<Analytics.CountVolumePoint>> =
        hsProvider.transactionDataSingle(authToken, coinUid, timePeriod, platform)

    fun activeAddressesSingle(
        authToken: String,
        coinUid: String,
        timePeriod: HsTimePeriod
    ): Single<List<Analytics.CountPoint>> {
        return hsProvider.activeAddressesSingle(authToken, coinUid, timePeriod)
    }

    fun analyticsPreviewSingle(
        coinUid: String,
        addresses: List<String>,
    ): Single<AnalyticsPreview> {
        return hsProvider.analyticsPreviewSingle(coinUid, addresses)
    }

    fun analyticsSingle(
        authToken: String,
        coinUid: String,
        currencyCode: String,
    ): Single<Analytics> {
        return hsProvider.analyticsSingle(authToken, coinUid, currencyCode)
    }

    fun cexVolumeRanksSingle(
        authToken: String,
        currencyCode: String
    ): Single<List<RankMultiValue>> {
        return hsProvider.rankMultiValueSingle(authToken, "cex_volume", currencyCode)
    }

    fun dexVolumeRanksSingle(
        authToken: String,
        currencyCode: String
    ): Single<List<RankMultiValue>> {
        return hsProvider.rankMultiValueSingle(authToken, "dex_volume", currencyCode)
    }

    fun dexLiquidityRanksSingle(authToken: String, currencyCode: String): Single<List<RankValue>> {
        return hsProvider.rankValueSingle(authToken, "dex_liquidity", currencyCode)
    }

    fun activeAddressRanksSingle(
        authToken: String,
        currencyCode: String
    ): Single<List<RankMultiValue>> {
        return hsProvider.rankMultiValueSingle(authToken, "address", currencyCode)
    }

    fun transactionCountsRanksSingle(
        authToken: String,
        currencyCode: String
    ): Single<List<RankMultiValue>> {
        return hsProvider.rankMultiValueSingle(authToken, "tx_count", currencyCode)
    }

    fun holderRanksSingle(authToken: String, currencyCode: String): Single<List<RankValue>> {
        return hsProvider.rankValueSingle(authToken, "holders", currencyCode)
    }

    fun revenueRanksSingle(authToken: String, currencyCode: String): Single<List<RankMultiValue>> {
        return hsProvider.rankMultiValueSingle(authToken, "revenue", currencyCode)
    }

    fun feeRanksSingle(authToken: String, currencyCode: String): Single<List<RankMultiValue>> {
        return hsProvider.rankMultiValueSingle(authToken, "fee", currencyCode)
    }

    fun subscriptionsSingle(addresses: List<String>): Single<List<SubscriptionResponse>> {
        return hsProvider.subscriptionsSingle(addresses)
    }

    // Overview
    fun marketOverviewSingle(currencyCode: String): Single<MarketOverview> =
        marketOverviewManager.marketOverviewSingle(currencyCode).map { marketOverview ->
            marketOverview.copy(
                topPairs = marketOverview.topPairs.map { topPairWithCoin(it) }
            )
        }

    private fun topPairWithCoin(topPair: TopPair) =
        topPair.copy(
            baseCoin = coinsMap[topPair.baseCoinUid],
            targetCoin = coinsMap[topPair.targetCoinUid]
        )

    fun marketGlobalSingle(currencyCode: String): Single<MarketGlobal> =
        hsProvider.marketGlobalSingle(currencyCode)

    fun topPairsSingle(currencyCode: String, page: Int, limit: Int): Single<List<TopPair>> =
        hsProvider.topPairsSingle(currencyCode, page, limit).map { topPairs ->
            topPairs.map { topPairWithCoin(it) }
        }


    fun topMoversSingle(currencyCode: String): Single<TopMovers> =
        hsProvider.topMoversRawSingle(currencyCode)
            .map { raw ->
                TopMovers(
                    gainers100 = coinManager.getMarketInfos(raw.gainers100),
                    gainers200 = coinManager.getMarketInfos(raw.gainers200),
                    gainers300 = coinManager.getMarketInfos(raw.gainers300),
                    losers100 = coinManager.getMarketInfos(raw.losers100),
                    losers200 = coinManager.getMarketInfos(raw.losers200),
                    losers300 = coinManager.getMarketInfos(raw.losers300)
                )
            }

    suspend fun chartPointsSingle(
        coinUid: String,
        currencyCode: String,
        periodType: HsPeriodType
    ): Pair<Long, List<ChartPoint>> {
        val data = intervalData(periodType)
        return hsProvider.coinPriceChartSingle(
            coinGeckoUid = coinUid,
            currencyCode = currencyCode,
            periodType = periodType.timePeriod,
            fromTimestamp = data.fromTimestamp
        ).let {
            Pair(data.visibleTimestamp, it.map { it.chartPoint })
        }
    }

    private fun intervalData(periodType: HsPeriodType): IntervalData {
        val interval = HsChartRequestHelper.pointInterval(periodType)
        val visibleTimestamp: Long
        val fromTimestamp: Long?
        when (periodType) {
            is HsPeriodType.ByPeriod -> {
                val currentTime = Date().time / 1000
                visibleTimestamp = HsChartRequestHelper.fromTimestamp(currentTime, periodType)
                fromTimestamp = visibleTimestamp
            }

            is HsPeriodType.ByCustomPoints -> {
                val currentTime = Date().time / 1000
                visibleTimestamp = HsChartRequestHelper.fromTimestamp(currentTime, periodType)
                val customPointsInterval = interval.interval * periodType.pointsCount
                fromTimestamp = visibleTimestamp - customPointsInterval
            }

            is HsPeriodType.ByStartTime -> {
                visibleTimestamp = periodType.startTime
                fromTimestamp = null
            }
        }

        return IntervalData(interval, fromTimestamp, visibleTimestamp)
    }

    fun topPlatformMarketCapStartTimeSingle(platform: String): Single<Long> {
        return hsProvider.topPlatformMarketCapStartTime(platform)
    }

    // Global Market Info

    fun globalMarketPointsSingle(
        currencyCode: String,
        timePeriod: HsTimePeriod
    ): Single<List<GlobalMarketPoint>> {
        return globalMarketInfoManager.globalMarketInfoSingle(currencyCode, timePeriod)
    }

    fun topPlatformsSingle(currencyCode: String): Single<List<TopPlatform>> {
        return hsProvider.topPlatformsSingle(currencyCode)
            .map { responseList -> responseList.map { it.topPlatform } }
    }

    fun topPlatformMarketCapPointsSingle(
        chain: String,
        currencyCode: String,
        periodType: HsPeriodType
    ): Single<List<TopPlatformMarketCapPoint>> {
        val data = intervalData(periodType)
        return hsProvider.topPlatformMarketCapPointsSingle(
            chain,
            currencyCode,
            data.interval,
            data.fromTimestamp
        )
    }

    fun topPlatformMarketInfosSingle(
        chain: String,
        currencyCode: String,
    ): Single<List<MarketInfo>> {
        return hsProvider.topPlatformCoinListSingle(chain, currencyCode)
            .map { coinManager.getMarketInfos(it) }
    }

    // NFT

    suspend fun nftTopCollections(): List<NftTopCollection> = nftManager.topCollections()

    // Auth

    fun authGetSignMessage(address: String): Single<String> {
        return hsProvider.authGetSignMessage(address)
    }

    fun authenticate(signature: String, address: String): Single<String> {
        return hsProvider.authenticate(signature, address)
    }

    fun requestPersonalSupport(authToken: String, username: String): Single<Response<Void>> {
        return hsProvider.requestPersonalSupport(authToken, username)
    }

    //Misc

    fun syncInfo(): SyncInfo {
        return coinSyncer.syncInfo()
    }

    //ETF

    fun etfSingle(currencyCode: String): Single<List<Etf>> {
        return hsProvider.etfsSingle(currencyCode)
            .map { items ->
                items.map { EtfResponse.toEtf(it) }
            }
    }

    fun etfPointSingle(currencyCode: String): Single<List<EtfPoint>> {
        return hsProvider.etfPointsSingle(currencyCode)
            .map { points ->
                points.mapNotNull { EtfPointResponse.toEtfPoint(it) }
            }
    }

    //Stats

    fun sendStats(statsJson: String, appVersion: String, appId: String?): Single<Unit> {
        return hsProvider.sendStats(statsJson, appVersion, appId)
    }

    companion object {
        fun getInstance(config: MarketApiConfig): MarketKit {
            val marketDatabase: MarketDatabase = get(MarketDatabase::class.java)
            val hsProvider = HsProvider(config.baseUrl, config.apiKey)
            val hsNftProvider = HsNftProvider(config.baseUrl, config.apiKey)
            val coinStorage = CoinStorage(marketDatabase)
            val coinManager = CoinManager(coinStorage)
            val nftManager = NftManager(coinManager, hsNftProvider)
            val marketOverviewManager = MarketOverviewManager(nftManager, hsProvider)
            val virtualCoinMapper: VirtualCoinMapper = get(VirtualCoinMapper::class.java)
            val coinsListRepository: CoinsListRepository = get(CoinsListRepository::class.java)
            val coinSyncer = CoinSyncer(
                coinsListRepository,
                coinStorage,
                marketDatabase.syncerStateDao(),
                virtualCoinMapper,
                CoinsListMapper(),
                DefaultDispatcherProvider(),
            )
            val coinPriceManager = CoinPriceManager(CoinPriceStorage(marketDatabase))
            val coinHistoricalPriceManager = CoinHistoricalPriceManager(
                CoinHistoricalPriceStorage(marketDatabase),
                hsProvider,
            )
            val coinPriceSchedulerFactory = CoinPriceSchedulerFactory(coinPriceManager, hsProvider)
            val coinPriceSyncManager = CoinPriceSyncManager(coinPriceSchedulerFactory)
            coinPriceManager.listener = coinPriceSyncManager
            val cryptoCompareProvider by inject<CryptoCompareProvider>(CryptoCompareProvider::class.java)
            val postManager = PostManager(cryptoCompareProvider)
            val globalMarketInfoStorage = GlobalMarketInfoStorage(marketDatabase)
            val globalMarketInfoManager =
                GlobalMarketInfoManager(hsProvider, globalMarketInfoStorage)

            return MarketKit(
                nftManager,
                marketOverviewManager,
                coinManager,
                coinSyncer,
                coinPriceManager,
                coinHistoricalPriceManager,
                coinPriceSyncManager,
                postManager,
                globalMarketInfoManager,
                hsProvider,
            )
        }
    }

}

//Errors

sealed class ProviderError : Exception() {
    class ApiRequestLimitExceeded : ProviderError()
    class NoDataForCoin : ProviderError()
    class ReturnedTimestampIsVeryInaccurate : ProviderError()
}

data class SyncInfo(
    val listTimestamp: String?,
    val listDownloadedAt: Long?,
    val ranksDownloadedAt: Long?,
    val coinsCount: Int?,
    val blockchainsCount: Int?,
    val tokensCount: Int?,
    val serverAvailable: Boolean?
)
