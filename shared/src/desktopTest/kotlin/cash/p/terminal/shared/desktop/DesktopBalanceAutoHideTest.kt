package cash.p.terminal.shared.desktop

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.shared.desktop.vault.DesktopStorageSession
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.DesktopVaultState
import io.horizontalsystems.core.DesktopAppForegroundState
import io.horizontalsystems.core.DesktopSettingsStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopBalanceAutoHideTest {

    private val directory = Files.createTempDirectory("balance-auto-hide").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val settingsStore = DesktopSettingsStore(File(directory, "settings.preferences_pb"), scope)
    private val dispatcherProvider = TestDispatcherProvider(Dispatchers.Unconfined, scope)
    private val foregroundState = DesktopAppForegroundState()
    private val vaultState = MutableStateFlow<DesktopVaultState>(DesktopVaultState.Locked)
    private val vault = mockk<DesktopVault> { every { state } returns vaultState }
    private val managerLazy = lazy { balanceHiddenManager(settingsStore, dispatcherProvider) }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        directory.deleteRecursively()
    }

    @Test
    fun start_focusLostWithAutoHideEnabled_hidesBalance() {
        unlockWithBalanceShown(autoHide = true)

        foregroundState.setInForeground(false)

        assertTrue(managerLazy.value.balanceHiddenFlow.value)
    }

    @Test
    fun start_focusLostWithAutoHideDisabled_keepsBalanceShown() {
        unlockWithBalanceShown(autoHide = false)

        foregroundState.setInForeground(false)

        assertFalse(managerLazy.value.balanceHiddenFlow.value)
    }

    @Test
    fun start_focusLostBeforeFirstUnlock_doesNotBuildManager() {
        DesktopBalanceAutoHide(foregroundState, vault, managerLazy).start(scope)

        foregroundState.setInForeground(false)

        assertFalse(managerLazy.isInitialized())
    }

    private fun unlockWithBalanceShown(autoHide: Boolean) {
        vaultState.value = DesktopVaultState.Unlocked(mockk<DesktopStorageSession>(relaxed = true))
        managerLazy.value.setBalanceAutoHidden(autoHide)
        managerLazy.value.setBalanceHidden(false)
        DesktopBalanceAutoHide(foregroundState, vault, managerLazy).start(scope)
    }
}
