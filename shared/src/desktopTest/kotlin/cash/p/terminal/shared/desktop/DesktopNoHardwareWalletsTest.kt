package cash.p.terminal.shared.desktop

import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.modules.send.offline.OfflineBroadcastTokenResolver
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopNoHardwareWalletsTest {

    @Test
    fun resolveTokenToEnable_mnemonicOnDogecoin_returnsToken() {
        val marketKit = mockk<MarketKitWrapper> {
            every { token(any()) } answers { firstArg<TokenQuery>().let { token(it.blockchainType, it.tokenType) } }
        }
        val btcBlockchainManager = mockk<BtcBlockchainManager> {
            every { blockchainTypes } returns listOf(BlockchainType.Dogecoin)
        }
        val resolver = OfflineBroadcastTokenResolver(marketKit, btcBlockchainManager, mockk(), DesktopNoHardwareWallets)

        val token = resolver.resolveTokenToEnable(BlockchainType.Dogecoin, account(AccountType.Mnemonic(listOf("word"), "")))

        assertEquals(BlockchainType.Dogecoin, token?.blockchainType)
    }

    @Test
    fun isSupported_hardwareCardAccount_returnsFalse() {
        val account = account(AccountType.HardwareCard("card", 0, "pub", 0))

        assertFalse(DesktopNoHardwareWallets.isSupported(account, token(BlockchainType.Bitcoin, TokenType.Native)))
    }

    private fun account(type: AccountType) =
        Account(id = "id", name = "name", type = type, origin = AccountOrigin.Restored, level = 0)

    private fun token(blockchainType: BlockchainType, tokenType: TokenType) = Token(
        coin = Coin(uid = blockchainType.uid, name = blockchainType.uid, code = blockchainType.uid),
        blockchain = Blockchain(blockchainType, blockchainType.uid, null),
        type = tokenType,
        decimals = 8,
    )
}
