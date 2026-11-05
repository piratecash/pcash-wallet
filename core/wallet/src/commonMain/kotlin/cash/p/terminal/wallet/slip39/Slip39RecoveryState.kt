// Port of RecoveryState from trezor/python-shamir-mnemonic (MIT, Copyright (c) 2018 Andrew R. Kozlik).
package cash.p.terminal.wallet.slip39

/**
 * Immutable progress of a share-by-share restore.
 * Invariant: every group holding at least its member threshold of shares passed the digest check.
 */
class Slip39RecoveryState private constructor(private val groups: Map<Int, List<Slip39Share>>) {

    constructor() : this(emptyMap())

    sealed interface AddResult {
        data class Admitted(val state: Slip39RecoveryState) : AddResult
        data class Rejected(val error: Slip39Exception) : AddResult

        /** The group's shares come from different splits; [state] no longer holds that group. */
        data class GroupInconsistent(val groupIndex: Int, val state: Slip39RecoveryState) : AddResult
    }

    private val anyShare: Slip39Share? get() = groups.values.firstOrNull()?.first()

    val groupThreshold: Int? get() = anyShare?.groupThreshold

    val groupCount: Int? get() = anyShare?.groupCount

    val isComplete: Boolean get() = groupThreshold?.let { completeGroups().size >= it } ?: false

    fun memberThreshold(groupIndex: Int): Int? = groups[groupIndex]?.first()?.memberThreshold

    fun receivedCount(groupIndex: Int): Int = groups[groupIndex]?.size ?: 0

    /** Exactly group-threshold complete groups (lowest indices), each with its first member-threshold shares. */
    fun selectedForCombine(): List<Slip39Share> {
        val threshold = groupThreshold ?: return emptyList()
        return completeGroups().take(threshold).flatMap { it.take(it.first().memberThreshold) }
    }

    fun add(share: Slip39Share): AddResult {
        rejectionOf(share)?.let { return AddResult.Rejected(it) }
        val group = groups[share.groupIndex].orEmpty() + share
        if (group.size == share.memberThreshold && !isConsistent(group)) {
            return AddResult.GroupInconsistent(share.groupIndex, Slip39RecoveryState(groups - share.groupIndex))
        }
        return AddResult.Admitted(Slip39RecoveryState(groups + (share.groupIndex to group)))
    }

    private fun rejectionOf(share: Slip39Share): Slip39Exception? = try {
        Slip39Shamir.groupedSet(groups.values.flatten() + share)
        null
    } catch (e: Slip39Exception) {
        e
    }

    private fun completeGroups(): List<List<Slip39Share>> =
        groups.toSortedMap().values.filter { it.size >= it.first().memberThreshold }

    private fun isConsistent(group: List<Slip39Share>): Boolean = try {
        Slip39Shamir.recoverGroupSecret(group)
        true
    } catch (_: Slip39Exception.InvalidDigest) {
        false
    }
}
