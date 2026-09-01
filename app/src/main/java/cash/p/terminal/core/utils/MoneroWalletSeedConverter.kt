package cash.p.terminal.core.utils

import androidx.annotation.VisibleForTesting
import cash.p.terminal.core.toFixedSize
import cash.p.terminal.core.toRawHexString
import com.m2049r.xmrwallet.util.ledger.Monero
import io.horizontalsystems.hdwalletkit.Mnemonic
import org.bitcoinj.crypto.DeterministicKey
import org.bitcoinj.crypto.HDKeyDerivation
import org.bitcoinj.crypto.HDPath.parsePath
import org.bouncycastle.jcajce.provider.digest.Keccak
import java.math.BigInteger
import java.util.zip.CRC32

data class MoneroSecretKeys(
    val spendKey: String,
    val viewKey: String
)

object MoneroWalletSeedConverter {
    private const val PAYLOAD_WORD_COUNT = MoneroConfig.WORD_COUNT - 1
    private const val PREFIX_LENGTH = 3
    private const val WORDS_PER_GROUP = 3
    private const val BYTES_PER_GROUP = 4
    private const val MAX_UINT32 = 0xFFFFFFFFL

    private val ed25519CurveOrder =
        BigInteger("1000000000000000000000000000000014DEF9DEA2F79CD65812631A5CF5D3ED", 16)

    private val englishWordIndexes: Map<String, Int> by lazy {
        Monero.ENGLISH_WORDS.withIndex().associate { (index, word) -> word to index }
    }

    /**
     * Convert a BIP39 mnemonic to a Monero 25-word legacy seed.
     *
     * The caller MUST pass NFKD-normalized [words] and [passphrase]. BIP39 PBKDF2 derives
     * the seed from the UTF-8 bytes of the mnemonic, so two visually identical strings
     * with different Unicode normalization forms (e.g. precomposed "á" vs "a"+combining
     * acute) produce different seeds and therefore different Monero accounts. Skipping
     * normalization on non-English wordlists silently routes users to the wrong wallet.
     */
    fun getLegacySeedFromBip39(
        words: List<String>,
        passphrase: String = "",
        accountIndex: Int = 0
    ): List<String> {
        val seed = Mnemonic().toSeed(words, passphrase)
        val bip32Seed = derivePath(seed, "m/44'/128'/$accountIndex'/0/0")
        val spendKey = reduceECKey(bip32Seed.privKeyBytes.toFixedSize(32))
        return encodePhrase(spendKey)
    }

    /**
     * Derive the Monero secret keys from a 25-word legacy (Electrum) seed. Both reductions
     * mirror native `account_base::generate()`, so a non-canonical seed yields native's keys.
     *
     * @throws IllegalArgumentException if the seed is malformed.
     */
    fun getSecretKeys(legacySeedWords: List<String>): MoneroSecretKeys {
        val words = legacySeedWords.map { it.trim().lowercase() }
        require(words.size == MoneroConfig.WORD_COUNT) {
            "Monero mnemonic must be ${MoneroConfig.WORD_COUNT} words long"
        }

        val payload = words.subList(0, PAYLOAD_WORD_COUNT)
        val wordIndexes = payload.map { word ->
            requireNotNull(englishWordIndexes[word]) {
                "Monero mnemonic contains a word outside the English wordlist"
            }
        }
        require(words.last() == checksumWord(payload)) { "Invalid Monero mnemonic checksum" }

        val spendKey = reduceECKey(decodeSpendKey(wordIndexes))
        val viewKey = reduceECKey(Keccak.Digest256().digest(spendKey))

        return MoneroSecretKeys(
            spendKey = spendKey.toRawHexString(),
            viewKey = viewKey.toRawHexString()
        )
    }

    internal fun checksumWord(words: List<String>): String {
        require(words.size == PAYLOAD_WORD_COUNT) {
            "Monero checksum is computed over $PAYLOAD_WORD_COUNT words"
        }

        val prefixes = words.joinToString("") { it.take(PREFIX_LENGTH) }
        val crc32 = CRC32().apply { update(prefixes.toByteArray()) }

        return words[(crc32.value % PAYLOAD_WORD_COUNT).toInt()]
    }

    private fun derivePath(seed: ByteArray, path: String): DeterministicKey {
        val masterKey = HDKeyDerivation.createMasterPrivateKey(seed)

        val pathParts = parsePath(path.replace("'", "H"))

        var currentKey = masterKey
        for (childNumber in pathParts.list()) {
            currentKey = HDKeyDerivation.deriveChildKey(currentKey, childNumber)
        }

        return currentKey
    }

    @VisibleForTesting
    internal fun encodePhrase(bytes: ByteArray): List<String> {
        require(bytes.size == 32) { "Private key must be exactly 32 bytes" }

        val wordList = Monero.ENGLISH_WORDS
        val wordCount = wordList.size // 1626
        val words = mutableListOf<String>()

        for (i in 0 until 32 step BYTES_PER_GROUP) {
            val group = ((bytes[i].toUInt() and 0xFFu) shl 0) or
                    ((bytes[i + 1].toUInt() and 0xFFu) shl 8) or
                    ((bytes[i + 2].toUInt() and 0xFFu) shl 16) or
                    ((bytes[i + 3].toUInt() and 0xFFu) shl 24)

            val w1 = (group % wordCount.toUInt()).toInt()
            val w2 = (((group / wordCount.toUInt()) + w1.toUInt()) % wordCount.toUInt()).toInt()
            val w3 =
                (((group / wordCount.toUInt() / wordCount.toUInt()) + w2.toUInt()) % wordCount.toUInt()).toInt()

            words.add(wordList[w1])
            words.add(wordList[w2])
            words.add(wordList[w3])
        }

        return words + checksumWord(words)
    }

    private fun decodeSpendKey(wordIndexes: List<Int>): ByteArray {
        val wordCount = Monero.ENGLISH_WORDS.size
        val bytes = ByteArray(32)

        wordIndexes.chunked(WORDS_PER_GROUP).forEachIndexed { group, (w1, w2, w3) ->
            val value = w1 +
                    wordCount.toLong() * Math.floorMod(w2 - w1, wordCount) +
                    wordCount.toLong() * wordCount * Math.floorMod(w3 - w2, wordCount)
            require(value <= MAX_UINT32) {
                "Invalid Monero mnemonic: word group is out of range"
            }

            repeat(BYTES_PER_GROUP) { offset ->
                bytes[group * BYTES_PER_GROUP + offset] = (value shr (offset * 8)).toByte()
            }
        }

        return bytes
    }

    @VisibleForTesting
    internal fun reduceECKey(buffer: ByteArray): ByteArray {
        // 1. Replicate Dart's _readBytes behavior: read the input buffer as if it were little-endian
        //    Since BigInteger(1, bytes) expects big-endian, we must reverse the bytes first
        //    to effectively treat the original big-endian input as if it were little-endian
        //    for the BigInteger construction.
        val littleEndianBuffer = buffer.reversedArray()
        val num = BigInteger(
            1,
            littleEndianBuffer
        ) // Now, this BigInteger effectively represents the little-endian value

        val reduced = num.mod(ed25519CurveOrder)

        val result = ByteArray(32)
        var temp = reduced
        for (i in 0 until 32) {
            result[i] = temp.and(BigInteger.valueOf(0xff)).toByte()
            temp = temp.shiftRight(8)
        }
        return result
    }
}
