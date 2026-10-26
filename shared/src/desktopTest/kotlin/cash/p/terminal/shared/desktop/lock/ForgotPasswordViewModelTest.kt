package cash.p.terminal.shared.desktop.lock

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.desktop_forgot_password_failed
import cash.p.terminal.resources.desktop_restart_manually
import cash.p.terminal.resources.desktop_wipe_restart_manually
import cash.p.terminal.shared.desktop.DesktopWipeCoordinator
import cash.p.terminal.shared.desktop.WipeProgress
import cash.p.terminal.shared.desktop.vault.WipeOutcome
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {

    private val progress = MutableStateFlow<WipeProgress>(WipeProgress.Idle)
    private val coordinator = mockk<DesktopWipeCoordinator>(relaxed = true) {
        every { progress } returns this@ForgotPasswordViewModelTest.progress
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun onDeleteClick_understood_asksTheCoordinatorOnly() {
        val viewModel = ForgotPasswordViewModel(coordinator)

        viewModel.onUnderstoodChange(true)
        viewModel.onDeleteClick()

        verify(exactly = 1) { coordinator.wipe() }
    }

    @Test
    fun progress_wipeRunning_blocksASecondRequest() {
        val viewModel = ForgotPasswordViewModel(coordinator)
        viewModel.onUnderstoodChange(true)

        progress.value = WipeProgress.InProgress

        assertTrue(viewModel.uiState.inProgress)
        assertFalse(viewModel.uiState.canSubmit)
    }

    @Test
    fun restartManuallyMessages_outcome_saysTheDataIsGoneOnlyAfterASuccess() {
        assertEquals(listOf(Res.string.desktop_wipe_restart_manually), restartManuallyMessages(WipeOutcome.SUCCESS))
        assertEquals(
            listOf(Res.string.desktop_forgot_password_failed, Res.string.desktop_restart_manually),
            restartManuallyMessages(WipeOutcome.FAILED),
        )
    }
}
