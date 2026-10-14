package cash.p.terminal.modules.coin.overview

import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token

interface TokenWalletActions {
    fun supports(token: Token, accountType: AccountType): Boolean
    fun enable(token: Token)
    fun disable(token: Token)

    @Composable
    fun Dialogs(navigation: HSNavigation)
}
