package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.storage.desktop.CollapseIntent
import cash.p.terminal.core.storage.desktop.SafeLink

/**
 * Removes every level below a safe. Each step is one durable write, repeatable after a crash, and a place
 * returns to the pool only once it has been erased.
 */
internal class CollapseOperation(private val environment: StorageEnvironment, private val opener: SafeOpener) {

    /** Steps 0 and 1, on a chain that is open. */
    fun begin(safe: OpenSafe, lower: List<OpenSafe>) {
        safe.dao.putMeta(safe.meta.copy(collapse = intentOf(lower)))
        checkpoint(StorageCheckpoint.COLLAPSE_INTENT_WRITTEN)
        adoptAccounts(safe, lower)
    }

    /** Runs only for a safe with an intent: a live link to a level below is no sign of a crash. */
    fun recover(safe: OpenSafe) {
        val intent = safe.meta.collapse ?: return
        if (!intent.envelopesFreed) {
            val lower = openChainBelow(safe)
            try {
                adoptAccounts(safe, lower)
            } finally {
                lower.forEach(OpenSafe::close)
            }
        }
        finish(safe)
    }

    /** Steps 2 to 4. The levels below must be closed. */
    fun finish(safe: OpenSafe) {
        val intent = freeEnvelopes(safe)
        intent.levels.forEach {
            environment.slots.erase(it.slot)
            checkpoint(StorageCheckpoint.COLLAPSE_SLOT_ERASED)
        }
        val meta = safe.meta
        safe.dao.putMeta(
            meta.copy(
                child = null,
                collapse = null,
                slotPool = meta.slotPool + intent.slotPool + intent.levels.map { it.slot },
                envelopePool = meta.envelopePool + intent.envelopePool + intent.levels.map { it.envelopeIndex },
            )
        )
        checkpoint(StorageCheckpoint.COLLAPSE_POOL_RETURNED)
    }

    /** The intent is rebuilt in the same transaction: what a level gave away since the crash is not returned. */
    private fun adoptAccounts(safe: OpenSafe, lower: List<OpenSafe>) {
        val pointers = lower.flatMap { it.dao.pointers() }
        safe.dao.adoptPointers(pointers, safe.meta.copy(collapse = intentOf(lower)))
        pointers.forEach { it.storeKey.fill(0) }
        checkpoint(StorageCheckpoint.COLLAPSE_ACCOUNTS_ADOPTED)
    }

    private fun freeEnvelopes(safe: OpenSafe): CollapseIntent {
        val meta = safe.meta
        val intent = checkNotNull(meta.collapse) { "No level removal in progress" }
        if (intent.envelopesFreed) return intent
        environment.passwordVault.freeEnvelopes(intent.levels.map { it.envelopeIndex })
        checkpoint(StorageCheckpoint.COLLAPSE_ENVELOPES_FREED)
        val freed = intent.copy(envelopesFreed = true)
        safe.dao.putMeta(meta.copy(collapse = freed))
        checkpoint(StorageCheckpoint.COLLAPSE_ENVELOPES_MARKED)
        return freed
    }

    private fun intentOf(lower: List<OpenSafe>): CollapseIntent {
        val metas = lower.map { it.meta }
        return CollapseIntent(
            levels = lower.map { SafeLink(it.link.slot, it.link.key.copyOf(), it.link.envelopeIndex) },
            slotPool = metas.flatMapTo(mutableSetOf()) { it.slotPool },
            envelopePool = metas.flatMapTo(mutableSetOf()) { it.envelopePool },
        )
    }

    /** Any failure closes what it opened and throws, so the operation waits and nothing is erased. */
    private fun openChainBelow(safe: OpenSafe): List<OpenSafe> {
        val opened = mutableListOf<OpenSafe>()
        try {
            var link = safe.meta.child
            while (link != null) {
                val next = opener.open(link).also(opened::add)
                recover(next)
                link = next.meta.child
            }
            return opened
        } catch (e: Throwable) {
            opened.forEach(OpenSafe::close)
            throw e
        }
    }

    private fun checkpoint(checkpoint: StorageCheckpoint) = environment.checkpoints.reached(checkpoint)
}
