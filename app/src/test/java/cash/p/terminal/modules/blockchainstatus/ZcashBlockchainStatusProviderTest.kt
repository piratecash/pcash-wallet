package cash.p.terminal.modules.blockchainstatus

import cash.p.terminal.core.adapters.zcash.ZcashAdapter
import cash.p.terminal.core.managers.NetworkErrorInfo
import cash.p.terminal.core.managers.NetworkErrorTracker
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.logger.AppLog
import io.horizontalsystems.core.storage.LogsDao
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

class ZcashBlockchainStatusProviderTest {

    private val networkErrorTracker = NetworkErrorTracker()
    private val adapter = mockk<ZcashAdapter> {
        every { statusInfo } returns mapOf("Sync State" to "Synced")
    }
    private val wallet = mockk<Wallet> {
        every { token } returns mockk<Token> { every { blockchainType } returns BlockchainType.Zcash }
        every { account } returns mockk<Account> { every { id } returns ACCOUNT_ID }
    }
    private val walletManager = mockk<IWalletManager> { every { activeWallets } returns listOf(wallet) }
    private val adapterManager = mockk<IAdapterManager> {
        every { getAdapterForWallet<ZcashAdapter>(wallet) } returns adapter
    }

    @Before
    fun setUp() {
        AppLog.logsDao = mockk<LogsDao>(relaxed = true)
    }

    @Test
    fun getStatus_recentNetworkError_isMergedIntoTheStatusMap() {
        networkErrorTracker.record(
            BlockchainType.Zcash,
            ACCOUNT_ID,
            NetworkErrorInfo(
                source = "Zcash",
                method = "sync:TARGET",
                url = "https://zec.rocks:443",
                host = "zec.rocks",
                resolvedIps = emptyList(),
                throwable = IOException("connection reset"),
            ),
        )

        val items = statusItems()

        assertEquals("Synced", items["Sync State"])
        assertEquals("sync:TARGET", items["Recent Network Error Method"])
        assertEquals("zec.rocks", items["Recent Network Error Host"])
    }

    @Test
    fun getStatus_noNetworkError_hasNoErrorRows() {
        assertNull(statusItems()["Recent Network Error Method"])
    }

    private fun statusItems(): Map<String, String> {
        val provider = ZcashBlockchainStatusProvider(walletManager, adapterManager, networkErrorTracker)
        return provider.getStatus().sections.single().items
            .filterIsInstance<StatusItem.KeyValue>()
            .associate { it.key to it.value }
    }

    private companion object {
        const val ACCOUNT_ID = "account"
    }
}
