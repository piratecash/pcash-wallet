package cash.p.terminal.core.adapters.zcash

import cash.p.zcash.ZcashNetwork
import cash.p.zcash.ZcashSdk
import cash.p.zcash.deriveAddresses
import cash.p.zcash.deriveTransparentAccountKey
import io.horizontalsystems.hdwalletkit.Base58
import io.horizontalsystems.hdwalletkit.Curve
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.horizontalsystems.hdwalletkit.HDKeychain
import io.horizontalsystems.hdwalletkit.Utils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Proves the exported account key really controls the account's transparent address, by rebuilding
 * that address with hdwalletkit instead of the SDK. A change to the account index or derivation
 * path inside the SDK moves both sides of [ZcashSdk.deriveAddresses] together and would go
 * unnoticed; it cannot move this one.
 */
class ZcashTransparentKeyEquivalenceTest {

    @Test
    fun exportedAccountKey_derivesTheAccountsOwnTransparentAddress() = runBlocking {
        assertReceiverMatches(passphrase = null)
    }

    @Test
    fun exportedAccountKey_bip39Passphrase_derivesThatWalletsTransparentAddress() = runBlocking {
        assertReceiverMatches(passphrase = "pepper")
    }

    private suspend fun assertReceiverMatches(passphrase: String?) {
        val addresses = ZcashSdk.deriveAddresses(PHRASE, ZcashNetwork.MAIN, passphrase = passphrase)
        val accountKey = ZcashSdk.deriveTransparentAccountKey(PHRASE, ZcashNetwork.MAIN, passphrase = passphrase)

        val receiver = HDKeychain(HDExtendedKey(accountKey).key, Curve.Secp256K1)
            .getKeyByPath("m/0/${addresses.diversifierIndex}")

        assertEquals(addresses.transparent, transparentAddress(receiver.pubKeyHash))
    }

    private fun transparentAddress(publicKeyHash: ByteArray): String {
        val payload = T_ADDRESS_PREFIX + publicKeyHash
        return Base58.encode(payload + Utils.doubleDigest(payload).copyOfRange(0, 4))
    }

    private companion object {
        /** BIP-39 test vector; holds no funds. */
        const val PHRASE =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon" +
                " abandon about"

        /** Zcash mainnet P2PKH prefix. */
        val T_ADDRESS_PREFIX = byteArrayOf(0x1C, 0xB8.toByte())
    }
}
