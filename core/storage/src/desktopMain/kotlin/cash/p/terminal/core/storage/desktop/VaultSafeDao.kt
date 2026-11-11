package cash.p.terminal.core.storage.desktop

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface VaultSafeDao {
    @Query("SELECT * FROM VaultSafeMeta LIMIT 1")
    fun meta(): VaultSafeMeta?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putMeta(meta: VaultSafeMeta)

    @Query("SELECT * FROM AccountPointer")
    fun pointers(): List<AccountPointer>

    @Query("SELECT * FROM AccountPointer WHERE accountId = :accountId")
    fun pointer(accountId: String): AccountPointer?

    @Query("SELECT COUNT(*) FROM AccountPointer")
    fun pointerCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertPointers(pointers: List<AccountPointer>)

    @Query("DELETE FROM AccountPointer WHERE accountId IN (:accountIds)")
    fun deletePointers(accountIds: List<String>)

    @Query("SELECT value FROM LevelSetting WHERE `key` = :key")
    fun setting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putSetting(setting: LevelSetting)

    @Query("DELETE FROM LevelSetting WHERE `key` = :key")
    fun deleteSetting(key: String)

    @Transaction
    fun adoptPointers(pointers: List<AccountPointer>, meta: VaultSafeMeta) {
        insertPointers(pointers)
        putMeta(meta)
    }
}

@Dao
interface AccountStoreDao {
    @Query("SELECT * FROM AccountStoreMeta LIMIT 1")
    fun meta(): AccountStoreMeta?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putMeta(meta: AccountStoreMeta)

    @Query("SELECT value FROM AccountSetting WHERE `key` = :key")
    fun setting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putSetting(setting: AccountSetting)
}
