package cash.p.terminal.core.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.horizontalsystems.core.storage.LogEntry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration_123_124Test {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() {
        context.deleteDatabase(APP_DATABASE_NAME)
    }

    @Test
    fun migrate123To124_existingLogRow_survivesWithUserLevelZero() = runBlocking {
        buildAppDatabase(context).run {
            openHelper.writableDatabase.run {
                execSQL("ALTER TABLE LogEntry DROP COLUMN userLevel")
                execSQL("INSERT INTO LogEntry (date, level, actionId, message) VALUES (1, 4, 'old', 'kept')")
                version = 123
            }
            close()
        }

        val database = buildAppDatabase(context)
        try {
            database.logsDao().insert(LogEntry(2, 4, "new", "stamped", userLevel = 1))

            assertEquals(listOf("kept"), database.logsDao().getRecent(10, 0).map { it.message })
            assertEquals(listOf("stamped"), database.logsDao().getRecent(10, 1).map { it.message })
        } finally {
            database.close()
        }
    }
}
