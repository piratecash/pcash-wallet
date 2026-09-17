package cash.p.terminal.wallet

import java.text.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MnemonicSeedTest {

    @Test
    fun derive_standardBip39Vector_matchesExpectedSeed() {
        val words = List(11) { "abandon" } + "about"

        val seed = MnemonicSeed.derive(words, MnemonicDerivation.Legacy, "TREZOR")

        assertEquals(BIP39_SEED, seed.toHex())
    }

    @Test
    fun derive_nonNormalizedStoredWords_preservesOriginalUtf8Bytes() {
        val precomposed = List(11) { "ábaco" } + "abierto"
        val decomposed = List(11) { "ábaco" } + "abierto"

        val precomposedSeed = MnemonicSeed.derive(precomposed, MnemonicDerivation.Legacy)
        val decomposedSeed = MnemonicSeed.derive(decomposed, MnemonicDerivation.Legacy)

        assertEquals(PRECOMPOSED_SEED, precomposedSeed.toHex())
        assertEquals(DECOMPOSED_SEED, decomposedSeed.toHex())
        assertNotEquals(precomposedSeed.toHex(), decomposedSeed.toHex())
    }

    @Test
    fun derive_nonNormalizedStoredPassphrase_preservesOriginalUtf8Bytes() {
        val words = List(11) { "abandon" } + "about"

        val precomposedSeed = MnemonicSeed.derive(words, MnemonicDerivation.Legacy, "páss")
        val decomposedSeed = MnemonicSeed.derive(words, MnemonicDerivation.Legacy, "páss")

        assertEquals(PRECOMPOSED_PASSPHRASE_SEED, precomposedSeed.toHex())
        assertEquals(DECOMPOSED_PASSPHRASE_SEED, decomposedSeed.toHex())
        assertNotEquals(precomposedSeed.toHex(), decomposedSeed.toHex())
    }

    @Test
    fun derive_japanesePublishedVectors_normalizesSentenceAndUnicodeSalt() {
        // Public vectors: https://github.com/bip32JP/bip32JP.github.io/blob/master/test_JP_BIP39.json
        val vectors = listOf(
            (List(11) { "あいこくしん" } + "あおぞら") to
                "a262d6fb6122ecf45be09c50492b31f92e9beb7d9a845987a02cefda57a15f9c" +
                "467a17872029a9e92299b5cbdf306e3a0ee620245cbd508959b6cb7ca637bd55",
            "そつう れきだい ほんやく わかす りくつ ばいか ろせん やちん そつう れきだい ほんやく わかめ".split(" ") to
                "aee025cbe6ca256862f889e48110a6a382365142f7d16f2b9545285b3af64e54" +
                "2143a577e9c144e101a6bdca18f8d97ec3366ebf5b088b1c1af9bc31346e60d9",
            (List(23) { "あいこくしん" } + "いってい") to
                "23f500eec4a563bf90cfda87b3e590b211b959985c555d17e88f46f7183590cd" +
                "5793458b094a4dccc8f05807ec7bd2d19ce269e20568936a751f6f1ec7c14ddd"
        )
        vectors.forEach { (words, expected) ->
            listOf(Normalizer.Form.NFC, Normalizer.Form.NFKD).forEach { form ->
                val input = words.map { Normalizer.normalize(it, form) }
                assertEquals(expected, MnemonicSeed.derive(input, MnemonicDerivation.Bip39, "㍍ガバヴァぱばぐゞちぢ十人十色").toHex())
            }
        }
    }

    @Test
    fun derive_japaneseEmptyAndTrezorPassphrases_selectsExplicitMode() {
        val words = List(11) { "あいこくしん" } + "あおぞら"
        val vectors = listOf(
            "" to "646f1a38134c556e948e6daef213609a62915ef568edb07ffa6046c87638b4b1" +
                "40fef2e0c6d7233af640c4a63de6d1a293288058c8ac1d113255d0504e63f301",
            "TREZOR" to "5a6c23b5abdd5c3e1f7d77ad25ecd715647bdafb44dab324c730a76a45d7421d" +
                "accee1a4ff0739715a2c56a8a9f1e527a5e3496224d91293bfcd9b5393bfff83"
        )
        vectors.forEach { (passphrase, expected) ->
            assertEquals(expected, MnemonicSeed.derive(words, MnemonicDerivation.Bip39, passphrase).toHex())
        }
        assertEquals("ecacfdb4246c55f59f5ca23f1599824ad2c2ee04c125aa854c93a6d3fb84354a" +
                "062a5f707687da8d9b9151075316dcbdb8b1d46f840e1e5d46c13310b135ce13",
            MnemonicSeed.derive(words, MnemonicDerivation.Legacy, "TREZOR").toHex())
    }

    private fun ByteArray.toHex() = joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val BIP39_SEED =
            "c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e534955" +
                "31f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04"
        const val PRECOMPOSED_SEED =
            "a95185390a5b79770af0811570a05db80bdd3d018a61768fe9c5c6feca930d04" +
                "5b7f04273a565f3f777cb1d10d11a2595421cfca1a82ffbaa37824409b121501"
        const val DECOMPOSED_SEED =
            "fdfe9b7c7a5e5079bb36d6381838867a34358db0a0307d060adbaf5edadb08b0" +
                "a87c06ede1a96afd8566ef499792ffcbd37f43f6f554fa344138660eacdefcf8"
        const val PRECOMPOSED_PASSPHRASE_SEED =
            "13fd19ca104b44f81fbd5666cbc8289c43f56543cfe977034d2057efdeac8587" +
                "594ffa04d02ca56c3a016d0dcd721b971442c36c6e20e57c5f4bb2e31c58c4c6"
        const val DECOMPOSED_PASSPHRASE_SEED =
            "8ae6eb6d6ebcb8091a99649668295a9fb97c169817d9f6435f193307aa9f8e008" +
                "e643d5d12fb5cab44ea43f19f0952609b143f7ee67d3703a375977675fc9b06"
    }
}
