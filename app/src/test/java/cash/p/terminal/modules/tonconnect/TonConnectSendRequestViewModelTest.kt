package cash.p.terminal.modules.tonconnect

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
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.wallet.data.core.entity.RawMessageEntity
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import io.horizontalsystems.sqlcipher.room.DatabaseKeyMismatchException
import io.horizontalsystems.sqlcipher.room.InsufficientDatabaseMigrationSpaceException
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.core.TransactionSigner
import io.horizontalsystems.tonkit.models.SignTransaction
import io.horizontalsystems.tonkit.tonconnect.TonConnectKit
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertFalse
import kotlin.test.assertIs

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
    }
    private val tonKit = mockk<TonKit>(relaxed = true) { every { account } returns null }
    private val tonKitManager = mockk<TonKitManager>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        stopKoin()
        startKoin {
            modules(module {
                single { mockk<HardwarePublicKeyStorage>(relaxed = true) }
                single { mockk<OfflineOperationGate> { every { isBlocked(any<Account>(), any()) } returns false } }
            })
        }
        mockkStatic("cash.p.terminal.core.managers.TonKitManagerKt")
        every { any<Account>().toTonWalletFullAccess(any(), any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun prepareEnv_migrationLacksSpace_showsKitUnavailableWithoutConfirm() = testScope.runTest {
        coEvery { tonKitManager.getNonActiveTonKitWrapper(account, any()) } throws
            InsufficientDatabaseMigrationSpaceException(requiredBytes = 2, availableBytes = 1)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertIs<TonConnectSendRequestError.KitUnavailable>(viewModel.uiState.error)
        assertFalse(viewModel.uiState.confirmEnabled)
    }

    @Test
    fun prepareEnv_databaseKeyMismatch_showsKitUnavailableWithoutConfirm() = testScope.runTest {
        coEvery { tonKitManager.getNonActiveTonKitWrapper(account, any()) } throws
            DatabaseKeyMismatchException("account-id-MainNet", IllegalStateException("wrong key"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertIs<TonConnectSendRequestError.KitUnavailable>(viewModel.uiState.error)
        assertFalse(viewModel.uiState.confirmEnabled)
    }

    @Test
    fun reject_tonConnectKitUnavailable_doesNotCrash() = testScope.runTest {
        coEvery { tonKitManager.getNonActiveTonKitWrapper(account, any()) } throws
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
        coEvery { tonKitManager.getNonActiveTonKitWrapper(account, any()) } returns
            TonKitWrapper(tonKit, mockk(relaxed = true))
        // Stops prepareEnv before the coin lookup; confirm stays available.
        coEvery { transactionSigner.getDetails(any(), any()) } throws IllegalStateException("no details")
        coEvery { transactionSigner.sign(any(), any()) } returns "boc"
        val viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { tonConnectManager.kit() } throws IllegalStateException("TON Connect unavailable")

        viewModel.confirm()
        advanceUntilIdle()

        coVerify(exactly = 1) { tonKit.send("boc") }
        assertFalse(viewModel.uiState.success)
    }

    private fun createViewModel() = TonConnectSendRequestViewModel(
        signTransaction = SignTransaction(sendRequest(), mockk(relaxed = true) { every { accountId } returns "" }),
        accountManager = accountManager,
        tonConnectManager = tonConnectManager,
        tonKitManager = tonKitManager,
        dispatcherProvider = TestDispatcherProvider(dispatcher, testScope),
    )

    private fun sendRequest() = mockk<SendRequestEntity> {
        every { network } returns TonNetwork.MAINNET
        every { fromAccountId } returns null
        every { validUntil } returns System.currentTimeMillis() / 1000 + VALID_FOR_SECONDS
        every { messages } returns listOf(RawMessageEntity(RECIPIENT, 1, null, ""))
        every { dAppId } returns "$ACCOUNT_ID:https://dapp.example"
    }

    private companion object {
        const val ACCOUNT_ID = "account-id"
        const val RECIPIENT = "UQD5mxRgCuRNLxKxeOjG6r14iSroLF5FtomPnet-sgP5xNJb"
        const val VALID_FOR_SECONDS = 600
    }
}
