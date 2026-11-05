// SLIP-39 share encoding, ported from trezor/python-shamir-mnemonic (MIT, Copyright (c) 2018 Andrew R. Kozlik).
package cash.p.terminal.wallet.slip39

import java.math.BigInteger

class Slip39Share internal constructor(
    val identifier: Int,
    val extendable: Boolean,
    val iterationExponent: Int,
    val groupIndex: Int,
    val groupThreshold: Int,
    val groupCount: Int,
    val memberIndex: Int,
    val memberThreshold: Int,
    internal val value: ByteArray,
) {
    val wordCount: Int get() = wordCountFor(value.size)

    val isSingleShare: Boolean get() = groupThreshold == 1 && groupCount == 1 && memberThreshold == 1

    fun words(): List<String> {
        val data = headerIndices() + toIndices(BigInteger(1, value), valueWordCount(value.size))
        return (data + createChecksum(data, extendable)).map(Slip39Wordlist.words::get)
    }

    /** Word count is part of the set: shares of a 16- and a 32-byte secret never combine. */
    internal fun belongsToSameSet(other: Slip39Share): Boolean =
        identifier == other.identifier &&
            extendable == other.extendable &&
            iterationExponent == other.iterationExponent &&
            groupThreshold == other.groupThreshold &&
            groupCount == other.groupCount &&
            value.size == other.value.size

    override fun toString(): String = "Slip39Share(groupIndex=$groupIndex, memberIndex=$memberIndex)"

    private fun headerIndices(): List<Int> {
        val extendableBit = if (extendable) 1 else 0
        val idExp = (identifier shl ID_SHIFT) or (extendableBit shl ITERATION_EXP_BITS) or iterationExponent
        val params = listOf(groupIndex, groupThreshold - 1, groupCount - 1, memberIndex, memberThreshold - 1)
            .fold(0) { acc, nibble -> (acc shl NIBBLE_BITS) or nibble }
        return toIndices(BigInteger.valueOf(idExp.toLong()), HEADER_PART_WORDS) +
            toIndices(BigInteger.valueOf(params.toLong()), HEADER_PART_WORDS)
    }

    internal companion object {
        val SUPPORTED_WORD_COUNTS = setOf(20, 33)

        fun wordCountFor(valueSize: Int): Int = METADATA_LENGTH_WORDS + valueWordCount(valueSize)

        fun decode(mnemonic: String): Slip39Share {
            val indices = wordIndices(mnemonic)
            val idExp = fromIndices(indices.subList(0, HEADER_PART_WORDS)).toInt()
            val extendable = (idExp shr ITERATION_EXP_BITS) and 1 == 1
            if (!verifyChecksum(indices, extendable)) throw Slip39Exception.InvalidChecksum()
            // Fields are read from the lowest nibble up; thresholds and counts are encoded minus one.
            var params = fromIndices(indices.subList(HEADER_PART_WORDS, HEADER_WORDS)).toInt()
            fun nextNibble(): Int = (params and NIBBLE_MASK).also { params = params shr NIBBLE_BITS }
            val memberThreshold = nextNibble() + 1
            val memberIndex = nextNibble()
            val groupCount = nextNibble() + 1
            val groupThreshold = nextNibble() + 1
            val groupIndex = nextNibble()
            if (groupThreshold > groupCount) throw Slip39Exception.InvalidGroupThreshold()
            return Slip39Share(
                identifier = idExp shr ID_SHIFT,
                extendable = extendable,
                iterationExponent = idExp and NIBBLE_MASK,
                groupIndex = groupIndex,
                groupThreshold = groupThreshold,
                groupCount = groupCount,
                memberIndex = memberIndex,
                memberThreshold = memberThreshold,
                value = decodeValue(indices.subList(HEADER_WORDS, indices.size - CHECKSUM_LENGTH_WORDS)),
            )
        }

        private fun wordIndices(mnemonic: String): List<Int> {
            val words = mnemonic.lowercase().split(WHITESPACE).filter { it.isNotEmpty() }
            val indices = words.mapIndexed { position, word ->
                Slip39Wordlist.indexOf(word) ?: throw Slip39Exception.InvalidWord(position)
            }
            if (indices.size !in SUPPORTED_WORD_COUNTS) throw Slip39Exception.InvalidLength()
            return indices
        }

        private fun decodeValue(valueIndices: List<Int>): ByteArray {
            val paddingBits = RADIX_BITS * valueIndices.size % 16
            val byteCount = (RADIX_BITS * valueIndices.size - paddingBits) / Byte.SIZE_BITS
            val value = fromIndices(valueIndices)
            if (value.bitLength() > byteCount * Byte.SIZE_BITS) throw Slip39Exception.InvalidPadding()
            val raw = value.toByteArray().takeLast(byteCount).toByteArray()
            return ByteArray(byteCount - raw.size) + raw
        }

        private fun valueWordCount(valueSize: Int): Int = (valueSize * Byte.SIZE_BITS + RADIX_BITS - 1) / RADIX_BITS

        private fun fromIndices(indices: List<Int>): BigInteger =
            indices.fold(BigInteger.ZERO) { acc, index ->
                acc.shiftLeft(RADIX_BITS).or(BigInteger.valueOf(index.toLong()))
            }

        private fun toIndices(value: BigInteger, count: Int): List<Int> =
            (count - 1 downTo 0).map { value.shiftRight(it * RADIX_BITS).toInt() and RADIX_MASK }

        private fun createChecksum(data: List<Int>, extendable: Boolean): List<Int> {
            val polymod = polymod(customization(extendable) + data + List(CHECKSUM_LENGTH_WORDS) { 0 }) xor 1
            return (CHECKSUM_LENGTH_WORDS - 1 downTo 0).map { (polymod shr (RADIX_BITS * it)) and RADIX_MASK }
        }

        private fun verifyChecksum(indices: List<Int>, extendable: Boolean): Boolean =
            polymod(customization(extendable) + indices) == 1

        private fun customization(extendable: Boolean): List<Int> =
            (if (extendable) "shamir_extendable" else "shamir").map { it.code }

        private fun polymod(values: List<Int>): Int = values.fold(1) { checksum, value ->
            val top = checksum shr 20
            RS1024_GENERATOR.foldIndexed(((checksum and 0xFFFFF) shl RADIX_BITS) xor value) { i, acc, generator ->
                if ((top shr i) and 1 == 1) acc xor generator else acc
            }
        }

        private val WHITESPACE = Regex("\\s+")
        private val RS1024_GENERATOR = intArrayOf(
            0xE0E040, 0x1C1C080, 0x3838100, 0x7070200, 0xE0E0009,
            0x1C0C2412, 0x38086C24, 0x3090FC48, 0x21B1F890, 0x3F3F120,
        )
        private const val RADIX_BITS = 10
        private const val RADIX_MASK = (1 shl RADIX_BITS) - 1
        private const val ITERATION_EXP_BITS = 4
        private const val ID_SHIFT = ITERATION_EXP_BITS + 1
        private const val NIBBLE_BITS = 4
        private const val NIBBLE_MASK = 0xF
        private const val HEADER_PART_WORDS = 2
        private const val HEADER_WORDS = 2 * HEADER_PART_WORDS
        private const val CHECKSUM_LENGTH_WORDS = 3
        private const val METADATA_LENGTH_WORDS = HEADER_WORDS + CHECKSUM_LENGTH_WORDS
    }
}
