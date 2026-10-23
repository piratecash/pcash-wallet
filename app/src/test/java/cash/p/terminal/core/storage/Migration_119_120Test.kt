package cash.p.terminal.core.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.entities.StackingCache
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration_119_120Test {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() {
        context.deleteDatabase(APP_DATABASE_NAME)
    }

    @Test
    fun migrate119To120_rowPerCoinAndAddress_replacesOnSameKey() = runTest {
        buildAppDatabase(context).run {
            openHelper.writableDatabase.run {
                execSQL("DROP TABLE StackingCache")
                version = 119
            }
            close()
        }

        val database = buildAppDatabase(context)
        try {
            val dao = database.stackingCacheDao()
            dao.save(cache(coin = "pirate", unpaid = "1"))
            dao.save(cache(coin = "pirate", unpaid = "2"))
            dao.save(cache(coin = "cosa", unpaid = "3"))

            assertEquals(BigDecimal("2"), dao.get("pirate", ADDRESS)?.unpaid)
            assertEquals(BigDecimal("3"), dao.get("cosa", ADDRESS)?.unpaid)
        } finally {
            database.close()
        }
    }

    private fun cache(coin: String, unpaid: String) = StackingCache(
        coin = coin,
        address = ADDRESS,
        unpaid = BigDecimal(unpaid),
        totalIncome = BigDecimal.ZERO,
        nextAccrualAt = null,
        nextPayoutAt = null,
        cachedBalance = BigDecimal.ZERO,
        timestamp = 0,
    )

    private companion object {
        const val ADDRESS = "addr"
    }
}
