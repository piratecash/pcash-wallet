package cash.p.terminal.modules.backuplocal

import cash.p.terminal.modules.backuplocal.fullbackup.RestoreOutcome
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.EnabledWallet
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

internal class BackupProviderTonW5Test : BackupProviderRestoreTestFixture() {

    private val w5NativeQueryId = TokenQuery(BlockchainType.Ton, TokenType.TonW5Native).id
    private val w5JettonQueryId = TokenQuery(BlockchainType.Ton, TokenType.TonW5Jetton(USDT_MASTER)).id

    @Test
    fun enabledWalletBackups_w5Wallets_exportsTheirTokenQueryIds() {
        every { walletStorage.enabledWallets(restoredAccount.id) } returns listOf(
            enabledWallet(w5NativeQueryId),
            enabledWallet(w5JettonQueryId),
        )

        val backups = backupProvider.enabledWalletBackups(restoredAccount)

        assertEquals(listOf(w5NativeQueryId, w5JettonQueryId), backups.map { it.tokenQueryId })
    }

    @Test
    fun restoreSingleWalletBackup_curatedW5Wallets_restoresThem() = runTest {
        curateTon()
        val saved = captureRestoredWallets()

        val outcome = backupProvider.restoreSingleWalletBackup(
            walletBackupItem(enabledWallets = listOf(backedUpWallet(w5NativeQueryId), backedUpWallet(w5JettonQueryId)))
        )

        assertEquals(RestoreOutcome.Restored, outcome)
        assertEquals(listOf(w5NativeQueryId, w5JettonQueryId), saved.captured.map { it.tokenQueryId })
    }

    // The shared curate() puts every token on Ethereum, which never matches a TON query's identity.
    private fun curateTon() {
        every { marketKit.tokens(any<List<TokenQuery>>()) } answers {
            firstArg<List<TokenQuery>>().map { query ->
                Token(
                    coin = Coin(uid = "the-open-network", name = "Toncoin", code = "TON"),
                    blockchain = Blockchain(BlockchainType.Ton, "TON", null),
                    type = query.tokenType,
                    decimals = 9,
                )
            }
        }
    }

    private fun enabledWallet(tokenQueryId: String) = EnabledWallet(
        tokenQueryId = tokenQueryId,
        accountId = restoredAccount.id,
        coinName = "Toncoin",
        coinCode = "TON",
        coinDecimals = 9,
        coinImage = null,
    )

    private companion object {
        const val USDT_MASTER = "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs"
    }
}
