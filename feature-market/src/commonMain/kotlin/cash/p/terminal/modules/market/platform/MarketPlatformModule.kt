package cash.p.terminal.modules.market.platform

import cash.p.terminal.modules.market.MarketField
import cash.p.terminal.modules.market.SortingField
import cash.p.terminal.ui_compose.Select

object MarketPlatformModule {

    data class Menu(
        val sortingFieldSelect: Select<SortingField>,
        val marketFieldSelect: Select<MarketField>
    )

}
