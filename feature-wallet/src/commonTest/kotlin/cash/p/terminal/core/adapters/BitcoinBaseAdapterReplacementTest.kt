package cash.p.terminal.core.adapters

import cash.p.terminal.core.TransactionSettingsStorage
import cash.p.terminal.core.managers.BackgroundKeepAliveManager
import cash.p.terminal.core.managers.NetworkErrorTracker
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.wallet.WalletFactory
import io.horizontalsystems.bitcoincore.BitcoinCore
import io.horizontalsystems.bitcoincore.models.TransactionInfo
import io.horizontalsystems.bitcoincore.models.TransactionStatus
import io.horizontalsystems.bitcoincore.models.TransactionType
import io.horizontalsystems.bitcoinkit.BitcoinKit
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.Single
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class BitcoinBaseAdapterReplacementTest {

    private val kit = mockk<BitcoinKit>(relaxed = true)
    private val wallet = WalletFactory.previewWallet()
    private var storedLinks = emptySet<String>()
    private val storage = mockk<TransactionSettingsStorage> {
        every { bitcoinReplacementLinks } answers { storedLinks }
        every { bitcoinReplacementLinks = any() } answers { storedLinks = firstArg() }
    }
    private val registry = BitcoinReplacementRegistry(storage)

    private val original = info(uid = "uid-A", hash = "A")
    private val replacement = info(uid = "uid-B", hash = "B")

    @Before
    fun setUp() {
        startKoin {
            modules(module {
                single { mockk<OfflineModeManager>(relaxed = true) }
                single { mockk<BackgroundKeepAliveManager>(relaxed = true) }
                single { mockk<NetworkErrorTracker>(relaxed = true) }
                single { registry }
            })
        }
        every { kit.getTransaction("A") } returns original
        every { kit.getTransaction("B") } returns replacement
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun getTransactions_pageWithSupersededRecord_refilledUpToLimit() = runTest {
        rememberSpeedUp()
        kitHolds(replacement, original, info("uid-1", "t1"), info("uid-2", "t2"), info("uid-3", "t3"))

        val page = transactions(limit = 3)

        assertEquals(listOf("uid-B", "uid-1", "uid-2"), page.map { it.uid })
        verify { kit.transactions("uid-1", null, 1) }
    }

    @Test
    fun getTransactions_kitExhausted_returnsShortPage() = runTest {
        rememberSpeedUp()
        kitHolds(replacement, original, info("uid-1", "t1"))

        val page = transactions(limit = 3)

        assertEquals(listOf("uid-B", "uid-1"), page.map { it.uid })
    }

    @Test
    fun getTransactions_supersededRecordLastInKit_continuesFromItAndStops() = runTest {
        rememberSpeedUp()
        kitHolds(info("uid-1", "t1"), replacement, original)

        val page = transactions(limit = 3)

        assertEquals(listOf("uid-1", "uid-B"), page.map { it.uid })
        verify(exactly = 1) { kit.transactions("uid-A", null, 1) }
    }

    @Test
    fun getTransactions_foreignConflictWithoutLink_recordStaysVisible() = runTest {
        val doubleSpent = info(uid = "uid-1", hash = "t1", conflictingHash = "foreign")
        kitHolds(doubleSpent)

        val record = transactions(limit = 3).single() as BitcoinTransactionRecord

        assertEquals("foreign", record.conflictingHash)
        assertFalse(record.ownReplacementConflict)
        assertNull(record.replacedByHash)
    }

    @Test
    fun transactionRecord_supersededOriginal_notReplaceableAndPointsToReplacement() = runTest {
        rememberSpeedUp()

        val record = createAdapter().transactionRecord(original)

        assertFalse(record.replaceable)
        assertEquals("B", record.replacedByHash)
    }

    @Test
    fun transactionRecord_winnerRestoredWithNewUid_replacesRootAndKeepsPreviousUid() = runTest {
        rememberSpeedUp()
        every { kit.getTransaction("A") } returns null

        val record = createAdapter().transactionRecord(info(uid = "uid-B-restored", hash = "B"))

        assertEquals("A", record.replacesHash)
        assertNull(record.replacedByHash)
        assertEquals(setOf("uid-A", "uid-B"), record.replacementChainUids.toSet())
    }

    private suspend fun rememberSpeedUp() = registry.remember(
        wallet.account.id,
        wallet.token.blockchainType.uid,
        BitcoinReplacementLink("A", "uid-A", "B", "uid-B")
    )

    private fun kitHolds(vararg transactions: TransactionInfo) {
        every { kit.transactions(any(), any(), any()) } answers {
            val start = transactions.indexOfFirst { it.uid == firstArg<String?>() } + 1
            Single.just(transactions.drop(start).take(requireNotNull(thirdArg<Int?>())))
        }
    }

    private suspend fun transactions(limit: Int) =
        createAdapter().getTransactions(null, null, limit, FilterTransactionType.All, null)

    private fun createAdapter() = BitcoinAdapter(
        kit = kit,
        syncMode = BitcoinCore.SyncMode.Full(),
        backgroundManager = mockk(relaxed = true),
        wallet = wallet,
    )

    private fun info(uid: String, hash: String, conflictingHash: String? = null) = TransactionInfo(
        uid = uid,
        transactionHash = hash,
        transactionIndex = 0,
        inputs = emptyList(),
        outputs = emptyList(),
        amount = 1_000,
        type = TransactionType.Outgoing,
        fee = 10,
        blockHeight = null,
        timestamp = 0,
        status = TransactionStatus.RELAYED,
        conflictingTxHash = conflictingHash,
        rbfEnabled = true,
    )
}
