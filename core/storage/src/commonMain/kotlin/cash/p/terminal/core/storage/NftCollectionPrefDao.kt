package cash.p.terminal.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cash.p.terminal.entities.NftCollectionPrefRecord
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.flow.Flow

@Dao
interface NftCollectionPrefDao {

    @Query("SELECT * FROM NftCollectionPrefRecord WHERE accountId = :accountId")
    fun observe(accountId: String): Flow<List<NftCollectionPrefRecord>>

    @Query(
        "SELECT * FROM NftCollectionPrefRecord " +
            "WHERE accountId = :accountId AND blockchainType = :blockchainType AND collectionKey = :collectionKey"
    )
    suspend fun get(accountId: String, blockchainType: BlockchainType, collectionKey: String): NftCollectionPrefRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: NftCollectionPrefRecord)

    @Query("DELETE FROM NftCollectionPrefRecord WHERE accountId IN (:accountIds)")
    suspend fun deleteByAccountIds(accountIds: List<String>)
}
