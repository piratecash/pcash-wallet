package cash.p.terminal.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cash.p.terminal.entities.RestoreSettingRecord

@Dao
interface RestoreSettingDao {

    @Query("SELECT EXISTS(SELECT 1 FROM RestoreSettingRecord WHERE blockchainTypeUid = 'beam')")
    fun hasBeamSettings(): Boolean

    @Query("DELETE FROM RestoreSettingRecord WHERE blockchainTypeUid = 'beam' AND accountId IN (:accountIds)")
    fun deleteBeam(accountIds: List<String>)

    @Query("DELETE FROM RestoreSettingRecord WHERE blockchainTypeUid = 'beam'")
    fun deleteAllBeam()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(records: List<RestoreSettingRecord>)

    @Query(
        "SELECT * FROM `RestoreSettingRecord` WHERE accountId = :accountId AND blockchainTypeUid = :blockchainTypeUid"
    )
    fun get(accountId: String, blockchainTypeUid: String): List<RestoreSettingRecord>

    @Query("SELECT * FROM `RestoreSettingRecord` WHERE accountId = :accountId")
    fun get(accountId: String): List<RestoreSettingRecord>

    @Query("DELETE FROM `RestoreSettingRecord` WHERE accountId = :accountId")
    fun delete(accountId: String)

}
