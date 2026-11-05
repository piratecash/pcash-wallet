package cash.p.terminal.core

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarketKitExtensionsTest {

    private fun nativeToken(blockchainType: BlockchainType) = Token(
        coin = Coin(uid = "test-coin", name = "Test Coin", code = "TEST"),
        blockchain = Blockchain(type = blockchainType, name = blockchainType.uid, eip3091url = null),
        type = TokenType.Native,
        decimals = 8,
    )

    @Test
    fun isSupported_thorchainNative_returnsTrue() {
        assertTrue(TokenQuery(BlockchainType.Thorchain, TokenType.Native).isSupported)
    }

    @Test
    fun isSupported_thorchainAsset_returnsTrue() {
        assertTrue(
            TokenQuery(BlockchainType.Thorchain, TokenType.ThorchainAsset("rune")).isSupported
        )
    }

    @Test
    fun isSupported_mayachainNative_returnsTrue() {
        assertTrue(TokenQuery(BlockchainType.Mayachain, TokenType.Native).isSupported)
    }

    @Test
    fun isSupported_mayachainThorchainAsset_returnsFalse() {
        assertFalse(
            TokenQuery(BlockchainType.Mayachain, TokenType.ThorchainAsset("rune")).isSupported
        )
    }

    @Test
    fun supports_hardwareCardOnThorchainAndMayachain_returnsFalse() {
        val hardwareCard = AccountType.HardwareCard("card-id", 0, "public-key", 0)

        assertFalse(BlockchainType.Thorchain.supports(hardwareCard))
        assertFalse(BlockchainType.Mayachain.supports(hardwareCard))
    }

    @Test
    fun supports_slip39_hidesZcashAndBeamOnly() {
        val slip39 = AccountType.Slip39(listOf("word"), "", ByteArray(16))

        assertFalse(BlockchainType.Zcash.supports(slip39))
        assertFalse(BlockchainType.Beam.supports(slip39))
        listOf(
            BlockchainType.Bitcoin,
            BlockchainType.Litecoin,
            BlockchainType.Ethereum,
            BlockchainType.Monero,
            BlockchainType.Thorchain,
            BlockchainType.Mayachain,
        ).forEach { assertTrue(it.supports(slip39), "$it") }
    }

    @Test
    fun supports_slip39MwebToken_onlyOnLitecoin() {
        val slip39 = AccountType.Slip39(listOf("word"), "", ByteArray(16))
        val litecoinMweb = nativeToken(BlockchainType.Litecoin).copy(type = TokenType.Mweb)
        val bitcoinMweb = nativeToken(BlockchainType.Bitcoin).copy(type = TokenType.Mweb)

        assertTrue(litecoinMweb.supports(slip39))
        assertFalse(bitcoinMweb.supports(slip39))
    }

    @Test
    fun supports_thorchainAddress_returnsTrueOnlyForThorchain() {
        val thorchainAddress = AccountType.ThorchainAddress("thor1watched")

        assertTrue(BlockchainType.Thorchain.supports(thorchainAddress))
        assertFalse(BlockchainType.Mayachain.supports(thorchainAddress))
        assertFalse(BlockchainType.Stellar.supports(thorchainAddress))
    }

    @Test
    fun supports_mayachainAddress_returnsTrueOnlyForMayachain() {
        val mayachainAddress = AccountType.MayachainAddress("maya1watched")

        assertTrue(BlockchainType.Mayachain.supports(mayachainAddress))
        assertFalse(BlockchainType.Thorchain.supports(mayachainAddress))
        assertFalse(BlockchainType.Stellar.supports(mayachainAddress))
    }

    @Test
    fun eligibleTokens_hardwareCardAccountOnThorchain_returnsEmpty() {
        val fullCoin = FullCoin(
            coin = Coin(uid = "rune", name = "THORChain", code = "RUNE"),
            tokens = listOf(nativeToken(BlockchainType.Thorchain)),
        )
        val hardwareCard = AccountType.HardwareCard("card-id", 0, "public-key", 0)

        assertTrue(fullCoin.eligibleTokens(hardwareCard).isEmpty())
    }

    @Test
    fun eligibleTokens_mnemonicAccountOnThorchain_containsToken() {
        val fullCoin = FullCoin(
            coin = Coin(uid = "rune", name = "THORChain", code = "RUNE"),
            tokens = listOf(nativeToken(BlockchainType.Thorchain)),
        )
        val mnemonic = AccountType.Mnemonic(listOf("word"), "")

        assertTrue(fullCoin.eligibleTokens(mnemonic).isNotEmpty())
    }

    @Test
    fun supported_containsThorchainAndMayachain() {
        assertTrue(BlockchainType.supported.contains(BlockchainType.Thorchain))
        assertTrue(BlockchainType.supported.contains(BlockchainType.Mayachain))
    }
}
