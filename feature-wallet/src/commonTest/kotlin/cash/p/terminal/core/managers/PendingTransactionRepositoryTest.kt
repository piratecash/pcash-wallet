package cash.p.terminal.core.managers

import cash.p.terminal.core.StorageLockedException
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.PendingTransactionStorage
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.IBackgroundManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingTransactionRepositoryTest {

    @Test
    fun init_storageClosedUnderCleanup_doesNotThrowUncaught() = runTest(UnconfinedTestDispatcher()) {
        val storage = mockk<PendingTransactionStorage> {
            coEvery { hasPendingTransactions() } throws StorageLockedException()
        }
        val backgroundManager = mockk<IBackgroundManager> {
            every { stateFlow } returns MutableStateFlow(BackgroundManagerState.EnterForeground)
        }

        PendingTransactionRepository(
            storage,
            TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler), backgroundScope),
            backgroundManager,
        )

        coVerify { storage.hasPendingTransactions() }
    }
}
