package cash.p.terminal.modules.multiswap

import cash.p.terminal.core.ServiceStateFlow
import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionServiceState
import io.horizontalsystems.core.Translator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class SwapConfirmViewModelClampTest : SwapConfirmViewModelTestBase() {

    private val provider = mockk<IMultiSwapProvider>(relaxed = true) {
        every { mevProtectionAvailable } returns false
        coEvery { fetchFinalQuote(any(), any(), any(), any(), any(), any()) } answers {
            finalQuote(amountIn = thirdArg())
        }
    }

    private val shrunkAmount = BigDecimal("0.6")
    private val errorCaution = CautionViewItem("title", "text", CautionViewItem.Type.Error)
    private val shortfallState = sendTransactionServiceState.copy(
        availableBalance = shrunkAmount,
        cautions = listOf(errorCaution),
        feeCaution = errorCaution,
        sendable = false,
    )
    private val shrunkQuoteGate = CompletableDeferred<Unit>()

    // The final quote for the shrunk amount waits for shrunkQuoteGate.
    private val gatedProvider = mockk<IMultiSwapProvider>(relaxed = true) {
        every { mevProtectionAvailable } returns false
        coEvery { fetchFinalQuote(any(), any(), any(), any(), any(), any()) } coAnswers {
            val amountIn: BigDecimal = thirdArg()
            if (amountIn.compareTo(shrunkAmount) == 0) shrunkQuoteGate.await()
            finalQuote(amountIn = amountIn)
        }
    }

    @Before
    fun mockTranslator() {
        mockkObject(Translator)
        every { Translator.getString(any(), *anyVararg()) } returns "text"
    }

    @Test
    fun init_availableBalanceBelowAmountIn_reducesAmountAndRefetchesQuote() = runTest(dispatcher) {
        val available = BigDecimal("0.6")

        val viewModel = createViewModel(provider, sendTransactionServiceState.copy(availableBalance = available))
        advanceUntilIdle()

        assertEquals(available, viewModel.uiState.amountIn)
        coVerify { provider.fetchFinalQuote(any(), any(), available, any(), any(), any()) }
    }

    @Test
    fun init_availableBalanceZero_keepsAmountAndShowsInsufficientBalance() = runTest(dispatcher) {
        val viewModel = createViewModel(provider, sendTransactionServiceState.copy(availableBalance = BigDecimal.ZERO))
        advanceUntilIdle()

        assertEquals(BigDecimal.ONE, viewModel.uiState.amountIn)
        coVerify(exactly = 0) {
            provider.fetchFinalQuote(any(), any(), match { it.signum() <= 0 }, any(), any(), any())
        }
        assertTrue(viewModel.uiState.cautions.any { it.type == CautionViewItem.Type.Error })
    }

    @Test
    fun init_shrinkRefetchInFlight_hidesStaleBalanceErrors() = runTest(dispatcher) {
        val viewModel = createViewModel(gatedProvider, shortfallState)
        advanceUntilIdle()

        viewModel.assertStaleErrorsHidden()
        assertTrue(viewModel.uiState.loading)

        transactionStates.emit(shortfallState)
        advanceUntilIdle()

        viewModel.assertStaleErrorsHidden()
    }

    @Test
    fun init_shrinkRefetchCompleted_showsNewTransactionState() = runTest(dispatcher) {
        val viewModel = createViewModel(gatedProvider, shortfallState)
        advanceUntilIdle()

        shrunkQuoteGate.complete(Unit)
        transactionStates.emit(sendTransactionServiceState.copy(availableBalance = shrunkAmount))
        advanceUntilIdle()

        assertTrue(viewModel.errorCautions().isEmpty())
        assertTrue(viewModel.uiState.validQuote)

        transactionStates.emit(shortfallState.copy(feeCaution = null))
        advanceUntilIdle()

        assertEquals(listOf(errorCaution), viewModel.errorCautions())
        assertFalse(viewModel.uiState.validQuote)
    }

    @Test
    fun refresh_withoutShrink_keepsTransactionErrorsVisible() = runTest(dispatcher) {
        val refreshGate = CompletableDeferred<Unit>()
        var quotes = 0
        val slowRefreshProvider = mockk<IMultiSwapProvider>(relaxed = true) {
            every { mevProtectionAvailable } returns false
            coEvery { fetchFinalQuote(any(), any(), any(), any(), any(), any()) } coAnswers {
                if (++quotes > 1) refreshGate.await()
                finalQuote(amountIn = thirdArg())
            }
        }
        val viewModel = createViewModel(slowRefreshProvider, shortfallState.copy(availableBalance = null))
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.loading)
        assertEquals(listOf(errorCaution), viewModel.errorCautions())
        assertEquals(errorCaution, viewModel.uiState.feeCaution)
    }

    @Test
    fun fetchFinalQuote_shortfallPublishedDuringInstall_keepsStaleErrorsHidden() = runTest(dispatcher) {
        val installGate = CompletableDeferred<Unit>()
        var publishOnInstall: SendTransactionServiceState? = null
        val service = sendTransactionServiceMock().also {
            // The TON service publishes its fee verdict before its setter returns; cancellation cannot stop that tail.
            coEvery { it.setSendTransactionData(any()) } coAnswers {
                val state = publishOnInstall ?: return@coAnswers
                publishOnInstall = null
                transactionStates.emit(state)
                withContext(NonCancellable) { installGate.await() }
            }
        }
        val viewModel = createViewModel(gatedProvider, serviceOverride = service)
        advanceUntilIdle()

        publishOnInstall = shortfallState
        viewModel.refresh()
        advanceUntilIdle()
        installGate.complete(Unit)
        advanceUntilIdle()

        viewModel.assertStaleErrorsHidden()
    }

    @Test
    fun fetchFinalQuote_freshStatePublishedDuringInstall_startsQuoteTimer() = runTest(dispatcher) {
        val installGate = CompletableDeferred<Unit>()
        var installs = 0
        val service = sendTransactionServiceMock().also {
            coEvery { it.setSendTransactionData(any()) } coAnswers {
                if (++installs == 2) {
                    transactionStates.emit(sendTransactionServiceState.copy(availableBalance = shrunkAmount))
                    installGate.await()
                }
            }
        }
        val timer = mockk<TimerService>(relaxed = true) {
            every { stateFlow } returns ServiceStateFlow(
                MutableSharedFlow<TimerService.State>(replay = 1).apply { tryEmit(TimerService.State(null, false)) }
            )
        }
        shrunkQuoteGate.complete(Unit)
        val viewModel = createViewModel(gatedProvider, shortfallState, serviceOverride = service, timerService = timer)
        advanceUntilIdle()

        verify { timer.start(10) }

        installGate.complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.validQuote)
    }

    private fun SwapConfirmViewModel.assertStaleErrorsHidden() {
        assertTrue(errorCautions().isEmpty())
        assertNull(uiState.feeCaution)
        assertFalse(uiState.validQuote)
    }
}
