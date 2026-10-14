package cash.p.terminal.wallet

import io.horizontalsystems.hdwalletkit.Base58
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.horizontalsystems.hdwalletkit.HDExtendedKeyVersion
import io.horizontalsystems.hdwalletkit.HDKey
import java.nio.ByteBuffer

object PublicHDExtendedKeyParser {
    fun parse(serialized: String): HDExtendedKey {
        val raw = Base58.decode(serialized)
        raw.validateSize()
        HDExtendedKey.validateChecksum(raw)
        val version = raw.publicVersion()
        val depth = raw[DEPTH_OFFSET].toInt() and 0xff
        val parentFingerprint = raw.readInt(PARENT_FINGERPRINT_OFFSET)
        val sequence = raw.readInt(SEQUENCE_OFFSET)
        val key = HDKey(
            pubKey = raw.copyOfRange(KEY_OFFSET, PAYLOAD_SIZE),
            chainCode = raw.copyOfRange(CHAIN_CODE_OFFSET, KEY_OFFSET),
            parent = null,
            parentFingerprint = parentFingerprint,
            depth = depth,
            childNumber = sequence and Int.MAX_VALUE,
            isHardened = sequence and HDKey.HARDENED_FLAG != 0,
        )
        return HDExtendedKey(key, version)
    }

    private fun ByteArray.validateSize() {
        if (size != SERIALIZED_KEY_SIZE) throw HDExtendedKey.ParsingError.WrongKeyLength
    }

    private fun ByteArray.publicVersion(): HDExtendedKeyVersion =
        HDExtendedKeyVersion.initFrom(copyOfRange(0, VERSION_SIZE))
            ?.takeIf(HDExtendedKeyVersion::isPublic)
            ?: throw HDExtendedKey.ParsingError.WrongVersion

    private fun ByteArray.readInt(offset: Int): Int = ByteBuffer.wrap(this, offset, Int.SIZE_BYTES).int

    private const val VERSION_SIZE = 4
    private const val DEPTH_OFFSET = 4
    private const val PARENT_FINGERPRINT_OFFSET = 5
    private const val SEQUENCE_OFFSET = 9
    private const val CHAIN_CODE_OFFSET = 13
    private const val KEY_OFFSET = 45
    private const val PAYLOAD_SIZE = 78
    private const val SERIALIZED_KEY_SIZE = 82
}
