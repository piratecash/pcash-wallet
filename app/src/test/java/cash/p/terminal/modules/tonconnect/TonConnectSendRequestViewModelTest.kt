package cash.p.terminal.modules.tonconnect

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.TonConnectManager
import cash.p.terminal.core.managers.TonKitManager
import cash.p.terminal.core.managers.TonKitWrapper
import cash.p.terminal.core.managers.toTonWalletFullAccess
import cash.p.terminal.core.storage.HardwarePublicKeyStorage
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.entities.TonWalletVersion
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.wallet.data.core.entity.RawMessageEntity
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import io.horizontalsystems.sqlcipher.room.DatabaseKeyMismatchException
import io.horizontalsystems.sqlcipher.room.InsufficientDatabaseMigrationSpaceException
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.core.TonWallet
import io.horizontalsystems.tonkit.core.TransactionSigner
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.SignTransaction
import io.horizontalsystems.tonkit.tonconnect.TonConnectKit
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TonConnectSendRequestViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val account = Account(
        id = ACCOUNT_ID,
        name = "Ton",
        type = AccountType.Mnemonic(List(12) { "word$it" }, ""),
        origin = AccountOrigin.Created,
        level = 0,
    )
    private val accountManager = mockk<IAccountManager>(relaxed = true) {
        every { account(ACCOUNT_ID) } returns account
        every { activeAccount } returns account
    }
    private val transactionSigner = mockk<TransactionSigner>()
    private val tonConnectKit = mockk<TonConnectKit>(relaxed = true)
    private val tonConnectManager = mockk<TonConnectManager>(relaxed = true) {
        every { transactionSigner } returns this@TonConnectSendRequestViewModelTest.transactionSigner
        coEvery { kit() } returns tonConnectKit
        coEvery { networkTimeSeconds() } returns null
    }
    private val tonKit = mockk<TonKit>(relaxed = true) { every { account } returns null }
    private val tonKitManager = mockk<TonKitManager>()
    private val coinManager = mockk<ICoinManager>()
    private var requestValidUntil = deviceNowSeconds() + VALID_FOR_SECONDS
    private var validUntilReads = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        stopKoin()
        startKoin {
            modules(module {
                single { mockk<HardwarePublicKeyStorage>(relaxed = true) }
                single { mockk<OfflineOperationGate> { every { isBlocked(any<Account>(), any()) } returns false } }
                single { coinManager }
            })
        }
        mockkStatic("cash.p.terminal.core.managers.TonKitManagerKt")
        every { any<Account>().toTonWalletFullAccess(any(), any(), any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun prepareEnv_migrationLacksSpace_showsKitUnavailableWithoutConfirm() = testScope.runTest {
        coEvery { tonKitManager.getNonActiveTonKitWrapper(account, TonWalletVersion.V4R2, any()) } throws
            InsufficientDatabaseMigrationSpaceException(requiredBytes = 2, availableBytes = 1)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertIs<TonConnectSendRequestError.KitUnavailable>(viewModel.uiState.error)
        assertFalse(viewModel.uiState.confirmEnabled)
    }

    @Test
    fun prepareEnv_databaseKeyMismatch_showsKitUnavailableWithoutConfirm() = testScope.runTest {
        coEvery { tonKitManager.getNonActiveTonKitWrapper(account, TonWalletVersion.V4R2, any()) } throws
            DatabaseKeyMismatchException("account-id-MainNet", IllegalStateException("wrong key"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertIs<TonConnectSendRequestError.KitUnavailable>(viewModel.uiState.error)
        assertFalse(viewModel.uiState.confirmEnabled)
    }

    @Test
    fun reject_tonConnectKitUnavailable_doesNotCrash() = testScope.runTest {
        coEvery { tonKitManager.getNonActiveTonKitWrapper(account, TonWalletVersion.V4R2, any()) } throws
            InsufficientDatabaseMigrationSpaceException(requiredBytes = 2, availableBytes = 1)
        val viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { tonConnectManager.kit() } throws IllegalStateException("TON Connect unavailable")

        viewModel.reject()
        advanceUntilIdle()

        coVerify(exactly = 1) { tonConnectManager.kit() }
    }

    @Test
    fun confirm_tonConnectKitUnavailable_doesNotCrashOrReportSuccess() = testScope.runTest {
        coEvery { tonKitManager.getNonActiveTonKitWrapper(account, TonWalletVersion.V4R2, any()) } returns
            TonKitWrapper(tonKit, mockk(relaxed = true), TonWalletVersion.V4R2)
        // Stops prepareEnv before the coin lookup; confirm stays available.
        coEvery { transactionSigner.getDetails(any(), any<TonWallet.FullAccess>()) } throws
            IllegalStateException("no details")
        coEvery { transactionSigner.sign(any(), any<TonWallet.FullAccess>()) } returns "boc"
        val viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { tonConnectManager.kit() } throws IllegalStateException("TON Connect unavailable")

        viewModel.confirm()
        advanceUntilIdle()

        coVerify(exactly = 1) { tonKit.send("boc") }
        assertFalse(viewModel.uiState.success)
    }

    @Test
    fun confirm_w5Session_signsAndSendsWithW5WalletAndKit() = testScope.runTest {
        val w5Wallet = mockk<TonWallet.FullAccess>(relaxed = true)
        every { account.toTonWalletFullAccess(TonWalletVersion.W5, any(), any()) } returns w5Wallet
        coEvery { tonKitManager.getNonActiveTonKitWrapper(account, TonWalletVersion.W5, any()) } returns
            TonKitWrapper(tonKit, w5Wallet, TonWalletVersion.W5)
        // Stops prepareEnv before the coin lookup; confirm stays available.
        coEvery { transactionSigner.getDetails(any(), any<TonWallet.FullAccess>()) } throws
            IllegalStateException("no details")
        coEvery { transactionSigner.sign(any(), w5Wallet) } returns "boc"
        val viewModel = createViewModel(walletId = "$ACCOUNT_ID-w5")
        advanceUntilIdle()

        viewModel.confirm()
        advanceUntilIdle()

        coVerify(exactly = 1) { transactionSigner.sign(any(), w5Wallet) }
        coVerify(exactly = 1) { tonKit.send("boc") }
    }

    @Test
    fun prepareEnv_networkTimePastDeadlineDeviceClockNot_expiresAndRespondsBadRequest() = testScope.runTest {
        coEvery { tonConnectManager.networkTimeSeconds() } returns requestValidUntil + 1

        val viewModel = createViewModel()
        runCurrent()

        assertIs<TonConnectSendRequestError.Expired>(viewModel.uiState.error)
        assertFalse(viewModel.uiState.confirmEnabled)
        coVerify(exactly = 1) { tonConnectKit.badRequest(any()) }
    }

    @Test
    fun prepareEnv_deviceClockPastDeadlineNetworkTimeNot_staysConfirmable() = testScope.runTest {
        stubPreparableRequest()
        requestValidUntil = deviceNowSeconds() - VALID_FOR_SECONDS
        coEvery { tonConnectManager.networkTimeSeconds() } returns requestValidUntil - 1

        val viewModel = createViewModel()
        runCurrent()

        assertNull(viewModel.uiState.error)
        assertTrue(viewModel.uiState.confirmEnabled)
        coVerify(exactly = 0) { tonConnectKit.badRequest(any()) }
    }

    @Test
    fun prepareEnv_networkTimeUnavailable_usesDeviceClock() = testScope.runTest {
        requestValidUntil = deviceNowSeconds() - VALID_FOR_SECONDS

        val viewModel = createViewModel()
        runCurrent()

        assertIs<TonConnectSendRequestError.Expired>(viewModel.uiState.error)
        coVerify(exactly = 1) { tonConnectKit.badRequest(any()) }
    }

    @Test
    fun prepareEnv_noDeadline_staysConfirmable() = testScope.runTest {
        stubPreparableRequest()
        requestValidUntil = 0

        val viewModel = createViewModel()
        runCurrent()
        val readsAfterPreparation = validUntilReads
        advanceTimeBy(TICKS_AFTER_PREPARATION)
        runCurrent()

        assertEquals(readsAfterPreparation, validUntilReads, "no timer may poll a request without a deadline")
        assertNull(viewModel.uiState.error)
        assertTrue(viewModel.uiState.confirmEnabled)
        coVerify(exactly = 0) { tonConnectKit.badRequest(any()) }
    }

    @Test
    fun watchExpiry_deadlinePassesOnOpenScreen_disablesConfirmAndRespondsOnce() = testScope.runTest {
        stubPreparableRequest()
        val viewModel = createViewModel()
        runCurrent()
        assertTrue(viewModel.uiState.confirmEnabled)

        passDeadline()

        assertIs<TonConnectSendRequestError.Expired>(viewModel.uiState.error)
        assertFalse(viewModel.uiState.confirmEnabled)
        coVerify(exactly = 1) { tonConnectKit.badRequest(any()) }
    }

    @Test
    fun confirm_deadlinePassedBetweenChecks_throwsExpiredWithoutSigningAndRespondsOnce() = testScope.runTest {
        stubPreparableRequest()
        val viewModel = createViewModel()
        runCurrent()
        requestValidUntil = deviceNowSeconds() - VALID_FOR_SECONDS

        assertFailsWith<TonConnectSendRequestError.Expired> { viewModel.confirm() }
        passDeadline()

        assertIs<TonConnectSendRequestError.Expired>(viewModel.uiState.error)
        coVerify(exactly = 0) { transactionSigner.sign(any(), any<TonWallet.FullAccess>()) }
        coVerify(exactly = 1) { tonConnectKit.badRequest(any()) }
    }

    @Test
    fun confirm_beforeDeadline_laterDeadlineDoesNotReportBadRequest() = testScope.runTest {
        stubPreparableRequest()
        coEvery { transactionSigner.sign(any(), any<TonWallet.FullAccess>()) } returns "boc"
        val viewModel = createViewModel()
        runCurrent()

        viewModel.confirm()
        runCurrent()
        passDeadline()

        coVerify(exactly = 1) { tonKit.send("boc") }
        assertFalse(viewModel.uiState.confirmEnabled)
        coVerify(exactly = 0) { tonConnectKit.badRequest(any()) }
    }

    @Test
    fun confirm_signingFailedThenDeadlinePasses_expiresAndRespondsBadRequest() = testScope.runTest {
        stubPreparableRequest()
        coEvery { transactionSigner.sign(any(), any<TonWallet.FullAccess>()) } throws
            IllegalStateException("signing cancelled")
        val viewModel = createViewModel()
        runCurrent()

        viewModel.confirm()
        runCurrent()
        assertTrue(viewModel.uiState.confirmEnabled)
        passDeadline()

        assertFalse(viewModel.uiState.confirmEnabled)
        assertIs<TonConnectSendRequestError.Expired>(viewModel.uiState.error)
        coVerify(exactly = 1) { tonConnectKit.badRequest(any()) }
    }

    @Test
    fun confirm_signingFailsAfterDeadline_expiresAndRespondsBadRequest() = testScope.runTest {
        stubPreparableRequest()
        val signing = CompletableDeferred<String>()
        coEvery { transactionSigner.sign(any(), any<TonWallet.FullAccess>()) } coAnswers { signing.await() }
        val viewModel = createViewModel()
        runCurrent()
        viewModel.confirm()
        runCurrent()
        passDeadline()
        coVerify(exactly = 0) { tonConnectKit.badRequest(any()) }

        signing.completeExceptionally(IllegalStateException("signing cancelled"))
        runCurrent()

        assertIs<TonConnectSendRequestError.Expired>(viewModel.uiState.error)
        coVerify(exactly = 1) { tonConnectKit.badRequest(any()) }
    }

    @Test
    fun confirm_sendFailsAfterSigning_deadlineDoesNotReportBadRequest() = testScope.runTest {
        stubPreparableRequest()
        coEvery { transactionSigner.sign(any(), any<TonWallet.FullAccess>()) } returns "boc"
        coEvery { tonKit.send("boc") } throws IllegalStateException("network lost after broadcast")
        val viewModel = createViewModel()
        runCurrent()

        viewModel.confirm()
        runCurrent()
        passDeadline()

        assertFalse(viewModel.uiState.confirmEnabled)
        coVerify(exactly = 0) { tonConnectKit.badRequest(any()) }
    }

    @Test
    fun confirm_oneOfTwoSigningAttemptsFails_deadlineDoesNotReportBadRequest() = testScope.runTest {
        stubPreparableRequest()
        val first = CompletableDeferred<String>()
        val second = CompletableDeferred<String>()
        val attempts = ArrayDeque(listOf(first, second))
        coEvery { transactionSigner.sign(any(), any<TonWallet.FullAccess>()) } coAnswers {
            attempts.removeFirst().await()
        }
        val viewModel = createViewModel()
        runCurrent()
        viewModel.confirm()
        runCurrent()
        viewModel.confirm()
        runCurrent()

        first.completeExceptionally(IllegalStateException("signing cancelled"))
        runCurrent()
        passDeadline()

        coVerify(exactly = 0) { tonConnectKit.badRequest(any()) }
    }

    @Test
    fun confirm_secondCallAfterDeadlineWhileFirstStarted_doesNotReportBadRequest() = testScope.runTest {
        stubPreparableRequest()
        val signing = CompletableDeferred<String>()
        coEvery { transactionSigner.sign(any(), any<TonWallet.FullAccess>()) } coAnswers { signing.await() }
        val viewModel = createViewModel()
        runCurrent()
        viewModel.confirm()
        runCurrent()
        passDeadline()

        viewModel.confirm()
        runCurrent()

        coVerify(exactly = 1) { transactionSigner.sign(any(), any<TonWallet.FullAccess>()) }
        coVerify(exactly = 0) { tonConnectKit.badRequest(any()) }
    }

    @Test
    fun reject_beforeDeadline_laterDeadlineDoesNotReportBadRequest() = testScope.runTest {
        stubPreparableRequest()
        val viewModel = createViewModel()
        runCurrent()

        viewModel.reject()
        runCurrent()
        passDeadline()

        coVerify(exactly = 1) { tonConnectKit.reject(any()) }
        coVerify(exactly = 0) { tonConnectKit.badRequest(any()) }
    }

    @Test
    fun reject_whileFetchingNetworkTime_expiredOnArrivalDoesNotReportBadRequest() = testScope.runTest {
        val networkTime = CompletableDeferred<Long?>()
        coEvery { tonConnectManager.networkTimeSeconds() } coAnswers { networkTime.await() }
        createViewModel().also { runCurrent() }.reject()
        runCurrent()

        networkTime.complete(requestValidUntil + 1)
        runCurrent()

        coVerify(exactly = 1) { tonConnectKit.reject(any()) }
        coVerify(exactly = 0) { tonConnectKit.badRequest(any()) }
    }

    @Test
    fun reject_duringPreparation_deadlineDoesNotReportBadRequest() = testScope.runTest {
        stubPreparableRequest()
        val details = CompletableDeferred<Event>()
        coEvery { transactionSigner.getDetails(any(), any<TonWallet.FullAccess>()) } coAnswers { details.await() }
        val viewModel = createViewModel()
        runCurrent()

        viewModel.reject()
        runCurrent()
        details.complete(mockk(relaxed = true))
        runCurrent()
        passDeadline()

        assertFalse(viewModel.uiState.confirmEnabled)
        coVerify(exactly = 1) { tonConnectKit.reject(any()) }
        coVerify(exactly = 0) { tonConnectKit.badRequest(any()) }
    }

    /** Lets prepareEnv finish without an error, so Confirm is offered and the timer runs. */
    private fun stubPreparableRequest() {
        coEvery { tonKitManager.getNonActiveTonKitWrapper(account, TonWalletVersion.V4R2, any()) } returns
            TonKitWrapper(tonKit, mockk(relaxed = true), TonWalletVersion.V4R2)
        coEvery { transactionSigner.getDetails(any(), any<TonWallet.FullAccess>()) } returns mockk(relaxed = true)
        val tonToken = mockk<Token>(relaxed = true) { every { type } returns TokenType.Native }
        every { coinManager.getToken(any()) } returns tonToken
    }

    private fun TestScope.passDeadline() {
        requestValidUntil = deviceNowSeconds() - VALID_FOR_SECONDS
        advanceTimeBy(TICKS_AFTER_PREPARATION)
        runCurrent()
    }

    private fun deviceNowSeconds() = System.currentTimeMillis() / 1000

    private fun createViewModel(walletId: String = ACCOUNT_ID) = TonConnectSendRequestViewModel(
        signTransaction = SignTransaction(
            sendRequest(walletId),
            mockk(relaxed = true) { every { accountId } returns "" },
        ),
        accountManager = accountManager,
        tonConnectManager = tonConnectManager,
        tonKitManager = tonKitManager,
        dispatcherProvider = TestDispatcherProvider(dispatcher, testScope),
    )

    private fun sendRequest(walletId: String) = mockk<SendRequestEntity> {
        every { network } returns TonNetwork.MAINNET
        every { fromAccountId } returns null
        every { validUntil } answers {
            validUntilReads++
            requestValidUntil
        }
        every { messages } returns listOf(RawMessageEntity(RECIPIENT, 1, null, ""))
        every { dAppId } returns "$walletId:https://dapp.example"
    }

    private companion object {
        const val ACCOUNT_ID = "account-id"
        const val RECIPIENT = "UQD5mxRgCuRNLxKxeOjG6r14iSroLF5FtomPnet-sgP5xNJb"
        const val VALID_FOR_SECONDS = 600
        val TICKS_AFTER_PREPARATION = 3.seconds
    }
}
