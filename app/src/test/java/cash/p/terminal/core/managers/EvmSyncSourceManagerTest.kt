package cash.p.terminal.core.managers

import android.app.Application
import cash.p.terminal.core.providers.AddressResolutionConfig
import cash.p.terminal.core.providers.EvmExplorerApiKeys
import cash.p.terminal.core.storage.BlockchainSettingsStorage
import cash.p.terminal.core.storage.EvmSyncSourceStorage
import cash.p.terminal.entities.EvmSyncSourceRecord
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric: customSyncSources parses through android.net.Uri.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EvmSyncSourceManagerTest {

    private val addressResolutionConfig = mockk<AddressResolutionConfig>()

    private val manager = EvmSyncSourceManager(
        blockchainSettingsStorage = mockk<BlockchainSettingsStorage>(relaxed = true),
        evmSyncSourceStorage = mockk<EvmSyncSourceStorage>(relaxed = true),
        addressResolutionConfig = addressResolutionConfig,
    )

    @Test
    fun defaultSyncSources_robinhood_usesOfficialRpcAndBlockscout() {
        mockkObject(EvmExplorerApiKeys)
        every { EvmExplorerApiKeys.blockscout } returns emptyList()
        every { EvmExplorerApiKeys.etherscan } returns emptyList()
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
            unmockkObject(EvmExplorerApiKeys)
        }
    }

    @Test
    fun defaultSyncSources_zkSync_usesOfficialApiPrimaryAndBlockscoutFallback() {
        mockkObject(EvmExplorerApiKeys)
        every { EvmExplorerApiKeys.blockscout } returns emptyList()
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
            unmockkObject(EvmExplorerApiKeys)
        }
    }

    @Test
    fun getSyncSource_storedUrlNoLongerListed_returnsFirstDefault() {
        mockkObject(EvmExplorerApiKeys)
        every { addressResolutionConfig.blocksDecodedEthereumRpc } returns BLOCKS_DECODED_RPC
        every { EvmExplorerApiKeys.etherscan } returns emptyList()
        every { EvmExplorerApiKeys.blockscout } returns emptyList()
        val settingsStorage = mockk<BlockchainSettingsStorage> {
            every { evmSyncSourceUrl(BlockchainType.Ethereum) } returns "https://eth.llamarpc.com"
        }
        val evmSyncSourceStorage = mockk<EvmSyncSourceStorage> {
            every { evmSyncSources(BlockchainType.Ethereum) } returns emptyList()
        }
        try {
            val source = EvmSyncSourceManager(settingsStorage, evmSyncSourceStorage, addressResolutionConfig)
                .getSyncSource(BlockchainType.Ethereum)

            assertEquals(BLOCKS_DECODED_RPC, source.uri.toString())
        } finally {
            unmockkObject(EvmExplorerApiKeys)
        }
    }

    @Test
    fun customSyncSources_malformedUnsupportedRecord_returnsValidRecord() {
        mockkObject(EvmExplorerApiKeys)
        every { EvmExplorerApiKeys.etherscan } returns emptyList()
        val evmSyncSourceStorage = mockk<EvmSyncSourceStorage> {
            every { evmSyncSources(BlockchainType.Ethereum) } returns listOf(
                EvmSyncSourceRecord(BlockchainType.Ethereum.uid, MALFORMED_URL, null),
                EvmSyncSourceRecord(BlockchainType.Ethereum.uid, VALID_URL, null),
            )
        }
        try {
            val sources = EvmSyncSourceManager(mockk(), evmSyncSourceStorage, addressResolutionConfig)
                .customSyncSources(BlockchainType.Ethereum)

            assertEquals(listOf(VALID_URL), sources.map { it.uri.toString() })
            assertEquals("rpc.example.com", sources.single().name)
        } finally {
            unmockkObject(EvmExplorerApiKeys)
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
            platformContexts = mockk(),
        )

        assertSame(Chain.RobinhoodChain, blockchainManager.getChain(BlockchainType.RobinhoodChain))
    }

    private companion object {
        const val BLOCKS_DECODED_RPC = "https://api-dev.blocksdecoded.com/v1/ethereum-rpc/mainnet"
        const val MALFORMED_URL = "ftp://bad host"
        const val VALID_URL = "https://rpc.example.com"
    }
}
