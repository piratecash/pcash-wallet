package cash.p.terminal.modules.coin.overview

import cash.p.terminal.modules.coin.CoinDataItem
import cash.p.terminal.modules.coin.CoinLink
import cash.p.terminal.modules.coin.RoiViewItem
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.models.MarketInfoOverview
import org.jetbrains.compose.resources.StringResource

data class CoinOverviewItem(
    val coinCode: String,
    val marketInfoOverview: MarketInfoOverview,
    val guideUrl: String?,
)

data class TokenVariant(
    val value: String,
    val copyValue: String?,
    val imgUrl: String,
    val explorerUrl: String?,
    val name: String?,
    val token: Token,
    val canAddToWallet: Boolean,
    val inWallet: Boolean,
)

data class HudMessage(
    val text: StringResource,
    val type: HudMessageType,
)

enum class HudMessageType{
    Success, Error
}

data class CoinOverviewViewItem(
    val roi: List<RoiViewItem>,
    val links: List<CoinLink>,
    val about: String,
    val marketData: MutableList<CoinDataItem>,
    val marketCapRank: Int?
)
