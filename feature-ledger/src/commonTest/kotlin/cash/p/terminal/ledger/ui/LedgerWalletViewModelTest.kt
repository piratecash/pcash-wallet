package cash.p.terminal.ledger.ui

import cash.p.terminal.ledger.domain.ICreateLedgerWalletUseCase
import cash.p.terminal.ledger.domain.LedgerAppProgress
import cash.p.terminal.ledger.domain.LedgerConnector
import cash.p.terminal.ledger.domain.LedgerNoSupportedAppsException
import cash.p.terminal.ledger.domain.LedgerNotFoundException
import cash.p.terminal.ledger.domain.LedgerRejectedException
import cash.p.terminal.ledger.domain.LedgerWalletCreation
import cash.p.terminal.ledgerkit.device.LedgerModel
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerWalletViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val connector = FakeConnector()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun connect_openingApps_showsProgressThenSucceeds() = runTest(dispatcher) {
        val finish = CompletableDeferred<LedgerWalletCreation>()
        val viewModel = viewModel { onOpeningApp ->
            onOpeningApp(LedgerAppProgress(1, 2, "Bitcoin"))
            finish.await()
        }

        viewModel.connect()

        assertTrue(viewModel.uiState.loading)
        assertEquals(LedgerAppProgress(1, 2, "Bitcoin"), viewModel.uiState.progress)

        finish.complete(LedgerWalletCreation(created = true, skippedApps = emptyList()))

        assertFalse(viewModel.uiState.loading)
        assertNull(viewModel.uiState.progress)
        assertTrue(viewModel.uiState.success)
    }

    @Test
    fun connect_someAppsSkipped_showsSkippedAppsBeforeSuccess() = runTest(dispatcher) {
        val viewModel = viewModel { LedgerWalletCreation(created = true, skippedApps = listOf("Monero")) }

        viewModel.connect()

        assertEquals(listOf("Monero"), viewModel.uiState.skippedApps)
        assertFalse(viewModel.uiState.success)

        viewModel.dismissSkippedApps()

        assertEquals(emptyList(), viewModel.uiState.skippedApps)
        assertTrue(viewModel.uiState.success)
    }

    @Test
    fun connect_everyAppSkipped_closesDialogWithoutSuccess() = runTest(dispatcher) {
        val viewModel = viewModel { LedgerWalletCreation(created = false, skippedApps = listOf("Bitcoin")) }

        viewModel.connect()
        viewModel.dismissSkippedApps()

        assertFalse(viewModel.uiState.success)
    }

    @Test
    fun connect_noSupportedApps_showsNoSupportedAppsError() = runTest(dispatcher) {
        val viewModel = viewModel { LedgerWalletCreation(created = false, skippedApps = emptyList()) }

        viewModel.connect()

        assertIs<LedgerNoSupportedAppsException>(viewModel.uiState.error)
        assertFalse(viewModel.uiState.success)
    }

    @Test
    fun connect_deviceMissing_showsErrorAndAllowsRetry() = runTest(dispatcher) {
        var attempts = 0
        val viewModel = viewModel {
            attempts++
            throw LedgerNotFoundException()
        }

        viewModel.connect()

        assertIs<LedgerNotFoundException>(viewModel.uiState.error)
        assertFalse(viewModel.uiState.loading)

        viewModel.connect()

        assertEquals(2, attempts)
    }

    @Test
    fun awaitingPermission_connectorWaitsForUsbAccess_reflectedInState() = runTest(dispatcher) {
        val viewModel = viewModel { LedgerWalletCreation(created = true, skippedApps = emptyList()) }

        connector.awaitingPermission.value = true
        assertTrue(viewModel.uiState.awaitingPermission)

        connector.awaitingPermission.value = false
        assertFalse(viewModel.uiState.awaitingPermission)
    }

    @Test
    fun connect_rejectedOnDevice_showsRejection() = runTest(dispatcher) {
        val viewModel = viewModel { throw LedgerRejectedException() }

        viewModel.connect()

        assertIs<LedgerRejectedException>(viewModel.uiState.error)
        assertFalse(viewModel.uiState.loading)
        assertFalse(viewModel.uiState.success)
    }

    @Test
    fun connect_exportingViewKey_showsInstructionUntilDone() = runTest(dispatcher) {
        val finish = CompletableDeferred<LedgerWalletCreation>()
        val viewModel = LedgerWalletViewModel(
            accountName = "Ledger",
            createLedgerWallet = object : ICreateLedgerWalletUseCase {
                override suspend fun invoke(
                    accountName: String,
                    onOpeningApp: (LedgerAppProgress) -> Unit,
                    onExportingViewKey: () -> Unit,
                ): LedgerWalletCreation {
                    onOpeningApp(LedgerAppProgress(2, 2, "Monero"))
                    onExportingViewKey()
                    return finish.await()
                }
            },
            connector = connector,
        )

        viewModel.connect()

        assertTrue(viewModel.uiState.exportingViewKey)
        assertNull(viewModel.uiState.progress)

        finish.complete(LedgerWalletCreation(created = true, skippedApps = emptyList()))

        assertFalse(viewModel.uiState.exportingViewKey)
        assertTrue(viewModel.uiState.success)
    }

    private fun viewModel(create: suspend ((LedgerAppProgress) -> Unit) -> LedgerWalletCreation) =
        LedgerWalletViewModel(
            accountName = "Ledger",
            createLedgerWallet = object : ICreateLedgerWalletUseCase {
                override suspend fun invoke(
                    accountName: String,
                    onOpeningApp: (LedgerAppProgress) -> Unit,
                    onExportingViewKey: () -> Unit,
                ): LedgerWalletCreation = create(onOpeningApp)
            },
            connector = connector,
        )

    private class FakeConnector : LedgerConnector {
        override val awaitingPermission = MutableStateFlow(false)
        override val isDevicePresent = true
        override val model = LedgerModel.NanoX
        override suspend fun open(): LedgerTransport = error("Not used")
    }
}
