package cash.p.terminal.modules.chart

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChartIndicatorSettingsDao {
    @Query("SELECT COUNT(*) FROM ChartIndicatorSetting")
    fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(settings: List<ChartIndicatorSetting>)

    @Query("SELECT * FROM ChartIndicatorSetting")
    fun getAll(): Flow<List<ChartIndicatorSetting>>

    @Query("SELECT * FROM ChartIndicatorSetting")
    fun getAllBlocking(): List<ChartIndicatorSetting>

    @Query("SELECT * FROM ChartIndicatorSetting WHERE enabled = 1")
    fun getEnabled(): List<ChartIndicatorSetting>

    @Query("UPDATE ChartIndicatorSetting SET enabled = 1 WHERE id = :indicatorId")
    fun enableIndicator(indicatorId: String)

    @Query("UPDATE ChartIndicatorSetting SET enabled = 0 WHERE id = :indicatorId")
    fun disableIndicator(indicatorId: String)

    @Query("SELECT * FROM ChartIndicatorSetting WHERE id = :id")
    fun get(id: String): ChartIndicatorSetting?

    @Update
    fun update(chartIndicatorSetting: ChartIndicatorSetting)
}
