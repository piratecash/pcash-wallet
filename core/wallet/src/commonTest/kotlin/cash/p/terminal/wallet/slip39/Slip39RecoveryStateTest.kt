package cash.p.terminal.wallet.slip39

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.test.assertIs

class Slip39RecoveryStateTest {

    private val random = SecureRandom()

    // One 2-of-4 group set (id 9497) spread over vectors 14-19: group 2 needs 3 members, group 3 needs 2.
    private val groupedShares = (14..19).flatMap { slip39Vector(it).mnemonics }.distinct().map(Slip39Shamir::parseShare)
    private val groupedSecret = slip39Vector(17).secretHex

    @Test
    fun add_twoGroupSetEnteredAcrossGroups_tracksProgressAndCombines() {
        val c1 = share(groupIndex = 2, memberIndex = 0)
        var state = admit(Slip39RecoveryState(), c1)

        assertEquals(2, state.groupThreshold)
        assertEquals(4, state.groupCount)
        assertEquals(3, state.memberThreshold(2))
        assertEquals(1, state.receivedCount(2))
        assertNull(state.memberThreshold(3))

        state = admit(state, share(groupIndex = 3, memberIndex = 0), share(groupIndex = 3, memberIndex = 4))
        assertEquals(2, state.receivedCount(3))
        assertFalse(state.isComplete)

        state = admit(state, share(groupIndex = 2, memberIndex = 2), share(groupIndex = 2, memberIndex = 4))
        assertTrue(state.isComplete)
        assertEquals(groupedSecret, combinedSecret(state))
    }

    @Test
    fun add_surplusMembersAndGroups_admittedButNotSelected() {
        val state = admit(Slip39RecoveryState(), *groupedShares.toTypedArray())

        assertEquals(4, state.receivedCount(3))
        assertEquals(2, state.selectedForCombine().size)
        assertEquals(groupedSecret, combinedSecret(state))
    }

    @Test
    fun add_anyOrder_completesToSameSecret() {
        repeat(10) { seed ->
            val state = admit(Slip39RecoveryState(), *groupedShares.shuffled(Random(seed)).toTypedArray())

            assertTrue(state.isComplete)
            assertEquals(groupedSecret, combinedSecret(state))
        }
    }

    @Test
    fun add_sameShareTwice_rejectsDuplicate() {
        val first = share(groupIndex = 3, memberIndex = 0)
        val state = admit(Slip39RecoveryState(), first)

        val result = state.add(first)

        assertIs<Slip39Exception.DuplicateShare>(assertIs<Slip39RecoveryState.AddResult.Rejected>(result).error)
        assertEquals(1, state.receivedCount(3))
    }

    @Test
    fun add_shareOfAnotherSet_rejectsMismatchedSet() {
        val state = admit(Slip39RecoveryState(), groupedShares.first())

        val result = state.add(Slip39Shamir.parseShare(slip39Vector(4).mnemonics.first()))

        assertIs<Slip39Exception.MismatchedSet>(assertIs<Slip39RecoveryState.AddResult.Rejected>(result).error)
    }

    @Test
    fun add_differentMemberThresholdInGroup_rejectsMismatchedSet() {
        val (first, second) = slip39Vector(12).mnemonics.map(Slip39Shamir::parseShare)

        val result = admit(Slip39RecoveryState(), first).add(second)

        assertIs<Slip39Exception.MismatchedSet>(assertIs<Slip39RecoveryState.AddResult.Rejected>(result).error)
    }

    @Test
    fun add_twentyAndThirtyThreeWordSharesWithEqualMetadata_rejectsMismatchedSet() {
        val short = EncryptedMasterSecret(1234, false, 1, ByteArray(16).also(random::nextBytes))
        val long = EncryptedMasterSecret(1234, false, 1, ByteArray(32).also(random::nextBytes))
        val state = admit(Slip39RecoveryState(), splitShares(short)[0])

        val result = state.add(splitShares(long)[1])

        assertIs<Slip39Exception.MismatchedSet>(assertIs<Slip39RecoveryState.AddResult.Rejected>(result).error)
    }

    @Test
    fun add_groupCompletedFromTwoSplitsOfSameEms_clearsGroupAndAllowsReentry() {
        val ems = EncryptedMasterSecret(4321, false, 1, ByteArray(16).also(random::nextBytes))
        val first = splitShares(ems)
        val second = splitShares(ems)
        val partial = admit(Slip39RecoveryState(), first[0])

        val inconsistent = assertIs<Slip39RecoveryState.AddResult.GroupInconsistent>(partial.add(second[1]))

        assertEquals(0, inconsistent.groupIndex)
        assertEquals(0, inconsistent.state.receivedCount(0))
        assertFalse(inconsistent.state.isComplete)
        val recovered = admit(inconsistent.state, first[0], first[1])
        assertTrue(recovered.isComplete)
        assertArrayEquals(ems.ciphertext, Slip39Shamir.combine(recovered.selectedForCombine()).ciphertext)
    }

    @Test
    fun selectedForCombine_emptyState_returnsNothing() {
        val state = Slip39RecoveryState()

        assertFalse(state.isComplete)
        assertTrue(state.selectedForCombine().isEmpty())
        assertNull(state.groupThreshold)
    }

    private fun share(groupIndex: Int, memberIndex: Int) =
        groupedShares.first { it.groupIndex == groupIndex && it.memberIndex == memberIndex }

    private fun splitShares(ems: EncryptedMasterSecret) =
        Slip39Shamir.split(ems, 2, 3, random).map { Slip39Shamir.parseShare(it.joinToString(" ")) }

    private fun admit(state: Slip39RecoveryState, vararg shares: Slip39Share): Slip39RecoveryState =
        shares.fold(state) { current, share ->
            assertIs<Slip39RecoveryState.AddResult.Admitted>(current.add(share)).state
        }

    private fun combinedSecret(state: Slip39RecoveryState) =
        Slip39Shamir.combine(state.selectedForCombine()).decrypt("TREZOR").toHexString()
}
