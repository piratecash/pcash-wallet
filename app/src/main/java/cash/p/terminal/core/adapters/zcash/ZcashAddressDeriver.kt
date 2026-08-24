package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import cash.p.zcash.Addresses
import cash.p.zcash.ZcashNetwork
import cash.p.zcash.ZcashSdk
import cash.p.zcash.deriveAddresses
import cash.p.zcash.deriveAddressesFromViewingKey

/** Single source of truth for which ZEC receiver an address spec maps to; `null` spec means Native. */
fun AddressSpecType?.selectZcashReceiver(addresses: Addresses): String? = when (this) {
    AddressSpecType.Shielded, null -> addresses.sapling
    AddressSpecType.Transparent -> addresses.transparent
    AddressSpecType.Unified -> addresses.unified
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
            passphrase = key.passphrase.ifEmpty { null },
        )

        is ZcashKey.ViewingKey -> ZcashSdk.deriveAddressesFromViewingKey(key.key, ZcashNetwork.MAIN)
    }
}
