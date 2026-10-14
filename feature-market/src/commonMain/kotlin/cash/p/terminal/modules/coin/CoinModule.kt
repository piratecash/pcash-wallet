package cash.p.terminal.modules.coin

import cash.p.terminal.resources.Coin_Tab_Details
import cash.p.terminal.resources.Coin_Tab_Market
import cash.p.terminal.resources.Coin_Tab_Overview
import cash.p.terminal.resources.Res
import org.jetbrains.compose.resources.StringResource

object CoinModule {

    enum class Tab(val titleResId: StringResource) {
        Overview(Res.string.Coin_Tab_Overview),
        Details(Res.string.Coin_Tab_Details),
        Market(Res.string.Coin_Tab_Market),
    }
}
