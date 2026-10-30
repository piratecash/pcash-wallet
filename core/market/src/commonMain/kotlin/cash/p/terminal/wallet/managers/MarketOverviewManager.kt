package cash.p.terminal.wallet.managers

import cash.p.terminal.wallet.models.MarketOverview
import cash.p.terminal.wallet.models.MarketOverviewResponse
import cash.p.terminal.wallet.providers.HsProvider
import io.reactivex.Single

class MarketOverviewManager(
    private val hsProvider: HsProvider
) {

    private fun marketOverview(response: MarketOverviewResponse): MarketOverview =
        MarketOverview(
            globalMarketPoints = response.globalMarketPoints,
            coinCategories = response.coinCategories,
            topPlatforms = response.topPlatforms.map { it.topPlatform },
            topPairs = response.pairs
        )

    fun marketOverviewSingle(currencyCode: String): Single<MarketOverview> =
        hsProvider.marketOverviewSingle(currencyCode).map { marketOverview(it) }

}
