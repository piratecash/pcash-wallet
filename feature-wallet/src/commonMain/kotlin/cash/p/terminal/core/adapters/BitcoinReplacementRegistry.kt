package cash.p.terminal.core.adapters

import cash.p.terminal.core.TransactionSettingsStorage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BitcoinReplacementLink(
    val originalHash: String,
    val originalUid: String,
    val replacementHash: String,
    val replacementUid: String,
)

enum class ReplacementMemberState { Mined, Alive, Dead }

/** Links glued by hash: A->B and B->C form one chain A, B, C. */
class BitcoinReplacementChain(val links: List<BitcoinReplacementLink>) {

    /** Root first, then every replacement in the order it was made. */
    val hashes: List<String> = orderedHashes(links)

    val uids: Set<String> = links.flatMapTo(mutableSetOf()) { listOf(it.originalUid, it.replacementUid) }

    fun roleOf(
        hash: String,
        uid: String,
        conflictingHash: String?,
        stateOf: (hash: String) -> ReplacementMemberState,
    ): BitcoinReplacementRole {
        val ownConflict = conflictingHash in hashes
        // Without a winner every member stays as it was, so no two visible records fight over the swap.
        val winner = resolveReplacementWinner(hashes, stateOf)
            ?: return BitcoinReplacementRole(ownConflict = ownConflict)
        return BitcoinReplacementRole(
            replacedByHash = winner.takeIf { it != hash },
            replacesHash = hashes.first().takeIf { winner == hash && it != hash },
            otherChainUids = (uids - uid).toList(),
            ownConflict = ownConflict,
        )
    }

    private companion object {
        fun orderedHashes(links: List<BitcoinReplacementLink>): List<String> {
            val next = links.groupBy({ it.originalHash }, { it.replacementHash })
            val replaced = links.mapTo(mutableSetOf()) { it.replacementHash }
            val ordered = LinkedHashSet<String>()
            val pending = ArrayDeque(links.map { it.originalHash }.filter { it !in replaced })
            while (pending.isNotEmpty()) {
                val hash = pending.removeFirst()
                if (ordered.add(hash)) pending.addAll(next[hash].orEmpty())
            }
            // Corrupted storage can leave no root; keep every member anyway.
            links.forEach { ordered += it.originalHash; ordered += it.replacementHash }
            return ordered.toList()
        }
    }
}

data class BitcoinReplacementRole(
    val replacedByHash: String? = null,
    val replacesHash: String? = null,
    val otherChainUids: List<String> = emptyList(),
    val ownConflict: Boolean = false,
)

/**
 * At most one member can be mined; otherwise the latest member the kit still holds wins. With every
 * member dead there is no winner: the kit may have dropped some of them, so nothing is hidden.
 */
fun resolveReplacementWinner(
    hashes: List<String>,
    stateOf: (hash: String) -> ReplacementMemberState,
): String? {
    val states = hashes.associateWith(stateOf)
    return hashes.firstOrNull { states[it] == ReplacementMemberState.Mined }
        ?: hashes.lastOrNull { states[it] == ReplacementMemberState.Alive }
}

/**
 * Links between a sped-up transaction and its replacement. Only this app knows them: the kit
 * cannot tell our own replacement from a foreign double spend.
 */
class BitcoinReplacementRegistry(private val storage: TransactionSettingsStorage) {

    private val mutex = Mutex()
    private val _changes =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    suspend fun remember(accountId: String, blockchainTypeUid: String, link: BitcoinReplacementLink) {
        mutex.withLock {
            storage.bitcoinReplacementLinks = storage.bitcoinReplacementLinks +
                with(link) {
                    listOf(accountId, blockchainTypeUid, originalHash, originalUid, replacementHash, replacementUid)
                        .joinToString(SEPARATOR)
                }
        }
        _changes.tryEmit(Unit)
    }

    /** Read from storage on every call: several adapters of one account list the same transactions. */
    fun chainOf(accountId: String, blockchainTypeUid: String, hash: String): BitcoinReplacementChain? {
        val stored = storage.bitcoinReplacementLinks
        if (stored.none { hash in it }) return null
        val links = stored.mapNotNull { it.toLink(accountId, blockchainTypeUid) }
        val members = mutableSetOf(hash)
        var chain: List<BitcoinReplacementLink>
        do {
            val knownMembers = members.size
            chain = links.filter { it.originalHash in members || it.replacementHash in members }
            chain.forEach { members += it.originalHash; members += it.replacementHash }
        } while (members.size != knownMembers)
        return chain.takeIf { it.isNotEmpty() }?.let(::BitcoinReplacementChain)
    }

    private fun String.toLink(accountId: String, blockchainTypeUid: String): BitcoinReplacementLink? {
        val parts = split(SEPARATOR)
        if (parts.size != FIELD_COUNT || parts.any(String::isBlank)) return null
        if (parts[0] != accountId || parts[1] != blockchainTypeUid) return null
        return BitcoinReplacementLink(parts[2], parts[3], parts[4], parts[5])
    }

    private companion object {
        const val SEPARATOR = "|"
        const val FIELD_COUNT = 6
    }
}
