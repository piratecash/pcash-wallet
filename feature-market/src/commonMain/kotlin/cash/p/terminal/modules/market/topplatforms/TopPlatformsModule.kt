package cash.p.terminal.modules.market.topplatforms

import androidx.compose.runtime.Immutable
import cash.p.terminal.core.iconUrl
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_platform_placeholder_24
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.market.MarketField
import cash.p.terminal.modules.market.SortingField
import cash.p.terminal.modules.market.TimeDuration
import cash.p.terminal.ui_compose.Select
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import java.math.BigDecimal
import org.jetbrains.compose.resources.DrawableResource

object TopPlatformsModule {

    data class Menu(
        val sortingFieldSelect: Select<SortingField>,
        val marketFieldSelect: Select<MarketField>
    )

    data class UiState(
        val sortingField: SortingField,
        val timePeriod: TimeDuration,
        val viewItems: List<TopPlatformViewItem>,
        val viewState: ViewState,
        val isRefreshing: Boolean
    )

}

@CommonParcelize
data class Platform(
    val uid: String,
    val name: String,
) : CommonParcelable

data class TopPlatformItem(
    val platform: Platform,
    val rank: Int,
    val protocols: Int,
    val marketCap: BigDecimal,
    val rankDiff: Int?,
    val changeDiff: BigDecimal?
)

@Immutable
data class TopPlatformViewItem(
    val platform: Platform,
    val subtitle: String,
    val marketCap: String,
    val marketCapDiff: BigDecimal?,
    val rank: String?,
    val rankDiff: Int?,
) {


    val iconUrl: String
        get() = platform.iconUrl

    val iconPlaceHolder: DrawableResource
        get() = Res.drawable.ic_platform_placeholder_24

}
