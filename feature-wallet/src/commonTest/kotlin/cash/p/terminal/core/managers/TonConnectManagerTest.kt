package cash.p.terminal.core.managers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.providers.SharedAppConfig
import cash.p.terminal.core.storage.AccountsDao
import cash.p.terminal.core.storage.AppDatabase
import cash.p.terminal.core.storage.SingleDatabaseStorageRouter
import cash.p.terminal.core.storage.StaticAppDatabaseProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import io.horizontalsystems.databasenaming.DatabaseNameResolver
import io.horizontalsystems.sqlcipher.room.DatabaseKeyMismatchException
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationConflictException
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationResult
import io.horizontalsystems.sqlcipher.room.InsufficientDatabaseMigrationSpaceException
import io.horizontalsystems.tonkit.PlatformContext
import io.horizontalsystems.tonkit.api.TonApi
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.models.SignTransaction
import io.horizontalsystems.tonkit.tonconnect.TonConnectKit
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.reactivex.BackpressureStrategy
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TonConnectManagerTest {

    private val dispatcher = StandardTestDispatcher()
    private val keyBytes = ByteArray(32) { it.toByte() }
    private val accountsDao = mockk<AccountsDao>()
    private val appDatabase = mockk<AppDatabase> { every { accountsDao() } returns accountsDao }
    private val accountsDeleted = PublishSubject.create<Unit>()
    private val accountsFlow = MutableSharedFlow<List<Account>>(extraBufferCapacity = 8)
    private val visibleAccounts = mutableSetOf(VISIBLE_ID, ACCOUNT_A, ACCOUNT_B)
    private val accountManager = mockk<IAccountManager>(relaxed = true) {
        every { accountsDeletedFlowable } returns accountsDeleted.toFlowable(BackpressureStrategy.BUFFER)
        every { accountsFlow } returns this@TonConnectManagerTest.accountsFlow
        every { account(any()) } answers { if (firstArg<String>() in this@TonConnectManagerTest.visibleAccounts) mockk() else null }
    }
    private val storage = FakeStorage()
    private val kit = kitMock()
    private val tonApi = mockk<TonApi>()

    @BeforeTest
    fun setUp() {
        mockkObject(TonKit.Companion, SharedAppConfig)
        every { SharedAppConfig.tonApiKeys } returns listOf("api-key")
        every { TonKit.getTonApi(any(), any()) } returns tonApi
        every { TonKit.getTransactionSigner(any()) } returns mockk()
        mockkObject(TonConnectKit.Companion)
        coEvery { TonConnectKit.migrateDatabase(any(), any(), any()) } returns DatabaseMigrationResult(0, 1)
        coEvery { TonConnectKit.getInstance(any(), any(), any(), any(), any()) } returns kit
        coEvery { TonConnectKit.clear(any(), any()) } returns Unit
        every { accountsDao.getIds() } returns listOf(VISIBLE_ID, HIDDEN_ID, DELETED_ID)
        every { accountsDao.getDeletedIds() } returns listOf(DELETED_ID)
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun start_createdScope_migratesWithStoredKeyAndDropsDeletedAccountsBeforeStarting() = runTest(dispatcher) {
        createManager().start()
        runCurrent()

        coVerifyOrder {
            TonConnectKit.migrateDatabase(storage.context(GLOBAL), keyBytes, storage.resolver)
            TonConnectKit.getInstance(storage.context(GLOBAL), keyBytes, any(), any(), storage.resolver)
            kit.removeDAppsExcept(LIVE_WALLET_IDS)
            kit.start()
        }
    }

    @Test
    fun start_scopeNotCreated_opensNoKit() = runTest(dispatcher) {
        storage.created.clear()

        createManager().start()
        runCurrent()

        coVerify(exactly = 0) { TonConnectKit.getInstance(any(), any(), any(), any(), any()) }
    }

    @Test
    fun getManifest_secondCall_reusesOpenKit() = runTest(dispatcher) {
        val manager = createManager()

        manager.getManifest(VISIBLE_ID, MANIFEST_URL)
        manager.getManifest(VISIBLE_ID, MANIFEST_URL)

        coVerify(exactly = 1) { TonConnectKit.getInstance(any(), any(), any(), any(), any()) }
        coVerify(exactly = 2) { kit.getManifest(MANIFEST_URL) }
    }

    @Test
    fun start_firstOpenFailed_nextCallOpensKit() = runTest(dispatcher) {
        coEvery { TonConnectKit.migrateDatabase(any(), any(), any()) } throws
            InsufficientDatabaseMigrationSpaceException(requiredBytes = 2, availableBytes = 1) andThen
            DatabaseMigrationResult(0, 1)
        val manager = createManager()
        manager.start()
        runCurrent()
        assertEquals(emptySet(), manager.openScopes.value)

        manager.start()
        runCurrent()

        assertEquals(setOf(GLOBAL), manager.openScopes.value)
        coVerify(exactly = 2) { TonConnectKit.migrateDatabase(any(), any(), any()) }
    }

    @Test
    fun getManifest_databaseKeyMismatch_propagatesAndNeverClears() = runTest(dispatcher) {
        coEvery { TonConnectKit.getInstance(any(), any(), any(), any(), any()) } throws keyMismatch()
        val manager = createManager()

        assertFailsWith<DatabaseKeyMismatchException> { manager.getManifest(VISIBLE_ID, MANIFEST_URL) }

        coVerify(exactly = 0) { TonConnectKit.clear(any(), any()) }
        assertEquals(emptySet(), manager.openScopes.value)
    }

    @Test
    fun getManifest_storedKeyAndMigrationMismatch_propagatesAndKeepsKey() = runTest(dispatcher) {
        coEvery { TonConnectKit.migrateDatabase(any(), any(), any()) } throws keyMismatch()
        val manager = createManager()

        assertFailsWith<DatabaseKeyMismatchException> { manager.getManifest(VISIBLE_ID, MANIFEST_URL) }

        coVerify(exactly = 0) { TonConnectKit.clear(any(), any()) }
        assertEquals(emptyList(), storage.removedKeys)
    }

    @Test
    fun getManifest_freshKeyAndOldDatabase_clearsDatabaseAndMigratesAgain() = runTest(dispatcher) {
        storage.keyIsNew = true
        coEvery { TonConnectKit.migrateDatabase(any(), any(), any()) } throws keyMismatch() andThen
            DatabaseMigrationResult(0, 1)
        val manager = createManager()

        manager.getManifest(VISIBLE_ID, MANIFEST_URL)

        coVerifyOrder {
            TonConnectKit.migrateDatabase(storage.context(GLOBAL), keyBytes, storage.resolver)
            TonConnectKit.clear(storage.context(GLOBAL), storage.resolver)
            TonConnectKit.migrateDatabase(storage.context(GLOBAL), keyBytes, storage.resolver)
            TonConnectKit.getInstance(storage.context(GLOBAL), keyBytes, any(), any(), storage.resolver)
        }
        assertEquals(emptyList(), storage.removedKeys)
    }

    @Test
    fun getManifest_freshKeyDiscardFails_removesKeySoNextOpenRetries() = runTest(dispatcher) {
        storage.keyIsNew = true
        coEvery { TonConnectKit.migrateDatabase(any(), any(), any()) } throws keyMismatch()
        coEvery { TonConnectKit.clear(any(), any()) } throws DatabaseMigrationConflictException("clear conflict")
        val manager = createManager()

        assertFailsWith<DatabaseMigrationConflictException> { manager.getManifest(VISIBLE_ID, MANIFEST_URL) }

        assertEquals(listOf(GLOBAL), storage.removedKeys)
        coVerify(exactly = 0) { TonConnectKit.getInstance(any(), any(), any(), any(), any()) }
    }

    @Test
    fun getManifest_cancelledDuringFreshKeyMigration_stillRecreatesDatabase() = runTest(dispatcher) {
        val migrationGate = CompletableDeferred<Unit>()
        storage.keyIsNew = true
        coEvery { TonConnectKit.migrateDatabase(any(), any(), any()) } coAnswers {
            migrationGate.await()
            throw keyMismatch()
        } andThen DatabaseMigrationResult(0, 1)
        val manager = createManager()
        val job = launch { manager.getManifest(VISIBLE_ID, MANIFEST_URL) }
        runCurrent()

        job.cancel()
        migrationGate.complete(Unit)
        runCurrent()

        coVerify(exactly = 1) { TonConnectKit.clear(any(), any()) }
        coVerify(exactly = 2) { TonConnectKit.migrateDatabase(any(), any(), any()) }
        assertEquals(emptyList(), storage.removedKeys)
    }

    @Test
    fun reset_kitOpen_removesKeyClosesKitAndNextUseOpensNewKit() = runTest(dispatcher) {
        val manager = createManager()
        manager.getManifest(VISIBLE_ID, MANIFEST_URL)

        manager.reset()
        runCurrent()

        coVerify(exactly = 1) { kit.close() }
        assertEquals(listOf(GLOBAL, GLOBAL), storage.removedKeys)
        assertEquals(emptySet(), manager.openScopes.value)
        manager.getManifest(VISIBLE_ID, MANIFEST_URL)
        coVerify(exactly = 2) { TonConnectKit.getInstance(any(), any(), any(), any(), any()) }
    }

    @Test
    fun reset_whileKeyCreationInFlight_removesKeyPersistedMeanwhile() = runTest(dispatcher) {
        val keyGate = CompletableDeferred<Unit>()
        storage.keyGate = keyGate
        val manager = createManager()
        launch { manager.getManifest(VISIBLE_ID, MANIFEST_URL) }
        runCurrent()

        manager.reset()
        assertEquals(listOf(GLOBAL), storage.removedKeys)
        keyGate.complete(Unit)
        runCurrent()

        coVerifyOrder {
            TonConnectKit.getInstance(any(), any(), any(), any(), any())
            kit.close()
        }
        assertEquals(listOf(GLOBAL, GLOBAL), storage.removedKeys)
    }

    @Test
    fun reset_afterKitOpened_ignoresOldKitSendRequests() = runTest(dispatcher) {
        val oldRequests = MutableSharedFlow<SignTransaction>()
        every { kit.sendRequestFlow } returns oldRequests
        val manager = createManager()
        manager.getManifest(VISIBLE_ID, MANIFEST_URL)
        val beforeReset = signTransaction(VISIBLE_ID)
        oldRequests.emit(beforeReset)
        runCurrent()

        manager.reset()
        runCurrent()
        oldRequests.emit(signTransaction(VISIBLE_ID))
        runCurrent()

        assertNull(manager.pendingSendRequest.value)
        assertEquals(0, oldRequests.subscriptionCount.value)
    }

    @Test
    fun start_accountDeleted_removesDAppsOfAccountsDeletedOnAnyLevelOnly() = runTest(dispatcher) {
        val manager = createManager()
        manager.start()
        runCurrent()

        accountsDeleted.onNext(Unit)
        runCurrent()

        // At opening, on the first pass and for the deletion; the hidden wallet of another level stays.
        coVerify(exactly = 3) { kit.removeDAppsExcept(LIVE_WALLET_IDS) }
    }

    @Test
    fun start_accountScopes_neverPrunesDAppsOfOtherLevels() = runTest(dispatcher) {
        val kitA = kitMock()
        useAccountScopes(ACCOUNT_A to kitA)
        createManager().start()
        runCurrent()

        accountsDeleted.onNext(Unit)
        runCurrent()

        coVerify(exactly = 0) { kitA.removeDAppsExcept(any()) }
    }

    @Test
    fun dApps_beforeKitOpen_emitsEmptyThenKitDApps() = runTest(dispatcher) {
        val dApp = mockk<DAppEntity>()
        every { kit.getDApps() } returns flowOf(listOf(dApp))
        val manager = createManager()
        val emitted = mutableListOf<List<DAppEntity>>()
        backgroundScope.launch { manager.dApps.toList(emitted) }
        runCurrent()

        manager.getManifest(VISIBLE_ID, MANIFEST_URL)
        runCurrent()

        assertEquals(emptyList(), emitted.first())
        assertEquals(listOf(dApp), emitted.last())
    }

    @Test
    fun dApps_scopeLeftLiveSetBeforeItsRelease_listsOnlyLiveScopes() = runTest(dispatcher) {
        val dAppA = dApp(ACCOUNT_A)
        val dAppB = dApp(ACCOUNT_B)
        val kitA = kitMock().also { every { it.getDApps() } returns flowOf(listOf(dAppA)) }
        val kitB = kitMock().also { every { it.getDApps() } returns flowOf(listOf(dAppB)) }
        useAccountScopes(ACCOUNT_A to kitA, ACCOUNT_B to kitB)
        val manager = createManager()
        manager.start()
        runCurrent()
        storage.live = setOf(ACCOUNT_B)
        val emitted = mutableListOf<List<DAppEntity>>()

        backgroundScope.launch { manager.dApps.toList(emitted) }
        runCurrent()

        assertEquals(listOf(dAppB), emitted.last())
    }

    @Test
    fun approve_twoScopes_routesToKitOfRequestAccount() = runTest(dispatcher) {
        val kitA = kitMock()
        val kitB = kitMock()
        useAccountScopes(ACCOUNT_A to kitA, ACCOUNT_B to kitB)
        val manager = createManager()
        manager.start()
        runCurrent()
        val request = sendRequest(ACCOUNT_B)

        assertTrue(manager.approve(request, BOC))

        coVerify(exactly = 1) { kitB.approve(request, BOC) }
        coVerify(exactly = 0) { kitA.approve(any(), any()) }
    }

    @Test
    fun reject_scopeClosed_returnsFalseWithoutOpeningKit() = runTest(dispatcher) {
        useAccountScopes(ACCOUNT_A to kitMock())
        storage.created.clear()
        val manager = createManager()

        assertFalse(manager.reject(sendRequest(ACCOUNT_A)))

        coVerify(exactly = 0) { TonConnectKit.getInstance(any(), any(), any(), any(), any()) }
    }

    @Test
    fun badRequest_scopeLeftLiveSetBeforeItsRelease_notOpenAndNeverTouchesKit() = runTest(dispatcher) {
        val kitA = kitMock()
        useAccountScopes(ACCOUNT_A to kitA)
        val manager = createManager()
        manager.start()
        runCurrent()
        storage.live = emptySet()

        assertFalse(manager.isOpen(sendRequest(ACCOUNT_A)))
        assertFalse(manager.badRequest(sendRequest(ACCOUNT_A)))

        coVerify(exactly = 0) { kitA.badRequest(any()) }
    }

    @Test
    fun start_scopeLeavesLiveSet_closesKitAndDropsItsPendingRequest() = runTest(dispatcher) {
        val requests = MutableSharedFlow<SignTransaction>()
        val kitA = kitMock(requests)
        useAccountScopes(ACCOUNT_A to kitA)
        val manager = createManager()
        manager.start()
        runCurrent()
        requests.emit(signTransaction(ACCOUNT_A))
        runCurrent()

        storage.live = emptySet()
        accountsFlow.emit(emptyList())
        runCurrent()

        coVerify(exactly = 1) { kitA.close() }
        assertNull(manager.pendingSendRequest.value)
        assertFalse(manager.isOpen(sendRequest(ACCOUNT_A)))
    }

    @Test
    fun start_accountDeletedWhileItsKitOpens_closesKitOnNextPass() = runTest(dispatcher) {
        val openGate = CompletableDeferred<Unit>()
        val kitA = kitMock()
        useAccountScopes(ACCOUNT_A to kitA)
        coEvery { TonConnectKit.getInstance(storage.context(ACCOUNT_A), any(), any(), any(), any()) } coAnswers {
            openGate.await()
            kitA
        }
        val manager = createManager()
        manager.start()
        runCurrent()

        storage.live = emptySet()
        accountsFlow.emit(emptyList())
        openGate.complete(Unit)
        runCurrent()

        coVerify(exactly = 1) { kitA.close() }
        assertEquals(emptySet(), manager.openScopes.value)
    }

    @Test
    fun retainAccounts_otherAccountsOpen_closesOnlyTheirKits() = runTest(dispatcher) {
        val kitA = kitMock()
        val kitB = kitMock()
        useAccountScopes(ACCOUNT_A to kitA, ACCOUNT_B to kitB)
        val manager = createManager()
        manager.start()
        runCurrent()

        manager.retainAccounts(setOf(ACCOUNT_A))

        coVerify(exactly = 0) { kitA.close() }
        coVerify(exactly = 1) { kitB.close() }
        assertEquals(setOf(ACCOUNT_A), manager.openScopes.value)
    }

    @Test
    fun retainAccounts_disconnectInFlight_closesKitOnlyAfterDisconnectCompletes() = runTest(dispatcher) {
        val disconnectGate = CompletableDeferred<Unit>()
        val kitA = kitMock()
        coEvery { kitA.disconnect(any()) } coAnswers { disconnectGate.await() }
        useAccountScopes(ACCOUNT_A to kitA)
        val manager = createManager()
        manager.start()
        runCurrent()
        val disconnected = async { manager.disconnect(dApp(ACCOUNT_A)) }
        runCurrent()

        val closing = launch { manager.retainAccounts(emptySet()) }
        runCurrent()

        assertFalse(manager.isOpen(sendRequest(ACCOUNT_A)))
        coVerify(exactly = 0) { kitA.close() }
        disconnectGate.complete(Unit)
        runCurrent()
        assertTrue(disconnected.await())
        assertTrue(closing.isCompleted)
        coVerifyOrder {
            kitA.disconnect(any())
            kitA.close()
        }
    }

    @Test
    fun retainAccounts_callerCancelledDuringClose_kitStillClosedAndForgotten() = runTest(dispatcher) {
        val closeGate = CompletableDeferred<Unit>()
        val kitA = kitMock()
        coEvery { kitA.close() } coAnswers { closeGate.await() }
        useAccountScopes(ACCOUNT_A to kitA)
        val manager = createManager()
        manager.start()
        runCurrent()
        val closing = launch { manager.retainAccounts(emptySet()) }
        runCurrent()

        closing.cancel()
        closeGate.complete(Unit)
        runCurrent()
        manager.retainAccounts(emptySet())

        coVerify(exactly = 1) { kitA.close() }
        assertEquals(emptySet(), manager.openScopes.value)
    }

    @Test
    fun connect_callerCancelled_cancelsKitOperationAndReleasesKit() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        coEvery { kit.connect(any(), any(), any(), any()) } coAnswers {
            started.complete(Unit)
            try {
                CompletableDeferred<Unit>().await()
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
            mockk()
        }
        val manager = createManager()
        val connecting = launch { manager.connect(VISIBLE_ID, mockk(), mockk(), VISIBLE_ID, mockk()) }
        runCurrent()

        connecting.cancel()
        runCurrent()
        manager.reset()
        runCurrent()

        assertTrue(started.isCompleted)
        assertTrue(cancelled)
        coVerify(exactly = 1) { kit.close() }
    }

    @Test
    fun getManifest_twoOperationsOnOneKit_runConcurrently() = runTest(dispatcher) {
        val slowGate = CompletableDeferred<Unit>()
        coEvery { kit.getManifest(SLOW_URL) } coAnswers {
            slowGate.await()
            mockk()
        }
        val manager = createManager()
        val slow = async { manager.getManifest(VISIBLE_ID, SLOW_URL) }
        runCurrent()

        val fast = async { manager.getManifest(VISIBLE_ID, MANIFEST_URL) }
        runCurrent()

        assertTrue(fast.isCompleted)
        assertFalse(slow.isCompleted)
        slowGate.complete(Unit)
    }

    @Test
    fun pendingSendRequest_kitEmitsRightAfterStart_isReceived() = runTest(dispatcher) {
        val requests = MutableSharedFlow<SignTransaction>()
        val kitA = kitMock(requests)
        val request = signTransaction(ACCOUNT_A)
        every { kitA.start() } answers {
            backgroundScope.launch { requests.emit(request) }
            Unit
        }
        useAccountScopes(ACCOUNT_A to kitA)
        val manager = createManager()
        val arrivals = mutableListOf<SignTransaction>()
        backgroundScope.launch { manager.sendRequestArrivals.toList(arrivals) }

        manager.start()
        runCurrent()

        assertSame(request, manager.pendingSendRequest.value)
        assertEquals(listOf(request), arrivals)
    }

    @Test
    fun sendRequestArrivals_accountHiddenBeforeDelivery_emitsNothingButKeepsRequestPending() = runTest(dispatcher) {
        val requests = MutableSharedFlow<SignTransaction>()
        useAccountScopes(ACCOUNT_A to kitMock(requests))
        val manager = createManager()
        // Resumed before the arrival collector: the account narrows away while the arrival waits in the buffer.
        backgroundScope.launch { manager.pendingSendRequest.collect { if (it != null) visibleAccounts.remove(ACCOUNT_A) } }
        val arrivals = mutableListOf<SignTransaction>()
        backgroundScope.launch { manager.sendRequestArrivals.toList(arrivals) }
        manager.start()
        runCurrent()
        val request = signTransaction(ACCOUNT_A)

        requests.emit(request)
        runCurrent()

        assertEquals(emptyList(), arrivals)
        assertSame(request, manager.pendingSendRequest.value)
    }

    @Test
    fun takeShowableSendRequest_accountNotVisible_returnsNullAndKeepsRequest() = runTest(dispatcher) {
        val requests = MutableSharedFlow<SignTransaction>()
        useAccountScopes(ACCOUNT_A to kitMock(requests))
        val manager = createManager()
        manager.start()
        runCurrent()
        val request = signTransaction(ACCOUNT_A)
        requests.emit(request)
        runCurrent()

        visibleAccounts.remove(ACCOUNT_A)
        assertNull(manager.takeShowableSendRequest())
        assertSame(request, manager.pendingSendRequest.value)

        visibleAccounts.add(ACCOUNT_A)
        assertSame(request, manager.takeShowableSendRequest())
        assertNull(manager.pendingSendRequest.value)
    }

    @Test
    fun handle_malformedUri_emitsNoRequest() = runTest(dispatcher) {
        every { TonConnectKit.readData(MALFORMED_URI) } throws IllegalArgumentException("id is required")
        val manager = createManager()
        val emitted = mutableListOf<DAppRequest>()
        backgroundScope.launch { manager.dappRequestFlow.toList(emitted) }
        runCurrent()

        manager.handle(MALFORMED_URI)
        runCurrent()

        assertEquals(emptyList(), emitted)
    }

    @Test
    fun handle_malformedUri_doesNotLogPayload() = runTest(dispatcher) {
        every { TonConnectKit.readData(MALFORMED_URI) } throws IllegalArgumentException("bad json: $SECRET")
        val logs = CapturingLogWriter()
        val previousWriters = Logger.config.logWriterList
        Logger.setLogWriters(logs)
        try {
            createManager().handle(MALFORMED_URI)
        } finally {
            Logger.setLogWriters(previousWriters)
        }

        assertTrue(logs.entries.isNotEmpty())
        assertFalse(logs.entries.any { (message, throwable) ->
            SECRET in message || throwable?.message.orEmpty().contains(SECRET)
        })
    }

    @Test
    fun handle_validUri_emitsSourceUri() = runTest(dispatcher) {
        every { TonConnectKit.readData(VALID_URI) } returns mockk()
        val manager = createManager()
        val emitted = mutableListOf<DAppRequest>()
        backgroundScope.launch { manager.dappRequestFlow.toList(emitted) }
        runCurrent()

        manager.handle(VALID_URI, closeAppOnResult = true)
        runCurrent()

        assertEquals(listOf(DAppRequest(VALID_URI, closeAppOnResult = true)), emitted)
    }

    @Test
    fun handle_beforeAnyCollector_deliversTheRequestOnceCollected() = runTest(dispatcher) {
        every { TonConnectKit.readData(VALID_URI) } returns mockk()
        val manager = createManager()

        manager.handle(VALID_URI)
        val emitted = mutableListOf<DAppRequest>()
        backgroundScope.launch { manager.dappRequestFlow.toList(emitted) }
        runCurrent()

        assertEquals(listOf(DAppRequest(VALID_URI, closeAppOnResult = false)), emitted)
    }

    @Test
    fun networkTimeSeconds_apiFails_returnsNull() = runTest(dispatcher) {
        coEvery { tonApi.getRawTime() } throws IllegalStateException("no network")

        assertNull(createManager().networkTimeSeconds())
    }

    @Test
    fun networkTimeSeconds_apiSucceeds_returnsApiValue() = runTest(dispatcher) {
        coEvery { tonApi.getRawTime() } returns NETWORK_TIME

        assertEquals(NETWORK_TIME.toLong(), createManager().networkTimeSeconds())
    }

    private fun TestScope.createManager() = TonConnectManager(
        adapterFactory = mockk(relaxed = true),
        appName = "P.cash Wallet",
        appVersion = "1.0",
        storage = storage,
        accountManager = accountManager,
        router = SingleDatabaseStorageRouter(StaticAppDatabaseProvider(appDatabase)),
        dispatcherProvider = TestDispatcherProvider(dispatcher, backgroundScope),
    )

    /** Desktop shape: one scope per account, each with its own kit. */
    private fun useAccountScopes(vararg kits: Pair<String, TonConnectKit>) {
        storage.perAccount = true
        storage.live = kits.mapTo(mutableSetOf()) { it.first }
        storage.created.addAll(storage.live)
        kits.forEach { (scope, scopeKit) ->
            coEvery { TonConnectKit.getInstance(storage.context(scope), any(), any(), any(), any()) } returns scopeKit
        }
    }

    private fun kitMock(requests: MutableSharedFlow<SignTransaction> = MutableSharedFlow()) =
        mockk<TonConnectKit>(relaxed = true) {
            every { sendRequestFlow } returns requests
            every { getDApps() } returns flowOf(emptyList())
        }

    private fun sendRequest(accountId: String) = mockk<SendRequestEntity> { every { dAppId } returns "$accountId:dapp" }

    private fun dApp(accountId: String) = mockk<DAppEntity> { every { walletId } returns accountId }

    private fun signTransaction(accountId: String): SignTransaction {
        val dApp = dApp(accountId)
        return mockk { every { this@mockk.dApp } returns dApp }
    }

    private fun keyMismatch() = DatabaseKeyMismatchException("ton-connect", IllegalStateException("wrong key"))

    private inner class FakeStorage : TonConnectStorage {
        var perAccount = false
        var live = setOf(GLOBAL)
        val created = mutableSetOf(GLOBAL)
        val removedKeys = mutableListOf<String>()
        var keyIsNew = false
        var keyGate: CompletableDeferred<Unit>? = null
        val resolver = DatabaseNameResolver { "resolved-$it" }
        private val contexts = mutableMapOf<String, PlatformContext>()

        fun context(scope: String) = contexts.getOrPut(scope) { mockk() }

        override fun scopeOf(accountId: String) = if (perAccount) accountId else GLOBAL
        override val sharedByAccounts get() = !perAccount
        override fun liveScopes() = live
        override fun isCreated(scope: String) = scope in created
        override fun platformContext(scope: String) = context(scope)
        override fun nameResolver(scope: String) = resolver
        override suspend fun databaseKey(scope: String): DatabaseKey {
            keyGate?.await()
            return DatabaseKey(keyBytes, isNew = keyIsNew)
        }
        override fun removeDatabaseKey(scope: String) {
            removedKeys += scope
        }
    }

    private class CapturingLogWriter : LogWriter() {
        val entries = mutableListOf<Pair<String, Throwable?>>()

        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            entries += message to throwable
        }
    }

    private companion object {
        const val GLOBAL = "ton-connect"
        const val VISIBLE_ID = "visible-account"
        const val HIDDEN_ID = "hidden-account"
        const val ACCOUNT_A = "account-a"
        const val ACCOUNT_B = "account-b"
        val LIVE_WALLET_IDS = listOf(VISIBLE_ID, "$VISIBLE_ID-w5", HIDDEN_ID, "$HIDDEN_ID-w5")
        const val DELETED_ID = "deleted-account"
        const val MANIFEST_URL = "https://dapp.example.com/manifest.json"
        const val SLOW_URL = "https://slow.example.com/manifest.json"
        const val BOC = "boc"
        const val VALID_URI = "tc://?v=2&id=client&r=%7B%7D"
        const val MALFORMED_URI = "tc://?v=2"
        const val SECRET = "SECRET-PAYLOAD"
        const val NETWORK_TIME = 1_800_000_000
    }
}
