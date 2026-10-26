package cash.p.terminal.shared.desktop

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.WipeOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopWipeCoordinatorTest {

    private val vault = mockk<DesktopVault>()
    private val relauncher = mockk<DesktopAppRelauncher>()
    private val appExit = DesktopAppExit()

    @Test
    fun wipe_success_relaunchesAndExits() = runTest {
        coEvery { vault.wipe() } returns WipeOutcome.SUCCESS
        every { relauncher.relaunch(wipeFailed = false) } returns true

        startCoordinator().wipe()

        verify { relauncher.relaunch(wipeFailed = false) }
        assertTrue(appExit.requested.value)
    }

    @Test
    fun wipe_failed_stillRelaunchesAndTellsTheNewProcess() = runTest {
        coEvery { vault.wipe() } returns WipeOutcome.FAILED
        every { relauncher.relaunch(wipeFailed = true) } returns true

        startCoordinator().wipe()

        verify { relauncher.relaunch(wipeFailed = true) }
        assertTrue(appExit.requested.value)
    }

    @Test
    fun wipe_relaunchImpossible_asksToRestartManuallyWithTheOutcome() = runTest {
        WipeOutcome.entries.forEach { outcome ->
            coEvery { vault.wipe() } returns outcome
            every { relauncher.relaunch(any()) } returns false
            val coordinator = startCoordinator()

            coordinator.wipe()

            assertEquals(WipeProgress.RestartManually(outcome), coordinator.progress.value)
        }
        assertFalse(appExit.requested.value)
    }

    @Test
    fun wipe_requestedTwice_wipesOnce() = runTest {
        coEvery { vault.wipe() } returns WipeOutcome.SUCCESS
        every { relauncher.relaunch(any()) } returns false
        val coordinator = startCoordinator()

        coordinator.wipe()
        coordinator.wipe()

        coVerify(exactly = 1) { vault.wipe() }
    }

    private fun TestScope.startCoordinator(): DesktopWipeCoordinator {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return DesktopWipeCoordinator(vault, relauncher, appExit, TestDispatcherProvider(dispatcher, backgroundScope))
            .also { it.start(backgroundScope) }
    }
}
