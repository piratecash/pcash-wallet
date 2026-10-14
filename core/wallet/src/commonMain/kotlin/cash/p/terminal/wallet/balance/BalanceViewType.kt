package cash.p.terminal.wallet.balance

import cash.p.terminal.resources.BalanceViewType_CoinValue
import cash.p.terminal.resources.BalanceViewType_FiatValue
import cash.p.terminal.resources.Res
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.WithTranslatableTitle
import com.google.gson.annotations.SerializedName
import org.jetbrains.compose.resources.StringResource

enum class BalanceViewType(val titleResId: StringResource, val subtitleResId: StringResource) :
    WithTranslatableTitle {
    @SerializedName("coin")
    CoinThenFiat(Res.string.BalanceViewType_CoinValue, Res.string.BalanceViewType_FiatValue),

    @SerializedName("currency")
    FiatThenCoin(Res.string.BalanceViewType_FiatValue, Res.string.BalanceViewType_CoinValue);

    override val title: TranslatableString
        get() = TranslatableString.ResString(titleResId)
}
