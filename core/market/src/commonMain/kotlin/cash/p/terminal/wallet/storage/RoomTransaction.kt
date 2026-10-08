package cash.p.terminal.wallet.storage

import androidx.room.RoomDatabase

internal expect fun RoomDatabase.inTransaction(body: () -> Unit)
