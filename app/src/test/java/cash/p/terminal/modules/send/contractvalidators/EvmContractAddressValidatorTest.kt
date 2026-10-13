package cash.p.terminal.modules.send.contractvalidators

import cash.p.terminal.core.managers.EvmSyncSourceManager
import cash.p.terminal.core.managers.ROBINHOOD_RPC_URL
import cash.p.terminal.entities.EvmSyncSource
import cash.p.terminal.network.binance.api.EthereumRpcApi
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class EvmContractAddressValidatorTest {

    private val rpcUrl = slot<String>()
    private val ethereumRpcApi = mockk<EthereumRpcApi> {
        coEvery { getCode(capture(rpcUrl), ADDRESS) } returns "0x01"
    }
    private val evmSyncSourceManager = mockk<EvmSyncSourceManager>()
    private val validator =
        EvmContractAddressValidator(ethereumRpcApi, ExcludedContractValidator(), evmSyncSourceManager)

    @Test
    fun isContract_robinhood_usesSharedOfficialRpc() = runTest {
        givenDefaultSyncSources(BlockchainType.RobinhoodChain, ROBINHOOD_RPC_URL)

        val result = validator.isContract(ADDRESS, BlockchainType.RobinhoodChain)

        assertTrue(result == true)
        assertEquals(ROBINHOOD_RPC_URL, rpcUrl.captured)
    }

    @Test
    fun isContract_ethereum_usesDefaultSyncSourceRpc() = runTest {
        givenDefaultSyncSources(BlockchainType.Ethereum, ETHEREUM_RPC_URL, "https://second.example")

        val result = validator.isContract(ADDRESS, BlockchainType.Ethereum)

        assertTrue(result == true)
        assertEquals(ETHEREUM_RPC_URL, rpcUrl.captured)
    }

    @Test
    fun isContract_noDefaultSyncSource_returnsNull() = runTest {
        givenDefaultSyncSources(BlockchainType.Ethereum)

        assertNull(validator.isContract(ADDRESS, BlockchainType.Ethereum))
    }

    private fun givenDefaultSyncSources(blockchainType: BlockchainType, vararg uris: String) {
        every { evmSyncSourceManager.defaultSyncSources(blockchainType) } returns uris.map { uri ->
            mockk<EvmSyncSource> { every { this@mockk.uri } returns URI(uri) }
        }
    }

    private companion object {
        const val ETHEREUM_RPC_URL = "https://eth.example"
        const val ADDRESS = "0x1111111111111111111111111111111111111111"
    }
}
