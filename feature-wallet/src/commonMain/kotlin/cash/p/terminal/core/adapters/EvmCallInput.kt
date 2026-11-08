package cash.p.terminal.core.adapters

internal const val SELECTOR_SIZE = 4
private const val WORD_SIZE = 32
private const val MAX_MULTICALL_DEPTH = 2

/** Head position of the `bytes[]` argument for each multicall overload. */
private val MULTICALL_ARRAY_HEADS = mapOf(0xac9650d8L to 0, 0x5ae401dcL to 1, 0x1f0464d1L to 1)

internal val ByteArray.selector: Long?
    get() = if (size < SELECTOR_SIZE) {
        null
    } else {
        (0 until SELECTOR_SIZE).fold(0L) { value, i -> (value shl 8) or (this[i].toLong() and 0xFF) }
    }

/** The call itself or the inner calls of a multicall; malformed or too deeply nested input gives none. */
internal fun flattenCalls(input: ByteArray): List<ByteArray> = flatten(input, MAX_MULTICALL_DEPTH).orEmpty()

private fun flatten(call: ByteArray, depthLeft: Int): List<ByteArray>? {
    val selector = call.selector ?: return null
    val arrayHead = MULTICALL_ARRAY_HEADS[selector] ?: return listOf(call)
    if (depthLeft == 0) return null
    val inner = EvmCallInput(call, SELECTOR_SIZE).bytesArray(arrayHead) ?: return null
    return inner.flatMap { flatten(it, depthLeft - 1) ?: return null }
}

/** Reads untrusted ABI data: anything out of bounds gives null, nothing is sized by an unchecked length. */
internal class EvmCallInput(private val data: ByteArray, private val base: Int = 0) {

    fun isWordZero(head: Int): Boolean? {
        val start = base + head * WORD_SIZE
        if (start > data.size - WORD_SIZE) return null
        return (start until start + WORD_SIZE).all { data[it] == ZERO }
    }

    fun bytes(head: Int): ByteArray? = dynamicPosition(base, head)?.let(::bytesAt)

    fun bytesArray(head: Int): List<ByteArray>? {
        val arrayPosition = dynamicPosition(base, head) ?: return null
        val count = uintAt(arrayPosition) ?: return null
        val elements = arrayPosition + WORD_SIZE
        if (count > (data.size - elements) / WORD_SIZE) return null

        // Elements may alias one payload, so the sum is capped to keep the copies linear in the input.
        var totalSize = 0
        return buildList {
            repeat(count) { index ->
                val element = dynamicPosition(elements, index)?.let(::bytesAt) ?: return null
                totalSize += element.size
                if (totalSize > data.size) return null
                add(element)
            }
        }
    }

    private fun dynamicPosition(frame: Int, head: Int): Int? {
        val offset = uintAt(frame + head * WORD_SIZE) ?: return null
        return (frame + offset).takeIf { it <= data.size }
    }

    private fun bytesAt(position: Int): ByteArray? {
        val length = uintAt(position) ?: return null
        val start = position + WORD_SIZE
        if (length > data.size - start) return null
        return data.copyOfRange(start, start + length)
    }

    private fun uintAt(position: Int): Int? {
        if (position > data.size - WORD_SIZE) return null
        val lowBytes = position + WORD_SIZE - Int.SIZE_BYTES
        if ((position until lowBytes).any { data[it] != ZERO }) return null
        val value = (lowBytes until position + WORD_SIZE).fold(0) { acc, i -> (acc shl 8) or (data[i].toInt() and 0xFF) }
        return value.takeIf { it in 0..data.size }
    }

    private companion object {
        const val ZERO: Byte = 0
    }
}
