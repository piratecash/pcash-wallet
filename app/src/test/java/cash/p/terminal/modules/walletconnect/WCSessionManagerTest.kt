package cash.p.terminal.modules.walletconnect

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.AccountsDao
import cash.p.terminal.core.storage.AppDatabase
import cash.p.terminal.modules.walletconnect.storage.WCSessionStorage
import cash.p.terminal.modules.walletconnect.storage.WalletConnectV2Session
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.wallet.IAccountManager
import com.reown.walletkit.client.Wallet
import com.reown.walletkit.client.WalletKit
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import io.reactivex.BackpressureStrategy
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WCSessionManagerTest {

    private val dispatcher = StandardTestDispatcher()
    private val accountsDeleted = PublishSubject.create<Unit>()
    private val accountsDao = mockk<AccountsDao>()
    private lateinit var database: AppDatabase
    private lateinit var appDatabase: AppDatabase
    private lateinit var storage: WCSessionStorage

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appDatabase = mockk<AppDatabase> {
            every { wcSessionDao() } returns database.wcSessionDao()
            every { accountsDao() } returns accountsDao
        }
        storage = WCSessionStorage(appDatabase)

        mockkObject(WCDelegate, WalletKit)
        // getListOfActiveSessions is @JvmStatic, which mockkObject does not intercept.
        mockkStatic(WalletKit::class)
        every { WCDelegate.deleteSession(any(), any(), any()) } just runs
        every { WalletKit.getPendingListOfSessionRequests(any()) } returns emptyList()
        liveTopics(T_A)
    }

    @After
    fun tearDown() {
        unmockkAll()
        database.close()
    }

    @Test
    fun accountDeleted_liveSessionOfDeletedAccount_disconnectsWithoutRebinding() = runTest(dispatcher) {
        storage.save(listOf(WalletConnectV2Session(A, T_A)))
        accountIds(all = listOf(A, B), deleted = listOf(A))

        val manager = startAndDeleteAccount(active = B)

        verify { WCDelegate.deleteSession(T_A, any(), any()) }
        assertTrue(manager.sessionsFlow.value.isEmpty())
        assertTrue(storage.getSessionsByAccountId(B).isEmpty())
    }

    @Test
    fun accountDeleted_liveSessionOfOtherLevelAccount_keepsSessionWithItsAccount() = runTest(dispatcher) {
        liveTopics(T_H)
        storage.save(listOf(WalletConnectV2Session(H, T_H)))
        accountIds(all = listOf(B, H), deleted = emptyList())

        val manager = startAndDeleteAccount(active = B)

        verify(exactly = 0) { WCDelegate.deleteSession(any(), any(), any()) }
        assertEquals(listOf(WalletConnectV2Session(H, T_H)), storage.getAllSessions())
        assertTrue(manager.sessionsFlow.value.isEmpty())
    }

    @Test
    fun start_noActiveAccount_disconnectsOrphanedSession() = runTest(dispatcher) {
        storage.save(listOf(WalletConnectV2Session(A, T_A)))
        accountIds(all = listOf(A), deleted = listOf(A))

        startManager(active = null)

        verify { WCDelegate.deleteSession(T_A, any(), any()) }
    }

    @Test
    fun accountDeleted_disconnectFails_keepsRowAndRetriesOnNextSync() = runTest(dispatcher) {
        var attempts = 0
        every { WCDelegate.deleteSession(any(), any(), any()) } answers {
            attempts++
            thirdArg<(Throwable) -> Unit>().invoke(IllegalStateException("offline"))
        }
        storage.save(listOf(WalletConnectV2Session(A, T_A)))
        accountIds(all = listOf(A, B), deleted = listOf(A))

        startManager(active = B)
        val attemptsAfterStart = attempts

        assertEquals(listOf(WalletConnectV2Session(A, T_A)), storage.getAllSessions())
        assertTrue(attemptsAfterStart > 0)

        accountsDeleted.onNext(Unit)
        runCurrent()

        assertTrue(attempts > attemptsAfterStart)
    }

    private fun accountIds(all: List<String>, deleted: List<String>) {
        every { accountsDao.getIds() } returns all
        every { accountsDao.getDeletedIds() } returns deleted
    }

    private fun liveTopics(vararg topics: String) {
        val sessions = topics.map { topic -> mockk<Wallet.Model.Session> { every { this@mockk.topic } returns topic } }
        every { WCDelegate.getActiveSessions() } returns sessions
        every { WalletKit.getListOfActiveSessions() } returns sessions
    }

    private suspend fun TestScope.startManager(active: String?): WCSessionManager {
        val activeAccount = active?.let { id -> mockk<Account> { every { this@mockk.id } returns id } }
        val accountManager = mockk<IAccountManager> {
            every { this@mockk.activeAccount } returns activeAccount
            every { accounts } returns listOfNotNull(activeAccount)
            every { activeAccountStateFlow } returns
                MutableStateFlow(ActiveAccountState.ActiveAccount(activeAccount))
            every { accountsDeletedFlowable } returns accountsDeleted.toFlowable(BackpressureStrategy.BUFFER)
        }
        val manager = WCSessionManager(
            accountManager,
            storage,
            appDatabase,
            TestDispatcherProvider(dispatcher, backgroundScope),
        )
        manager.start()
        runCurrent()
        return manager
    }

    private suspend fun TestScope.startAndDeleteAccount(active: String): WCSessionManager {
        val manager = startManager(active)
        accountsDeleted.onNext(Unit)
        runCurrent()
        return manager
    }

    private companion object {
        const val A = "account-a"
        const val B = "account-b"
        const val H = "account-hidden"
        const val T_A = "topic-a"
        const val T_H = "topic-h"
    }
}
