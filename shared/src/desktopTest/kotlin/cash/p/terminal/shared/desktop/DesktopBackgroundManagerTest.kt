package cash.p.terminal.shared.desktop

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.shared.desktop.vault.DesktopStorageSession
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.DesktopVaultState
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.DesktopAppForegroundState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopBackgroundManagerTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val foregroundState = DesktopAppForegroundState()
    private val vaultState = MutableStateFlow<DesktopVaultState>(unlocked())
    private val vault = mockk<DesktopVault> { every { state } returns vaultState }

    @Test
    fun stateFlow_windowFocusLost_staysInForeground() = runTest(dispatcher) {
        val manager = createManager()

        foregroundState.setInForeground(false)

        assertEquals(BackgroundManagerState.EnterForeground, manager.stateFlow.value)
    }

    @Test
    fun stateFlow_windowMinimized_entersBackground() = runTest(dispatcher) {
        val manager = createManager()

        foregroundState.setWindowMinimized(true)

        assertEquals(BackgroundManagerState.EnterBackground, manager.stateFlow.value)
    }

    @Test
    fun stateFlow_windowRestored_entersForegroundAfterRaisingEpoch() = runTest(dispatcher) {
        val manager = createManager()
        foregroundState.setWindowMinimized(true)
        val epochSeenOnForeground = mutableListOf<Int>()
        backgroundScope.launch {
            manager.stateFlow.collect {
                if (it == BackgroundManagerState.EnterForeground) epochSeenOnForeground += manager.foregroundEpoch.value
            }
        }

        foregroundState.setWindowMinimized(false)

        assertEquals(BackgroundManagerState.EnterForeground, manager.stateFlow.value)
        assertEquals(listOf(1), epochSeenOnForeground)
    }

    @Test
    fun stateFlow_vaultLocked_entersBackground() = runTest(dispatcher) {
        val manager = createManager()

        vaultState.value = DesktopVaultState.Locked

        assertEquals(BackgroundManagerState.EnterBackground, manager.stateFlow.value)
    }

    @Test
    fun stateFlow_noVault_isBackground() = runTest(dispatcher) {
        vaultState.value = DesktopVaultState.NoVault

        assertEquals(BackgroundManagerState.EnterBackground, createManager().stateFlow.value)
    }

    private fun TestScope.createManager() =
        DesktopBackgroundManager(foregroundState, vault, TestDispatcherProvider(dispatcher, backgroundScope))

    private fun unlocked() = DesktopVaultState.Unlocked(mockk<DesktopStorageSession>(relaxed = true))
}
