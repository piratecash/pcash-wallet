package cash.p.terminal.modules.multiswap

import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SwapSideWalletTest {

    private val bip44Wallet = wallet(TokenType.Derivation.Bip44)
    private val bip84Wallet = wallet(TokenType.Derivation.Bip84)
    private val wallets = listOf(bip44Wallet, bip84Wallet)

    @Test
    fun findBySwapSide_tokenQueryIdSet_returnsWalletOfExactToken() {
        val side = SwapSide(bip84Wallet.token.tokenQuery.id, "bitcoin", BlockchainType.Bitcoin.uid)

        assertSame(bip84Wallet, wallets.findBySwapSide(side))
    }

    @Test
    fun findBySwapSide_tokenQueryIdSetCoinLabelDiffers_returnsWalletOfExactToken() {
        val side = SwapSide(bip84Wallet.token.tokenQuery.id, "old-bitcoin-label", BlockchainType.Bitcoin.uid)

        assertSame(bip84Wallet, wallets.findBySwapSide(side))
    }

    @Test
    fun findBySwapSide_tokenQueryIdOfMissingWallet_returnsNull() {
        val side = SwapSide("bitcoin|derived:bip86", "bitcoin", BlockchainType.Bitcoin.uid)

        assertNull(wallets.findBySwapSide(side))
    }

    @Test
    fun findBySwapSide_tokenQueryIdNull_matchesCoinUidAndChain() {
        val side = SwapSide(null, "bitcoin", BlockchainType.Bitcoin.uid)

        assertSame(bip44Wallet, wallets.findBySwapSide(side))
    }

    @Test
    fun findBySwapSide_tokenQueryIdNullOtherChain_returnsNull() {
        val side = SwapSide(null, "bitcoin", BlockchainType.Litecoin.uid)

        assertNull(wallets.findBySwapSide(side))
    }

    private fun wallet(derivation: TokenType.Derivation): Wallet {
        val token = Token(
            coin = Coin(uid = "bitcoin", name = "Bitcoin", code = "BTC"),
            blockchain = Blockchain(BlockchainType.Bitcoin, "Bitcoin", null),
            type = TokenType.Derived(derivation),
            decimals = 8,
        )
        return mockk {
            every { this@mockk.token } returns token
            every { coin } returns token.coin
        }
    }
}
