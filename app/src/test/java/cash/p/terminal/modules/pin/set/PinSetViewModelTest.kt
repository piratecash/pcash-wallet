package cash.p.terminal.modules.pin.set

import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.pin.set.PinSetModule.SetStage
import io.horizontalsystems.core.IPinComponent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PinSetViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    // Inside a hidden wallet there is no duress level, so duress uniqueness is never satisfied
    private val pinComponent = mockk<IPinComponent> {
        every { isUnique(any(), forDuress = true) } returns false
        every { isUnique(any(), forDuress = false) } returns true
        every { getPinLevel(any()) } returns null
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onKeyClick_transactionsHideUnusedPinWithoutDuressLevel_movesToConfirm() =
        assertUnusedPinAccepted(PinType.TRANSACTIONS_HIDE)

    @Test
    fun onKeyClick_transferUnusedPinWithoutDuressLevel_movesToConfirm() =
        assertUnusedPinAccepted(PinType.TRANSFER)

    private fun assertUnusedPinAccepted(pinType: PinType) = runTest(dispatcher) {
        val viewModel = PinSetViewModel(pinComponent, pinType, mockk(), mockk(), mockk())

        repeat(6) { viewModel.onKeyClick(7) }
        advanceUntilIdle()

        assertNull(viewModel.uiState.error)
        assertEquals(SetStage.Confirm, viewModel.uiState.stage)
    }
}
