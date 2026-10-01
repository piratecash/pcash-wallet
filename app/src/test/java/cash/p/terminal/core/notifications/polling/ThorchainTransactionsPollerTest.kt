package cash.p.terminal.core.notifications.polling

import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.managers.ThorchainKitManager
import cash.p.terminal.core.managers.ThorchainKitManagers
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.thorchainkit.network.Network
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Flowable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ThorchainTransactionsPollerTest {

    private val thorchainKitManager = mockk<ThorchainKitManager>(relaxed = true)
    private val mayaKitManager = mockk<ThorchainKitManager>(relaxed = true)
    private val kitManagers = mockk<ThorchainKitManagers> {
        every { forType(BlockchainType.Thorchain) } returns thorchainKitManager
        every { forType(BlockchainType.Mayachain) } returns mayaKitManager
    }
    private val transactionAdapterManager = mockk<TransactionAdapterManager>(relaxed = true)
    private val poller = ThorchainTransactionsPoller(kitManagers, transactionAdapterManager)

    private fun mockWallet(blockchainType: BlockchainType) = mockk<Wallet>(relaxed = true) {
        every { token.blockchainType } returns blockchainType
    }

    private fun mockAdapter(records: List<TransactionRecord>) = mockk<ITransactionsAdapter>(relaxed = true) {
        every { transactionsState } returns AdapterState.Synced
        every { transactionsStateUpdatedFlowable } returns Flowable.never()
        coEvery { getTransactions(null, null, 100, any(), null) } returns records
    }

    @Test
    fun pollOnce_walletsOfBothChains_runsPollingSessionPerKitAndReturnsRecords() = runTest {
        val thorchainWallet = mockWallet(BlockchainType.Thorchain)
        val mayaWallet = mockWallet(BlockchainType.Mayachain)
        val thorchainRecord = mockk<TransactionRecord>()
        val mayaRecord = mockk<TransactionRecord>()
        every { transactionAdapterManager.adaptersReadyFlow } returns MutableStateFlow(
            mapOf(
                thorchainWallet.transactionSource to mockAdapter(listOf(thorchainRecord)),
                mayaWallet.transactionSource to mockAdapter(listOf(mayaRecord)),
            )
        )

        val result = poller.pollOnce(listOf(thorchainWallet, mayaWallet))

        assertEquals(setOf(thorchainRecord, mayaRecord), result.toSet())
        coVerifyOrder {
            thorchainKitManager.startForPolling()
            thorchainKitManager.stopForPolling()
        }
        coVerifyOrder {
            mayaKitManager.startForPolling()
            mayaKitManager.stopForPolling()
        }
    }

    @Test
    fun pollOnce_onlyThorchainWallet_leavesMayaKitAlone() = runTest {
        every { transactionAdapterManager.adaptersReadyFlow } returns MutableStateFlow(emptyMap())

        poller.pollOnce(listOf(mockWallet(BlockchainType.Thorchain)))

        coVerify(exactly = 0) { mayaKitManager.startForPolling() }
    }

    @Test
    fun pollOnce_timeout_returnsEmpty() = runTest {
        coEvery { thorchainKitManager.startForPolling() } coAnswers { delay(60_001) }

        val result = poller.pollOnce(listOf(mockWallet(BlockchainType.Thorchain)))

        assertTrue(result.isEmpty())
    }

    @Test
    fun pollOnce_timeoutWhileLifecycleMutexHeld_releasesPollingSession() = runTest {
        val kitManager = realKitManager()
        val lifecycleMutex = kitManagerField(kitManager, "lifecycleMutex") as Mutex
        val wallet = mockWallet(BlockchainType.Thorchain)
        val stalledAdapter = mockk<ITransactionsAdapter>(relaxed = true) {
            every { transactionsState } returns AdapterState.Synced
            every { transactionsStateUpdatedFlowable } returns Flowable.never()
            coEvery { getTransactions(null, null, 100, any(), null) } coAnswers {
                lifecycleMutex.lock()
                awaitCancellation()
            }
        }
        every { transactionAdapterManager.adaptersReadyFlow } returns MutableStateFlow(
            mapOf(wallet.transactionSource to stalledAdapter)
        )
        val poller = ThorchainTransactionsPoller(
            mockk { every { forType(BlockchainType.Thorchain) } returns kitManager },
            transactionAdapterManager,
        )
        launch {
            delay(TransactionsPoller.POLLING_TIMEOUT_MS + 1)
            lifecycleMutex.unlock()
        }

        poller.pollOnce(listOf(wallet))

        assertEquals(0, (kitManagerField(kitManager, "pollingSessionCount") as AtomicInteger).get())
    }

    private fun realKitManager() = ThorchainKitManager(
        Network.Mainnet,
        BlockchainType.Thorchain,
        emptyList(),
        mockk(),
        mockk(),
        mockk<BackgroundManager> {
            every { stateFlow } returns MutableStateFlow(BackgroundManagerState.EnterForeground)
        },
        mockk(),
        mockk(),
        mockk(relaxed = true),
    )

    private fun kitManagerField(manager: ThorchainKitManager, name: String): Any? =
        ThorchainKitManager::class.java.getDeclaredField(name).apply { isAccessible = true }.get(manager)
}
