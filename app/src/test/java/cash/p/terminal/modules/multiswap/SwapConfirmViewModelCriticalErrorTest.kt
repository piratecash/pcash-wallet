package cash.p.terminal.modules.multiswap

import cash.p.terminal.R
import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.network.exolix.data.entity.BackendExolixResponseError
import cash.p.terminal.network.yifi.data.entity.BackendYiFiResponseError
import cash.p.terminal.strings.helpers.Translator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwapConfirmViewModelCriticalErrorTest : SwapConfirmViewModelTestBase() {

    private fun providerFailingWith(error: Throwable): IMultiSwapProvider =
        mockk<IMultiSwapProvider>(relaxed = true) {
            every { mevProtectionAvailable } returns false
            coEvery { fetchFinalQuote(any(), any(), any(), any(), any(), any()) } throws error
        }

    private fun SwapConfirmViewModel.errorCautions() =
        uiState.cautions.filter { it.type == CautionViewItem.Type.Error }

    @Test
    fun createState_yiFiOrderFailed_showsGenericErrorCaution() = runTest(dispatcher) {
        mockkObject(Translator)
        every { Translator.getString(R.string.swap_provider_order_failed) } returns "generic-provider-error"
        val rawMessage = "Provider \"ff\" did not return transactionId"
        val provider = providerFailingWith(
            BackendYiFiResponseError(code = "SWAP_ERROR", message = rawMessage, statusCode = 500),
        )

        val viewModel = createViewModel(provider)
        advanceUntilIdle()

        val errorCautions = viewModel.errorCautions()
        assertEquals(1, errorCautions.size)
        assertEquals("generic-provider-error", errorCautions.first().text)
        assertTrue(viewModel.uiState.cautions.none {
            it.title.contains(rawMessage) || it.text.contains(rawMessage)
        })
        assertFalse(viewModel.uiState.validQuote)
        assertTrue(viewModel.uiState.criticalError != null)
    }

    @Test
    fun createState_yiFiInvalidReceiveAddress_showsUnsupportedAddressCaution() = runTest(dispatcher) {
        mockkObject(Translator)
        every { Translator.getString(R.string.unsupported_address) } returns "unsupported-address"
        val provider = providerFailingWith(
            BackendYiFiResponseError(
                code = BackendYiFiResponseError.INVALID_RECEIVE_ADDRESS,
                message = "irrelevant",
                statusCode = 400,
            ),
        )

        val viewModel = createViewModel(provider)
        advanceUntilIdle()

        val errorCautions = viewModel.errorCautions()
        assertEquals(1, errorCautions.size)
        assertEquals("unsupported-address", errorCautions.first().text)
    }

    @Test
    fun createState_yiFiInvalidRefundAddress_showsUnsupportedRefundAddressCaution() = runTest(dispatcher) {
        mockkObject(Translator)
        every { Translator.getString(R.string.unsupported_refund_address) } returns "unsupported-refund-address"
        val provider = providerFailingWith(
            BackendYiFiResponseError(
                code = BackendYiFiResponseError.INVALID_REFUND_ADDRESS,
                message = "irrelevant",
                statusCode = 400,
            ),
        )

        val viewModel = createViewModel(provider)
        advanceUntilIdle()

        val errorCautions = viewModel.errorCautions()
        assertEquals(1, errorCautions.size)
        assertEquals("unsupported-refund-address", errorCautions.first().text)
    }

    @Test
    fun createState_exolixError_keepsProviderMessageInCaution() = runTest(dispatcher) {
        val exolixMessage = "Exolix couldn't process the swap"
        val provider = providerFailingWith(
            BackendExolixResponseError(error = "SOME_ERROR", message = exolixMessage, statusCode = 500),
        )

        val viewModel = createViewModel(provider)
        advanceUntilIdle()

        val errorCautions = viewModel.errorCautions()
        assertEquals(1, errorCautions.size)
        assertEquals(exolixMessage, errorCautions.first().text)
    }

    @Test
    fun refresh_afterProviderError_successfulQuoteClearsErrorCaution() = runTest(dispatcher) {
        mockkObject(Translator)
        every { Translator.getString(R.string.swap_provider_order_failed) } returns "generic-provider-error"
        var callCount = 0
        val provider = mockk<IMultiSwapProvider>(relaxed = true) {
            every { mevProtectionAvailable } returns false
            coEvery { fetchFinalQuote(any(), any(), any(), any(), any(), any()) } coAnswers {
                callCount++
                if (callCount == 1) {
                    throw BackendYiFiResponseError(code = "SWAP_ERROR", message = "fail", statusCode = 500)
                } else {
                    finalQuote()
                }
            }
        }

        val viewModel = createViewModel(provider)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.criticalError != null)

        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(viewModel.errorCautions().isEmpty())
        assertEquals(null, viewModel.uiState.criticalError)
        assertTrue(viewModel.uiState.validQuote)
    }
}
