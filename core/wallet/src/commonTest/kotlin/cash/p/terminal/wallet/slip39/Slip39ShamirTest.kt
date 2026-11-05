package cash.p.terminal.wallet.slip39

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import kotlin.test.assertFailsWith

class Slip39ShamirTest {

    private val random = SecureRandom()

    @Test
    fun split_everySchemeAndSecretKind_anyThresholdSubsetCombinesToEms() {
        forEachSecretKind { ems ->
            SCHEMES.forEach { (threshold, count) ->
                val shares = Slip39Shamir.split(ems, threshold, count, random)

                assertEquals(count, shares.size)
                subsets(count, threshold).forEach { subset ->
                    assertSameEms(ems, combine(subset.map { shares[it].joinToString(" ") }))
                }
            }
        }
    }

    @Test
    fun combine_thresholdMinusOneShares_throwsInsufficientShares() {
        forEachSecretKind { ems ->
            SCHEMES.forEach { (threshold, count) ->
                val shares = Slip39Shamir.split(ems, threshold, count, random).map { it.joinToString(" ") }

                assertFailsWith<Slip39Exception.InsufficientShares> { combine(shares.take(threshold - 1)) }
            }
        }
    }

    @Test
    fun combine_sharesOfTwoSplitsOfSameEms_failsInsteadOfWrongSecret() {
        forEachSecretKind { ems ->
            SCHEMES.forEach { (threshold, count) ->
                val first = Slip39Shamir.split(ems, threshold, count, random).map { it.joinToString(" ") }
                val second = Slip39Shamir.split(ems, threshold, count, random).map { it.joinToString(" ") }

                assertFailsWith<Slip39Exception.InvalidDigest> {
                    combine(first.take(threshold - 1) + second[threshold - 1])
                }
            }
        }
    }

    @Test
    fun split_thresholdOne_returnsStableCanonicalShareOfEms() {
        forEachSecretKind { ems ->
            val canonical = Slip39Shamir.split(ems, 1, 1, random).single()
            val share = Slip39Shamir.parseShare(canonical.joinToString(" "))

            assertEquals(canonical, Slip39Shamir.split(ems, 1, 1, random).single())
            assertTrue(share.isSingleShare)
            assertEquals(Slip39Share.wordCountFor(ems.ciphertext.size), canonical.size)
            assertSameEms(ems, Slip39Shamir.combine(listOf(share)))
        }
    }

    @Test
    fun split_unsupportedSchemeOrSecretSize_throwsIllegalArgument() {
        val ems = randomEms(size = 16, extendable = false)

        listOf(0 to 1, 3 to 2, 1 to 2, 2 to 17).forEach { (threshold, count) ->
            assertFailsWith<IllegalArgumentException> { Slip39Shamir.split(ems, threshold, count, random) }
        }
        assertFailsWith<IllegalArgumentException> {
            Slip39Shamir.split(randomEms(size = 24, extendable = false), 2, 3, random)
        }
    }

    @Test
    fun parseShare_uppercaseAndExtraWhitespace_parsesSameShare() {
        val mnemonic = slip39Vector(42).mnemonics.single()

        val share = Slip39Shamir.parseShare("  " + mnemonic.uppercase().replace(" ", " \n\t") + " ")

        assertEquals(mnemonic.split(" "), share.words())
        assertEquals(29019, share.identifier)
        assertTrue(share.extendable)
        assertEquals(3, share.iterationExponent)
        assertTrue(share.isSingleShare)
    }

    @Test
    fun parseShare_unknownWord_throwsInvalidWordWithItsPosition() {
        val words = slip39Vector(4).mnemonics.first().split(" ").toMutableList()
        words[5] = "bitcoin"

        val error = assertFailsWith<Slip39Exception.InvalidWord> { Slip39Shamir.parseShare(words.joinToString(" ")) }

        assertEquals(5, error.index)
        assertFalse(error.message.orEmpty().contains("bitcoin"))
    }

    @Test
    fun parseShare_memberOfTwoOfThree_isNotSingleShare() {
        val share = Slip39Shamir.parseShare(slip39Vector(4).mnemonics.first())

        assertFalse(share.isSingleShare)
        assertEquals(2, share.memberThreshold)
        assertEquals(20, share.wordCount)
    }

    private fun forEachSecretKind(block: (EncryptedMasterSecret) -> Unit) {
        listOf(16, 32).forEach { size ->
            listOf(false, true).forEach { extendable -> block(randomEms(size, extendable)) }
        }
    }

    private fun randomEms(size: Int, extendable: Boolean) = EncryptedMasterSecret(
        identifier = random.nextInt(1 shl 15),
        extendable = extendable,
        iterationExponent = random.nextInt(16),
        ciphertext = ByteArray(size).also(random::nextBytes),
    )

    private fun assertSameEms(expected: EncryptedMasterSecret, actual: EncryptedMasterSecret) {
        assertEquals(expected.identifier, actual.identifier)
        assertEquals(expected.extendable, actual.extendable)
        assertEquals(expected.iterationExponent, actual.iterationExponent)
        assertArrayEquals(expected.ciphertext, actual.ciphertext)
    }

    private fun subsets(count: Int, size: Int, start: Int = 0): List<List<Int>> = when {
        size == 0 -> listOf(emptyList())
        start >= count -> emptyList()
        else -> subsets(count, size - 1, start + 1).map { listOf(start) + it } + subsets(count, size, start + 1)
    }

    private companion object {
        val SCHEMES = listOf(2 to 3, 3 to 5, 4 to 7, 16 to 16)
    }
}
