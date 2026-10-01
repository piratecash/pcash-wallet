package cash.p.terminal.core.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.core.storage.migrations.Migration_116_117
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration_116_117Test {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory database
            .callback(object : SupportSQLiteOpenHelper.Callback(START_VERSION) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        db = helper.writableDatabase
        db.execSQL(
            "CREATE TABLE SwapProviderTransaction (" +
                "date INTEGER NOT NULL, outgoingRecordUid TEXT, transactionId TEXT NOT NULL, " +
                "status TEXT NOT NULL, provider TEXT NOT NULL, coinUidIn TEXT NOT NULL, " +
                "blockchainTypeIn TEXT NOT NULL, amountIn TEXT NOT NULL, addressIn TEXT NOT NULL, " +
                "coinUidOut TEXT NOT NULL, blockchainTypeOut TEXT NOT NULL, amountOut TEXT NOT NULL, " +
                "addressOut TEXT NOT NULL, amountOutReal TEXT, finishedAt INTEGER, incomingRecordUid TEXT, " +
                "accountId TEXT NOT NULL DEFAULT '', depositTransactionHash TEXT, unstoppableSubProviderId TEXT, " +
                "PRIMARY KEY(date))"
        )
    }

    @After
    fun tearDown() {
        helper.close()
    }

    @Test
    fun migrate116To117_existingRow_movesSubProviderAndKeepsValuesWithNullNewColumns() {
        insertRow(date = 1, provider = "YIFI", subProviderId = "'fixedfloat'")

        Migration_116_117.migrate(db)

        db.query(
            "SELECT transactionId, provider, accountId, subProviderId, providerExternalId, providerWalletAddress " +
                "FROM SwapProviderTransaction WHERE date = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("tx-1", cursor.getString(0))
            assertEquals("YIFI", cursor.getString(1))
            assertEquals("acc", cursor.getString(2))
            assertEquals("fixedfloat", cursor.getString(3))
            assertNull(cursor.getString(4))
            assertNull(cursor.getString(5))
        }
    }

    @Test
    fun migrate116To117_oldSubProviderColumn_isRemoved() {
        Migration_116_117.migrate(db)

        val columns = db.query("PRAGMA table_info(SwapProviderTransaction)").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }
        assertTrue("subProviderId" in columns)
        assertFalse("unstoppableSubProviderId" in columns)
    }

    @Test
    fun migrate116To117_insertWithNewColumns_storesThem() {
        Migration_116_117.migrate(db)

        insertRow(
            date = 2,
            provider = "PCASH_BACKEND",
            subProviderId = "'changelly'",
            subProviderColumn = "subProviderId",
            extraColumns = ", providerExternalId, providerWalletAddress",
            extraValues = ", 'ext-1', '0xabc'",
        )

        db.query(
            "SELECT providerExternalId, providerWalletAddress FROM SwapProviderTransaction WHERE date = 2"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ext-1", cursor.getString(0))
            assertEquals("0xabc", cursor.getString(1))
        }
    }

    private fun insertRow(
        date: Long,
        provider: String,
        subProviderId: String,
        subProviderColumn: String = "unstoppableSubProviderId",
        extraColumns: String = "",
        extraValues: String = "",
    ) {
        db.execSQL(
            "INSERT INTO SwapProviderTransaction (date, transactionId, status, provider, coinUidIn, " +
                "blockchainTypeIn, amountIn, addressIn, coinUidOut, blockchainTypeOut, amountOut, addressOut, " +
                "accountId, $subProviderColumn$extraColumns) VALUES ($date, 'tx-$date', 'new', '$provider', " +
                "'bitcoin', 'bitcoin', '1', 'addr-in', 'ethereum', 'ethereum', '2', 'addr-out', 'acc', " +
                "$subProviderId$extraValues)"
        )
    }

    private companion object {
        const val START_VERSION = 116
    }
}
