package cash.p.terminal.core.managers

import cash.p.terminal.core.storage.NftItemStorage
import cash.p.terminal.core.usecase.OfflineModeUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.IOException

class AccountStorageCleanerTest {

    private val locallyCreatedTransactionRepository = mockk<LocallyCreatedTransactionRepository>(relaxed = true)
    private val offlineModeUseCase = mockk<OfflineModeUseCase>(relaxed = true)

    private val nftItemStorage = mockk<NftItemStorage>(relaxed = true)

    private val cleaner = AccountStorageCleaner(locallyCreatedTransactionRepository, offlineModeUseCase, nftItemStorage)

    @Test
    fun clearAccounts_allCleanersSucceed_clearsEveryTable() = runTest {
        cleaner.clearAccounts(ACCOUNT_IDS)

        coVerify(exactly = 1) { locallyCreatedTransactionRepository.deleteByAccountIds(ACCOUNT_IDS) }
        coVerify(exactly = 1) { offlineModeUseCase.forgetAccounts(ACCOUNT_IDS) }
        coVerify(exactly = 1) { nftItemStorage.deleteByAccountIds(ACCOUNT_IDS) }
    }

    @Test
    fun clearAccounts_firstCleanerFails_stillClearsOtherTables() = runTest {
        coEvery { locallyCreatedTransactionRepository.deleteByAccountIds(any()) } throws IOException("disk full")

        var thrown: Throwable? = null
        try {
            cleaner.clearAccounts(ACCOUNT_IDS)
        } catch (e: IOException) {
            thrown = e
        }

        coVerify(exactly = 1) { offlineModeUseCase.forgetAccounts(ACCOUNT_IDS) }
        coVerify(exactly = 1) { nftItemStorage.deleteByAccountIds(ACCOUNT_IDS) }
        assertNotNull(thrown)
    }

    @Test
    fun clearAccounts_nftRowsFail_clearsOtherTablesAndPropagatesFailure() = runTest {
        coEvery { nftItemStorage.deleteByAccountIds(any()) } throws IOException("disk full")

        var thrown: Throwable? = null
        try {
            cleaner.clearAccounts(ACCOUNT_IDS)
        } catch (e: IOException) {
            thrown = e
        }

        coVerify(exactly = 1) { locallyCreatedTransactionRepository.deleteByAccountIds(ACCOUNT_IDS) }
        coVerify(exactly = 1) { offlineModeUseCase.forgetAccounts(ACCOUNT_IDS) }
        assertNotNull(thrown)
    }

    /** Swallowing this would let the caller drop the deleted-account records that drive the retry. */
    @Test
    fun clearAccounts_offlineRowsFail_propagatesFailure() = runTest {
        coEvery { offlineModeUseCase.forgetAccounts(any()) } throws IOException("disk full")

        var thrown: Throwable? = null
        try {
            cleaner.clearAccounts(ACCOUNT_IDS)
        } catch (e: IOException) {
            thrown = e
        }

        assertNotNull(thrown)
    }

    @Test
    fun clearAccounts_cleanerCancelled_propagatesCancellation() = runTest {
        coEvery { locallyCreatedTransactionRepository.deleteByAccountIds(any()) } throws CancellationException("stop")

        var thrown: Throwable? = null
        try {
            cleaner.clearAccounts(ACCOUNT_IDS)
        } catch (e: CancellationException) {
            thrown = e
        }

        assertNotNull(thrown)
        coVerify(exactly = 0) { offlineModeUseCase.forgetAccounts(any()) }
        coVerify(exactly = 0) { nftItemStorage.deleteByAccountIds(any()) }
    }

    private companion object {
        val ACCOUNT_IDS = listOf("acc-a", "acc-b")
    }
}
