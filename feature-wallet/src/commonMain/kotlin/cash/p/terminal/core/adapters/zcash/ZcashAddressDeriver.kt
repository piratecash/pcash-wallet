package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import cash.p.zcash.Addresses
import cash.p.zcash.ZcashNetwork
import cash.p.zcash.ZcashSdk
import cash.p.zcash.deriveAddresses
import cash.p.zcash.deriveAddressesFromKey

/**
 * Single source of truth for which ZEC receiver an address spec maps to; `null` spec means Native.
 * A Ledger can show only its Orchard-only unified address, so its account hands out that one.
 */
fun AddressSpecType?.selectZcashReceiver(addresses: Addresses, accountType: AccountType): String? = when (this) {
    AddressSpecType.Shielded, null -> addresses.sapling
    AddressSpecType.Transparent -> addresses.transparent
    AddressSpecType.Unified -> if (accountType is AccountType.LedgerDevice) addresses.orchard else addresses.unified
}

/**
 * Every address of an account without opening its database, so a receive address is available
 * before — and regardless of — the wallet session.
 */
class ZcashAddressDeriver {

    suspend fun addresses(key: ZcashKey): Addresses = when (key) {
        is ZcashKey.Phrase -> ZcashSdk.deriveAddresses(
            phrase = key.words.joinToString(" "),
            network = ZcashNetwork.MAIN,
            passphrase = key.passphrase,
        )

        is ZcashKey.Standalone -> ZcashSdk.deriveAddressesFromKey(key.key, ZcashNetwork.MAIN)
    }
}
