package cash.p.terminal.wallet.storage

import androidx.room.RoomDatabase
import androidx.room.util.performInTransactionSuspending
import androidx.room.util.runBlockingUninterruptible

/** Room's blocking `runInTransaction` is Android-only; the suspending form works on every target. */
internal actual fun RoomDatabase.inTransaction(body: () -> Unit) =
    runBlockingUninterruptible { performInTransactionSuspending(this@inTransaction) { body() } }
