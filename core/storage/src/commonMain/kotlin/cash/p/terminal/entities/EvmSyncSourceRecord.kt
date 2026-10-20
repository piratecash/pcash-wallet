package cash.p.terminal.entities

import androidx.room.Entity

@Entity(primaryKeys = ["blockchainTypeUid", "url"])
data class EvmSyncSourceRecord(
    val blockchainTypeUid: String,
    val url: String,
    val auth: String?,
)
