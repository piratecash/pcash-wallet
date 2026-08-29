package cash.p.terminal.modules.send.contractvalidators

import cash.p.terminal.core.managers.ROBINHOOD_RPC_URL
import cash.p.terminal.network.binance.api.EthereumRpcApi
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvmContractAddressValidatorTest {

    @Test
    fun isContract_robinhood_usesSharedOfficialRpc() = runTest {
        val rpcUrl = slot<String>()
        val ethereumRpcApi = mockk<EthereumRpcApi> {
            coEvery { getCode(capture(rpcUrl), ADDRESS) } returns "0x01"
        }
        val validator = EvmContractAddressValidator(ethereumRpcApi, ExcludedContractValidator())

        val result = validator.isContract(ADDRESS, BlockchainType.RobinhoodChain)

        assertTrue(result == true)
        assertEquals(ROBINHOOD_RPC_URL, rpcUrl.captured)
    }

    private companion object {
        const val ADDRESS = "0x1111111111111111111111111111111111111111"
    }
}
