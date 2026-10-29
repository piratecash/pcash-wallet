package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.entities.TonWalletVersion
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class TonWalletVersionTest {

    private val mnemonic = AccountType.Mnemonic(listOf("word"), "")

    @Test
    fun badge_tonNativeByAccountType_versionHiddenOnlyForWatchedAddress() {
        assertEquals("V4R2", wallet(TokenType.Native, mnemonic).badge)
        assertEquals("W5", wallet(TokenType.TonW5Native, mnemonic).badge)
        assertNull(wallet(TokenType.Native, AccountType.TonAddress("ton-address")).badge)
    }

    @Test
    fun badge_tonJettons_w5Marked() {
        assertEquals("JETTON", wallet(TokenType.Jetton("master"), mnemonic).badge)
        assertEquals("JETTON W5", wallet(TokenType.TonW5Jetton("master"), mnemonic).badge)
    }

    @Test
    fun transactionSource_tonVersions_separatedAndVersionRecovered() {
        val v4r2Source = wallet(TokenType.Native, mnemonic).transactionSource
        val w5Source = wallet(TokenType.TonW5Native, mnemonic).transactionSource

        assertNotEquals(v4r2Source, w5Source)
        assertEquals(w5Source, wallet(TokenType.TonW5Jetton("master"), mnemonic).transactionSource)
        assertEquals(TonWalletVersion.V4R2, v4r2Source.tonWalletVersion)
        assertEquals(TonWalletVersion.W5, w5Source.tonWalletVersion)
    }

    @Test
    fun tonWalletVersion_nonTonSource_null() {
        assertNull(wallet(TokenType.Native, mnemonic, BlockchainType.Ethereum).transactionSource.tonWalletVersion)
    }

    private fun wallet(
        tokenType: TokenType,
        accountType: AccountType,
        blockchainType: BlockchainType = BlockchainType.Ton,
    ) = Wallet(
        token = Token(
            coin = Coin(uid = "coin", name = "Coin", code = "COIN"),
            blockchain = Blockchain(type = blockchainType, name = blockchainType.uid, eip3091url = null),
            type = tokenType,
            decimals = 9,
        ),
        account = Account(
            id = "account-id",
            name = "Account",
            type = accountType,
            origin = AccountOrigin.Created,
            level = 0,
        ),
        hardwarePublicKey = null,
    )
}
