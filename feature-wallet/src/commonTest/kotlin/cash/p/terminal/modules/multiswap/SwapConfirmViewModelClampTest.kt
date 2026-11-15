package cash.p.terminal.modules.multiswap

import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import io.horizontalsystems.core.Translator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
