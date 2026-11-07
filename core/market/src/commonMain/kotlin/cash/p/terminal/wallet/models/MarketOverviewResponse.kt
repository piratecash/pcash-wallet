package cash.p.terminal.wallet.models

import com.google.gson.annotations.SerializedName

data class MarketOverviewResponse(
    @SerializedName("global")
    val globalMarketPoints: List<GlobalMarketPoint>,
    @SerializedName("sectors")
    val coinCategories: List<CoinCategory>,
    @SerializedName("platforms")
    val topPlatforms: List<TopPlatformResponse>,
    val pairs: List<TopPair>,
)
