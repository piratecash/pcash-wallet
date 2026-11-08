package cash.p.terminal.shared.desktop.lock

import cash.p.terminal.shared.desktop.vault.ChangePasswordOutcome
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.wallet.PassphraseValidator
import io.horizontalsystems.core.ICurrentDateProvider
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChangePasswordViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val currentDateProvider = object : ICurrentDateProvider {
        override val currentDate: Date get() = Date(START + dispatcher.scheduler.currentTime)
    }
    private val vault = mockk<DesktopVault>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { vault.lockedOutUntil() } returns null
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_persistedLockout_showsTheTimeAndDisablesTheFormUntilItEnds() = runTest(dispatcher) {
        val until = Date(START + LOCKOUT_MS)
        coEvery { vault.lockedOutUntil() } returns until
        val viewModel = filledViewModel()
        runCurrent()
        assertEquals(until, viewModel.uiState.lockedOutUntil)
        assertFalse(viewModel.uiState.canSubmit)

        advanceTimeBy(LOCKOUT_MS + PAST_DEADLINE_MS)
        runCurrent()

        assertNull(viewModel.uiState.lockedOutUntil)
        assertTrue(viewModel.uiState.canSubmit)
    }

    @Test
    fun onChangeClick_attemptLocksOut_showsTheTimeEvenAfterEditingAField() = runTest(dispatcher) {
        val until = Date(START + LOCKOUT_MS)
        val viewModel = filledViewModel()
        runCurrent()
        coEvery { vault.changePassword(any(), any()) } returns ChangePasswordOutcome.LOCKED_OUT
        coEvery { vault.lockedOutUntil() } returns until

        viewModel.onChangeClick()
        runCurrent()
        viewModel.onCurrentPasswordChange(OLD_PASSWORD + "x")

        assertEquals(until, viewModel.uiState.lockedOutUntil)
        assertFalse(viewModel.uiState.canSubmit)
    }

    private fun filledViewModel() = ChangePasswordViewModel(vault, PassphraseValidator(), currentDateProvider).apply {
        onCurrentPasswordChange(OLD_PASSWORD)
        onNewPasswordChange(NEW_PASSWORD)
        onConfirmationChange(NEW_PASSWORD)
    }

    private companion object {
        const val START = 1_000_000L
        const val LOCKOUT_MS = 60_000L
        const val PAST_DEADLINE_MS = 1_001L
        const val OLD_PASSWORD = "Correct-horse1"
        const val NEW_PASSWORD = "Another-horse2"
    }
}
