package cash.p.terminal.core.managers

import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.core.storage.BlockchainSettingsStorage
import cash.p.terminal.core.storage.EvmSyncSourceStorage
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.RpcSource
import io.horizontalsystems.ethereumkit.models.TransactionSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EvmSyncSourceManagerTest {

    private val manager = EvmSyncSourceManager(
        blockchainSettingsStorage = mockk<BlockchainSettingsStorage>(relaxed = true),
        evmSyncSourceStorage = mockk<EvmSyncSourceStorage>(relaxed = true),
    )

    @Test
    fun defaultSyncSources_robinhood_usesOfficialRpcAndBlockscout() {
        mockkObject(AppConfigProvider)
        every { AppConfigProvider.blockscoutApiKey } returns emptyList()
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
            val sourceType = source.transactionSource.type
            assertTrue(sourceType is TransactionSource.SourceType.Etherscan)
            assertEquals(
                "https://api.blockscout.com/v2/",
                (sourceType as TransactionSource.SourceType.Etherscan).apiBaseUrl
            )

            val fallback = source.transactionSource.fallbacks.single()
            assertEquals("https://api.etherscan.io/v2/", fallback.apiBaseUrl)
            assertEquals("https://robin.etherscan.io", fallback.txBaseUrl)
        } finally {
            unmockkObject(AppConfigProvider)
        }
    }

    @Test
    fun defaultSyncSources_zkSync_usesOfficialApiPrimaryAndBlockscoutFallback() {
        mockkObject(AppConfigProvider)
        every { AppConfigProvider.blockscoutApiKey } returns emptyList()
        try {
            val source = manager.defaultSyncSources(BlockchainType.ZkSync).single()

            assertEquals(
                "https://explorer.zksync.io/tx/0x1234",
                source.transactionSource.transactionUrl("0x1234")
            )
            val sourceType = source.transactionSource.type
            assertTrue(sourceType is TransactionSource.SourceType.Etherscan)
            assertEquals(
                "https://block-explorer-api.mainnet.zksync.io/",
                (sourceType as TransactionSource.SourceType.Etherscan).apiBaseUrl
            )

            val fallback = source.transactionSource.fallbacks.single()
            assertEquals("https://api.blockscout.com/v2/", fallback.apiBaseUrl)
            assertEquals("https://zksync.blockscout.com", fallback.txBaseUrl)
        } finally {
            unmockkObject(AppConfigProvider)
        }
    }

    @Test
    fun getSyncSource_storedUrlNoLongerListed_returnsFirstDefault() {
        mockkObject(AppConfigProvider)
        every { AppConfigProvider.blocksDecodedEthereumRpc } returns BLOCKS_DECODED_RPC
        every { AppConfigProvider.etherscanApiKey } returns emptyList()
        every { AppConfigProvider.blockscoutApiKey } returns emptyList()
        val settingsStorage = mockk<BlockchainSettingsStorage> {
            every { evmSyncSourceUrl(BlockchainType.Ethereum) } returns "https://eth.llamarpc.com"
        }
        val evmSyncSourceStorage = mockk<EvmSyncSourceStorage> {
            every { evmSyncSources(BlockchainType.Ethereum) } returns emptyList()
        }
        try {
            val source = EvmSyncSourceManager(settingsStorage, evmSyncSourceStorage)
                .getSyncSource(BlockchainType.Ethereum)

            assertEquals(BLOCKS_DECODED_RPC, source.uri.toString())
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
            databaseKeys = mockk(),
            context = mockk(),
        )

        assertSame(Chain.RobinhoodChain, blockchainManager.getChain(BlockchainType.RobinhoodChain))
    }

    private companion object {
        const val BLOCKS_DECODED_RPC = "https://api-dev.blocksdecoded.com/v1/ethereum-rpc/mainnet"
    }
}
