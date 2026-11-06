package cash.p.terminal.network.pirate.data.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import cash.p.terminal.network.pirate.data.database.entity.ChangeNowAssociationCoin
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CacheDatabaseCleanerTest {

    @Test
    fun clear_storedAssociations_removesThem() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder<CacheAppDatabase>(
            factory = CacheAppDatabaseConstructor::initialize,
        )
            .setDriver(BundledSQLiteDriver())
            .build()
        val dao = database.changeNowCoinDao()
        dao.insertCoins(ChangeNowAssociationCoin(uid = "bitcoin", coinData = emptyList(), timestamp = 1L))
        assertNotNull(dao.getCoins("bitcoin"))

        CacheDatabaseCleaner(dao).clear()

        assertNull(dao.getCoins("bitcoin"))
        database.close()
    }
}
