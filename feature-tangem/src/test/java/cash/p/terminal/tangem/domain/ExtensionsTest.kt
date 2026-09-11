package cash.p.terminal.tangem.domain

import io.horizontalsystems.ethereumkit.core.hexStringToByteArray
import io.horizontalsystems.hdwalletkit.ECDSASignature
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ExtensionsTest {

    @Test
    fun canonicalise_highS_preservesRAndNormalizesS() {
        val r = R.hexStringToByteArray()
        val signature = ECDSASignature.fromCompact(r + HIGH_S.hexStringToByteArray())

        val canonicalSignature = signature.canonicalise()

        assertArrayEquals(r, canonicalSignature.r)
        assertArrayEquals(ONE.hexStringToByteArray(), canonicalSignature.s)
    }

    private companion object {
        const val R = "0000000000000000000000000000000000000000000000000000000000000002"
        const val HIGH_S = "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140"
        const val ONE = "0000000000000000000000000000000000000000000000000000000000000001"
    }
}
