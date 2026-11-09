package cash.p.terminal.core.adapters

import cash.p.terminal.core.TransactionSettingsStorage
import cash.p.terminal.core.adapters.ReplacementMemberState.Alive
import cash.p.terminal.core.adapters.ReplacementMemberState.Dead
import cash.p.terminal.core.adapters.ReplacementMemberState.Mined
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BitcoinReplacementRegistryTest {

    private var storedLinks = emptySet<String>()
    private val storage = mockk<TransactionSettingsStorage> {
        every { bitcoinReplacementLinks } answers { storedLinks }
        every { bitcoinReplacementLinks = any() } answers { storedLinks = firstArg() }
    }
    private val registry = BitcoinReplacementRegistry(storage)

    @Test
    fun roleOf_replacementAlive_replacementWinsAndOriginalIsSuperseded() = runTest {
        remember(link("A", "B"))
        val states = mapOf("A" to Dead, "B" to Alive)

        val original = role("A", "uid-A", conflictingHash = "B", states)
        val replacement = role("B", "uid-B", conflictingHash = null, states)

        assertEquals("B", original.replacedByHash)
        assertTrue(original.ownConflict)
        assertNull(replacement.replacedByHash)
        assertEquals("A", replacement.replacesHash)
        assertEquals(listOf("uid-A"), replacement.otherChainUids)
    }

    @Test
    fun chainOf_twoSpeedUps_gluesLinksIntoOneChainFromAnyMember() = runTest {
        remember(link("B", "C"))
        remember(link("A", "B"))

        val chain = chain("B")

        assertEquals(listOf("A", "B", "C"), chain?.hashes)
        assertEquals(setOf("uid-A", "uid-B", "uid-C"), chain?.uids)
        assertEquals(chain?.hashes, chain("A")?.hashes)
    }

    @Test
    fun roleOf_chainOfThree_lastAliveWinsAndReplacesRoot() = runTest {
        remember(link("A", "B"))
        remember(link("B", "C"))
        val states = mapOf("A" to Dead, "B" to Alive, "C" to Alive)

        assertEquals("C", role("B", "uid-B", conflictingHash = "C", states).replacedByHash)
        assertEquals("A", role("C", "uid-C", conflictingHash = null, states).replacesHash)
    }

    @Test
    fun roleOf_originalMined_originalWinsBack() = runTest {
        remember(link("A", "B"))
        val states = mapOf("A" to Mined, "B" to Dead)

        val original = role("A", "uid-A", conflictingHash = null, states)

        assertNull(original.replacedByHash)
        assertNull(original.replacesHash)
        assertEquals("A", role("B", "uid-B", conflictingHash = "A", states).replacedByHash)
    }

    @Test
    fun roleOf_replacementGoneFromKitAndUnmined_originalWins() = runTest {
        remember(link("A", "B"))

        val original = role("A", "uid-A", conflictingHash = null, mapOf("A" to Alive, "B" to Dead))

        assertNull(original.replacedByHash)
    }

    @Test
    fun roleOf_everyMemberDead_hidesNothing() = runTest {
        remember(link("A", "B"))
        val states = mapOf("A" to Dead, "B" to Dead)

        val original = role("A", "uid-A", conflictingHash = null, states)
        val replacement = role("B", "uid-B", conflictingHash = null, states)

        assertNull(original.replacedByHash)
        assertNull(replacement.replacedByHash)
        assertNull(replacement.replacesHash)
        assertTrue(original.otherChainUids.isEmpty())
        assertTrue(replacement.otherChainUids.isEmpty())
    }

    @Test
    fun roleOf_restoredWithNewUid_keepsPreviousUidOfItself() = runTest {
        remember(link("A", "B"))

        val replacement = role("B", "uid-B-restored", conflictingHash = null, mapOf("A" to Dead, "B" to Mined))

        assertEquals(setOf("uid-A", "uid-B"), replacement.otherChainUids.toSet())
    }

    @Test
    fun chainOf_unparseableRow_ignored() = runTest {
        storedLinks = setOf("$ACCOUNT|$BLOCKCHAIN|A|uid-A|B", "garbage A")
        remember(link("C", "D"))

        assertNull(chain("A"))
        assertEquals(listOf("C", "D"), chain("C")?.hashes)
    }

    @Test
    fun chainOf_linkOfOtherAccountOrBlockchain_ignored() = runTest {
        registry.remember("other-account", BLOCKCHAIN, link("A", "B"))
        registry.remember(ACCOUNT, "litecoin", link("A", "B"))

        assertNull(chain("A"))
    }

    @Test
    fun remember_newLink_emitsChange() = runTest(UnconfinedTestDispatcher()) {
        val change = async { registry.changes.first() }

        remember(link("A", "B"))

        assertTrue(change.isCompleted)
    }

    private suspend fun remember(link: BitcoinReplacementLink) = registry.remember(ACCOUNT, BLOCKCHAIN, link)

    private fun chain(hash: String) = registry.chainOf(ACCOUNT, BLOCKCHAIN, hash)

    private fun role(
        hash: String,
        uid: String,
        conflictingHash: String?,
        states: Map<String, ReplacementMemberState>,
    ) = requireNotNull(chain(hash)).roleOf(hash, uid, conflictingHash) { states.getValue(it) }

    private fun link(original: String, replacement: String) =
        BitcoinReplacementLink(original, "uid-$original", replacement, "uid-$replacement")

    private companion object {
        const val ACCOUNT = "account"
        const val BLOCKCHAIN = "bitcoin"
    }
}
