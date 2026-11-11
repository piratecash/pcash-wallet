package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.vault.PasswordVault
import io.horizontalsystems.core.storage.LogEntry
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VaultFilesShapeTest {
    private val fixture = VaultLevelsFixture()

    @AfterTest
    fun tearDown() = runBlocking { fixture.tearDown() }

    @Test
    fun vaultDirectory_atZeroOneAndFiveLevels_holdsTheSameFiles() = runBlocking<Unit> {
        fixture.create()
        fixture.vault.close()
        val empty = fixture.vaultDirectoryNames()
        assertEquals(EXPECTED_FILES, empty.size)

        fixture.reloginOk()
        fixture.setDuress(levelPassword(1))
        fixture.vault.close()
        assertEquals(empty, fixture.vaultDirectoryNames())

        (2..5).forEach { level ->
            fixture.reloginOk(levelPassword(level - 1))
            fixture.setDuress(levelPassword(level))
        }
        fixture.vault.close()
        assertEquals(empty, fixture.vaultDirectoryNames())
    }

    @Test
    fun freeSlot_everyKeyOfTheVault_opensIt() = runBlocking<Unit> {
        fixture.mainWithDuress()
        val safes = fixture.session.chain.safes
        val keys = safes.map { it.link.key.copyOf() } + safes.map { it.meta.premiumKey }
        val used = safes.map { it.link.slot }
        fixture.vault.close()

        (0 until PasswordVault.SLOT_COUNT).filter { it !in used }.forEach { slot ->
            listOf(fixture.files.safeFile(slot), fixture.files.premiumFile(slot)).forEach { file ->
                keys.forEach { key -> assertFalse(fixture.opensWith(file, key), file.name) }
            }
        }
    }

    @Test
    fun slotFiles_realAndFree_haveSizesARealDatabaseCouldHave() = runBlocking<Unit> {
        fixture.create()
        fixture.addAccount(VaultLevelsFixture.MAIN_ACCOUNT)
        val rootSlot = fixture.session.chain.root.link.slot
        fixture.vault.close()

        val sizes = (0 until PasswordVault.SLOT_COUNT).flatMap { slot ->
            listOf(fixture.files.safeFile(slot), fixture.files.premiumFile(slot)).map { slot to it.length() }
        }

        assertTrue(sizes.all { (_, size) -> size > 0 && size % PAGE_SIZE == 0L }, "$sizes")
        val realMax = sizes.filter { it.first == rootSlot }.maxOf { it.second }
        val freeMin = sizes.filter { it.first != rootSlot }.minOf { it.second }
        assertTrue(freeMin <= realMax, "free slots all larger than a real one: $sizes")
    }

    @Test
    fun levelSafe_fullAppLogOfRepresentativeRows_fitsTheNoiseRange() = runBlocking<Unit> {
        fixture.create()
        val logs = fixture.session.level(0).logsDao()
        val rootSlot = fixture.session.chain.root.link.slot
        repeat(APP_LOG_MAX_ENTRIES) { logs.insert(logEntry(it)) }
        fixture.vault.close()

        val size = fixture.files.safeFile(rootSlot).length()

        println("A full AppLog makes a level safe of $size bytes")
        assertTrue(size <= NOISE_MAX_BYTES, "$size bytes")
    }

    @Test
    fun close_afterUsingALevel_leavesAllFilesWithOneTime() = runBlocking<Unit> {
        fixture.mainWithDuress()

        fixture.vault.close()

        val times = (fixture.files.vaultDirectory.walkTopDown() + fixture.files.kitsDirectory.walkTopDown())
            .map { it.lastModified() }.toSet() + fixture.files.vaultFile.lastModified()
        assertEquals(1, times.size)
    }

    @Test
    fun close_filesNestedInRealAndDecoyAccounts_allGetTheSameNewTime() = runBlocking<Unit> {
        fixture.mainWithDuress(mainAccount = REAL_ACCOUNT)
        val kits = fixture.files.kitsDirectory
        val realKit = File(fixture.storeDirectory(REAL_ACCOUNT), "kit").apply { mkdirs() }
        File(realKit, "database").createNewFile()
        val random = Random(1)
        KitDecoys(fixture.files, VaultSlots(fixture.files, random), random).create()
        val depth = { file: File -> file.relativeTo(kits).invariantSeparatorsPath.count { it == '/' } }
        assertTrue(kits.walkTopDown().count { depth(it) >= 2 } > 1)
        kits.walkTopDown().forEach { it.setLastModified(OLD_TIME) }

        fixture.vault.close()

        val times = kits.walkTopDown().map { it.lastModified() }.toSet()
        assertEquals(1, times.size)
        assertTrue(OLD_TIME !in times)
    }

    private fun levelPassword(level: Int) = "Level-password$level"

    /** One row in [STACK_TRACE_EVERY] is a warning with a stack trace; the rest are kit progress lines. */
    private fun logEntry(index: Int): LogEntry {
        val height = BASE_HEIGHT + index
        val message = if (index % STACK_TRACE_EVERY == 0) {
            "sync failed at $height: " + STACK_TRACE_LINE.repeat(STACK_TRACE_LINES)
        } else {
            "sync: progress current=$height target=${height + index % 500}"
        }
        return LogEntry(BASE_TIME + index, APP_LOG_INFO, "zcash-session-${index / ACTION_SPAN}", message)
    }

    private companion object {
        const val REAL_ACCOUNT = "real-account"
        const val OLD_TIME = 1_000_000L
        const val PAGE_SIZE = 4096L
        const val NOISE_MAX_BYTES = 16L * 1024 * 1024
        const val APP_LOG_MAX_ENTRIES = 20_000
        const val APP_LOG_INFO = 4
        const val BASE_HEIGHT = 2_890_000
        const val BASE_TIME = 1_790_000_000_000L
        const val ACTION_SPAN = 200
        const val STACK_TRACE_EVERY = 20
        const val STACK_TRACE_LINES = 25
        const val STACK_TRACE_LINE = "\n    at cash.p.terminal.core.adapters.zcash.session.ZcashSession.run(ZcashSession.kt:120)"
        // 24 slots x (app, prem) + shared, each with its Room lock file.
        const val EXPECTED_FILES = (PasswordVault.SLOT_COUNT * 2 + 1) * 2
    }
}
