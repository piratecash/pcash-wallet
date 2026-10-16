package cash.p.terminal.core.managers

import cash.p.terminal.core.providers.EvmExplorerApiKeys
import cash.p.terminal.core.storage.EvmSyncSourceStorage
import cash.p.terminal.entities.EvmSyncSourceRecord
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class EvmSyncSourceManagerDesktopTest {

    @Before
    fun setUp() {
        mockkObject(EvmExplorerApiKeys)
        every { EvmExplorerApiKeys.etherscan } returns emptyList()
    }

    @After
    fun tearDown() {
        unmockkObject(EvmExplorerApiKeys)
    }

    @Test
    fun customSyncSources_malformedUnsupportedRecord_returnsValidRecord() {
        val evmSyncSourceStorage = mockk<EvmSyncSourceStorage> {
            every { evmSyncSources(BlockchainType.Ethereum) } returns listOf(
                EvmSyncSourceRecord(BlockchainType.Ethereum.uid, "ftp://bad host", null),
                EvmSyncSourceRecord(BlockchainType.Ethereum.uid, VALID_URL, null),
            )
        }

        val sources = EvmSyncSourceManager(mockk(), evmSyncSourceStorage, mockk())
            .customSyncSources(BlockchainType.Ethereum)

        assertEquals(listOf(VALID_URL), sources.map { it.uri.toString() })
        assertEquals("rpc.example.com", sources.single().name)
    }

    private companion object {
        const val VALID_URL = "https://rpc.example.com"
    }
}
