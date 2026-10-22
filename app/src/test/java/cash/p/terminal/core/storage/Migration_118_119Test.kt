package cash.p.terminal.core.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.core.storage.migrations.Migration_118_119
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration_118_119Test {

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
    }

    @After
    fun tearDown() {
        helper.close()
    }

    @Test
    fun migrate118To119_rowPerCoinAndAddress_replacesOnSameKey() {
        Migration_118_119.migrate(db)

        insertRow(coin = "pirate", address = "addr", unpaid = "1")
        insertRow(coin = "pirate", address = "addr", unpaid = "2")
        insertRow(coin = "cosa", address = "addr", unpaid = "3")

        db.query("SELECT coin, unpaid FROM StackingCache ORDER BY coin").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("cosa" to "3", cursor.getString(0) to cursor.getString(1))
            assertTrue(cursor.moveToNext())
            assertEquals("pirate" to "2", cursor.getString(0) to cursor.getString(1))
            assertEquals(2, cursor.count)
        }
    }

    private fun insertRow(coin: String, address: String, unpaid: String) {
        db.execSQL(
            "INSERT OR REPLACE INTO StackingCache " +
                "(coin, address, unpaid, totalIncome, nextAccrualAt, nextPayoutAt, cachedBalance, timestamp) " +
                "VALUES (?, ?, ?, '0', NULL, NULL, '0', 0)",
            arrayOf(coin, address, unpaid)
        )
    }

    private companion object {
        const val START_VERSION = 118
    }
}
