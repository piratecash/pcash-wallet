package cash.p.terminal.tangem.common

import io.horizontalsystems.ethereumkit.core.hexStringToByteArray
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CustomXPubKeyAddressParserTest {

    @Test
    fun parse_depthFiveXpub_preservesPublicKeyAndEvmAddress() {
        val result = CustomXPubKeyAddressParser.parse(DEPTH_FIVE_XPUB)

        assertArrayEquals(EXPECTED_UNCOMPRESSED_PUBLIC_KEY.hexStringToByteArray(), result.publicKey)
        assertArrayEquals(EXPECTED_EVM_ADDRESS.hexStringToByteArray(), result.addressBytes)
    }

    private companion object {
        const val DEPTH_FIVE_XPUB =
            "xpub6FYoPSAotUgEYMV2bvooZmfoNUx1iTRZB9eeNktK31ixCogFGxxVs8mzNrX9x" +
                "U2S5XkRuWY2iXUrbZhGNy6oTph6MwEK2XvdnqZ4y2yhFXH"
        const val EXPECTED_UNCOMPRESSED_PUBLIC_KEY =
            "0439a36013301597daef41fbe593a02cc513d0b55527ec2df1050e2e8ff49c85c2" +
                "3cbe7ded0e7ce6a594896b8f62888fdbc5c8821305e2ea42bf01e37300116281"
        const val EXPECTED_EVM_ADDRESS = "056db290f8ba3250ca64a45d16284d04bc6f5fbf"
    }
}
