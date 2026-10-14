package cash.p.terminal.modules.market.etf

import androidx.compose.runtime.Immutable
import cash.p.terminal.resources.MarketEtf_HighestAssets
import cash.p.terminal.resources.MarketEtf_Inflow
import cash.p.terminal.resources.MarketEtf_LowestAssets
import cash.p.terminal.resources.MarketEtf_Outflow
import cash.p.terminal.resources.Res
import io.horizontalsystems.core.entities.Currency
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.market.MarketDataValue
import cash.p.terminal.modules.market.TimeDuration
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.WithTranslatableTitle
import cash.p.terminal.wallet.models.Etf
import cash.p.terminal.wallet.models.EtfPoint
import org.jetbrains.compose.resources.StringResource

object EtfModule {

    @Immutable
    data class EtfViewItem(
        val title: String,
        val iconUrl: String,
        val subtitle: String,
        val value: String?,
        val subvalue: MarketDataValue?,
        val rank: String?,
    )

    data class RankedEtf(
        val etf: Etf,
        val rank: Int
    )

    @Immutable
    data class UiState(
        val viewItems: List<EtfViewItem>,
        val viewState: ViewState,
        val isRefreshing: Boolean,
        val timeDuration: TimeDuration,
        val sortBy: SortBy,
        val chartDataLoading: Boolean,
        val etfPoints: List<EtfPoint>,
        val currency: Currency,
    )

    enum class SortBy(val titleResId: StringResource): WithTranslatableTitle {
        HighestAssets(Res.string.MarketEtf_HighestAssets),
        LowestAssets(Res.string.MarketEtf_LowestAssets),
        Inflow(Res.string.MarketEtf_Inflow),
        Outflow(Res.string.MarketEtf_Outflow);

        override val title = TranslatableString.ResString(titleResId)
    }
}

