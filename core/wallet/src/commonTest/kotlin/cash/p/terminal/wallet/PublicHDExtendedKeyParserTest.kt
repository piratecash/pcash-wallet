package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.HardwarePublicKeyType
import cash.p.terminal.wallet.entities.SecretString
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.test.assertFailsWith

class PublicHDExtendedKeyParserTest {

    @Test
    fun getHDExtendedKey_depthFiveHardwareXpub_preservesSerializedKey() {
        val hardwarePublicKey = HardwarePublicKey(
            accountId = "account-id",
            blockchainType = BlockchainType.Bitcoin.uid,
            type = HardwarePublicKeyType.PUBLIC_KEY,
            tokenType = TokenType.Native,
            key = SecretString(DEPTH_FIVE_XPUB),
            derivationPath = "m/86'/0'/0'/0/0",
            publicKey = byteArrayOf(),
            derivedPublicKey = byteArrayOf(),
        )
        val wallet = Wallet(mockk(), mockk(), hardwarePublicKey)

        val extendedKey = wallet.getHDExtendedKey()

        assertNotNull(extendedKey)
        assertEquals(5, extendedKey?.key?.depth)
        assertEquals(DEPTH_FIVE_XPUB, extendedKey?.serialize())
    }

    @Test
    fun parse_privateExtendedKey_rejectsWrongVersion() {
        assertFailsWith<HDExtendedKey.ParsingError.WrongVersion> {
            PublicHDExtendedKeyParser.parse(DEPTH_FIVE_XPRV)
        }
    }

    @Test
    fun parse_invalidChecksum_rejectsKey() {
        assertFailsWith<HDExtendedKey.ParsingError.InvalidChecksum> {
            PublicHDExtendedKeyParser.parse(DEPTH_FIVE_XPUB.dropLast(1) + "C")
        }
    }

    private companion object {
        const val DEPTH_FIVE_XPUB =
            "xpub6H3W6JmYJXN49h5TfcVjLC3onS6uPeUTTJoVvRC8oG9vsTn2J8LwigLzq5tH" +
                "brwAzH9DGo6ThGUdWsqce8dGfwHVBxSbixjDADGGdzF7t2B"
        const val DEPTH_FIVE_XPRV =
            "xprvA449goEeU9okwCzzZaxiy475EQGQzBkc65su82nXEvcwzfSskb2hAt2Wymrjy" +
                "RL6kpbVTGL3cKtp9herYXSjjQ1j4stsXXiRF7kXkCacK3T"
    }
}
