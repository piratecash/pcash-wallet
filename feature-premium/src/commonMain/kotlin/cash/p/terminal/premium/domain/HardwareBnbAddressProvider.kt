package cash.p.terminal.premium.domain

import cash.p.terminal.wallet.Account

/** Reads the BNB address of a hardware-card account from the card itself. */
interface HardwareBnbAddressProvider {
    suspend fun scanAddress(account: Account): String?
}
