package cash.p.terminal.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import cash.p.terminal.entities.NftItemRecord
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.flow.Flow

@Dao
interface NftItemDao {

    @Query(
        "SELECT * FROM NftItemRecord WHERE accountId = :accountId AND ownerAddress IN (:ownerAddresses) " +
            "ORDER BY ownerAddress, position"
    )
    fun observe(accountId: String, ownerAddresses: List<String>): Flow<List<NftItemRecord>>

    @Query(
        "DELETE FROM NftItemRecord " +
            "WHERE accountId = :accountId AND blockchainType = :blockchainType AND ownerAddress = :ownerAddress"
    )
    suspend fun deleteByOwner(accountId: String, blockchainType: BlockchainType, ownerAddress: String)

    /** `REPLACE`: a duplicate address in a remote list must not abort the whole write. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<NftItemRecord>)

    @Transaction
    suspend fun replace(
        accountId: String,
        blockchainType: BlockchainType,
        ownerAddress: String,
        items: List<NftItemRecord>,
    ) {
        deleteByOwner(accountId, blockchainType, ownerAddress)
        insertAll(items)
    }

    @Query("DELETE FROM NftItemRecord WHERE accountId IN (:accountIds)")
    suspend fun deleteByAccountIds(accountIds: List<String>)
}
