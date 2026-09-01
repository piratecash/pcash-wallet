package cash.p.terminal.domain.usecase

import android.content.Context
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.adapters.zcash.session.ZcashDatabaseFiles
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class ClearLegacyZcashDataUseCaseTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var noBackupDir: File
    private lateinit var databaseFiles: ZcashDatabaseFiles

    @Before
    fun setUp() {
        noBackupDir = Files.createTempDirectory("zcash-legacy").toFile()
        databaseFiles = ZcashDatabaseFiles(mockk<Context> { every { noBackupFilesDir } returns noBackupDir })
        databaseFiles.legacyDir.mkdirs()
    }

    @After
    fun tearDown() {
        databaseFiles.legacyDir.setWritable(true)
        noBackupDir.deleteRecursively()
    }

    @Test
    fun invoke_legacyDataPresent_dropsItButKeepsTheSaplingParameters() = runTest {
        val database = legacyEntry("zcash_mainnet_data.db")
        val cache = legacyEntry("zcash_mainnet_fs_cache").also { it.delete(); it.mkdirs() }
        File(cache, "blocks").writeText("cached")
        val params = legacyEntry("sapling-spend.params")

        useCase().invoke()

        assertFalse(database.exists())
        assertFalse(cache.exists())
        assertTrue(params.exists())
    }

    @Test
    fun invoke_entryCannotBeDeleted_leavesItForTheNextLaunch() = runTest {
        val leftover = legacyEntry("zcash_mainnet_data.db")
        databaseFiles.legacyDir.setWritable(false)

        useCase().invoke()

        assertTrue(leftover.exists())
    }

    private fun useCase() = ClearLegacyZcashDataUseCase(
        databaseFiles = databaseFiles,
        dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
    )

    private fun legacyEntry(name: String) = File(databaseFiles.legacyDir, name).apply { writeText("data") }
}
