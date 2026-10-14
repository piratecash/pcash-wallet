package cash.p.terminal.modules.coin.coinmarkets

import androidx.compose.runtime.Immutable
import cash.p.terminal.modules.market.MarketField
import cash.p.terminal.resources.CoinPage_MarketsVerifiedMenu_All
import cash.p.terminal.resources.CoinPage_MarketsVerifiedMenu_Verified
import cash.p.terminal.resources.Res
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.WithTranslatableTitle
import java.math.BigDecimal

object CoinMarketsModule {
    @Immutable
    data class Menu(
        val sortDescending: Boolean,
        val marketFieldSelect: Select<MarketField>
    )

    sealed class VolumeMenuType : WithTranslatableTitle {
        class Coin(val name: String) : VolumeMenuType()
        class Currency(val name: String) : VolumeMenuType()

        override val title: TranslatableString
            get() = when (this) {
                is Coin -> TranslatableString.PlainString(name)
                is Currency -> TranslatableString.PlainString(name)
            }
    }
}

data class MarketTickerItem(
    val market: String,
    val marketImageUrl: String?,
    val baseCoinCode: String,
    val targetCoinCode: String,
    val volumeFiat: BigDecimal,
    val volumeToken: BigDecimal,
    val tradeUrl: String?,
    val verified: Boolean
)

enum class VerifiedType: WithTranslatableTitle {
    Verified, All;

    override val title: TranslatableString
        get() = when(this) {
            Verified -> TranslatableString.ResString(Res.string.CoinPage_MarketsVerifiedMenu_Verified)
            All -> TranslatableString.ResString(Res.string.CoinPage_MarketsVerifiedMenu_All)
        }

    fun next() = values()[if (ordinal == values().size - 1) 0 else ordinal + 1]
}
