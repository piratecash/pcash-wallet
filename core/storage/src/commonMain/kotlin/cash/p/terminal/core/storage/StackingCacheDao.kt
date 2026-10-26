package cash.p.terminal.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cash.p.terminal.entities.StackingCache

@Dao
interface StackingCacheDao {

    @Query("SELECT * FROM StackingCache WHERE coin = :coin AND address = :address")
    suspend fun get(coin: String, address: String): StackingCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(cache: StackingCache)
}
