package cash.p.terminal.modules.market.category

import cash.p.terminal.modules.market.MarketField
import cash.p.terminal.modules.market.MarketItem
import cash.p.terminal.modules.market.SortingField
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.wallet.models.Analytics

object MarketCategoryModule {

    data class Menu(
        val sortingFieldSelect: Select<SortingField>,
        val marketFieldSelect: Select<MarketField>
    )

}

data class MarketItemWrapper(
    val marketItem: MarketItem,
    val favorited: Boolean,
    val signal: Analytics.TechnicalAdvice.Advice? =  null
)
