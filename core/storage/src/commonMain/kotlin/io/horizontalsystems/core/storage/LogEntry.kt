package io.horizontalsystems.core.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class LogEntry(
        var date: Long,
        var level: Int,
        var actionId: String,
        var message: String,
        @ColumnInfo(defaultValue = "0")
        var userLevel: Int = 0
) {
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null
}
