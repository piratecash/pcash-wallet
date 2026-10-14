package cash.p.terminal.modules.market.search

import androidx.compose.runtime.Immutable
import cash.p.terminal.wallet.models.CoinCategory
import cash.p.terminal.wallet.entities.FullCoin
import java.math.BigDecimal

object MarketSearchModule {

    sealed class DiscoveryItem {
        object TopCoins : DiscoveryItem()

        class Category(
            val coinCategory: CoinCategory,
            val marketData: CategoryMarketData? = null
        ) : DiscoveryItem()
    }

    @Immutable
    class CoinItem(val fullCoin: FullCoin, val favourited: Boolean)

    data class CategoryMarketData(
        val marketCap: String? = null,
        val diff: BigDecimal? = null
    )

}
