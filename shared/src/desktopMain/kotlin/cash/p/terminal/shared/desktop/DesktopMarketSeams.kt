package cash.p.terminal.shared.desktop

import androidx.compose.runtime.Composable
import cash.p.terminal.modules.coin.overview.TokenWalletActions
import cash.p.terminal.modules.market.MarketPages
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token

class DesktopMarketPages : MarketPages {
    override fun markdown(url: String): HSPage? = null
    override fun subscriptionInfo(): HSPage? = null
    override fun nftCollection(collectionUid: String, blockchainUid: String): HSPage? = null
}

class DesktopTokenWalletActions : TokenWalletActions {
    override fun supports(token: Token, accountType: AccountType) = false
    override fun enable(token: Token) = Unit
    override fun disable(token: Token) = Unit

    @Composable
    override fun Dialogs(navigation: HSNavigation) = Unit
}
