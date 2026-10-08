package cash.p.terminal.wallet.storage

import androidx.room.RoomDatabase

internal actual fun RoomDatabase.inTransaction(body: () -> Unit) = runInTransaction(body)
