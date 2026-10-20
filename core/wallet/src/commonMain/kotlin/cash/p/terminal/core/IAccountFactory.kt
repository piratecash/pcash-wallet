package cash.p.terminal.core

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType

interface IAccountFactory {
    fun account(
        name: String,
        type: AccountType,
        origin: AccountOrigin,
        backedUp: Boolean,
        fileBackedUp: Boolean
    ): Account
    fun watchAccount(name: String, type: AccountType): Account
    fun getNextWatchAccountName(): String
    fun getNextAccountName(): String
    fun getNextHardwareAccountName(): String
    fun getUniqueName(name: String, additionalExistingNames: Set<String> = emptySet()): String
}
