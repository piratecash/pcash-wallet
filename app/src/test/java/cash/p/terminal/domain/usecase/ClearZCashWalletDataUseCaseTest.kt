package cash.p.terminal.domain.usecase

import android.content.Context
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.adapters.zcash.session.ZcashDatabaseFiles
import cash.p.terminal.core.adapters.zcash.session.ZcashDbKeyProvider
import cash.p.terminal.core.adapters.zcash.session.ZcashSessionManager
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

    private var discoveredAccountIds = setOf(ACCOUNT_ID)
    private val localStorage = mockk<ILocalStorage>(relaxed = true) {
        every { zcashDiscoveredAccountIds } answers { discoveredAccountIds }
        every { zcashDiscoveredAccountIds = any() } answers { discoveredAccountIds = firstArg() }
        every { invalidateZcashAddressDiscovery(any()) } answers { callOriginal() }
    }

    @Before
    fun setUp() {
        noBackupDir = Files.createTempDirectory("zcash-erase").toFile()
        databaseFiles = ZcashDatabaseFiles(mockk<Context> { every { noBackupFilesDir } returns noBackupDir })
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

        assertEquals(ZcashEraseResult.NONE, useCase().invoke(ACCOUNT_ID))

        assertTrue(databasePaths().all { it.exists() })
        verify(exactly = 0) { dbKeyProvider.drop(any()) }
        coVerify(exactly = 0) { addressStorage.deleteAccountAddresses(any()) }
        assertEquals(setOf(ACCOUNT_ID), discoveredAccountIds)
    }

    @Test
    fun invoke_sessionClosed_erasesEveryArtifact() = runTest {
        assertEquals(ZcashEraseResult.ALL, useCase().invoke(ACCOUNT_ID))

        assertTrue(databasePaths().none { it.exists() })
        verify(exactly = 1) { dbKeyProvider.drop(ACCOUNT_ID) }
        coVerify(exactly = 1) { addressStorage.deleteAccountAddresses(ACCOUNT_ID) }
        // Without this the restored wallet would skip transparent-address discovery for good.
        assertEquals(emptySet<String>(), discoveredAccountIds)
    }

    @Test
    fun invoke_databaseFileSurvives_reportsPartial() = runTest {
        makeDatabaseUndeletable()

        assertEquals(ZcashEraseResult.PARTIAL, useCase().invoke(ACCOUNT_ID))

        coVerify(exactly = 1) { addressStorage.deleteAccountAddresses(ACCOUNT_ID) }
    }

    @Test
    fun invoke_dbKeyCommitFails_reportsPartial() = runTest {
        every { dbKeyProvider.drop(ACCOUNT_ID) } returns false

        assertEquals(ZcashEraseResult.PARTIAL, useCase().invoke(ACCOUNT_ID))
    }

    @Test
    fun invoke_nothingCouldBeDeleted_reportsPartialNotNone() = runTest {
        makeDatabaseUndeletable()
        every { dbKeyProvider.drop(ACCOUNT_ID) } returns false
        coEvery { addressStorage.deleteAccountAddresses(ACCOUNT_ID) } throws RuntimeException("locked")

        assertEquals(ZcashEraseResult.PARTIAL, useCase().invoke(ACCOUNT_ID))
    }

    @Test
    fun invoke_addressRowsSurvive_stillClearsTheDiscoveryFlag() = runTest {
        coEvery { addressStorage.deleteAccountAddresses(ACCOUNT_ID) } throws RuntimeException("locked")

        assertEquals(ZcashEraseResult.PARTIAL, useCase().invoke(ACCOUNT_ID))

        assertEquals(emptySet<String>(), discoveredAccountIds)
    }

    @Test
    fun invoke_always_deletesOnlyAfterTheSessionIsClosed() = runTest {
        var databaseIntactWhileClosing = false
        coEvery { sessionManager.closeForErase(ACCOUNT_ID) } coAnswers {
            databaseIntactWhileClosing = databasePaths().all { it.exists() }
            true
        }

        useCase().invoke(ACCOUNT_ID)

        assertTrue(databaseIntactWhileClosing)
        coVerifyOrder {
            sessionManager.closeForErase(ACCOUNT_ID)
            addressStorage.deleteAccountAddresses(ACCOUNT_ID)
        }
    }

    private fun useCase() = ClearZCashWalletDataUseCase(
        sessionManager = sessionManager,
        databaseFiles = databaseFiles,
        dbKeyProvider = dbKeyProvider,
        zcashSingleUseAddressStorage = addressStorage,
        localStorage = localStorage,
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
