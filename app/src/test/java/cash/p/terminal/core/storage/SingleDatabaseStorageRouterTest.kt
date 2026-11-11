package cash.p.terminal.core.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.entities.RestoreSettingRecord
import cash.p.terminal.wallet.entities.AccountRecord
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SingleDatabaseStorageRouterTest {

    private lateinit var database: AppDatabase
    private lateinit var router: SingleDatabaseStorageRouter
    private val previousLogWriters = Logger.config.logWriterList
    private val warnings = mutableListOf<String>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        router = SingleDatabaseStorageRouter(StaticAppDatabaseProvider(database))
        Logger.setLogWriters(object : LogWriter() {
            override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
                if (severity == Severity.Warn) warnings += message
            }
        })
    }

    @After
    fun tearDown() {
        Logger.setLogWriters(previousLogWriters)
        database.close()
    }

    @Test
    fun allGroups_anyLevelAndAccount_resolveToTheOneDatabase() {
        assertSame(database, router.shared)
        listOf(router.rootLevel, 1, Int.MAX_VALUE).forEach { assertSame(database, router.level(it)) }
        assertSame(database, router.account("any"))
        assertEquals(listOf<AccountDaos>(database), router.accounts)
    }

    @Test
    fun level_belowRoot_returnsTheRootDatabaseAndWarnsWithoutTheLevel() {
        assertSame(database, router.level(router.rootLevel - 7))

        val warning = warnings.single()
        assertFalse(warning, warning.contains("7"))
    }

    @Test
    fun level_atOrAboveRoot_doesNotWarn() {
        router.level(router.rootLevel)
        router.level(router.rootLevel + 1)

        assertTrue(warnings.isEmpty())
    }

    @Test
    fun setLevel_listedAccounts_movesOnlyThem() {
        insertAccounts("a" to 0, "b" to 0, "c" to 0)

        router.setLevel(listOf("a", "b"), 1)

        assertEquals(mapOf("a" to 1, "b" to 1, "c" to 0), storedLevels())
    }

    @Test
    fun collapseLevelsAbove_everyKindOfLevel_capsOnlyHigherOnes() {
        insertAccounts("hidden" to -1, "root" to 0, "at" to 1, "above" to 2, "farAbove" to 5)

        router.collapseLevelsAbove(1)

        assertEquals(mapOf("hidden" to -1, "root" to 0, "at" to 1, "above" to 1, "farAbove" to 1), storedLevels())
    }

    @Test
    fun levelOf_liveDeletedAndUnknownAccounts_returnsLevelOnlyForLive() {
        insertAccounts("live" to 3, "deleted" to 3)
        database.accountsDao().delete("deleted")

        assertEquals(3, router.levelOf("live"))
        assertNull(router.levelOf("deleted"))
        assertNull(router.levelOf("unknown"))
    }

    @Test
    fun restoreSettings_savedBeforeTheAccountRowExists_areKept() {
        val storage = RestoreSettingsStorage(router)

        storage.save(listOf(RestoreSettingRecord("new-account", "bitcoin", "birthdayHeight", "100")))

        assertEquals(listOf("100"), storage.restoreSettings("new-account").map { it.value })
    }

    private fun insertAccounts(vararg accounts: Pair<String, Int>) = accounts.forEach { (id, level) ->
        database.accountsDao().insert(
            AccountRecord(
                id = id,
                name = id,
                type = "evm_address",
                origin = "Restored",
                isBackedUp = false,
                isFileBackedUp = false,
                words = null,
                passphrase = null,
                key = null,
                level = level,
            )
        )
    }

    private fun storedLevels() = database.accountsDao().getIds().associateWith { router.levelOf(it) }
}
