package cash.p.terminal.modules.premium.smsnotification

import cash.p.terminal.core.ISendZcashAdapter
import cash.p.terminal.core.adapters.zcash.isTransparentZcashAddress
import cash.p.terminal.core.adapters.zcash.isValidZcashAddress
import cash.p.terminal.feature.logging.domain.usecase.GetZecWalletsUseCase
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.modules.offline.OperationAvailability
import cash.p.terminal.modules.pin.SendZecOnDuressUseCase
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.core.ISmsNotificationSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SendSmsNotificationViewModelTest {

    private val smsNotificationSettings = mockk<ISmsNotificationSettings>(relaxed = true)
    private val getZecWalletsUseCase = mockk<GetZecWalletsUseCase>()
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val userManager = mockk<UserManager>()
    private val sendZecOnDuressUseCase = mockk<SendZecOnDuressUseCase>()
    private val offlineOperationGate = mockk<OfflineOperationGate>()

    private val account = Account(
        id = ACCOUNT_ID,
        name = "Wallet 1",
        type = AccountType.Mnemonic(words = List(12) { "word$it" }, passphrase = ""),
        origin = AccountOrigin.Created,
        level = LEVEL,
        isBackedUp = true,
        isFileBackedUp = true
    )
    private val wallet = mockk<Wallet>(relaxed = true) {
        every { account } returns this@SendSmsNotificationViewModelTest.account
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { userManager.getUserLevel() } returns LEVEL
        coEvery { getZecWalletsUseCase.getZecWallets() } returns listOf(wallet)
        every { adapterManager.getAdapterForWallet<ISendZcashAdapter>(any()) } returns null
        every { offlineOperationGate.availability(any(), any()) } answers {
            OfflineOperationGate.availability(enabled = secondArg(), offline = false)
        }
        mockkStatic(ZCASH_ADDRESS_FILE)
        every { isValidZcashAddress(any()) } returns true
        every { isTransparentZcashAddress(any()) } returns false
    }

    @After
    fun tearDown() {
        unmockkStatic(ZCASH_ADDRESS_FILE)
        Dispatchers.resetMain()
    }

    @Test
    fun init_flagOn_isEnabledTrue() {
        stubSavedSettings(enabled = true)

        assertTrue(createViewModel().uiState.isEnabled)
    }

    @Test
    fun init_flagOff_isEnabledFalse() {
        stubSavedSettings(enabled = false)

        assertFalse(createViewModel().uiState.isEnabled)
    }

    @Test
    fun onEnabledChange_off_persistsFlagAndKeepsSavedSettings() {
        stubSavedSettings(enabled = true)
        val viewModel = createViewModel()

        viewModel.onEnabledChange(false)

        assertFalse(viewModel.uiState.isEnabled)
        verify { smsNotificationSettings.setSmsNotificationEnabled(LEVEL, false) }
        verify(exactly = 0) { smsNotificationSettings.setSmsNotificationAccountId(any(), any()) }
        verify(exactly = 0) { smsNotificationSettings.setSmsNotificationAddress(any(), any()) }
        verify(exactly = 0) { smsNotificationSettings.setSmsNotificationMemo(any(), any()) }
    }

    @Test
    fun onEnabledChange_offWhileTestSyncing_cancelsTestBeforeSend() = runBlocking {
        stubSavedSettings(enabled = true)
        val sendStarted = CompletableDeferred<Unit>()
        val sendCancelled = CompletableDeferred<Unit>()
        coEvery { sendZecOnDuressUseCase.sendTestTransaction(any(), any(), any()) } coAnswers {
            sendStarted.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                sendCancelled.complete(Unit)
            }
        }
        val viewModel = createViewModel()
        viewModel.onTestSmsClick()
        withTimeout(TIMEOUT_MS) { sendStarted.await() }
        assertEquals(TestResult.Syncing, viewModel.uiState.testResult)

        viewModel.onEnabledChange(false)

        withTimeout(TIMEOUT_MS) { sendCancelled.await() }
        assertNull(viewModel.uiState.testResult)
    }

    @Test
    fun onEnabledChange_off_disablesSaveAndTest() {
        stubSavedSettings(enabled = true)
        val viewModel = createViewModel()
        viewModel.onAddressChanged(ADDRESS)
        assertTrue(viewModel.uiState.isSaveEnabled)

        viewModel.onEnabledChange(false)

        assertFalse(viewModel.uiState.isSaveEnabled)
        assertEquals(OperationAvailability.Unavailable, viewModel.uiState.testAvailability)
    }

    @Test
    fun onSaveClick_afterSwitchedOff_doesNotSave() {
        stubSavedSettings(enabled = true)
        val viewModel = createViewModel()
        viewModel.onEnabledChange(false)
        viewModel.onAddressChanged("$ADDRESS-new")

        viewModel.onSaveClick()

        verify(exactly = 0) { smsNotificationSettings.setSmsNotificationAddress(any(), any()) }
        verify(exactly = 0) { smsNotificationSettings.setSmsNotificationEnabled(LEVEL, true) }
    }

    @Test
    fun onTestSmsClick_afterSwitchedOff_doesNotSend() {
        stubSavedSettings(enabled = true)
        val viewModel = createViewModel()
        viewModel.onEnabledChange(false)

        viewModel.onTestSmsClick()

        coVerify(exactly = 0) { sendZecOnDuressUseCase.sendTestTransaction(any(), any(), any()) }
    }

    private fun stubSavedSettings(enabled: Boolean) {
        every { smsNotificationSettings.getSmsNotificationEnabled(LEVEL) } returns enabled
        every { smsNotificationSettings.getSmsNotificationAccountId(LEVEL) } returns ACCOUNT_ID
        every { smsNotificationSettings.getSmsNotificationAddress(LEVEL) } returns ADDRESS
        every { smsNotificationSettings.getSmsNotificationMemo(LEVEL) } returns "memo"
    }

    private fun createViewModel() = SendSmsNotificationViewModel(
        smsNotificationSettings = smsNotificationSettings,
        getZecWalletsUseCase = getZecWalletsUseCase,
        adapterManager = adapterManager,
        userManager = userManager,
        sendZecOnDuressUseCase = sendZecOnDuressUseCase,
        offlineOperationGate = offlineOperationGate,
    )

    private companion object {
        const val LEVEL = 0
        const val ACCOUNT_ID = "account-1"
        const val ADDRESS = "zs1recipient"
        const val TIMEOUT_MS = 5_000L
        const val ZCASH_ADDRESS_FILE = "cash.p.terminal.core.adapters.zcash.ZcashAddressKt"
    }
}
