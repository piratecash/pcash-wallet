package cash.p.terminal.wallet

import io.horizontalsystems.hdwalletkit.Mnemonic
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object MnemonicSeed {
    fun derive(words: List<String>, passphrase: String = ""): ByteArray {
        validate(words)
        // Stored non-standard accounts intentionally derive from their original UTF-8 bytes.
        return pbkdf2(
            algorithm = "HmacSHA512",
            password = words.joinToString(" ").toByteArray(Charsets.UTF_8),
            salt = "mnemonic$passphrase".toByteArray(Charsets.UTF_8),
            iterations = PBKDF2_ROUNDS,
            keyLength = SEED_LENGTH,
        )
    }

    // The library puts the offending word into the message, and that message reaches logs.
    private fun validate(words: List<String>) {
        try {
            Mnemonic().validate(words)
        } catch (_: Mnemonic.InvalidMnemonicKeyException) {
            throw Mnemonic.InvalidMnemonicKeyException("Invalid mnemonic word")
        }
    }

    private const val PBKDF2_ROUNDS = 2048
    private const val SEED_LENGTH = 64
}

/** Single-block PBKDF2: [keyLength] must not exceed the MAC length. */
internal fun pbkdf2(
    algorithm: String,
    password: ByteArray,
    salt: ByteArray,
    iterations: Int,
    keyLength: Int,
): ByteArray {
    val mac = Mac.getInstance(algorithm)
    mac.init(SecretKeySpec(password, algorithm))
    require(keyLength <= mac.macLength)
    var block = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
    val result = block.copyOf()
    repeat(iterations - 1) {
        block = mac.doFinal(block)
        result.indices.forEach { index ->
            result[index] = (result[index].toInt() xor block[index].toInt()).toByte()
        }
    }
    return result.copyOf(keyLength)
}
