package cash.p.terminal.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MnemonicSeedTest {

    @Test
    fun derive_standardBip39Vector_matchesExpectedSeed() {
        val words = List(11) { "abandon" } + "about"

        val seed = MnemonicSeed.derive(words, "TREZOR")

        assertEquals(BIP39_SEED, seed.toHex())
    }

    @Test
    fun derive_nonNormalizedStoredWords_preservesOriginalUtf8Bytes() {
        val precomposed = List(11) { "ábaco" } + "abierto"
        val decomposed = List(11) { "ábaco" } + "abierto"

        val precomposedSeed = MnemonicSeed.derive(precomposed)
        val decomposedSeed = MnemonicSeed.derive(decomposed)

        assertEquals(PRECOMPOSED_SEED, precomposedSeed.toHex())
        assertEquals(DECOMPOSED_SEED, decomposedSeed.toHex())
        assertNotEquals(precomposedSeed.toHex(), decomposedSeed.toHex())
    }

    @Test
    fun derive_nonNormalizedStoredPassphrase_preservesOriginalUtf8Bytes() {
        val words = List(11) { "abandon" } + "about"

        val precomposedSeed = MnemonicSeed.derive(words, "páss")
        val decomposedSeed = MnemonicSeed.derive(words, "páss")

        assertEquals(PRECOMPOSED_PASSPHRASE_SEED, precomposedSeed.toHex())
        assertEquals(DECOMPOSED_PASSPHRASE_SEED, decomposedSeed.toHex())
        assertNotEquals(precomposedSeed.toHex(), decomposedSeed.toHex())
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
