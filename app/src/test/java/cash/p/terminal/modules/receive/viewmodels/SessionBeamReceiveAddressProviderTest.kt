package cash.p.terminal.modules.receive.viewmodels

import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cash.p.beam.BeamAddress
import cash.p.beam.BeamAddressType
import cash.p.beam.BeamWalletSession
import cash.p.beam.BeamWalletState
import cash.p.terminal.core.managers.BeamDatabaseKeyProvider
import cash.p.terminal.core.managers.BeamDeletionState
import cash.p.terminal.core.managers.BeamSessionFactory
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.core.managers.BeamStorageLocator
import cash.p.terminal.core.storage.AppDatabase
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.AccountRecord
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IEncryptionManager
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.reactivex.BackpressureStrategy
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.test.assertFailsWith
import cash.p.beam.BeamNetwork as SdkBeamNetwork

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [27])
class SessionBeamReceiveAddressProviderTest {
    private val main = StandardTestDispatcher()
    private val io = Executors.newSingleThreadExecutor { Thread(it, "beam-receive-io") }.asCoroutineDispatcher()
    private val dispatchers = mockk<DispatcherProvider> {
        every { io } returns this@SessionBeamReceiveAddressProviderTest.io
    }
    private val account = account("receive-first")
    private val active = MutableStateFlow<ActiveAccountState>(ActiveAccountState.ActiveAccount(account))
    private val accounts = mockk<IAccountManager> {
        every { activeAccountStateFlow } returns active
        every { activeAccount } answers { (active.value as? ActiveAccountState.ActiveAccount)?.account }
    }
    private val initializing = MutableStateFlow(false)
    private val ready = PublishSubject.create<Map<Wallet, IAdapter>>()
    private val adapters = mockk<IAdapterManager> {
        every { initializationInProgressFlow } returns initializing
        every { adaptersReadyObservable } returns ready.toFlowable(BackpressureStrategy.BUFFER)
    }
    private val sdk = mockk<BeamWalletSession>(relaxUnitFun = true)
    private val sdkState = MutableStateFlow<BeamWalletState>(BeamWalletState.Stopped)
    private val sdkFactory = mockk<BeamSessionFactory.Factory>()
    private val availabilityThreads = CopyOnWriteArrayList<String>()
    private val stores = mutableListOf<ViewModelStore>()
    private lateinit var database: AppDatabase
    private lateinit var owner: BeamSessionOwner
    private lateinit var provider: SessionBeamReceiveAddressProvider

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val locator = BeamStorageLocator(context)
        val encryption = mockk<IEncryptionManager> {
            every { encrypt(any()) } answers { firstArg<String>() }
            every { decrypt(any()) } answers { firstArg<String>() }
        }
        val deletionState = spyk(BeamDeletionState(context, database))
        every { deletionState.ensureAvailable(any()) } answers {
            availabilityThreads.add(Thread.currentThread().name)
            callOriginal()
        }
        val keys = BeamDatabaseKeyProvider(context, locator, encryption, deletionState)
        val factory = BeamSessionFactory(keys, locator, dispatchers, mockk(relaxed = true), sdkFactory)
        owner = BeamSessionOwner(factory)
        provider = SessionBeamReceiveAddressProvider(owner, accounts, adapters, dispatchers)
        coEvery { sdkFactory.createNew(any(), any(), any()) } returns sdk
        coEvery { sdkFactory.restore(any(), any(), any(), any()) } returns sdk
        every { sdk.state } returns sdkState
        coEvery { sdk.close() } answers { sdkState.value = BeamWalletState.Closed }
        coEvery { sdk.receiveAddress(any()) } answers {
            assertTrue(Thread.currentThread().name.startsWith("beam-receive-io"))
            BeamAddress("test-${firstArg<BeamAddressType>()}", firstArg(), SdkBeamNetwork.Mainnet)
        }
    }

    @After
    fun tearDown() {
        stores.forEach(ViewModelStore::clear)
        Dispatchers.resetMain()
        database.close()
        io.close()
    }

    @Test
    fun receiveAddress_mainCaller_realRoomAndSdkRunOnIoWhileStopped() = runTest(main) {
        open()
        availabilityThreads.clear()
        assertSame(Looper.getMainLooper(), Looper.myLooper())
        assertFailsWith<IllegalStateException> { database.accountsDao().isAvailable(account.id) }

        val result = provider.receiveAddress(account, BeamAddressType.PublicOffline)

        assertSame(Looper.getMainLooper(), Looper.myLooper())
        assertEquals("test-PublicOffline", result.address.token)
        assertTrue(result.isCurrent())
        assertTrue(availabilityThreads.isNotEmpty())
        assertTrue(availabilityThreads.all { it == "beam-receive-io" })
        coVerify(exactly = 0) { sdk.start() }
    }

    @Test
    fun receiveAddress_inactiveAccount_doesNotReplaceCurrentOwner() = runTest(main) {
        val original = open()
        val other = account("receive-second")
        active.value = ActiveAccountState.ActiveAccount(other)
        val current = open(other)

        assertFailsWith<IllegalStateException> { provider.receiveAddress(account, BeamAddressType.Offline) }

        assertSame(current, owner.current)
        assertFalse(original === current)
        coVerify(exactly = 1) { sdk.close() }
    }

    @Test
    fun receiveAddress_accountChangesDuringSdkCall_rejectsOldResultWithoutSelectingOwner() = runTest(main) {
        val session = open()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { sdk.receiveAddress(BeamAddressType.Offline) } coAnswers {
            entered.complete(Unit)
            release.await()
            BeamAddress("old-token", BeamAddressType.Offline, SdkBeamNetwork.Mainnet)
        }
        val request = async { assertFailsWith<IllegalStateException> {
            provider.receiveAddress(account, BeamAddressType.Offline)
        } }
        entered.await()
        active.value = ActiveAccountState.ActiveAccount(account("receive-second"))
        release.complete(Unit)
        request.await()

        assertSame(session, owner.current)
        coVerify(exactly = 0) { sdk.close() }
    }

    @Test
    fun accountChanged_retainedViewModelClearsTokenAndCannotReacquireOnRetryOrTypeChange() = runTest(main) {
        open()
        val viewModel = viewModel()
        settleReceive()
        assertEquals(ViewState.Success, viewModel.uiState.viewState)

        val other = account("receive-second")
        active.value = ActiveAccountState.ActiveAccount(other)
        settleReceive()
        assertEquals("", viewModel.uiState.address)
        assertEquals("", viewModel.uiState.uri)
        val current = open(other)
        ready.onNext(emptyMap())
        settleReceive()
        viewModel.onErrorClick()
        settleReceive()
        viewModel.onBeamAddressTypeSelect(BeamAddressType.MaxPrivacy)
        settleReceive()

        assertSame(current, owner.current)
        assertEquals("", viewModel.uiState.address)
        assertTrue(viewModel.uiState.viewState is ViewState.Error)
        coVerify(exactly = 0) { sdk.receiveAddress(BeamAddressType.MaxPrivacy) }
        coVerify(exactly = 1) { sdk.close() }
    }

    @Test
    fun initializationPending_typeAndRetryStayLoadingThenUsePublishedOwner() = runTest(main) {
        initializing.value = true
        val viewModel = viewModel()
        settleReceive()
        viewModel.onBeamAddressTypeSelect(BeamAddressType.Offline)
        settleReceive()
        viewModel.onErrorClick()
        settleReceive()
        assertEquals(ViewState.Loading, viewModel.uiState.viewState)
        assertEquals("", viewModel.uiState.address)
        assertEquals(null, owner.current)

        val session = open()
        ready.onNext(emptyMap())
        settleReceive()

        assertSame(session, owner.current)
        assertEquals(ViewState.Success, viewModel.uiState.viewState)
        assertEquals(BeamAddressType.Offline, viewModel.uiState.beamAddressType)
        assertEquals("test-Offline", viewModel.uiState.address)
        coVerify(exactly = 0) { sdk.start() }
    }

    @Test
    fun ownerClosed_beforeReplacementPublication_clearsPreviouslyDisplayedToken() = runTest(main) {
        open()
        val viewModel = viewModel()
        settleReceive()
        assertEquals(ViewState.Success, viewModel.uiState.viewState)

        withContext(io) { owner.close() }
        settleReceive()

        assertEquals("", viewModel.uiState.address)
        assertEquals("", viewModel.uiState.uri)
        assertTrue(viewModel.uiState.viewState is ViewState.Error)
        assertEquals(null, owner.current)
    }

    @Test
    fun receiveAddress_ownerChangesAfterReturn_invalidatesCapturedResult() = runTest(main) {
        open()
        val result = provider.receiveAddress(account, BeamAddressType.PublicOffline)
        withContext(io) { owner.close() }
        open()

        assertFalse(result.isCurrent())
    }

    private suspend fun open(value: Account = account) = withContext(io) {
        database.accountsDao().insert(AccountRecord(
            value.id, value.name, "mnemonic", "created", false, false, null, null, null, 0,
        ))
        owner.acquire(value).also { sdkState.value = BeamWalletState.Stopped }
    }

    private fun viewModel(): ReceiveAddressViewModel {
        val wallet = mockk<Wallet>(relaxed = true) {
            every { account } returns this@SessionBeamReceiveAddressProviderTest.account
            every { token.blockchainType } returns BlockchainType.Beam
            every { token.type } returns TokenType.Native
            every { token.blockchain.name } returns "BEAM"
        }
        return ReceiveAddressViewModel(wallet, adapters, dispatchers, provider).also {
            stores.add(ViewModelStore().apply { put("receive", it) })
        }
    }

    private suspend fun TestScope.settleReceive() {
        runCurrent()
        withContext(io) { /* Drain the real IO executor before checking Main state. */ }
        runCurrent()
    }

    private fun account(id: String) = Account(id, id, mockk<AccountType.Mnemonic> {
        every { seed } returns ByteArray(64) { 7 }
        every { isWatchAccountType } returns false
    }, AccountOrigin.Created, 0)
}
