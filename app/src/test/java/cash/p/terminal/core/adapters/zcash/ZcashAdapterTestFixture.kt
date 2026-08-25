package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.adapters.zcash.session.ZcashSession
import cash.p.terminal.core.adapters.zcash.session.ZcashSessionManager
import cash.p.terminal.core.adapters.zcash.session.ZcashSessionResult
import cash.p.terminal.core.adapters.zcash.session.ZcashSessionState
import cash.p.terminal.core.managers.BackgroundKeepAliveManager
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import cash.p.zcash.AccountInfo
import cash.p.zcash.Addresses
import cash.p.zcash.MigrationPhase
import cash.p.zcash.MigrationStatus
import cash.p.zcash.PoolBalance
import cash.p.zcash.PoolSet
import cash.p.zcash.SyncState
import cash.p.zcash.Transaction
import cash.p.zcash.ZcashWallet
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.CoreApp
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Shared harness for [ZcashAdapter] tests: a real adapter on top of a mocked session.
 *
 * The session's state is a plain [MutableStateFlow] the test drives directly, so a
 * "sync -> funds arrive" sequence plays out on virtual time; [zcashWallet] answers every call
 * the adapter routes through `withOperation`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class ZcashAdapterTestFixture {

    protected val dispatcher = StandardTestDispatcher()

    // Separate from the `runTest` scope on purpose: the adapter's collectors never complete on
    // their own, and as children of the `runTest` scope they would make it hang. They live in
    // `appScope` instead, cancelled explicitly in tearDownFixture().
    protected val appScope = CoroutineScope(SupervisorJob() + dispatcher)

    protected val wallet = mockk<Wallet>(relaxed = true)
    protected val localStorage = mockk<ILocalStorage>(relaxed = true)
    protected val backgroundManager = mockk<BackgroundManager>(relaxed = true)
    protected val singleUseAddressManager = mockk<ZcashSingleUseAddressManager>(relaxed = true)
    protected val backgroundKeepAliveManager = mockk<BackgroundKeepAliveManager>(relaxed = true)
    protected val offlineModeManager = mockk<OfflineModeManager>(relaxed = true)
    protected val addressDeriver = mockk<ZcashAddressDeriver>()

    /** In-memory stand-in for the persisted set, so a restart can be simulated. */
    protected var migrationTxIds = emptySet<String>()
    protected val ironwoodMigrations = ZcashIronwoodMigrationRegistry(localStorage)

    private val sessionStateFlow = MutableStateFlow(
        ZcashSessionState(syncState = SyncState.Connecting)
    )
    protected val backgroundStateFlow = MutableStateFlow(BackgroundManagerState.Unknown)

    protected val zcashWallet = mockk<ZcashWallet>(relaxed = true)
    protected lateinit var session: ZcashSession
    protected val sessionManager = mockk<ZcashSessionManager>(relaxed = true)
    protected lateinit var adapter: ZcashAdapter

    @Before
    fun setUpFixture() {
        Dispatchers.setMain(dispatcher)
        CoreApp.instance = mockk(relaxed = true)

        startKoin {
            modules(module {
                single { backgroundKeepAliveManager }
                single { offlineModeManager }
            })
        }

        stubAccount()
        stubLocalStorage()
        stubZcashWallet()
        stubWallet()
        stubSession()
        stubSessionManager()
    }

    private fun stubAccount() {
        val accountType = mockk<AccountType.Mnemonic>(relaxed = true) {
            every { words } returns List(24) { "abandon" }
            every { passphrase } returns ""
        }
        val account = mockk<Account>(relaxed = true) {
            every { id } returns ACCOUNT_ID
            every { name } returns "Test"
            every { type } returns accountType
            every { origin } returns AccountOrigin.Created
        }
        every { wallet.account } returns account
        every { backgroundManager.stateFlow } returns backgroundStateFlow
        coEvery { addressDeriver.addresses(any()) } returns Addresses(
            unified = "u1test",
            sapling = "zs1test",
            orchard = null,
            transparent = "t1test",
            diversifierIndex = 0,
        )
    }

    private fun stubLocalStorage() {
        every { localStorage.zcashIronwoodMigrationTxIds } answers { migrationTxIds }
        every { localStorage.zcashIronwoodMigrationTxIds = any() } answers {
            migrationTxIds = firstArg()
        }
    }

    private fun stubZcashWallet() {
        coEvery { zcashWallet.accounts() } returns listOf(
            AccountInfo(
                id = DB_ACCOUNT_ID,
                name = "Test",
                birthHeight = BIRTHDAY,
                accountIndex = 0,
                diversifierIndex = 0,
                position = 0,
                height = 0,
                time = 0L,
                balance = 0L,
                hidden = false,
                enabled = true,
                internal = false,
                hardwareWallet = false,
            )
        )
        coEvery { zcashWallet.balance(any(), any()) } returns PoolBalance(emptyMap())
        coEvery { zcashWallet.transactions(any()) } returns emptyList()
        coEvery { zcashWallet.latestHeight() } returns 0
        coEvery { zcashWallet.migrationStatus(any()) } returns MigrationStatus(
            phase = MigrationPhase.MIGRATING,
            standardNotes = 1,
            nonStandardNotes = 0,
            migratedNotes = 0,
        )
    }

    private fun stubSession() {
        session = mockk<ZcashSession>(relaxed = true) {
            every { accountId } returns ACCOUNT_ID
            every { dbAccountId } returns DB_ACCOUNT_ID
            every { state } returns sessionStateFlow
            coEvery { withOperation(any<suspend (ZcashWallet) -> Any?>()) } coAnswers {
                ZcashSessionResult.Success(firstArg<suspend (ZcashWallet) -> Any?>()(zcashWallet))
            }
            coEvery { reserveForBroadcast(any()) } returns ZcashSessionResult.Success(Unit)
            coEvery { refresh() } returns ZcashSessionResult.Success(Unit)
        }
        coEvery { sessionManager.acquire(any()) } returns session
    }

    @After
    fun tearDownFixture() {
        if (::adapter.isInitialized) {
            adapter.stop()
        }
        appScope.cancel()
        stopKoin()
        Dispatchers.resetMain()
        unmockkAll()
    }

    /** Stubs on [zcashWallet] that are specific to the subclass's scenarios. */
    protected open fun stubWallet() = Unit

    /** Stubs on [sessionManager] that are specific to the subclass's scenarios. */
    protected open fun stubSessionManager() = Unit

    protected fun createAdapter(addressSpecTyped: AddressSpecType? = null) = ZcashAdapter(
        wallet = wallet,
        addressSpecTyped = addressSpecTyped,
        backgroundManager = backgroundManager,
        singleUseAddressManager = singleUseAddressManager,
        sessionManager = sessionManager,
        ironwoodMigrations = ironwoodMigrations,
        addressDeriver = addressDeriver,
        dispatcherProvider = TestDispatcherProvider(dispatcher, appScope),
    )

    protected fun emitSessionSyncState(syncState: SyncState) {
        sessionStateFlow.update { state ->
            state.copy(
                syncState = syncState,
                latestHeight = (syncState as? SyncState.Syncing)?.target ?: state.latestHeight,
            )
        }
    }

    protected fun emitSessionBalance(
        balance: PoolBalance,
        maxSpendable: Map<PoolSet, Long> = emptyMap(),
    ) {
        sessionStateFlow.update { it.copy(balance = balance, maxSpendable = maxSpendable) }
    }

    protected fun emitSessionTransactions(transactions: List<Transaction>) {
        sessionStateFlow.update { it.copy(minedTransactions = transactions) }
    }

    protected fun emitSessionState(
        syncState: SyncState,
        balance: PoolBalance,
        transactions: List<Transaction>,
        latestHeight: Int,
        maxSpendable: Map<PoolSet, Long> = emptyMap(),
    ) {
        sessionStateFlow.value = ZcashSessionState(
            syncState = syncState,
            balance = balance,
            maxSpendable = maxSpendable,
            latestHeight = latestHeight,
            minedTransactions = transactions,
        )
    }

    protected companion object {
        const val ACCOUNT_ID = "test-account-id"
        const val DB_ACCOUNT_ID = 0
        const val BIRTHDAY = 2_000_000
    }
}
