package cash.p.terminal.modules.market.topnftcollections

import cash.p.terminal.modules.market.SortingField
import cash.p.terminal.modules.market.TimeDuration
import cash.p.terminal.ui_compose.Select
import io.horizontalsystems.core.entities.BlockchainType
import java.math.BigDecimal

data class Menu(
    val sortingFieldSelect: Select<SortingField>,
    val timeDurationSelect: Select<TimeDuration>
)

data class TopNftCollectionViewItem(
    val blockchainType: BlockchainType,
    val uid: String,
    val name: String,
    val imageUrl: String?,
    val volume: String,
    val volumeDiff: BigDecimal,
    val order: Int,
    val floorPrice: String
)
