package cash.p.terminal.shared.desktop.vault

import io.horizontalsystems.core.storage.LogEntry
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LevelSafeLogEntryTest {
    private val fixture = VaultLevelsFixture()

    @AfterTest
    fun tearDown() = runBlocking { fixture.tearDown() }

    @Test
    fun getRecent_freshLevelSafe_returnsRowsOfTheStampedLevelOnly() = runBlocking<Unit> {
        fixture.create()
        val logs = fixture.session.level(0).logsDao()

        logs.insert(LogEntry(date = 1, level = 4, actionId = "tag", message = "lower"))
        logs.insert(LogEntry(date = 2, level = 4, actionId = "tag", message = "upper", userLevel = 1))

        assertEquals(listOf("lower"), logs.getRecent(10, userLevel = 0).map { it.message })
        assertEquals(listOf("upper"), logs.getRecent(10, userLevel = 1).map { it.message })
    }
}
