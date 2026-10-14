package cash.p.terminal.modules.market.tvl

import androidx.compose.runtime.Immutable
import cash.p.terminal.resources.MarketGlobalMetrics_ChainSelectorAll
import cash.p.terminal.resources.Res
import io.horizontalsystems.core.entities.CurrencyValue
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.WithTranslatableTitle
import cash.p.terminal.wallet.entities.FullCoin
import java.math.BigDecimal
import org.jetbrains.compose.resources.DrawableResource

object TvlModule {

    data class MarketTvlItem(
        val fullCoin: FullCoin?,
        val name: String,
        val chains: List<String>,
        val iconUrl: String,
        val tvl: CurrencyValue,
        val diff: CurrencyValue?,
        val diffPercent: BigDecimal?,
        val rank: String
    )

    @Immutable
    data class TvlData(
        val chainSelect: Select<Chain>,
        val sortDescending: Boolean,
        val coinTvlViewItems: List<CoinTvlViewItem>
    )

    @Immutable
    data class CoinTvlViewItem(
        val coinUid: String?,
        val name: String,
        val chain: TranslatableString,
        val iconUrl: String,
        val iconPlaceholder: DrawableResource?,
        val tvl: CurrencyValue,
        val tvlChangePercent: BigDecimal?,
        val tvlChangeAmount: CurrencyValue?,
        val rank: String
    )

    @Immutable
    data class UiState(
        val viewState: ViewState,
        val tvlData: TvlData?,
        val tvlDiffType: TvlDiffType,
        val isRefreshing: Boolean,
        val chainSelectorDialogState: SelectorDialogState,
    )

    enum class Chain : WithTranslatableTitle {
        All, Ethereum, Solana, Binance, Avalanche, Terra, Fantom, Arbitrum, Polygon;

        override val title: TranslatableString
            get() = when (this) {
                All -> TranslatableString.ResString(Res.string.MarketGlobalMetrics_ChainSelectorAll)
                else -> TranslatableString.PlainString(name)
            }
    }

    enum class TvlDiffType {
        Percent, Currency
    }

    sealed class SelectorDialogState {
        object Closed : SelectorDialogState()
        class Opened(val select: Select<Chain>) : SelectorDialogState()
    }

}
