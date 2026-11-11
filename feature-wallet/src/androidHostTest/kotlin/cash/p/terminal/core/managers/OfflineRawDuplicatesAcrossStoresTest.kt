package cash.p.terminal.core.managers

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.AppDatabase
import cash.p.terminal.core.storage.SingleDatabaseStorageRouter
import cash.p.terminal.core.storage.StaticAppDatabaseProvider
import cash.p.terminal.core.storage.StorageRouter
import cash.p.terminal.entities.OfflineSignedTransactionEntity
import cash.p.terminal.entities.OfflineSignedTransactionStatus
import cash.p.terminal.wallet.entities.AccountRecord
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class OfflineRawDuplicatesAcrossStoresTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var firstStore: AppDatabase
    private lateinit var secondStore: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        firstStore = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        secondStore = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        firstStore.close()
        secondStore.close()
    }

    @Test
    fun markBroadcastedRawDuplicates_broadcastInAnotherStore_marksThePendingRecordBroadcasted() = runTest(dispatcher) {
        firstStore.seed(FIRST_ACCOUNT, row(FIRST_ACCOUNT, "local-hash", PENDING))
        secondStore.seed(SECOND_ACCOUNT, row(SECOND_ACCOUNT, "network-hash", BROADCASTED, broadcastedAt = 5))

        repository(splitRouter()).markBroadcastedRawDuplicates(FIRST_ACCOUNT)

        val record = firstStore.rows(FIRST_ACCOUNT).single()
        assertEquals("network-hash", record.txHash)
        assertEquals(BROADCASTED, record.status)
    }

    @Test
    fun markBroadcastedRawDuplicates_broadcastsInSeveralStores_takesTheNewestBroadcast() = runTest(dispatcher) {
        firstStore.seed(
            FIRST_ACCOUNT,
            row(FIRST_ACCOUNT, "local-hash", PENDING),
            row(FIRST_ACCOUNT, "older-hash", BROADCASTED, broadcastedAt = 5),
        )
        secondStore.seed(SECOND_ACCOUNT, row(SECOND_ACCOUNT, "newest-hash", BROADCASTED, broadcastedAt = 9))

        repository(splitRouter()).markBroadcastedRawDuplicates(FIRST_ACCOUNT)

        assertEquals(setOf("newest-hash", "older-hash"), firstStore.rows(FIRST_ACCOUNT).map { it.txHash }.toSet())
    }

    @Test
    fun markBroadcastedRawDuplicates_otherRawOnly_leavesThePendingRecord() = runTest(dispatcher) {
        firstStore.seed(FIRST_ACCOUNT, row(FIRST_ACCOUNT, "local-hash", PENDING))
        secondStore.seed(SECOND_ACCOUNT, row(SECOND_ACCOUNT, "network-hash", BROADCASTED, rawHex = "other"))

        repository(splitRouter()).markBroadcastedRawDuplicates(FIRST_ACCOUNT)

        assertEquals(PENDING, firstStore.rows(FIRST_ACCOUNT).single().status)
    }

    @Test
    fun markBroadcastedRawDuplicates_singleDatabase_touchesOnlyThePendingRecordOfTheAccount() = runTest(dispatcher) {
        firstStore.seed(FIRST_ACCOUNT, row(FIRST_ACCOUNT, "local-hash", PENDING))
        firstStore.seed(
            SECOND_ACCOUNT,
            row(SECOND_ACCOUNT, "other-pending", PENDING),
            row(SECOND_ACCOUNT, "network-hash", BROADCASTED, broadcastedAt = 5),
        )
        val router = SingleDatabaseStorageRouter(StaticAppDatabaseProvider(firstStore))

        repository(router).markBroadcastedRawDuplicates(FIRST_ACCOUNT)

        val record = firstStore.rows(FIRST_ACCOUNT).single()
        assertEquals("network-hash", record.txHash)
        assertEquals(BROADCASTED, record.status)
        assertEquals(PENDING, firstStore.rows(SECOND_ACCOUNT).single { it.txHash == "other-pending" }.status)
    }

    private fun splitRouter() = mockk<StorageRouter> {
        every { account(FIRST_ACCOUNT) } returns firstStore
        every { account(SECOND_ACCOUNT) } returns secondStore
        every { accounts } returns listOf(firstStore, secondStore)
    }

    private fun repository(router: StorageRouter) = OfflineSignedTransactionRepository(
        router = router,
        dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
    )

    private suspend fun AppDatabase.seed(accountId: String, vararg rows: OfflineSignedTransactionEntity) {
        accountsDao().insert(accountRecord(accountId))
        rows.forEach { offlineSignedTransactionDao().insertIfAbsent(it) }
    }

    private suspend fun AppDatabase.rows(accountId: String) = offlineSignedTransactionDao().observe(accountId).first()

    private fun accountRecord(id: String) = AccountRecord(
        id = id,
        name = id,
        type = "mnemonic",
        origin = "Restored",
        isBackedUp = true,
        isFileBackedUp = false,
        words = null,
        passphrase = null,
        key = null,
        level = 0,
    )

    private fun row(
        accountId: String,
        txHash: String,
        status: String,
        broadcastedAt: Long? = null,
        rawHex: String = RAW_HEX,
    ) = OfflineSignedTransactionEntity(
        accountId = accountId,
        txHash = txHash,
        blockchainTypeUid = "bitcoin",
        tokenQueryId = "bitcoin|native",
        sourceTokenQueryId = "bitcoin|native",
        coinUid = "bitcoin",
        coinCode = "BTC",
        coinName = "Bitcoin",
        tokenDecimals = 8,
        amount = "0.001",
        feeTokenQueryId = null,
        feeAtomic = null,
        solanaBlockHash = null,
        solanaLastValidBlockHeight = null,
        tonValidUntil = null,
        tonSenderAddress = null,
        tonSeqno = null,
        tronExpiration = null,
        stellarSourceAccountId = null,
        stellarSequenceNumber = null,
        stellarValidUntil = null,
        toAddress = "addr",
        rawHex = rawHex,
        pcashPayload = "pcash:tx:v1:bitcoin:body",
        createdAt = 1,
        status = status,
        broadcastAttempts = 0,
        lastBroadcastAt = null,
        broadcastedAt = broadcastedAt,
        lastError = null,
    )

    private companion object {
        const val FIRST_ACCOUNT = "first-account"
        const val SECOND_ACCOUNT = "second-account"
        const val RAW_HEX = "deadbeef"
        val PENDING = OfflineSignedTransactionStatus.Pending.value
        val BROADCASTED = OfflineSignedTransactionStatus.Broadcasted.value
    }
}
