package cash.p.terminal.shared.desktop.settings

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.shared.desktop.balanceHiddenManager
import io.horizontalsystems.core.DesktopSettingsStore
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSecurityViewModelTest {

    private val directory = Files.createTempDirectory("desktop-security").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val settingsStore = DesktopSettingsStore(File(directory, "settings.preferences_pb"), scope)
    private val dispatcherProvider = TestDispatcherProvider(Dispatchers.Unconfined, scope)

    @AfterTest
    fun tearDown() {
        scope.cancel()
        directory.deleteRecursively()
    }

    @Test
    fun onBalanceAutoHideChange_enabled_isOnWhenScreenReopens() {
        viewModel().onBalanceAutoHideChange(true)

        assertTrue(viewModel().balanceAutoHideEnabled)
    }

    @Test
    fun onBalanceAutoHideChange_disabledAfterEnabled_isOffWhenScreenReopens() {
        viewModel().onBalanceAutoHideChange(true)

        viewModel().onBalanceAutoHideChange(false)

        assertFalse(viewModel().balanceAutoHideEnabled)
    }

    private fun viewModel() = DesktopSecurityViewModel(
        vault = mockk(),
        settingsStore = settingsStore,
        balanceHiddenManager = balanceHiddenManager(settingsStore, dispatcherProvider),
    )
}
