package cash.p.terminal.core.managers

import android.content.Context
import io.horizontalsystems.bitcoincore.core.IConnectionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.test.assertFailsWith

class BitcoinKitDatabaseManagerTest {
    private val context = mockk<Context> {
        every { getDatabasePath(any()) } returns File("/app/databases/bitcoin-kit-path")
        every { noBackupFilesDir } returns File("/app/no-backup")
    }
    private val keyProvider = mockk<BitcoinKitDatabaseKeyProvider>()
    private val operations = mockk<BitcoinKitDatabaseOperations>()
    private val connectionManager = mockk<IConnectionManager>()
    private val databaseKey = ByteArray(32) { it.toByte() }

    @Test
    fun prepare_concurrentCalls_migratesOnceAndReturnsEncryptedEnvironment() = runTest {
        every { keyProvider.keyFor(ACCOUNT_ID) } returns databaseKey
        coEvery { operations.migrate(any(), any(), any()) } returns Unit
        val manager = createManager()

        val environments = List(4) { async { manager.prepare(ACCOUNT_ID) } }.awaitAll()

        coVerify(exactly = 1) { operations.migrate(DATA_DIR, ACCOUNT_ID, databaseKey) }
        environments.forEach { environment ->
            assertEquals(DATA_DIR, environment.dataDir)
            assertEquals(MWEB_DATA_DIR, environment.mwebDataDir)
            assertArrayEquals(databaseKey, environment.databaseKey)
            assertEquals(connectionManager, environment.connectionManager)
        }
    }

    @Test
    fun prepare_migrationFails_retriesWithExistingKey() = runTest {
        every { keyProvider.keyFor(ACCOUNT_ID) } returns databaseKey
        coEvery { operations.migrate(any(), any(), any()) } throws
            IllegalStateException("migration failed") andThen Unit
        val manager = createManager()

        assertFailsWith<IllegalStateException> { manager.prepare(ACCOUNT_ID) }
        manager.prepare(ACCOUNT_ID)

        verify(exactly = 2) { keyProvider.keyFor(ACCOUNT_ID) }
        coVerify(exactly = 2) { operations.migrate(DATA_DIR, ACCOUNT_ID, databaseKey) }
        verify(exactly = 0) { keyProvider.remove(any()) }
    }

    @Test
    fun prepare_migrationCancelled_retainsKeyAndCanRetry() = runTest {
        every { keyProvider.keyFor(ACCOUNT_ID) } returns databaseKey
        coEvery { operations.migrate(any(), any(), any()) } throws CancellationException() andThen Unit
        val manager = createManager()

        assertFailsWith<CancellationException> { manager.prepare(ACCOUNT_ID) }
        manager.prepare(ACCOUNT_ID)

        coVerify(exactly = 2) { operations.migrate(DATA_DIR, ACCOUNT_ID, databaseKey) }
        verify(exactly = 0) { keyProvider.remove(any()) }
    }

    @Test
    fun prepare_databaseKeyLocked_retriesUntilAuthenticationSucceeds() = runTest {
        every { keyProvider.keyFor(ACCOUNT_ID) } throws
            BitcoinKitDatabaseKeyLockedException(mockk()) andThen databaseKey
        coEvery { operations.migrate(any(), any(), any()) } returns Unit
        val manager = createManager()

        val environment = manager.prepare(ACCOUNT_ID)

        verify(exactly = 2) { keyProvider.keyFor(ACCOUNT_ID) }
        coVerify(exactly = 1) { operations.migrate(DATA_DIR, ACCOUNT_ID, databaseKey) }
        assertArrayEquals(databaseKey, environment.databaseKey)
    }

    @Test
    fun clear_success_clearsDatabasesBeforeRemovingKey() = runTest {
        every { operations.clear(any(), any(), any()) } returns Unit
        every { keyProvider.remove(any()) } returns Unit
        val manager = createManager()

        manager.clear(ACCOUNT_ID)

        verifyOrder {
            operations.clear(DATA_DIR, MWEB_DATA_DIR, ACCOUNT_ID)
            keyProvider.remove(ACCOUNT_ID)
        }
    }

    @Test
    fun clear_databaseClearFails_retainsKey() = runTest {
        every { operations.clear(any(), any(), any()) } throws IllegalStateException("clear failed")
        val manager = createManager()

        assertFailsWith<IllegalStateException> { manager.clear(ACCOUNT_ID) }

        verify(exactly = 0) { keyProvider.remove(any()) }
    }

    @Test
    fun clearMweb_anyAccount_keepsDatabaseKey() = runTest {
        every { operations.clearMweb(any(), any(), any()) } returns Unit
        val manager = createManager()

        manager.clearMweb(ACCOUNT_ID)

        verify(exactly = 1) { operations.clearMweb(DATA_DIR, MWEB_DATA_DIR, ACCOUNT_ID) }
        verify(exactly = 0) { keyProvider.remove(any()) }
    }

    private fun createManager() = BitcoinKitDatabaseManager(
        context,
        keyProvider,
        operations,
        connectionManager,
    )

    private companion object {
        const val ACCOUNT_ID = "account-id"
        const val DATA_DIR = "/app/databases"
        const val MWEB_DATA_DIR = "/app/no-backup"
    }
}
