package cash.p.terminal.tangem.common

import cash.p.terminal.tangem.domain.model.AddressBytesWithPublicKey
import cash.p.terminal.wallet.PublicHDExtendedKeyParser
import io.horizontalsystems.ethereumkit.crypto.InternalBouncyCastleProvider
import java.security.MessageDigest

object CustomXPubKeyAddressParser {
    fun parse(xPubKey: String): AddressBytesWithPublicKey {
        val extendedKey = PublicHDExtendedKeyParser.parse(xPubKey)
        val publicKey = extendedKey.key.pubKeyUncompressed
        val digest = MessageDigest.getInstance(KECCAK_256, InternalBouncyCastleProvider.getInstance())
        val address = digest.digest(publicKey.copyOfRange(1, publicKey.size)).takeLast(20).toByteArray()
        return AddressBytesWithPublicKey(address, publicKey)
    }

    private const val KECCAK_256 = "ETH-KECCAK-256"
}
