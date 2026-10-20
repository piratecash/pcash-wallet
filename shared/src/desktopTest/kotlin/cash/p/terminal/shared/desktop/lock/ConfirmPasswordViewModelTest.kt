package cash.p.terminal.shared.desktop.lock

import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.UnlockOutcome
import io.horizontalsystems.core.ICurrentDateProvider
import io.mockk.coEvery
import io.mockk.coVerify
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmPasswordViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val currentDateProvider = object : ICurrentDateProvider {
        override val currentDate: Date get() = Date(START + dispatcher.scheduler.currentTime)
    }
    private val now get() = currentDateProvider.currentDate
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
    fun onSubmitClick_correctPassword_confirmsAndClearsThePassword() = runTest(dispatcher) {
        coEvery { vault.verifyPassword(any()) } returns UnlockOutcome.Success
        val viewModel = ConfirmPasswordViewModel(vault, currentDateProvider)
        viewModel.onPasswordChange(PASSWORD)

        viewModel.onSubmitClick()
        runCurrent()

        assertTrue(viewModel.confirmed)
        assertEquals("", viewModel.uiState.password)
    }

    @Test
    fun onSubmitClick_wrongPassword_showsAttemptsLeftAndAllowsAnotherTry() = runTest(dispatcher) {
        coEvery { vault.verifyPassword(any()) } returns UnlockOutcome.WrongPassword(attemptsLeft = 4)
        val viewModel = ConfirmPasswordViewModel(vault, currentDateProvider)
        viewModel.onPasswordChange(PASSWORD)

        viewModel.onSubmitClick()
        runCurrent()

        assertFalse(viewModel.confirmed)
        assertEquals(UnlockOutcome.WrongPassword(attemptsLeft = 4), viewModel.uiState.outcome)
        assertTrue(viewModel.uiState.canSubmit)
    }

    @Test
    fun onSubmitClick_lockedOut_blocksUntilTheLockoutEnds() = runTest(dispatcher) {
        coEvery { vault.verifyPassword(any()) } returns
            UnlockOutcome.LockedOut(Date(now.time + LOCKOUT_MS)) andThen UnlockOutcome.Success
        val viewModel = ConfirmPasswordViewModel(vault, currentDateProvider)
        viewModel.onPasswordChange(PASSWORD)
        viewModel.onSubmitClick()
        runCurrent()

        viewModel.onSubmitClick()
        runCurrent()
        assertFalse(viewModel.uiState.canSubmit)
        coVerify(exactly = 1) { vault.verifyPassword(any()) }

        advanceTimeBy(LOCKOUT_MS + 1_000)
        runCurrent()
        viewModel.onSubmitClick()
        runCurrent()

        assertTrue(viewModel.confirmed)
    }

    @Test
    fun init_persistedLockout_keepsConfirmDisabledUntilItExpires() = runTest(dispatcher) {
        coEvery { vault.lockedOutUntil() } returns Date(now.time + LOCKOUT_MS)
        val viewModel = ConfirmPasswordViewModel(vault, currentDateProvider)
        viewModel.onPasswordChange(PASSWORD)
        runCurrent()
        assertFalse(viewModel.uiState.canSubmit)

        advanceTimeBy(LOCKOUT_MS + 1_001)
        runCurrent()

        assertTrue(viewModel.uiState.canSubmit)
    }

    private companion object {
        const val START = 1_700_000_000_000L
        const val PASSWORD = "Correct-horse1"
        const val LOCKOUT_MS = 60_000L
    }
}
