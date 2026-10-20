package cash.p.terminal.modules.backuplocal.fullbackup

import cash.p.terminal.core.managers.DeclinedToken

data class WalletDeclinedTokens(
    val accountId: String,
    val accountName: String,
    val tokens: List<DeclinedToken>,
)
