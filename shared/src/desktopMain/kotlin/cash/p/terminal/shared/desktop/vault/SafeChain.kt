package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.storage.desktop.AccountPointer
import cash.p.terminal.core.storage.desktop.SafeLink
import cash.p.terminal.core.storage.desktop.VaultSafeMeta
import cash.p.terminal.wallet.managers.logIfBelowSessionRoot
import co.touchlab.kermit.Logger
import java.security.MessageDigest

private val logger = Logger.withTag("SafeChain")

/** Once the envelopes are freed the levels below are being erased: their link may lead to noise. */
internal val VaultSafeMeta.nextLevel: SafeLink?
    get() = child.takeUnless { collapse?.envelopesFreed == true }

/** The safes the session can reach, from its root down; the root stands at [rootLevel]. */
internal class SafeChain(private val opener: SafeOpener, private val collapse: CollapseOperation) : AutoCloseable {
    private class Reach(val rootLevel: Int, val safes: List<OpenSafe>)

    // One reference: a reader must never pair the safes of one narrowing with the root level of another.
    @Volatile
    private var reach = Reach(rootLevel = 0, safes = emptyList())

    var safes: List<OpenSafe>
        get() = reach.safes
        private set(value) {
            reach = Reach(reach.rootLevel, value)
        }

    val rootLevel: Int get() = reach.rootLevel

    val root: OpenSafe get() = safes.first()

    /** False when a level below could not be opened: its accounts are invisible, not gone. */
    val isComplete: Boolean get() = safes.last().meta.nextLevel == null

    fun open(root: OpenSafe) {
        safes = listOf(root)
        var current = root
        while (true) {
            recoverOrWait(current)
            current = openNext(current) ?: return
            safes = safes + current
        }
    }

    fun safe(level: Int): OpenSafe {
        logIfBelowSessionRoot(level, rootLevel)
        return requireNotNull(safeOrNull(level.coerceAtLeast(rootLevel))) { "No safe at level $level" }
    }

    fun safeOrNull(level: Int): OpenSafe? = reach.let { it.safes.getOrNull(level - it.rootLevel) }

    fun levelOpenedBy(slotKey: ByteArray): Int? = reach.let { current ->
        val index = current.safes.indexOfFirst { MessageDigest.isEqual(it.link.key, slotKey) }
        if (index < 0) null else current.rootLevel + index
    }

    /** Makes [level] the root and hands over the safes above it, still open, for the caller to close. */
    fun narrowTo(level: Int): List<OpenSafe> {
        val current = reach
        val count = level - current.rootLevel
        reach = Reach(level, current.safes.drop(count))
        return current.safes.take(count)
    }

    fun append(safe: OpenSafe) {
        safes = safes + safe
    }

    fun below(safe: OpenSafe): List<OpenSafe> = safes.drop(safes.indexOf(safe) + 1)

    fun closeBelow(safe: OpenSafe) {
        val closing = below(safe)
        safes = safes - closing.toSet()
        closing.forEach(OpenSafe::close)
    }

    /** The lowest one, as a duplicate left by a crash resolves. The caller wipes its key. */
    fun durablePointer(accountId: String): AccountPointer? =
        safes.asReversed().firstNotNullOfOrNull { it.dao.pointer(accountId) }

    fun deletePointers(accountIds: List<String>) = safes.forEach { it.dao.deletePointers(accountIds) }

    override fun close() {
        val closing = safes
        safes = emptyList()
        closing.forEach(OpenSafe::close)
    }

    /** A failed recovery waits for the next login: nothing was erased that it had not secured. */
    private fun recoverOrWait(safe: OpenSafe) {
        try {
            collapse.recover(safe)
        } catch (e: Exception) {
            logger.w(e) { "Interrupted level removal waits for the next login" }
        }
    }

    private fun openNext(safe: OpenSafe): OpenSafe? {
        val link = safe.meta.nextLevel ?: return null
        return try {
            opener.open(link)
        } catch (e: Exception) {
            logger.w(e) { "A lower level did not open" }
            null
        }
    }
}
