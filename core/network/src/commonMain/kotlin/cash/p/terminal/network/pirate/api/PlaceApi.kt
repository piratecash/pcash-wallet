package cash.p.terminal.network.pirate.api

import cash.p.terminal.network.api.parseResponse
import cash.p.terminal.network.data.AppHeadersProvider
import cash.p.terminal.network.data.appHeaders
import cash.p.terminal.network.data.entity.ChartPeriod
import cash.p.terminal.network.data.setJsonBody
import cash.p.terminal.network.pirate.data.entity.CalculatorDataDto
import cash.p.terminal.network.pirate.data.entity.ChangeNowAssociatedCoinDto
import cash.p.terminal.network.pirate.data.entity.CoinsPriceChangeRequest
import cash.p.terminal.network.pirate.data.entity.InvestmentDataDto
import cash.p.terminal.network.pirate.data.entity.InvestmentGraphDataDto
import cash.p.terminal.network.pirate.data.entity.MarketTickerDto
import cash.p.terminal.network.pirate.data.entity.PiratePlaceCoinDto
import cash.p.terminal.network.pirate.data.entity.PriceChangeCoinInfoDto
import cash.p.terminal.network.pirate.data.entity.StakeDataDto
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import java.util.Locale

internal const val PCASH_BASE_URL = "https://p.cash"

internal class PlaceApi(
    private val httpClient: HttpClient,
    private val appHeadersProvider: AppHeadersProvider,
    private val premiumApiBaseUrl: String,
) {
    private companion object {
        const val PIRATE_BASE_PLACE_URL = "$PCASH_BASE_URL/api/"
    }

    suspend fun getCoinInfo(coinGeckoUid: String): PiratePlaceCoinDto {
        return httpClient.get {
            url(PIRATE_BASE_PLACE_URL + "coins/$coinGeckoUid")
            appHeaders(appHeadersProvider)
        }.parseResponse()
    }

    suspend fun getCoinsPriceChange(
        coinGeckoUidList: List<String>,
        currencyCode: String
    ): List<PriceChangeCoinInfoDto> {
        return httpClient.post {
            url(PIRATE_BASE_PLACE_URL + "mobile/coins")
            appHeaders(appHeadersProvider)
            setJsonBody(CoinsPriceChangeRequest(
                uids = coinGeckoUidList,
                currency = currencyCode
            ))
        }.parseResponse()
    }

    suspend fun getCoinPriceChart(
        coinGeckoUid: String,
        periodType: ChartPeriod
    ): List<List<String>> {
        return httpClient.get {
            url(PIRATE_BASE_PLACE_URL + "coins/$coinGeckoUid/graph/usd/${periodType.value}")
            appHeaders(appHeadersProvider)
        }.parseResponse()
    }

    suspend fun getMarketTickers(
        coinGeckoUid: String
    ): List<MarketTickerDto> {
        return httpClient.get {
            url(PIRATE_BASE_PLACE_URL + "coins/$coinGeckoUid/tickers")
            appHeaders(appHeadersProvider)
        }.parseResponse()
    }

    suspend fun getInvestmentData(coinGeckoUid: String, address: String, fiat: String): InvestmentDataDto {
        return httpClient.get {
            url(PIRATE_BASE_PLACE_URL + "invest/$coinGeckoUid/$address/compact/$fiat")
            appHeaders(appHeadersProvider)
        }.parseResponse()
    }

    suspend fun getChangeNowCoinAssociation(coinGeckoUid: String): List<ChangeNowAssociatedCoinDto> {
        return httpClient.get {
            url(PIRATE_BASE_PLACE_URL + "changenow/$coinGeckoUid")
            appHeaders(appHeadersProvider)
        }.parseResponse()
    }

    suspend fun getInvestmentChart(
        coinGeckoUid: String,
        address: String,
        period: String
    ): InvestmentGraphDataDto {
        return httpClient.get {
            url(PIRATE_BASE_PLACE_URL + "invest/$coinGeckoUid/$address/graph/$period")
            appHeaders(appHeadersProvider)
        }.parseResponse()
    }

    suspend fun getStakeData(coinGeckoUid: String, address: String): StakeDataDto {
        return httpClient.get {
            url(PIRATE_BASE_PLACE_URL + "invest/$coinGeckoUid/$address/stake")
            appHeaders(appHeadersProvider)
        }.parseResponse()
    }

    suspend fun getCalculatorData(coinGeckoUid: String, amount: Double): CalculatorDataDto {
        return httpClient.get {
            val formattedAmount = String.format(Locale.US, "%s", amount)
            url(PIRATE_BASE_PLACE_URL + "invest/$coinGeckoUid/calculator")
            parameter("amount", formattedAmount)
            appHeaders(appHeadersProvider)
        }.parseResponse()
    }

    // Premium API methods
    suspend fun checkTrialPremiumStatus(address: String): HttpResponse {
        return httpClient.get {
            url(premiumUrl(address))
            appHeaders(appHeadersProvider)
        }
    }

    suspend fun activateTrialPremium(address: String): HttpResponse {
        return httpClient.post {
            url(premiumUrl(address))
            appHeaders(appHeadersProvider)
        }
    }

    private fun premiumUrl(address: String): String =
        premiumApiBaseUrl + "mobile/premium/$address"
}