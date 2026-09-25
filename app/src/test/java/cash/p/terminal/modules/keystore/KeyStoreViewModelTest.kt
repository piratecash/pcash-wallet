package cash.p.terminal.modules.keystore

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.managers.BeamDeletionState
import cash.p.terminal.core.managers.BeamAccountDeletionPreflight
import cash.p.terminal.core.managers.BeamDatabaseKeyProvider
import cash.p.terminal.core.managers.BeamStorageLocator
import cash.p.terminal.wallet.AccountDeletionBlockedException
import cash.p.terminal.wallet.AccountDeletionPreflight
import cash.p.terminal.wallet.IEnabledWalletStorage
import cash.p.terminal.wallet.entities.EnabledWallet
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IKeyStoreManager
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class KeyStoreViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val manager = mockk<IKeyStoreManager>(relaxed = true)
    private val storage = mockk<ILocalStorage>(relaxed = true)
    private val preflight = mockk<AccountDeletionPreflight>(relaxed = true)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun init_eachBeamMarkerInAutomaticModes_requiresRecoveryWithoutResetOrKeyRemoval() = runTest(dispatcher) {
        for (mode in automaticModes) {
            for (marker in Marker.entries) {
                val model = KeyStoreViewModel(manager, storage, beamPreflight(marker), mode)
                assertRecoveryOnly(model)
            }
        }
        verify { manager wasNot Called }
        verify(exactly = 0) { storage.isSystemPinRequired = any() }
    }

    @Test
    fun init_noBeamInAutomaticModes_preservesResetAndWarningBehavior() = runTest(dispatcher) {
        for (mode in automaticModes) {
            val model = KeyStoreViewModel(manager, storage, beamPreflight(null), mode)
            assertFalse(model.recoveryRequired)
            verify(exactly = 1) { manager.resetApp(mode.name) }
            when (mode) {
                KeyStoreModule.ModeType.InvalidKey -> {
                    assertTrue(model.showInvalidKeyWarning)
                    model.onCloseInvalidKeyWarning()
                    assertTrue(model.openMainModule)
                    verify(exactly = 1) { manager.removeKey() }
                }
                else -> {
                    assertTrue(model.showSystemLockWarning)
                    model.changeSystemPinRequired(false)
                    assertTrue(model.showTermsDialog)
                    model.onTermsAccepted()
                    assertFalse(model.isSystemPinRequired)
                }
            }
        }
    }

    @Test
    fun init_cleanerBarrierBlocks_showsRecoveryInsteadOfCrashing() = runTest(dispatcher) {
        every { manager.resetApp(any()) } throws AccountDeletionBlockedException()
        for (mode in automaticModes) assertRecoveryOnly(viewModel(mode))
        verify(exactly = 0) { manager.removeKey() }
    }

    @Test
    fun onCloseInvalidKeyWarning_markerAppearsAfterReset_keepsKeyAndBlocksNavigation() = runTest(dispatcher) {
        val model = viewModel(KeyStoreModule.ModeType.InvalidKey)
        coEvery { preflight.ensureCanReset() } throws AccountDeletionBlockedException()
        model.onCloseInvalidKeyWarning()
        assertRecoveryOnly(model)
        verify(exactly = 0) { manager.removeKey() }
    }

    @Test
    fun onCloseInvalidKeyWarning_allowed_checksBeforeResetAndKeyRemoval() = runTest(dispatcher) {
        viewModel(KeyStoreModule.ModeType.InvalidKey).onCloseInvalidKeyWarning()
        coVerifyOrder {
            preflight.ensureCanReset()
            manager.resetApp("InvalidKey")
            preflight.ensureCanReset()
            manager.removeKey()
        }
    }

    @Test
    fun init_authenticationMode_doesNotPreflightOrReset() = runTest(dispatcher) {
        val model = viewModel(KeyStoreModule.ModeType.UserAuthentication)
        assertTrue(model.showBiometricPrompt)
        model.onAuthenticationSuccess()
        assertTrue(model.openMainModule)
        verify { listOf(manager, preflight) wasNot Called }
    }

    private fun viewModel(mode: KeyStoreModule.ModeType) = KeyStoreViewModel(manager, storage, preflight, mode)

    private fun assertRecoveryOnly(model: KeyStoreViewModel) {
        assertTrue(model.recoveryRequired)
        model.onCloseInvalidKeyWarning()
        model.changeSystemPinRequired(false)
        model.onTermsAccepted()
        model.onAuthenticationSuccess()
        assertFalse(model.openMainModule)
        assertFalse(model.showInvalidKeyWarning)
        assertFalse(model.showSystemLockWarning)
        assertFalse(model.showTermsDialog)
        model.onAuthenticationCanceled()
        assertTrue(model.closeApp)
    }

    private fun beamPreflight(marker: Marker?): AccountDeletionPreflight {
        val locator = mockk<BeamStorageLocator> { every { hasAnyData() } returns (marker == Marker.Database) }
        val keys = mockk<BeamDatabaseKeyProvider> { every { hasAnyKey() } returns (marker == Marker.Key) }
        val wallets = mockk<IEnabledWalletStorage> {
            every { enabledWallets } returns if (marker == Marker.NativeSelection) {
                listOf(EnabledWallet(tokenQueryId = "beam|native", accountId = "account", coinImage = null))
            } else emptyList()
        }
        val dispatchers = mockk<DispatcherProvider> { every { io } returns dispatcher }
        return BeamAccountDeletionPreflight(
            locator, lazy { keys }, wallets, dispatchers,
            mockk<BeamDeletionState>(relaxed = true) {
                every { hasRestoreIntent } returns (marker == Marker.RestoreIntent)
            }, lazy { mockk() }, lazy { mockk() },
        )
    }

    private enum class Marker { Database, Key, NativeSelection, RestoreIntent }

    private val automaticModes = listOf(KeyStoreModule.ModeType.InvalidKey, KeyStoreModule.ModeType.NoSystemLock)
}
