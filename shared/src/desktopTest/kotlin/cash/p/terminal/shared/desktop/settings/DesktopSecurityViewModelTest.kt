package cash.p.terminal.shared.desktop.settings

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.TransactionHiddenManager
import cash.p.terminal.shared.desktop.DesktopWalletSettingsStorage
import cash.p.terminal.shared.desktop.balanceHiddenManager
import cash.p.terminal.wallet.managers.TransactionDisplayLevel
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.DesktopSettingsStore
import io.horizontalsystems.core.IEncryptionManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopSecurityViewModelTest {

    private val directory = Files.createTempDirectory("desktop-security").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val settingsStore = DesktopSettingsStore(File(directory, "settings.preferences_pb"), scope)
    private val dispatcherProvider = TestDispatcherProvider(Dispatchers.Unconfined, scope)
    private val transactionSettings = DesktopWalletSettingsStorage(settingsStore, mockk(relaxed = true))
    private val transactionHiddenManager = TransactionHiddenManager(
        localStorage = transactionSettings,
        encryptionManager = mockk<IEncryptionManager> {
            every { encrypt(any()) } answers { firstArg() }
            every { decrypt(any()) } answers { firstArg() }
        },
        pinStateInfo = mockk(relaxed = true),
        backgroundManager = mockk { every { stateFlow } returns MutableStateFlow(BackgroundManagerState.EnterForeground) },
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        scope.cancel()
        directory.deleteRecursively()
    }

    @Test
    fun onBalanceAutoHideChange_enabled_isOnWhenScreenReopens() {
        viewModel().onBalanceAutoHideChange(true)

        assertTrue(viewModel().uiState.balanceAutoHideEnabled)
    }

    @Test
    fun onBalanceAutoHideChange_disabledAfterEnabled_isOffWhenScreenReopens() {
        viewModel().onBalanceAutoHideChange(true)

        viewModel().onBalanceAutoHideChange(false)

        assertFalse(viewModel().uiState.balanceAutoHideEnabled)
    }

    @Test
    fun onTransferPasswordChange_enabled_isOnWhenScreenReopens() {
        viewModel().onTransferPasswordChange(true)

        assertTrue(viewModel().uiState.transferPasswordEnabled)
    }

    @Test
    fun onTransactionAutoHideChange_enabled_startsWithoutSeparatePasswordAndShowsNothing() {
        transactionHiddenManager.setSeparatePin(SEPARATE_PASSWORD)
        transactionHiddenManager.setTransactionDisplayLevel(TransactionDisplayLevel.LAST_2_TRANSACTIONS)

        viewModel().onTransactionAutoHideChange(true)

        val state = viewModel().uiState
        assertTrue(state.transactionAutoHideEnabled)
        assertFalse(state.separatePasswordExists)
        assertEquals(TransactionDisplayLevel.NOTHING, state.displayLevel)
    }

    @Test
    fun onDeleteSeparatePassword_balanceAutoHideOn_keepsBalanceAndTransactionAutoHide() {
        val viewModel = viewModel()
        viewModel.onBalanceAutoHideChange(true)
        viewModel.onTransactionAutoHideChange(true)
        transactionHiddenManager.setSeparatePin(SEPARATE_PASSWORD)

        viewModel.onDeleteSeparatePassword()

        assertFalse(viewModel.uiState.separatePasswordExists)
        assertTrue(viewModel.uiState.transactionAutoHideEnabled)
        assertTrue(viewModel.uiState.balanceAutoHideEnabled)
    }

    @Test
    fun transactionHiddenFlow_displayLevelChangedOnAnotherPage_refreshesTheState() {
        val viewModel = viewModel()

        transactionHiddenManager.setTransactionDisplayLevel(TransactionDisplayLevel.LAST_4_TRANSACTIONS)
        transactionHiddenManager.setSeparatePin(SEPARATE_PASSWORD)

        assertEquals(TransactionDisplayLevel.LAST_4_TRANSACTIONS, viewModel.uiState.displayLevel)
        assertTrue(viewModel.uiState.separatePasswordExists)
    }

    private fun viewModel() = DesktopSecurityViewModel(
        vault = mockk(),
        settingsStore = settingsStore,
        balanceHiddenManager = balanceHiddenManager(settingsStore, dispatcherProvider),
        transactionSettings = transactionSettings,
        transactionHiddenManager = transactionHiddenManager,
    )

    private companion object {
        const val SEPARATE_PASSWORD = "Separate-secret3"
    }
}
