package cash.p.terminal.core.managers

import android.content.Context
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.AccountsDao
import cash.p.terminal.core.storage.AppDatabase
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import io.horizontalsystems.sqlcipher.room.DatabaseKeyMismatchException
import io.horizontalsystems.sqlcipher.room.DatabaseMigrationResult
import io.horizontalsystems.sqlcipher.room.InsufficientDatabaseMigrationSpaceException
import io.horizontalsystems.tonkit.core.TonKit
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

class TonConnectManagerTest {

    private val dispatcher = StandardTestDispatcher()
    private val databaseKey = ByteArray(32) { it.toByte() }
    private val context = mockk<Context>(relaxed = true)
    private val keyProvider = mockk<TonConnectDatabaseKeyProvider>()
    private val accountsDao = mockk<AccountsDao>()
    private val appDatabase = mockk<AppDatabase> { every { accountsDao() } returns accountsDao }
    private val accountsDeleted = PublishSubject.create<Unit>()
    private val accountManager = mockk<IAccountManager>(relaxed = true) {
        every { accountsDeletedFlowable } returns accountsDeleted.toFlowable(BackpressureStrategy.BUFFER)
        // Only the current level's accounts: live dApps must never be derived from this list.
        every { accounts } returns listOf(mockk<Account> { every { id } returns VISIBLE_ID })
    }
    private val kit = mockk<TonConnectKit>(relaxed = true)

    @Before
    fun setUp() {
        mockkObject(TonKit.Companion)
        every { TonKit.getTonApi(any(), any()) } returns mockk()
        every { TonKit.getTransactionSigner(any()) } returns mockk()
        mockkObject(TonConnectKit.Companion)
        coEvery { TonConnectKit.migrateDatabase(any(), any()) } returns DatabaseMigrationResult(0, 1)
        coEvery { TonConnectKit.getInstance(any(), any(), any(), any()) } returns kit
        coEvery { TonConnectKit.clear(any()) } returns Unit
        coEvery { keyProvider.awaitKey(any()) } returns databaseKey
        every { accountsDao.getIds() } returns listOf(VISIBLE_ID, HIDDEN_ID, DELETED_ID)
        every { accountsDao.getDeletedIds() } returns listOf(DELETED_ID)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun kit_firstCall_migratesWithStoredKeyAndDropsDeletedAccountsBeforeStarting() = runTest(dispatcher) {
        val manager = createManager()

        assertSame(kit, manager.kit())

        coVerifyOrder {
            keyProvider.awaitKey(TonConnectManager.TON_CONNECT_DATABASE_ID)
            TonConnectKit.migrateDatabase(context, databaseKey)
            TonConnectKit.getInstance(context, databaseKey, any(), any())
            kit.removeDAppsExcept(listOf(VISIBLE_ID, HIDDEN_ID))
            kit.start()
        }
        assertSame(kit, manager.kitState.value)
    }

    @Test
    fun kit_secondCall_reusesCreatedKit() = runTest(dispatcher) {
        val manager = createManager()

        manager.kit()
        manager.kit()

        coVerify(exactly = 1) { TonConnectKit.getInstance(any(), any(), any(), any()) }
    }

    @Test
    fun kit_initializationFails_notCachedAndNextCallCreatesKit() = runTest(dispatcher) {
        coEvery { TonConnectKit.migrateDatabase(any(), any()) } throws
            InsufficientDatabaseMigrationSpaceException(requiredBytes = 2, availableBytes = 1) andThen
            DatabaseMigrationResult(0, 1)
        val manager = createManager()

        assertFailsWith<InsufficientDatabaseMigrationSpaceException> { manager.kit() }
        assertNull(manager.kitState.value)

        assertSame(kit, manager.kit())
        coVerify(exactly = 2) { TonConnectKit.migrateDatabase(any(), any()) }
    }

    @Test
    fun kit_databaseKeyMismatch_propagatesAndNeverClears() = runTest(dispatcher) {
        coEvery { TonConnectKit.getInstance(any(), any(), any(), any()) } throws
            DatabaseKeyMismatchException("ton-connect", IllegalStateException("wrong key"))
        val manager = createManager()

        assertFailsWith<DatabaseKeyMismatchException> { manager.kit() }

        coVerify(exactly = 0) { TonConnectKit.clear(any()) }
        assertNull(manager.kitState.value)
    }

    @Test
    fun start_initializationFails_doesNotCrashAndLaterCallRetries() = runTest(dispatcher) {
        coEvery { TonConnectKit.getInstance(any(), any(), any(), any()) } throws
            IllegalStateException("open failed") andThen kit
        val manager = createManager()

        manager.start()
        runCurrent()

        assertNull(manager.kitState.value)
        assertSame(kit, manager.kit())
    }

    @Test
    fun start_accountDeleted_removesDAppsOfAccountsDeletedOnAnyLevelOnly() = runTest(dispatcher) {
        val manager = createManager()
        manager.start()
        runCurrent()

        accountsDeleted.onNext(Unit)
        runCurrent()

        // Once at startup and once for the deletion; the hidden wallet of another level stays.
        coVerify(exactly = 2) { kit.removeDAppsExcept(listOf(VISIBLE_ID, HIDDEN_ID)) }
    }

    @Test
    fun getDApps_beforeKitReady_emitsOnceKitIsCreated() = runTest(dispatcher) {
        val dApp = mockk<DAppEntity>()
        every { kit.getDApps() } returns flowOf(listOf(dApp))
        val manager = createManager()
        val emitted = mutableListOf<List<DAppEntity>>()
        backgroundScope.launch { manager.getDApps().toList(emitted) }
        runCurrent()
        assertEquals(emptyList<List<DAppEntity>>(), emitted)

        manager.kit()
        runCurrent()

        assertEquals(listOf(listOf(dApp)), emitted)
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

        assertEquals(emptyList<DAppRequest>(), emitted)
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

    private fun TestScope.createManager() = TonConnectManager(
        context = context,
        adapterFactory = mockk(relaxed = true),
        appName = "P.cash Wallet",
        appVersion = "1.0",
        databaseKeyProvider = keyProvider,
        accountManager = accountManager,
        appDatabase = appDatabase,
        dispatcherProvider = TestDispatcherProvider(dispatcher, backgroundScope),
    )

    private class CapturingLogWriter : LogWriter() {
        val entries = mutableListOf<Pair<String, Throwable?>>()

        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            entries += message to throwable
        }
    }

    private companion object {
        const val VISIBLE_ID = "visible-account"
        const val HIDDEN_ID = "hidden-account"
        const val DELETED_ID = "deleted-account"
        const val VALID_URI = "tc://?v=2&id=client&r=%7B%7D"
        const val MALFORMED_URI = "tc://?v=2"
        const val SECRET = "SECRET-PAYLOAD"
    }
}
