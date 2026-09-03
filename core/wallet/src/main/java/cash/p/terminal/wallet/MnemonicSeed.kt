package cash.p.terminal.wallet

import io.horizontalsystems.hdwalletkit.Language
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.horizontalsystems.hdwalletkit.WordList
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object MnemonicSeed {
    fun derive(words: List<String>, passphrase: String = ""): ByteArray {
        Mnemonic().validate(words)
        val separator = if (WordList.wordList(Language.Japanese).validWords(words)) "　" else " "
        return pbkdf2(words.joinToString(separator), "mnemonic$passphrase")
    }

    // Stored non-standard accounts intentionally derive from their original UTF-8 bytes.
    private fun pbkdf2(password: String, salt: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(password.toByteArray(Charsets.UTF_8), mac.algorithm))
        var block = mac.doFinal(salt.toByteArray(Charsets.UTF_8) + byteArrayOf(0, 0, 0, 1))
        val result = block.copyOf()
        repeat(PBKDF2_ROUNDS - 1) {
            block = mac.doFinal(block)
            result.indices.forEach { index ->
                result[index] = (result[index].toInt() xor block[index].toInt()).toByte()
            }
        }
        return result
    }

    private const val PBKDF2_ROUNDS = 2048
}
