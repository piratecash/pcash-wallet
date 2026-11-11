package cash.p.terminal.shared.desktop.vault

/** The durable steps of a level operation, where a crash leaves the state the next unlock must recover from. */
enum class StorageCheckpoint {
    PREPARE_POINTER_WRITTEN,
    SET_LEVEL_POINTERS_INSERTED,
    SET_LEVEL_POINTERS_DELETED_FROM_SAFE,
    COLLAPSE_INTENT_WRITTEN,
    COLLAPSE_ACCOUNTS_ADOPTED,
    COLLAPSE_ENVELOPES_FREED,
    COLLAPSE_ENVELOPES_MARKED,
    COLLAPSE_SLOT_ERASED,
    COLLAPSE_POOL_RETURNED,
    NARROWING_CHAIN_SPLIT,
}

/** Lets a test interrupt the real code path at a step. */
interface StorageCheckpoints {
    fun reached(checkpoint: StorageCheckpoint)
}

object NoStorageCheckpoints : StorageCheckpoints {
    override fun reached(checkpoint: StorageCheckpoint) = Unit
}
