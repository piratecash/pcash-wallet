package cash.p.terminal.core.storage

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StorageRouterExtensionsTest {

    private val first = mockk<AccountDaos>()
    private val second = mockk<AccountDaos>()
    private val router = mockk<StorageRouter> {
        every { accounts } returns listOf(first, second)
        every { accountStores } returns flowOf(listOf(first, second))
        every { account("first") } returns first
        every { account("second") } returns second
        every { account("invisible") } returns null
    }

    @Test
    fun observeAcrossAccounts_oneStoreChanges_reemitsAllStoresInOrder() = runTest {
        val firstRows = MutableStateFlow(listOf("a"))
        val secondRows = MutableStateFlow(listOf("b"))
        val flows: Map<AccountDaos, Flow<List<String>>> = mapOf(first to firstRows, second to secondRows)
        val emitted = mutableListOf<List<String>>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            router.observeAcrossAccounts { flows.getValue(this) }.collect(emitted::add)
        }
        secondRows.value = listOf("b", "c")
        job.cancel()

        assertEquals(listOf(listOf("a", "b"), listOf("a", "b", "c")), emitted)
    }

    @Test
    fun observeAcrossAccounts_storeAddedWhileSubscribed_recombinesWithItsRows() = runTest {
        val stores = MutableStateFlow(listOf(first))
        every { router.accountStores } returns stores
        val flows: Map<AccountDaos, Flow<List<String>>> =
            mapOf(first to MutableStateFlow(listOf("a")), second to MutableStateFlow(listOf("b")))
        val emitted = mutableListOf<List<String>>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            router.observeAcrossAccounts { flows.getValue(this) }.collect(emitted::add)
        }
        stores.value = listOf(first, second)
        job.cancel()

        assertEquals(listOf(listOf("a"), listOf("a", "b")), emitted)
    }

    @Test
    fun observeAcrossAccounts_noStores_emitsEmptyListInsteadOfNeverEmitting() = runTest {
        every { router.accountStores } returns flowOf(emptyList())

        val rows = router.observeAcrossAccounts<String> { error("no store to query") }.first()

        assertEquals(emptyList<String>(), rows)
    }

    @Test
    fun writePerStore_rowsOfSeveralStores_writesOneBatchPerStoreAndSkipsInvisible() {
        val batches = mutableMapOf<AccountDaos, List<String>>()

        router.writePerStore(listOf("first", "second", "invisible", "first"), { it }) { batches[this] = it }

        assertEquals(mapOf(first to listOf("first", "first"), second to listOf("second")), batches)
    }
}
