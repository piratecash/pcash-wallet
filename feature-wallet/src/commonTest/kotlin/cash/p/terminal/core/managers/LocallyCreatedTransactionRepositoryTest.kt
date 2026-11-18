package cash.p.terminal.core.managers

import cash.p.terminal.core.storage.SingleDatabaseStorageRouter
import io.mockk.mockk
import io.mockk.every
import cash.p.terminal.core.storage.StaticAppDatabaseProvider
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.LocallyCreatedTransactionDao
import cash.p.terminal.core.storage.LocallyCreatedTransactionStorage
import cash.p.terminal.entities.LocallyCreatedTransactionRecord
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.ton.TonTransactionRecord
import cash.p.terminal.modules.transactions.TransactionStatus
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.models.Event
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class LocallyCreatedTransactionRepositoryTest {

    @Test
    fun markCreated_validHash_exists() = runTest {
        val repository = createRepository()

        repository.markCreated("account-1", "ethereum", " 0xhash ")

        assertTrue(repository.isCreated("account-1", "ethereum", "0xhash"))
    }

    @Test
    fun isCreated_sameHashDifferentBlockchain_returnsFalse() = runTest {
        val repository = createRepository()

        repository.markCreated("account-1", "ethereum", "same-hash")

        assertFalse(repository.isCreated("account-1", "polygon", "same-hash"))
    }

    @Test
    fun isCreated_sameHashDifferentAccount_returnsFalse() = runTest {
        val repository = createRepository()

        repository.markCreated("account-1", "ethereum", "same-hash")

        assertFalse(repository.isCreated("account-2", "ethereum", "same-hash"))
    }

    @Test
    fun isCreated_storageFailure_returnsFalse() = runTest {
        val dao = FakeLocallyCreatedTransactionDao()
        val repository = createRepository(dao)
        dao.existsFails = true

        assertFalse(repository.isCreated("account-1", "ethereum", "hash"))
    }

    @Test
    fun isCreated_parentCancelled_propagatesCancellation() = runTest {
        val repository = createRepository()
        var completed = false

        val job = launch {
            coroutineContext[Job]?.cancel()
            repository.isCreated("account-1", "ethereum", "hash")
            completed = true
        }
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertFalse(completed)
    }

    @Test
    fun markCreated_blankHash_ignored() = runTest {
        val dao = FakeLocallyCreatedTransactionDao()
        val repository = createRepository(dao)

        repository.markCreated("account-1", "ethereum", " ")

        assertEquals(0, dao.records.size)
    }

    @Test
    fun markCreated_storageFailure_doesNotThrow() = runTest {
        val dao = FakeLocallyCreatedTransactionDao()
        val repository = createRepository(dao)
        dao.insertFails = true

        repository.markCreated("account-1", "ethereum", "hash")

        assertEquals(0, dao.records.size)
    }

    @Test
    fun markCreated_parentCancelled_stillPersists() = runTest {
        val repository = createRepository()

        launch {
            coroutineContext[Job]?.cancel()
            repository.markCreated("account-1", "ethereum", "hash")
        }
        advanceUntilIdle()

        assertTrue(repository.isCreated("account-1", "ethereum", "hash"))
    }

    @Test
    fun markCreated_duplicateHash_keepsSingleRecord() = runTest {
        val dao = FakeLocallyCreatedTransactionDao()
        val repository = createRepository(dao)

        repository.markCreated("account-1", "ethereum", "hash")
        repository.markCreated("account-1", "ethereum", "hash")

        assertEquals(1, dao.records.size)
    }

    @Test
    fun markCreated_newRecord_emitsChangedFlow() = runTest {
        val repository = createRepository()
        val emissions = mutableListOf<Unit>()
        val job = launch {
            repository.changedFlow.collect {
                emissions += Unit
            }
        }
        advanceUntilIdle()

        repository.markCreated("account-1", "ethereum", "hash")
        advanceUntilIdle()

        assertEquals(1, emissions.size)
        job.cancel()
    }

    @Test
    fun markCreated_underLimit_doesNotTrim() = runTest {
        val dao = FakeLocallyCreatedTransactionDao()
        val repository = createRepository(dao)
        dao.countOverride = LocallyCreatedTransactionRepository.MAX_RECORDS_PER_ACCOUNT

        repository.markCreated("account-1", "ethereum", "hash")

        assertEquals(0, dao.trimCalls)
    }

    @Test
    fun markCreated_overLimit_trimsAccount() = runTest {
        val dao = FakeLocallyCreatedTransactionDao()
        val repository = createRepository(dao)
        dao.countOverride = LocallyCreatedTransactionRepository.MAX_RECORDS_PER_ACCOUNT + 1

        repository.markCreated("account-1", "ethereum", "hash")

        assertEquals(1, dao.trimCalls)
    }

    @Test
    fun trimAllAccounts_overLimit_keepsLatestPerAccount() = runTest {
        val dao = FakeLocallyCreatedTransactionDao()
        val storage = LocallyCreatedTransactionStorage(
            SingleDatabaseStorageRouter(StaticAppDatabaseProvider(mockk { every { locallyCreatedTransactionDao() } returns dao }))
        )

        repeat(3) { index ->
            storage.insert(record("account-1", "hash-$index", createdAt = index.toLong()))
            storage.insert(record("account-2", "hash-$index", createdAt = index.toLong()))
        }

        storage.trimAllAccounts(limit = 2)

        assertEquals(listOf("hash-2", "hash-1"), dao.hashes("account-1"))
        assertEquals(listOf("hash-2", "hash-1"), dao.hashes("account-2"))
    }

    @Test
    fun deleteByAccountIds_deletesOnlySelectedAccounts() = runTest {
        val dao = FakeLocallyCreatedTransactionDao()
        val repository = createRepository(dao)

        repository.markCreated("account-1", "ethereum", "hash-1")
        repository.markCreated("account-2", "ethereum", "hash-2")

        repository.deleteByAccountIds(listOf("account-1"))

        assertFalse(repository.isCreated("account-1", "ethereum", "hash-1"))
        assertTrue(repository.isCreated("account-2", "ethereum", "hash-2"))
    }

    @Test
    fun isCreated_nftTransferMarkedWithinWindow_returnsTrue() = runTest {
        val repository = createRepository()

        repository.markNftTransferCreated(ACCOUNT_ID, NFT_RAW, RECIPIENT_RAW)

        assertTrue(repository.isCreated(nftSendRecord()))
    }

    @Test
    fun isCreated_nftTransferOtherNft_returnsFalse() = runTest {
        val repository = createRepository()

        repository.markNftTransferCreated(ACCOUNT_ID, OTHER_RAW, RECIPIENT_RAW)

        assertFalse(repository.isCreated(nftSendRecord()))
    }

    @Test
    fun isCreated_nftTransferOtherRecipient_returnsFalse() = runTest {
        val repository = createRepository()

        repository.markNftTransferCreated(ACCOUNT_ID, NFT_RAW, OTHER_RAW)

        assertFalse(repository.isCreated(nftSendRecord()))
    }

    @Test
    fun isCreated_nftTransferOtherAccount_returnsFalse() = runTest {
        val repository = createRepository()

        repository.markNftTransferCreated("account-2", NFT_RAW, RECIPIENT_RAW)

        assertFalse(repository.isCreated(nftSendRecord()))
    }

    @Test
    fun isCreated_nftTransferOutsideWindow_returnsFalse() = runTest {
        val repository = createRepository()

        repository.markNftTransferCreated(ACCOUNT_ID, NFT_RAW, RECIPIENT_RAW)

        assertFalse(repository.isCreated(nftSendRecord(timestamp = nowSeconds() - 2.hours.inWholeSeconds)))
    }

    @Test
    fun isCreated_tonCoinSendToMarkedRecipient_returnsFalse() = runTest {
        val repository = createRepository()

        repository.markNftTransferCreated(ACCOUNT_ID, NFT_RAW, RECIPIENT_RAW)

        val coinValue = TransactionValue.CoinValue(tonToken, BigDecimal("-1"))
        assertFalse(repository.isCreated(tonSendRecord(coinValue, friendly(RECIPIENT_RAW), nowSeconds())))
    }

    @Test
    fun isCreated_sameNftSameRecipientSentTwice_bothRecordsCreated() = runTest {
        val repository = createRepository()
        val earlierSeconds = nowSeconds() - 3.hours.inWholeSeconds
        val earlierMs = earlierSeconds * 1000
        repository.markCreated(
            ACCOUNT_ID,
            BlockchainType.Ton.uid,
            "nft-transfer:$NFT_RAW:$RECIPIENT_RAW:$earlierMs",
            createdAt = earlierMs,
        )

        repository.markNftTransferCreated(ACCOUNT_ID, NFT_RAW, RECIPIENT_RAW)

        assertTrue(repository.isCreated(nftSendRecord(timestamp = earlierSeconds)))
        assertTrue(repository.isCreated(nftSendRecord()))
    }

    @Test
    fun markNftTransferCreated_unparseableAddress_writesNothing() = runTest {
        val dao = FakeLocallyCreatedTransactionDao()
        val repository = createRepository(dao)

        repository.markNftTransferCreated(ACCOUNT_ID, "not-an-address", RECIPIENT_RAW)

        assertEquals(0, dao.records.size)
    }

    private fun TestScope.createRepository(
        dao: FakeLocallyCreatedTransactionDao = FakeLocallyCreatedTransactionDao(),
    ): LocallyCreatedTransactionRepository {
        return LocallyCreatedTransactionRepository(
            storage = LocallyCreatedTransactionStorage(
            SingleDatabaseStorageRouter(StaticAppDatabaseProvider(mockk { every { locallyCreatedTransactionDao() } returns dao }))
        ),
            dispatcherProvider = TestDispatcherProvider(StandardTestDispatcher(testScheduler), this),
        )
    }

    private val tonToken = Token(
        coin = Coin(uid = "toncoin", name = "The Open Network", code = "TONCOIN"),
        blockchain = Blockchain(BlockchainType.Ton, "The Open Network", null),
        type = TokenType.Native,
        decimals = 9,
    )

    private fun nowSeconds() = System.currentTimeMillis() / 1000

    private fun friendly(raw: String) = Address.parse(raw).toUserFriendly(bounceable = true)

    // The blockchain record carries user-friendly addresses while the mark is written from raw ones.
    private fun nftSendRecord(timestamp: Long = nowSeconds()): TonTransactionRecord {
        val nft = TransactionValue.NftValue(
            mintAddress = friendly(NFT_RAW),
            value = BigDecimal("-1"),
            tokenName = null,
            tokenSymbol = "NFT",
        )
        return tonSendRecord(nft, friendly(RECIPIENT_RAW), timestamp)
    }

    private fun tonSendRecord(value: TransactionValue, to: String, timestamp: Long) = TonTransactionRecord(
        source = TransactionSource(
            blockchain = tonToken.blockchain,
            account = Account(
                id = ACCOUNT_ID,
                name = "Main",
                type = mockk<AccountType>(relaxed = true),
                origin = AccountOrigin.Created,
                level = 0,
            ),
            meta = null,
        ),
        event = Event(
            id = "ton-event",
            lt = 1L,
            timestamp = timestamp,
            scam = false,
            inProgress = false,
            extra = 0L,
            actions = emptyList(),
        ),
        token = tonToken,
        actions = listOf(
            TonTransactionRecord.Action(
                type = TonTransactionRecord.Action.Type.Send(value, to, sentToSelf = false, comment = null),
                status = TransactionStatus.Completed,
            )
        ),
    )

    private fun record(
        accountId: String,
        transactionHash: String,
        createdAt: Long,
    ): LocallyCreatedTransactionRecord {
        return LocallyCreatedTransactionRecord(
            accountId = accountId,
            blockchainTypeUid = "ethereum",
            transactionHash = transactionHash,
            createdAt = createdAt,
        )
    }

    private class FakeLocallyCreatedTransactionDao : LocallyCreatedTransactionDao {
        val records = mutableListOf<LocallyCreatedTransactionRecord>()
        var insertFails = false
        var existsFails = false
        var countOverride: Int? = null
        var trimCalls = 0

        override suspend fun insert(record: LocallyCreatedTransactionRecord): Long {
            if (insertFails) error("insert failed")
            if (exists(record.accountId, record.blockchainTypeUid, record.transactionHash)) return -1
            records += record
            return records.size.toLong()
        }

        override suspend fun exists(
            accountId: String,
            blockchainTypeUid: String,
            transactionHash: String,
        ): Boolean {
            if (existsFails) error("exists failed")
            return records.any {
                it.accountId == accountId &&
                    it.blockchainTypeUid == blockchainTypeUid &&
                    it.transactionHash == transactionHash
            }
        }

        override suspend fun getHashesCreatedBetween(
            accountId: String,
            blockchainTypeUid: String,
            fromMs: Long,
            toMs: Long,
        ): List<String> = records
            .filter {
                it.accountId == accountId && it.blockchainTypeUid == blockchainTypeUid && it.createdAt in fromMs..toMs
            }
            .map { it.transactionHash }

        override suspend fun getAccountIds(): List<String> {
            return records.map { it.accountId }.distinct()
        }

        override suspend fun trimAccount(accountId: String, limit: Int) {
            trimCalls++
            val keep = records
                .filter { it.accountId == accountId }
                .sortedByDescending { it.createdAt }
                .take(limit)
                .toSet()

            records.removeAll { it.accountId == accountId && it !in keep }
        }

        override suspend fun deleteByAccountIds(accountIds: List<String>) {
            records.removeAll { it.accountId in accountIds }
        }

        override suspend fun count(accountId: String): Int {
            countOverride?.let { return it }
            return records.count { it.accountId == accountId }
        }

        fun hashes(accountId: String): List<String> {
            return records
                .filter { it.accountId == accountId }
                .sortedByDescending { it.createdAt }
                .map { it.transactionHash }
        }
    }

    private companion object {
        const val ACCOUNT_ID = "account-1"
        const val NFT_RAW = "0:335996ba9cce6625ebfdd7019ced50927c1a08b1846a289a55efc37c01d77df9"
        const val RECIPIENT_RAW = "0:47caefa86550c7458435fab2a51aaa47cb9c80a9a9cf062ac440d30d3a67c72e"
        const val OTHER_RAW = "0:1111111111111111111111111111111111111111111111111111111111111111"
    }
}
