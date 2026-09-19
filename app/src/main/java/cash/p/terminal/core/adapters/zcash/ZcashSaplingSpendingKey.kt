package cash.p.terminal.core.adapters.zcash

import io.horizontalsystems.bitcoincore.crypto.Bech32
import io.horizontalsystems.bitcoincore.crypto.Bech32Segwit

private const val SAPLING_HRP = "secret-extended-key-main"
private const val SAPLING_TYPECODE = 0x02
private const val SAPLING_KEY_SIZE = 169
private const val COMPACT_SIZE_LIMIT = 0xFD

/** `BranchId::Nu5` (0xC2D6D0B4) little-endian — the era header of every USK envelope. */
private val NU5_ERA = byteArrayOf(0xB4.toByte(), 0xD0.toByte(), 0xD6.toByte(), 0xC2.toByte())

/**
 * Encodes the Sapling component of a unified spending key as `secret-extended-key-main1…`,
 * the same string librustzcash produces. Returns null for any envelope this cannot read:
 * another era, no Sapling component, a duplicate one, truncated data anywhere in the envelope,
 * or a multi-byte CompactSize.
 */
fun saplingSpendingKey(usk: ByteArray): String? {
    val sapling = usk.saplingComponent() ?: return null
    val fiveBit = Bech32Segwit.convertBits(sapling, 0, sapling.size, 8, 5, true)
    try {
        return Bech32Segwit.encode(SAPLING_HRP, Bech32.Encoding.BECH32, fiveBit)
    } finally {
        sapling.fill(0)
        fiveBit.fill(0)
    }
}

/** The whole envelope is validated before any key byte is copied, so a reject leaves no copy behind. */
private fun ByteArray.saplingComponent(): ByteArray? {
    if (size < NU5_ERA.size || !NU5_ERA.contentEquals(copyOfRange(0, NU5_ERA.size))) return null

    var saplingStart = -1
    var offset = NU5_ERA.size
    while (offset + 2 <= size) {
        val typecode = this[offset].toInt() and 0xFF
        val length = this[offset + 1].toInt() and 0xFF
        val valueStart = offset + 2
        val valueEnd = valueStart + length
        if (typecode >= COMPACT_SIZE_LIMIT || length >= COMPACT_SIZE_LIMIT || valueEnd > size) {
            return null
        }
        if (typecode == SAPLING_TYPECODE) {
            if (saplingStart >= 0 || length != SAPLING_KEY_SIZE) return null
            saplingStart = valueStart
        }
        offset = valueEnd
    }
    return if (offset == size && saplingStart >= 0) {
        copyOfRange(saplingStart, saplingStart + SAPLING_KEY_SIZE)
    } else {
        null
    }
}
