package cash.p.terminal.core.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.network.swaprepository.SwapProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SwapProviderTransactionsDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: SwapProviderTransactionsDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.swapProviderTransactionsDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun getUnmatchedSwapsByTokenOut_actualAmountSet_matchesActualNotQuote() = runTest {
        dao.insert(
            swap(
                date = 1_000L,
                amountOut = BigDecimal("0.02034985"),
                amountOutReal = BigDecimal("0.02013031"),
            )
        )

        val result = unmatchedFor()

        assertEquals(1, result.size)
        assertEquals(1_000L, result.single().date)
    }

    @Test
    fun getUnmatchedSwapsByTokenOut_actualAmountNull_matchesQuote() = runTest {
        dao.insert(
            swap(
                date = 1_000L,
                amountOut = BigDecimal("0.02013"),
                amountOutReal = null,
            )
        )

        val result = unmatchedFor()

        assertEquals(1, result.size)
        assertEquals(1_000L, result.single().date)
    }

    @Test
    fun getUnmatchedSwapsByTokenOut_actualAmountSetFarFromIncoming_notReturned() = runTest {
        dao.insert(
            swap(
                date = 1_000L,
                amountOut = BigDecimal("0.02013"),
                amountOutReal = BigDecimal("0.03"),
            )
        )

        val result = unmatchedFor()

        assertTrue(result.isEmpty())
    }

    @Test
    fun getUnmatchedSwapsByTokenOut_alreadyMatched_notReturned() = runTest {
        dao.insert(
            swap(
                date = 1_000L,
                amountOut = BigDecimal("0.02013"),
                amountOutReal = null,
                incomingRecordUid = "already-matched-uid",
            )
        )

        val result = unmatchedFor()

        assertTrue(result.isEmpty())
    }

    @Test
    fun updateStatusFields_rowAlreadyMatched_keepsBlockchainAmount() = runTest {
        dao.insert(
            swap(
                date = 1_000L,
                amountOut = BigDecimal("0.02013"),
                amountOutReal = BigDecimal("0.02011"),
                incomingRecordUid = "matched-uid",
            )
        )

        dao.updateStatusFields(date = 1_000L, status = "failed", amountOutReal = BigDecimal("0.03"), finishedAt = null)

        val row = dao.getByDate(1_000L)
        assertEquals("failed", row?.status)
        assertEquals(0, BigDecimal("0.02011").compareTo(row?.amountOutReal))
    }

    @Test
    fun updateStatusFields_rowNotMatched_writesAmount() = runTest {
        dao.insert(swap(date = 1_000L, amountOut = BigDecimal("0.02013"), amountOutReal = null))

        dao.updateStatusFields(date = 1_000L, status = "failed", amountOutReal = BigDecimal("0.03"), finishedAt = null)

        val row = dao.getByDate(1_000L)
        assertEquals("failed", row?.status)
        assertEquals(0, BigDecimal("0.03").compareTo(row?.amountOutReal))
    }

    @Test
    fun getByAddressAndAmount_actualAmountNull_matchesQuote() = runTest {
        dao.insert(swap(date = 1_000L, amountOut = BigDecimal("18159.295232"), amountOutReal = null))

        assertEquals(1_000L, byAddressAndAmount(amount = 18159.2)?.date)
    }

    @Test
    fun getByAddressAndAmount_actualAmountZero_notReturned() = runTest {
        dao.insert(swap(date = 1_000L, amountOut = BigDecimal("18159.295232"), amountOutReal = BigDecimal.ZERO))

        assertNull(byAddressAndAmount(amount = 18159.2))
    }

    @Test
    fun syncReceivedAt_linkedRecord_writesTimestamp() = runTest {
        dao.insert(linkedSwap(incomingRecordUid = INCOMING_UID))

        dao.syncReceivedAt(date = 1_000L, incomingRecordUid = INCOMING_UID, receivedAt = 5_000L)

        assertEquals(5_000L, dao.getByDate(1_000L)?.receivedAt)
    }

    @Test
    fun syncReceivedAt_differentIncomingRecord_doesNotWrite() = runTest {
        dao.insert(linkedSwap(incomingRecordUid = "other-uid"))

        dao.syncReceivedAt(date = 1_000L, incomingRecordUid = INCOMING_UID, receivedAt = 5_000L)

        assertNull(dao.getByDate(1_000L)?.receivedAt)
    }

    @Test
    fun syncReceivedAt_changedTimestamp_overwrites() = runTest {
        dao.insert(linkedSwap(incomingRecordUid = INCOMING_UID))
        dao.syncReceivedAt(date = 1_000L, incomingRecordUid = INCOMING_UID, receivedAt = 5_000L)

        dao.syncReceivedAt(date = 1_000L, incomingRecordUid = INCOMING_UID, receivedAt = 6_000L)

        assertEquals(6_000L, dao.getByDate(1_000L)?.receivedAt)
    }

    @Test
    fun observeAllByAccount_finishedPayCoreRow_isReturnedWithPayCoreProvider() = runTest {
        dao.insert(swap(date = 1_000L, provider = SwapProvider.PAYCORE, coinUidIn = "rub"))

        val row = dao.observeAllByAccount(ACCOUNT_ID, limit = 10).first().single()

        assertEquals(SwapProvider.PAYCORE, row.provider)
        assertEquals("rub", row.coinUidIn)
    }

    @Test
    fun getByDate_finishedPayCoreRow_isReturnedWithPayCoreProvider() = runTest {
        dao.insert(swap(date = 1_000L, provider = SwapProvider.PAYCORE, coinUidIn = "rub"))

        assertEquals(SwapProvider.PAYCORE, dao.getByDate(1_000L)?.provider)
    }

    @Test
    fun getAllUnfinishedByAccount_newerPayCoreRowsExcluded_olderLiveProviderRowStillReturned() = runTest {
        insertPayCoreRowsNewerThanChangeNowRow()

        val rows = dao.getAllUnfinishedByAccount(
            accountId = ACCOUNT_ID,
            statusesExcluded = SwapProviderTransaction.FINISHED_STATUSES,
            providersExcluded = listOf(SwapProvider.PAYCORE),
            limit = 10,
        )

        assertEquals(listOf(SwapProvider.CHANGENOW), rows.map { it.provider })
    }

    @Test
    fun getAll_newerPayCoreRowsExcluded_olderLiveProviderRowStillReturned() = runTest {
        insertPayCoreRowsNewerThanChangeNowRow()

        val rows = dao.getAll(
            tokenQueryId = "ethereum|native",
            coinUid = "ethereum",
            blockchainType = BlockchainType.Ethereum.uid,
            address = "0xIn",
            statusesExcluded = SwapProviderTransaction.FINISHED_STATUSES,
            providersExcluded = listOf(SwapProvider.PAYCORE),
            limit = 10,
        )

        assertEquals(listOf(SwapProvider.CHANGENOW), rows.map { it.provider })
    }

    private fun insertPayCoreRowsNewerThanChangeNowRow() {
        dao.insert(swap(date = 1_000L, status = "waiting"))
        repeat(10) { dao.insert(swap(date = 2_000L + it, status = "waiting", provider = SwapProvider.PAYCORE)) }
    }

    private fun linkedSwap(incomingRecordUid: String) = swap(
        date = 1_000L,
        amountOut = BigDecimal("0.02013"),
        amountOutReal = BigDecimal("0.02011"),
        incomingRecordUid = incomingRecordUid,
    )

    private fun byAddressAndAmount(amount: Double) = dao.getByAddressAndAmount(
        address = "t1AddressOut",
        tokenQueryId = "zcash|native",
        coinUid = COIN_UID,
        blockchainType = BLOCKCHAIN_TYPE,
        accountId = ACCOUNT_ID,
        amount = amount,
        tolerance = 0.005,
        timestamp = 1_500L,
        timeWindowMs = 1_000L,
        dateFrom = 0L,
        dateTo = 2_000L,
    )

    private suspend fun unmatchedFor(amount: Double = 0.02013) = dao.getUnmatchedSwapsByTokenOut(
        tokenQueryId = "zcash|native",
        coinUid = COIN_UID,
        blockchainType = BLOCKCHAIN_TYPE,
        accountId = ACCOUNT_ID,
        dateFrom = 0L,
        dateTo = 2_000L,
        amount = amount,
        tolerance = 0.005,
        limit = 10,
    )

    private fun swap(
        date: Long,
        amountOut: BigDecimal = BigDecimal.ONE,
        amountOutReal: BigDecimal? = null,
        incomingRecordUid: String? = null,
        provider: SwapProvider = SwapProvider.CHANGENOW,
        coinUidIn: String = "ethereum",
        status: String = "finished",
    ) = SwapProviderTransaction(
        date = date,
        outgoingRecordUid = null,
        transactionId = "tx-$date",
        status = status,
        provider = provider,
        coinUidIn = coinUidIn,
        blockchainTypeIn = BlockchainType.Ethereum.uid,
        amountIn = BigDecimal("1"),
        addressIn = "0xIn",
        coinUidOut = COIN_UID,
        blockchainTypeOut = BLOCKCHAIN_TYPE,
        amountOut = amountOut,
        addressOut = "t1AddressOut",
        amountOutReal = amountOutReal,
        incomingRecordUid = incomingRecordUid,
        accountId = ACCOUNT_ID,
    )

    private companion object {
        const val COIN_UID = "zcash"
        const val ACCOUNT_ID = "test-account"
        const val INCOMING_UID = "incoming-uid"
        val BLOCKCHAIN_TYPE = BlockchainType.Zcash.uid
    }
}
