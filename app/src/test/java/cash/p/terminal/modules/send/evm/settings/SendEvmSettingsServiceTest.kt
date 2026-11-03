package cash.p.terminal.modules.send.evm.settings

import cash.p.terminal.modules.evmfee.GasData
import cash.p.terminal.modules.evmfee.IEvmFeeService
import cash.p.terminal.ui_compose.entities.DataState
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.GasPrice
import io.horizontalsystems.ethereumkit.models.TransactionData
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.io.IOException
import java.math.BigInteger
import cash.p.terminal.modules.evmfee.Transaction as TransactionFeeData

class SendEvmSettingsServiceTest {

    private val feeService = mockk<IEvmFeeService>(relaxed = true)
    private val nonceService = mockk<SendEvmNonceService>(relaxed = true)
    private val service = SendEvmSettingsService(feeService, nonceService)

    @Test
    fun retry_onlyNonceFailed_doesNotReEstimateFee() = runBlocking {
        stub(feeState = DataState.Success(feeData()), nonceState = DataState.Error(IOException("timeout")))

        retryAfterError()

        coVerify(exactly = 0) { feeService.retry() }
        coVerify(exactly = 1) { nonceService.retry() }
    }

    @Test
    fun retry_onlyFeeFailed_doesNotRefetchNonce() = runBlocking {
        stub(
            feeState = DataState.Error(IOException("timeout")),
            nonceState = DataState.Success(SendEvmNonceService.State(nonce = 7, default = true, fixed = false))
        )

        retryAfterError()

        coVerify(exactly = 1) { feeService.retry() }
        coVerify(exactly = 0) { nonceService.retry() }
    }

    private fun stub(
        feeState: DataState<TransactionFeeData>,
        nonceState: DataState<SendEvmNonceService.State>
    ) {
        every { feeService.transactionStatusFlow } returns MutableStateFlow(feeState)
        every { nonceService.state } returns nonceState
        every { nonceService.stateFlow } returns MutableStateFlow(nonceState)
    }

    private suspend fun retryAfterError() = coroutineScope {
        val collecting = launch { service.start() }
        withTimeout(TIMEOUT_MS) { service.stateFlow.first { it is DataState.Error } }

        service.retry()
        collecting.cancel()
    }

    private fun feeData(): TransactionFeeData {
        val data = TransactionData(Address(ADDRESS), BigInteger.ONE, byteArrayOf())
        return TransactionFeeData(
            transactionData = data,
            gasData = GasData(gasLimit = 21_000, gasPrice = GasPrice.Legacy(1)),
            default = true,
            estimatedFor = data
        )
    }

    private companion object {
        const val ADDRESS = "0x1111111111111111111111111111111111111111"
        const val TIMEOUT_MS = 5_000L
    }
}
