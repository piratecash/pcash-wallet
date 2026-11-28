package cash.p.terminal.domain.usecase

import cash.p.terminal.core.managers.AndroidKitPlatformContexts
import android.content.Context
import cash.p.terminal.core.adapters.zcash.ZcashTransparentIndexStore
import cash.p.terminal.core.adapters.zcash.session.ZcashAddressSearch
import cash.p.terminal.core.adapters.zcash.session.ZcashDatabaseFiles
import cash.p.terminal.core.adapters.zcash.session.ZcashDbKeyProvider
import cash.p.terminal.core.adapters.zcash.session.ZcashSessionManager
import cash.p.terminal.core.managers.IdentityKitStorageNaming
import cash.p.terminal.core.storage.ZcashSingleUseAddressStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

private const val ACCOUNT_ID = "account"

class ClearZCashWalletDataUseCaseTest {

    private lateinit var noBackupDir: File
    private lateinit var databaseFiles: ZcashDatabaseFiles

    private val sessionManager = mockk<ZcashSessionManager>()
    private val dbKeyProvider = mockk<ZcashDbKeyProvider>(relaxed = true)
    private val addressStorage = mockk<ZcashSingleUseAddressStorage>(relaxed = true)
    private val indexStore = mockk<ZcashTransparentIndexStore>(relaxed = true)
    private val addressSearch = mockk<ZcashAddressSearch>(relaxed = true)

    @Before
    fun setUp() {
        noBackupDir = Files.createTempDirectory("zcash-erase").toFile()
        databaseFiles = ZcashDatabaseFiles(
            AndroidKitPlatformContexts(mockk<Context> { every { noBackupFilesDir } returns noBackupDir }),
            IdentityKitStorageNaming,
        )
        databaseFiles.dataDir.mkdirs()
        databasePaths().forEach { it.writeText("data") }
        coEvery { sessionManager.closeForErase(ACCOUNT_ID) } returns true
        every { dbKeyProvider.drop(ACCOUNT_ID) } returns true
    }

    @After
    fun tearDown() {
        noBackupDir.deleteRecursively()
    }

    @Test
    fun invoke_drainTimesOut_touchesNothing() = runTest {
        coEvery { sessionManager.closeForErase(ACCOUNT_ID) } returns false

        assertEquals(ZcashEraseResult.NONE, useCase().invoke(ACCOUNT_ID, eraseIssuedAddresses = true))

        assertTrue(databasePaths().all { it.exists() })
        verify(exactly = 0) { dbKeyProvider.drop(any()) }
        coVerify(exactly = 0) { addressStorage.deleteAccountAddresses(any()) }
        coVerify(exactly = 0) { addressSearch.clear(any()) }
    }

    @Test
    fun invoke_sessionClosed_erasesEveryArtifact() = runTest {
        assertEquals(ZcashEraseResult.ALL, useCase().invoke(ACCOUNT_ID, eraseIssuedAddresses = true))

        assertTrue(databasePaths().none { it.exists() })
        verify(exactly = 1) { dbKeyProvider.drop(ACCOUNT_ID) }
        coVerify(exactly = 1) { addressStorage.deleteAccountAddresses(ACCOUNT_ID) }
        coVerify(exactly = 1) { indexStore.clear(ACCOUNT_ID) }
        coVerify(exactly = 1) { addressSearch.clear(ACCOUNT_ID) }
    }

    @Test
    fun invoke_issuedAddressesKept_erasesTheDatabaseOnly() = runTest {
        assertEquals(ZcashEraseResult.ALL, useCase().invoke(ACCOUNT_ID, eraseIssuedAddresses = false))

        assertTrue(databasePaths().none { it.exists() })
        verify(exactly = 1) { dbKeyProvider.drop(ACCOUNT_ID) }
        coVerify(exactly = 0) { addressStorage.deleteAccountAddresses(any()) }
        coVerify(exactly = 0) { indexStore.clear(any()) }
        coVerify(exactly = 1) { addressSearch.clear(ACCOUNT_ID) }
    }

    @Test
    fun invoke_rescanSearchProgressNotCleared_deletesNothing() = runTest {
        databaseFiles.markPristine(ACCOUNT_ID)
        coEvery { addressSearch.clear(ACCOUNT_ID) } throws RuntimeException("locked")

        assertEquals(ZcashEraseResult.NONE, useCase().invoke(ACCOUNT_ID, eraseIssuedAddresses = false))

        assertTrue(databasePaths().all { it.exists() })
        assertTrue(databaseFiles.isPristine(ACCOUNT_ID))
        verify(exactly = 0) { dbKeyProvider.drop(any()) }
        coVerify(exactly = 0) { addressStorage.deleteAccountAddresses(any()) }
        coVerify(exactly = 0) { indexStore.clear(any()) }
    }

    @Test
    fun invoke_deletionSearchProgressNotCleared_stillErasesTheWallet() = runTest {
        coEvery { addressSearch.clear(ACCOUNT_ID) } throws RuntimeException("locked")

        assertEquals(ZcashEraseResult.PARTIAL, useCase().invoke(ACCOUNT_ID, eraseIssuedAddresses = true))

        assertTrue(databasePaths().none { it.exists() })
        verify(exactly = 1) { dbKeyProvider.drop(ACCOUNT_ID) }
        coVerify(exactly = 1) { addressStorage.deleteAccountAddresses(ACCOUNT_ID) }
        coVerify(exactly = 1) { indexStore.clear(ACCOUNT_ID) }
    }

    /**
     * There is no separate discovery flag any more: the coverage record lives in the database
     * file, so deleting it is what makes the account's next restore sweep deep again. That must
     * hold even when the other artifacts fail to clear.
     */
    @Test
    fun clear_zcashAccount_forgetsTheDeepSweepFlag() = runTest {
        every { dbKeyProvider.drop(ACCOUNT_ID) } returns false
        coEvery { addressStorage.deleteAccountAddresses(ACCOUNT_ID) } throws RuntimeException("locked")

        assertEquals(ZcashEraseResult.PARTIAL, useCase().invoke(ACCOUNT_ID, eraseIssuedAddresses = true))

        assertTrue(databasePaths().none { it.exists() })
    }

    @Test
    fun invoke_databaseFileSurvives_reportsPartial() = runTest {
        makeDatabaseUndeletable()

        assertEquals(ZcashEraseResult.PARTIAL, useCase().invoke(ACCOUNT_ID, eraseIssuedAddresses = true))

        coVerify(exactly = 1) { addressStorage.deleteAccountAddresses(ACCOUNT_ID) }
    }

    @Test
    fun invoke_dbKeyCommitFails_reportsPartial() = runTest {
        every { dbKeyProvider.drop(ACCOUNT_ID) } returns false

        assertEquals(ZcashEraseResult.PARTIAL, useCase().invoke(ACCOUNT_ID, eraseIssuedAddresses = true))
    }

    @Test
    fun invoke_nothingCouldBeDeleted_reportsPartialNotNone() = runTest {
        makeDatabaseUndeletable()
        every { dbKeyProvider.drop(ACCOUNT_ID) } returns false
        coEvery { addressStorage.deleteAccountAddresses(ACCOUNT_ID) } throws RuntimeException("locked")

        assertEquals(ZcashEraseResult.PARTIAL, useCase().invoke(ACCOUNT_ID, eraseIssuedAddresses = true))
    }

    @Test
    fun invoke_always_deletesOnlyAfterTheSessionIsClosed() = runTest {
        var databaseIntactWhileClosing = false
        coEvery { sessionManager.closeForErase(ACCOUNT_ID) } coAnswers {
            databaseIntactWhileClosing = databasePaths().all { it.exists() }
            true
        }

        useCase().invoke(ACCOUNT_ID, eraseIssuedAddresses = true)

        assertTrue(databaseIntactWhileClosing)
        coVerifyOrder {
            sessionManager.closeForErase(ACCOUNT_ID)
            addressStorage.deleteAccountAddresses(ACCOUNT_ID)
        }
    }

    @Test
    fun invoke_sessionClosed_erasesThePristineMark() = runTest {
        databaseFiles.markPristine(ACCOUNT_ID)

        assertEquals(ZcashEraseResult.ALL, useCase().invoke(ACCOUNT_ID, eraseIssuedAddresses = true))

        assertFalse(databaseFiles.isPristine(ACCOUNT_ID))
    }

    @Test
    fun invoke_drainTimesOut_keepsThePristineMark() = runTest {
        databaseFiles.markPristine(ACCOUNT_ID)
        coEvery { sessionManager.closeForErase(ACCOUNT_ID) } returns false

        assertEquals(ZcashEraseResult.NONE, useCase().invoke(ACCOUNT_ID, eraseIssuedAddresses = true))

        assertTrue(databaseFiles.isPristine(ACCOUNT_ID))
    }

    private fun useCase() = ClearZCashWalletDataUseCase(
        sessionManager = sessionManager,
        databaseFiles = databaseFiles,
        dbKeyProvider = dbKeyProvider,
        zcashSingleUseAddressStorage = addressStorage,
        transparentIndexStore = indexStore,
        addressSearch = addressSearch,
    )

    /** A non-empty directory in place of a database file: `File.delete` refuses it. */
    private fun makeDatabaseUndeletable() {
        val wal = File(databaseFiles.databaseFile(ACCOUNT_ID).path + "-wal")
        wal.delete()
        wal.mkdirs()
        File(wal, "child").writeText("busy")
    }

    private fun databasePaths(): List<File> =
        listOf("", "-wal", "-shm", "-journal").map { suffix ->
            File(databaseFiles.databaseFile(ACCOUNT_ID).path + suffix)
        }
}
