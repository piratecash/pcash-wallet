// SLIP-39 Feistel cipher, ported from trezor/python-shamir-mnemonic (MIT, Copyright (c) 2018 Andrew R. Kozlik).
package cash.p.terminal.wallet.slip39

import cash.p.terminal.wallet.pbkdf2

class EncryptedMasterSecret(
    val identifier: Int,
    val extendable: Boolean,
    val iterationExponent: Int,
    val ciphertext: ByteArray,
) {
    /** Costs 10000 << [iterationExponent] PBKDF2 iterations: never call it on the main thread. */
    fun decrypt(passphrase: String): ByteArray {
        val password = passphrase.toByteArray(Charsets.UTF_8)
        var left = ciphertext.copyOfRange(0, ciphertext.size / 2)
        var right = ciphertext.copyOfRange(ciphertext.size / 2, ciphertext.size)
        for (round in ROUND_COUNT - 1 downTo 0) {
            val mixed = xor(left, roundFunction(round, password, right))
            left = right
            right = mixed
        }
        return right + left
    }

    override fun toString(): String =
        "EncryptedMasterSecret(identifier=$identifier, extendable=$extendable, iterationExponent=$iterationExponent)"

    private fun roundFunction(round: Int, password: ByteArray, right: ByteArray): ByteArray = pbkdf2(
        algorithm = "HmacSHA256",
        password = byteArrayOf(round.toByte()) + password,
        salt = salt() + right,
        iterations = (BASE_ITERATION_COUNT shl iterationExponent) / ROUND_COUNT,
        keyLength = right.size,
    )

    // The identifier salts the cipher only for non-extendable sets.
    private fun salt(): ByteArray = if (extendable) {
        ByteArray(0)
    } else {
        "shamir".toByteArray(Charsets.US_ASCII) + byteArrayOf((identifier shr 8).toByte(), identifier.toByte())
    }

    private fun xor(a: ByteArray, b: ByteArray) = ByteArray(a.size) { (a[it].toInt() xor b[it].toInt()).toByte() }

    private companion object {
        const val ROUND_COUNT = 4
        const val BASE_ITERATION_COUNT = 10000
    }
}
