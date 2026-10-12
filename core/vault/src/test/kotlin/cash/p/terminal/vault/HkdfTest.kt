package cash.p.terminal.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class HkdfTest {

    @Test
    fun expand_rfc5869Case1_returnsPublishedOkm() {
        assertOkm(
            prk = "077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5",
            info = "f0f1f2f3f4f5f6f7f8f9",
            okm = "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
        )
    }

    @Test
    fun expand_rfc5869Case2_returnsPublishedOkm() {
        assertOkm(
            prk = "06a6b88c5853361a06104c9ceb35b45cef760014904671014a193f40c15fc244",
            info = "b0b1b2b3b4b5b6b7b8b9babbbcbdbebfc0c1c2c3c4c5c6c7c8c9cacbcccdcecf" +
                "d0d1d2d3d4d5d6d7d8d9dadbdcdddedfe0e1e2e3e4e5e6e7e8e9eaebecedeeef" +
                "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff",
            okm = "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c" +
                "59045a99cac7827271cb41c65e590e09da3275600c2f09b8367793a9aca3db71" +
                "cc30c58179ec3e87c14c01d5c1f3434f1d87",
        )
    }

    @Test
    fun expand_rfc5869Case3_returnsPublishedOkm() {
        assertOkm(
            prk = "19ef24a32c717b167f33a91d6f648bdf96596776afdb6377ac434c1c293ccb04",
            info = "",
            okm = "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
        )
    }

    private fun assertOkm(prk: String, info: String, okm: String) {
        val expected = okm.hexToByteArray()
        val actual = Hkdf.expand(prk.hexToByteArray(), info.hexToByteArray(), expected.size)
        assertArrayEquals(expected, actual)
    }
}
