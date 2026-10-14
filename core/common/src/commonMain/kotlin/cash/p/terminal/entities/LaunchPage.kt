package cash.p.terminal.entities

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SettingsLaunchScreen_Auto
import cash.p.terminal.resources.SettingsLaunchScreen_Balance
import cash.p.terminal.resources.SettingsLaunchScreen_MarketOverview
import cash.p.terminal.resources.SettingsLaunchScreen_Watchlist
import cash.p.terminal.resources.ic_market_20
import cash.p.terminal.resources.ic_settings_20
import cash.p.terminal.resources.ic_star_20
import cash.p.terminal.resources.ic_wallet_20
import com.google.gson.annotations.SerializedName
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.WithTranslatableTitle
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

enum class LaunchPage(val titleRes: StringResource, val iconRes: DrawableResource):
    WithTranslatableTitle {
    @SerializedName("auto")
    Auto(Res.string.SettingsLaunchScreen_Auto, Res.drawable.ic_settings_20),
    @SerializedName("balance")
    Balance(Res.string.SettingsLaunchScreen_Balance, Res.drawable.ic_wallet_20),
    @SerializedName("market")
    Market(Res.string.SettingsLaunchScreen_MarketOverview, Res.drawable.ic_market_20),
    @SerializedName("watchlist")
    Watchlist(Res.string.SettingsLaunchScreen_Watchlist, Res.drawable.ic_star_20);

    override val title: TranslatableString
        get() = TranslatableString.ResString(titleRes)

    companion object {
        private val map = entries.associateBy(LaunchPage::name)

        fun fromString(type: String?): LaunchPage? = map[type]
    }
}
