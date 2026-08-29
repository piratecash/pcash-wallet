package cash.p.terminal.core.managers

import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.core.storage.BlockchainSettingsStorage
import cash.p.terminal.core.storage.EvmSyncSourceStorage
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.RpcSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EvmSyncSourceManagerTest {

    private val manager = EvmSyncSourceManager(
        blockchainSettingsStorage = mockk<BlockchainSettingsStorage>(relaxed = true),
        evmSyncSourceStorage = mockk<EvmSyncSourceStorage>(relaxed = true),
    )

    @Test
    fun defaultSyncSources_robinhood_usesOfficialRpcAndBlockscout() {
        mockkObject(AppConfigProvider)
        every { AppConfigProvider.etherscanApiKey } returns emptyList()
        try {
            val source = manager.defaultSyncSources(BlockchainType.RobinhoodChain).single()
            val rpcSource = source.rpcSource as RpcSource.Http

            assertEquals("Robinhood Chain", source.name)
            assertEquals(listOf(ROBINHOOD_RPC_URL), rpcSource.uris.map { it.toString() })
            assertEquals(
                "https://robinhoodchain.blockscout.com/tx/0x1234",
                source.transactionSource.transactionUrl("0x1234")
            )
        } finally {
            unmockkObject(AppConfigProvider)
        }
    }

    @Test
    fun getChain_robinhood_returnsEthereumKitRobinhoodChain() {
        val blockchainManager = EvmBlockchainManager(
            backgroundManager = mockk(),
            syncSourceManager = manager,
            marketKit = mockk(),
            accountManagerFactory = mockk(),
            backgroundKeepAliveManager = mockk(),
            networkErrorTracker = mockk(),
            offlineModeManager = mockk(),
        )

        assertSame(Chain.RobinhoodChain, blockchainManager.getChain(BlockchainType.RobinhoodChain))
    }
}
