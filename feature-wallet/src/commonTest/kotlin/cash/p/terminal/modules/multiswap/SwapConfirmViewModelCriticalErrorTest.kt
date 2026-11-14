package cash.p.terminal.modules.multiswap

import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings
import cash.p.terminal.network.exolix.data.entity.BackendExolixResponseError
import cash.p.terminal.network.yifi.data.entity.BackendYiFiResponseError
import cash.p.terminal.resources.Res
import cash.p.terminal.core.HardwareWalletSupport
import cash.p.terminal.resources.swap_card_scan_failed
import cash.p.terminal.resources.swap_provider_order_failed
import cash.p.terminal.resources.unsupported_address
import cash.p.terminal.resources.unsupported_refund_address
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import io.horizontalsystems.core.Translator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwapConfirmViewModelCriticalErrorTest : SwapConfirmViewModelTestBase() {

    @Test
    fun createState_yiFiOrderFailed_showsGenericErrorCaution() = runTest(dispatcher) {
        mockkObject(Translator)
        every { Translator.getString(any(), *anyVararg()) } returns "title"
        every {
            Translator.getString(Res.string.swap_provider_order_failed, *anyVararg())
        } returns "generic-provider-error"
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
        every { Translator.getString(any(), *anyVararg()) } returns "title"
        every {
            Translator.getString(Res.string.unsupported_address, *anyVararg())
        } returns "unsupported-address"
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
        every { Translator.getString(any(), *anyVararg()) } returns "title"
        every {
            Translator.getString(Res.string.unsupported_refund_address, *anyVararg())
        } returns "unsupported-refund-address"
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
        mockkObject(Translator)
        every { Translator.getString(any(), *anyVararg()) } returns "title"
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
        every { Translator.getString(any(), *anyVararg()) } returns "title"
        every {
            Translator.getString(Res.string.swap_provider_order_failed, *anyVararg())
        } returns "generic-provider-error"
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

    @Test
    fun createState_hardwareCancelled_showsRefreshWithoutErrorCaution() = runTest(dispatcher) {
        mockkObject(Translator)
        every { Translator.getString(any(), *anyVararg()) } returns "title"
        val provider = providerFailingWith(
            HardwareWalletOperationException(HardwareWalletErrorCode.Cancelled, "cancelled"),
        )

        val viewModel = createViewModel(provider)
        advanceUntilIdle()
        transactionStates.emit(sendTransactionServiceState.copy(loading = true))
        advanceUntilIdle()

        assertTrue(viewModel.errorCautions().isEmpty())
        assertEquals(null, viewModel.uiState.criticalError)
        assertTrue(viewModel.uiState.finalQuoteCancelled)
        assertFalse(viewModel.uiState.loading)
    }

    @Test
    fun refresh_afterHardwareCancel_successfulQuoteClearsCancelled() = runTest(dispatcher) {
        mockkObject(Translator)
        every { Translator.getString(any(), *anyVararg()) } returns "title"
        var callCount = 0
        val provider = mockk<IMultiSwapProvider>(relaxed = true) {
            every { mevProtectionAvailable } returns false
            coEvery { fetchFinalQuote(any(), any(), any(), any(), any(), any()) } coAnswers {
                callCount++
                if (callCount == 1) {
                    throw HardwareWalletOperationException(HardwareWalletErrorCode.Cancelled, "cancelled")
                } else {
                    finalQuote()
                }
            }
        }

        val viewModel = createViewModel(provider)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.finalQuoteCancelled)

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.finalQuoteCancelled)
        assertTrue(viewModel.uiState.validQuote)
    }

    @Test
    fun createState_criticalErrorThenServiceLoading_keepsRefreshAvailable() = runTest(dispatcher) {
        mockkObject(Translator)
        every { Translator.getString(any(), *anyVararg()) } returns "title"
        val provider = providerFailingWith(
            BackendYiFiResponseError(code = "SWAP_ERROR", message = "fail", statusCode = 500),
        )

        val viewModel = createViewModel(provider)
        advanceUntilIdle()
        transactionStates.emit(sendTransactionServiceState.copy(loading = true))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.criticalError != null)
        assertFalse(viewModel.uiState.loading)
    }

    @Test
    fun createState_cardSdkUserCancel_showsRefreshWithoutErrorCaution() = runTest(dispatcher) {
        mockkObject(Translator, HardwareWalletSupport)
        every { Translator.getString(any(), *anyVararg()) } returns "title"
        val sdkCancel = IllegalStateException("user cancelled")
        every { HardwareWalletSupport.isUserCancellation(sdkCancel) } returns true
        every { HardwareWalletSupport.isReportedBySdk(sdkCancel) } returns true

        val viewModel = createViewModel(providerFailingWith(sdkCancel))
        advanceUntilIdle()

        assertTrue(viewModel.errorCautions().isEmpty())
        assertTrue(viewModel.uiState.finalQuoteCancelled)
    }

    @Test
    fun createState_cardSdkError_showsCardScanFailedCaution() = runTest(dispatcher) {
        mockkObject(Translator, HardwareWalletSupport)
        every { Translator.getString(any(), *anyVararg()) } returns "title"
        every { Translator.getString(Res.string.swap_card_scan_failed, *anyVararg()) } returns "card-scan-failed"
        val sdkError = IllegalStateException("tag lost")
        every { HardwareWalletSupport.isReportedBySdk(sdkError) } returns true

        val viewModel = createViewModel(providerFailingWith(sdkError))
        advanceUntilIdle()

        assertEquals(listOf("card-scan-failed"), viewModel.errorCautions().map { it.text })
        assertFalse(viewModel.uiState.finalQuoteCancelled)
    }

    @Test
    fun sendTransactionSettingsChanged_afterProviderError_showsLoadingUntilNewQuote() = runTest(dispatcher) {
        mockkObject(Translator)
        every { Translator.getString(any(), *anyVararg()) } returns "title"
        val pendingQuote = CompletableDeferred<ISwapFinalQuote>()
        var callCount = 0
        val provider = mockk<IMultiSwapProvider>(relaxed = true) {
            every { mevProtectionAvailable } returns false
            coEvery { fetchFinalQuote(any(), any(), any(), any(), any(), any()) } coAnswers {
                callCount++
                if (callCount == 1) {
                    throw BackendYiFiResponseError(code = "SWAP_ERROR", message = "fail", statusCode = 500)
                } else {
                    pendingQuote.await()
                }
            }
        }

        val viewModel = createViewModel(provider)
        advanceUntilIdle()
        sendTransactionSettings.value = SendTransactionSettings.Ton("receive-address")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.loading)
        assertTrue(viewModel.uiState.criticalError != null)

        pendingQuote.complete(finalQuote())
        advanceUntilIdle()

        assertFalse(viewModel.uiState.loading)
        assertEquals(null, viewModel.uiState.criticalError)
    }
}
