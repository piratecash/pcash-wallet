package cash.p.terminal.core

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TonTokenSupportTest {

    @Test
    fun supports_tonAddress_onlyV4R2Tokens() {
        val tonAddress = AccountType.TonAddress("ton-address")

        assertTrue(ton(TokenType.Native).supports(tonAddress))
        assertTrue(ton(TokenType.Jetton("master")).supports(tonAddress))
        assertFalse(ton(TokenType.TonW5Native).supports(tonAddress))
        assertFalse(ton(TokenType.TonW5Jetton("master")).supports(tonAddress))
    }

    @Test
    fun supports_mnemonic_tonW5Tokens() {
        val mnemonic = AccountType.Mnemonic(listOf("word"), "")

        assertTrue(ton(TokenType.TonW5Native).supports(mnemonic))
        assertTrue(ton(TokenType.TonW5Jetton("master")).supports(mnemonic))
    }

    private fun ton(tokenType: TokenType) = Token(
        coin = Coin(uid = "the-open-network", name = "Toncoin", code = "TON"),
        blockchain = Blockchain(BlockchainType.Ton, "TON", null),
        type = tokenType,
        decimals = 9,
    )
}
