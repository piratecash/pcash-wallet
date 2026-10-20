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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UnlockViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    // Wall time runs on while the computer sleeps; the coroutine clock does not.
    private var sleptMillis = 0L
    private val currentDateProvider = object : ICurrentDateProvider {
        override val currentDate: Date get() = Date(START + dispatcher.scheduler.currentTime + sleptMillis)
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
    fun onSubmitClick_afterTheLockoutDeadlinePasses_acceptsTheCorrectPassword() = runTest(dispatcher) {
        coEvery { vault.unlock(any()) } returns
            UnlockOutcome.LockedOut(Date(now.time + LOCKOUT_MS)) andThen UnlockOutcome.Success
        val viewModel = UnlockViewModel(vault, currentDateProvider)

        viewModel.onPasswordChange(PASSWORD)
        viewModel.onSubmitClick()
        runCurrent()
        assertFalse(viewModel.uiState.canSubmit)

        advanceTimeBy(LOCKOUT_MS + 1_000)
        runCurrent()

        assertTrue(viewModel.uiState.canSubmit)
        viewModel.onSubmitClick()
        runCurrent()
        assertNull(viewModel.uiState.outcome)
        coVerify(exactly = 2) { vault.unlock(any()) }
    }

    @Test
    fun onSubmitClick_lockoutExpiresWhileTheComputerSleeps_acceptsAPasswordAgain() = runTest(dispatcher) {
        coEvery { vault.unlock(any()) } returns UnlockOutcome.LockedOut(Date(now.time + LOCKOUT_MS))
        val viewModel = UnlockViewModel(vault, currentDateProvider)
        viewModel.onPasswordChange(PASSWORD)
        viewModel.onSubmitClick()
        runCurrent()

        sleptMillis = 2 * LOCKOUT_MS
        advanceTimeBy(1_001)
        runCurrent()

        assertTrue(viewModel.uiState.canSubmit)
    }

    @Test
    fun onSubmitClick_success_clearsThePassword() = runTest(dispatcher) {
        coEvery { vault.unlock(any()) } returns UnlockOutcome.Success
        val viewModel = UnlockViewModel(vault, currentDateProvider)
        viewModel.onPasswordChange(PASSWORD)

        viewModel.onSubmitClick()
        runCurrent()

        assertEquals("", viewModel.uiState.password)
        assertFalse(viewModel.uiState.canSubmit)
    }

    @Test
    fun init_persistedLockout_keepsUnlockDisabledUntilItExpires() = runTest(dispatcher) {
        coEvery { vault.lockedOutUntil() } returns Date(now.time + LOCKOUT_MS)
        val viewModel = UnlockViewModel(vault, currentDateProvider)
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
