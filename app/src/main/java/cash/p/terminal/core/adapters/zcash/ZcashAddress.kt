package cash.p.terminal.core.adapters.zcash

import cash.p.zcash.ZcashAddressKind
import cash.p.zcash.ZcashNetwork
import cash.p.zcash.ZcashSdk
import cash.p.zcash.addressKind

/** Single source of truth for recognizing a foreign ZEC address; `null` means it is not one. */
internal fun zcashAddressKind(address: String): ZcashAddressKind? =
    ZcashSdk.addressKind(address, ZcashNetwork.MAIN)

internal fun isValidZcashAddress(address: String): Boolean = zcashAddressKind(address) != null

internal fun isTransparentZcashAddress(address: String): Boolean =
    zcashAddressKind(address) == ZcashAddressKind.TRANSPARENT
