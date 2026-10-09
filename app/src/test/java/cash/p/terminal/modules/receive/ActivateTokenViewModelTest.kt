package cash.p.terminal.modules.receive

import android.database.sqlite.SQLiteException
import cash.p.terminal.core.App
import cash.p.terminal.core.adapters.stellar.StellarAssetAdapter
import cash.p.terminal.modules.offline.OfflineOperationBlockedException
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.stellarkit.EnablingAssetError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class ActivateTokenViewModelTest {

    private val wallet = mockk<Wallet>(relaxed = true)
    private val adapter = mockk<StellarAssetAdapter>(relaxed = true) {
        every { activationFee } returns BigDecimal.ONE
        coEvery { isTrustlineEstablished() } returns false
    }
    private val adapterManager = mockk<IAdapterManager> {
        every { getAdapterForWallet<StellarAssetAdapter>(wallet) } returns adapter
    }
    private val xRateService = mockk<XRateService> {
        every { getRate(any()) } returns null
    }
    private val blockedFlow = MutableStateFlow(false)
    private val offlineOperationGate = mockk<OfflineOperationGate> {
        every { isBlocked(wallet) } answers { blockedFlow.value }
        every { blockedFlow(wallet) } returns blockedFlow
        every { requireOnline(wallet) } returns Unit
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(App)
        every { App.currencyManager } returns mockk<CurrencyManager> {
            every { baseCurrency } returns Currency("USD", "$", 2, 0)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun init_validateActivationThrowsDatabaseError_showsNullAdapterError() {
        coEvery { adapter.validateActivation() } throws SQLiteException("Connection pool is closed")

        val uiState = createViewModel().awaitErrorState()

        assertFalse(uiState.activateEnabled)
        assertTrue(uiState.error is ActivateTokenError.NullAdapter)
    }

    @Test
    fun init_insufficientBalance_showsInsufficientBalanceError() {
        coEvery { adapter.validateActivation() } throws EnablingAssetError.InsufficientBalance()

        val uiState = createViewModel().awaitErrorState()

        assertFalse(uiState.activateEnabled)
        assertTrue(uiState.error is ActivateTokenError.InsufficientBalance)
    }

    @Test
    fun init_networkOffline_activateDisabledWithOfflineError() {
        blockedFlow.value = true

        val uiState = createViewModel().awaitErrorState()

        assertFalse(uiState.activateEnabled)
        assertTrue(uiState.error is ActivateTokenError.Offline)
    }

    @Test
    fun blockedFlow_offlineAfterInit_disablesActivateWithOfflineError() {
        val viewModel = createViewModel()
        assertTrue(viewModel.awaitErrorState().activateEnabled)

        blockedFlow.value = true
        assertFalse(viewModel.uiState.activateEnabled)
        assertTrue(viewModel.uiState.error is ActivateTokenError.Offline)

        blockedFlow.value = false
        assertTrue(viewModel.uiState.activateEnabled)
    }

    @Test
    fun activate_networkOffline_throwsAndDoesNotCallAdapter() = runBlocking {
        every { offlineOperationGate.requireOnline(wallet) } throws OfflineOperationBlockedException("Stellar")
        val viewModel = createViewModel()

        try {
            withTimeout(STATE_TIMEOUT_MS) { viewModel.activate() }
            fail("OfflineOperationBlockedException expected")
        } catch (_: OfflineOperationBlockedException) {
        }

        coVerify(exactly = 0) { adapter.activate() }
    }

    private fun createViewModel() = ActivateTokenViewModel(
        wallet = wallet,
        feeToken = mockk(relaxed = true),
        adapterManager = adapterManager,
        xRateService = xRateService,
        offlineOperationGate = offlineOperationGate,
    )

    // Validation runs on Dispatchers.Default and publishes the fee last, so the fee marks a settled state.
    private fun ActivateTokenViewModel.awaitErrorState(): ActivateTokenUiState = runBlocking {
        withTimeout(STATE_TIMEOUT_MS) {
            while (uiState.feeCoinValue == null) delay(POLL_INTERVAL_MS)
        }
        uiState
    }

    private companion object {
        const val STATE_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 10L
    }
}
