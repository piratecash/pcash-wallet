package cash.p.terminal.core.utils

import cash.p.terminal.core.tryOrNull

/** ZIP-302 memo bytes: the only two forms a payment URI may carry are a text memo and no memo. */
object ZcashMemo {
    const val MAX_SIZE_BYTES = 512

    private const val EMPTY_MARKER = 0xF6.toByte()
    private const val MAX_TEXT_LEAD = 0xF4

    fun decodeOrNull(bytes: ByteArray): String? {
        if (bytes.size > MAX_SIZE_BYTES) return null
        val padded = bytes.copyOf(MAX_SIZE_BYTES)
        if (padded[0] == EMPTY_MARKER) return "".takeIf { padded.isEmptyMemoTail() }
        if (padded[0].toInt() and 0xFF > MAX_TEXT_LEAD) return null
        val text = tryOrNull {
            padded.decodeToString(endIndex = padded.textLength(), throwOnInvalidSequence = true)
        }
        return text?.takeUnless { it.isBlank() }
    }

    /** Text that is left empty once the trailing zeros go is the empty memo, not a zero-length one. */
    fun encode(text: String): ByteArray {
        val bytes = text.encodeToByteArray()
        require(bytes.size <= MAX_SIZE_BYTES) { "Zcash memo is longer than $MAX_SIZE_BYTES bytes" }
        val length = bytes.textLength()
        return if (length == 0) byteArrayOf(EMPTY_MARKER) else bytes.copyOf(length)
    }

    private fun ByteArray.isEmptyMemoTail(): Boolean = (1 until size).all { this[it] == 0.toByte() }

    /** Padding is indistinguishable from trailing zero bytes, so both are dropped. */
    private fun ByteArray.textLength(): Int {
        var length = size
        while (length > 0 && this[length - 1] == 0.toByte()) length--
        return length
    }
}
