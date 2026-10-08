package cash.p.terminal.wallet.managers

import cash.p.terminal.wallet.ProviderError
import cash.p.terminal.wallet.models.CoinHistoricalPrice
import cash.p.terminal.wallet.providers.HsProvider
import cash.p.terminal.wallet.storage.CoinHistoricalPriceStorage
import co.touchlab.kermit.Logger
import java.math.BigDecimal
import kotlin.math.abs

class CoinHistoricalPriceManager(
    private val storage: CoinHistoricalPriceStorage,
    private val hsProvider: HsProvider,
) {
    private val logger = Logger.withTag("CoinHistoricalPriceManager")

    suspend fun coinHistoricalPriceSingle(
        coinGeckoUid: String,
        currencyCode: String,
        timestamp: Long
    ): BigDecimal {

        storage.coinPrice(coinGeckoUid, currencyCode, timestamp)?.let {
            return it.value
        }

        val response = hsProvider.historicalCoinPriceSingle(coinGeckoUid, currencyCode, timestamp)
        if (abs(timestamp - response.timestamp) < 24 * 60 * 60) {
            val coinHistoricalPrice =
                CoinHistoricalPrice(coinGeckoUid, currencyCode, response.price, timestamp)
            logger.d { "Saving coinHistoricalPrice: $coinHistoricalPrice" }
            storage.save(coinHistoricalPrice)
            return response.price
        }
        throw ProviderError.ReturnedTimestampIsVeryInaccurate()
    }

    fun coinHistoricalPrice(coinGeckoUid: String, currencyCode: String, timestamp: Long): BigDecimal? {
        return storage.coinPrice(coinGeckoUid, currencyCode, timestamp)?.value
    }

}
