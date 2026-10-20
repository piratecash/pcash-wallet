package cash.p.terminal.core.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.wallet.entities.AccountRecord
import cash.p.terminal.wallet.entities.EnabledWallet
import cash.p.terminal.wallet.entities.SecretList
import cash.p.terminal.wallet.entities.SecretString
import io.horizontalsystems.core.IEncryptionManager
import io.horizontalsystems.core.storage.LogEntry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Opens a pre-existing version 118 file with the production builder: a schema or identity-hash
 * drift would make Room reject the file instead of reusing it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AppDatabaseUpgradeTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        startKoin { modules(module { single<IEncryptionManager> { PlainTextEncryptionManager() } }) }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.getDatabasePath(APP_DATABASE_NAME)
        seedVersion118File(file)
        database = buildAppDatabase(context)
    }

    @After
    fun tearDown() {
        database.close()
        stopKoin()
    }

    @Test
    fun buildAppDatabase_existingVersion118File_reusesStoredRows() {
        assertEquals(listOf(SEEDED_ACCOUNT_ID), database.accountsDao().getIds())
    }

    @Test
    fun accountsDao_existingVersion118File_roundTripsSecretFields() {
        val account = AccountRecord(
            id = "account-2",
            name = "Wallet",
            type = "mnemonic",
            origin = "Created",
            isBackedUp = true,
            isFileBackedUp = false,
            words = SecretList(listOf("one", "two")),
            passphrase = SecretString("passphrase"),
            key = null,
            level = 0,
        )

        database.accountsDao().insert(account)

        val stored = database.accountsDao().loadAccount("account-2")
        assertEquals(listOf("one", "two"), stored?.words?.list)
        assertEquals("passphrase", stored?.passphrase?.value)
    }

    @Test
    fun walletsDao_existingVersion118File_persistsEnabledWallet() {
        val wallet = EnabledWallet(
            tokenQueryId = "bitcoin|native",
            accountId = SEEDED_ACCOUNT_ID,
            walletOrder = 0,
            coinImage = null,
        )

        database.walletsDao().insert(wallet)

        assertEquals(
            listOf("bitcoin|native"),
            database.walletsDao().enabledCoins(SEEDED_ACCOUNT_ID).map { it.tokenQueryId },
        )
    }

    @Test
    fun logsDao_existingVersion118File_persistsLogEntry() = runBlocking {
        database.logsDao().insert(LogEntry(date = 1, level = Log.INFO, actionId = "tag", message = "message"))

        assertEquals(listOf("message"), database.logsDao().getRecent(10).map { it.message })
    }

    /** Builds the file straight from the checked-in version 118 DDL, as a released app would leave it. */
    private fun seedVersion118File(file: File) {
        file.parentFile?.mkdirs()
        file.delete()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            readSchemaStatements().forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO AccountRecord (id, name, type, origin, isBackedUp, isFileBackedUp, level, deleted) " +
                    "VALUES ('$SEEDED_ACCOUNT_ID', 'Seeded', 'mnemonic', 'Created', 1, 0, 0, 0)"
            )
            db.version = APP_DATABASE_VERSION
        }
    }

    private fun readSchemaStatements(): List<String> {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream(SCHEMA_RESOURCE)) {
            "Missing schema resource: $SCHEMA_RESOURCE"
        }
        return stream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readLines().map(String::trim).filter(String::isNotEmpty)
        }
    }

    private companion object {
        const val SCHEMA_RESOURCE = "schema/app_db_v118.sql"
        const val SEEDED_ACCOUNT_ID = "account-1"
        const val APP_DATABASE_VERSION = 118
    }
}
