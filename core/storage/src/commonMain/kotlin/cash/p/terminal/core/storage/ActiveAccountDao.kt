package cash.p.terminal.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cash.p.terminal.entities.ActiveAccount

@Dao
interface ActiveAccountDao {
    @Query("SELECT * FROM ActiveAccount WHERE level = :level LIMIT 1")
    fun getActiveAccount(level: Int): ActiveAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertActiveAccount(activeAccount: ActiveAccount)

    @Query("DELETE FROM ActiveAccount WHERE level = :level")
    fun deleteActiveAccount(level: Int)
}
