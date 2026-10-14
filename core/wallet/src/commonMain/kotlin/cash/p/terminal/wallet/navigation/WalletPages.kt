package cash.p.terminal.wallet.navigation

import cash.p.terminal.navigation.HSPage
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token

/** Pages of `:app` taking wallet types, opened by feature modules without depending on `:app`. */
interface WalletPages {
    fun swap(tokenOut: Token): HSPage
    fun backupLocal(account: Account): HSPage
}
