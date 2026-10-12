package cash.p.terminal.vault

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** HKDF-Expand with HMAC-SHA256 (RFC 5869); no Extract step, the PRK is already uniformly random. */
object Hkdf {
    private const val MAX_LENGTH = 255 * 32

    fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 0..MAX_LENGTH) { "HKDF-SHA256 output is limited to $MAX_LENGTH bytes" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))

        val result = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var counter: Byte = 1

        while (offset < length) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(counter)
            t = mac.doFinal()

            val copyLen = minOf(t.size, length - offset)
            System.arraycopy(t, 0, result, offset, copyLen)
            offset += copyLen
            counter++
        }
        return result
    }
}
