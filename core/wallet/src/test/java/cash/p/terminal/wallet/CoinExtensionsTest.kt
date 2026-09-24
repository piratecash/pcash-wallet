package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoinExtensionsTest {

    private val securedBitcoinToken = TokenType.ThorchainAsset("btc-btc")

    private fun fullCoin(
        uid: String,
        coinGeckoId: String?,
        vararg tokenTypes: TokenType,
    ): FullCoin {
        val coin = Coin(uid = uid, name = uid, code = uid.uppercase(), coinGeckoId = coinGeckoId)
        val blockchain = Blockchain(BlockchainType.Thorchain, "THORChain", null)
        return FullCoin(coin, tokenTypes.map { Token(coin, blockchain, it, 8) })
    }

    @Test
    fun isSynthetic_coinGeckoIdNull_returnsFalse() {
        val coin = fullCoin("bitcoin", null, securedBitcoinToken)

        assertFalse(coin.isSynthetic)
    }

    @Test
    fun isSynthetic_coinGeckoIdEqualsUid_returnsFalse() {
        val coin = fullCoin("bitcoin", "bitcoin", securedBitcoinToken)

        assertFalse(coin.isSynthetic)
    }

    @Test
    fun isSynthetic_coinGeckoIdDiffersFromUid_returnsTrue() {
        val coin = fullCoin("thorchain-secured-bitcoin", "bitcoin", securedBitcoinToken)

        assertTrue(coin.isSynthetic)
    }

    @Test
    fun isSynthetic_eip20CoinWithDifferentCoinGeckoId_returnsFalse() {
        val coin = fullCoin("apecoin-ape", "apecoin", TokenType.Eip20("0x4d224452801aced8b2f0aebe155379bb5d594381"))

        assertFalse(coin.isSynthetic)
    }

    @Test
    fun isSynthetic_bridgedCoin_returnsFalse() {
        val coin = fullCoin("wdash", "dash", TokenType.Eip20("0xwdash"))

        assertFalse(coin.isSynthetic)
    }

    @Test
    fun isSynthetic_mixedTokenTypes_returnsFalse() {
        val coin = fullCoin("thorchain-secured-bitcoin", "bitcoin", securedBitcoinToken, TokenType.Native)

        assertFalse(coin.isSynthetic)
    }

    @Test
    fun isSynthetic_noTokens_returnsFalse() {
        val coin = fullCoin("thorchain-secured-bitcoin", "bitcoin")

        assertFalse(coin.isSynthetic)
    }
}
