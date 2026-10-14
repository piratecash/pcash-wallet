package cash.p.terminal.wallet

import java.util.Date

/** The adapter, or the session behind it, is gone for good: nothing will bring this instance back. */
class AdapterStoppedException : IllegalStateException("adapter stopped")

val AdapterState.isAdapterStopped: Boolean
    get() = this is AdapterState.NotSynced && error is AdapterStoppedException

sealed class AdapterState {
    object Synced : AdapterState()
    object Connecting : AdapterState()
    data class Syncing(
        val progress: Double? = null,
        val lastBlockDate: Date? = null,
        val blocksRemained: Long? = null,
        val substatus: Substatus? = null
    ) : AdapterState()

    data class SearchingTxs(val count: Int) : AdapterState()
    data class NotSynced(val error: Throwable) : AdapterState()

    sealed class Substatus {
        data class WaitingForPeers(val connected: Int, val required: Int) : Substatus()

        data class SnapshotRestore(
            val stage: SnapshotRestoreStage,
            val downloadedBytes: Long? = null,
            val totalBytes: Long? = null,
        ) : Substatus()
    }

    enum class SnapshotRestoreStage {
        ResolvingBirthday,
        DownloadingSnapshot,
        ValidatingSnapshot,
        CountingShieldedOutputs,
        ScanningWalletOutputs,
        ImportingSnapshot,
        CatchingUp,
    }

    override fun toString(): String {
        return when (this) {
            is Synced -> "Synced"
            is Connecting -> "Connecting"
            is Syncing -> {
                val sub = substatus?.let { " substatus: $it" }.orEmpty()
                "Syncing ${
                    progress?.let { "${"%.2f".format(it)}%" }.orEmpty()
                } blocksRemained: $blocksRemained lastBlockDate: $lastBlockDate$sub"
            }

            is SearchingTxs -> "SearchingTxs count: $count"
            is NotSynced -> "NotSynced ${error.javaClass.simpleName} - message: ${error.message}"
        }
    }
}
