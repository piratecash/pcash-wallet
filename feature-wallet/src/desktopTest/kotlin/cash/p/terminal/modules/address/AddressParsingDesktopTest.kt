package cash.p.terminal.modules.address

import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.EthereumKit
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Public, well-known addresses: proves every chain's kit (native code included) loads on the desktop JVM. */
class AddressParsingDesktopTest {

    private val factory = AddressHandlerFactory(udnApiKey = "")

    // App startup does this too: EVM checksums need the kit's Keccak provider.
    @Before
    fun registerEvmCryptoProvider() {
        EthereumKit.init()
    }

    @Test
    fun parserChain_publicAddressOfEveryFamily_parsesToItsChain() {
        val failures = CASES.mapNotNull { (blockchainType, address) ->
            val parsed = factory.parserChain(blockchainType).supportedHandler(address)?.parseAddress(address)
            "$blockchainType: $address -> ${parsed?.blockchainType}"
                .takeIf { parsed?.blockchainType != blockchainType }
        }

        assertEquals(emptyList<String>(), failures)
    }

    private companion object {
        val CASES = listOf(
            BlockchainType.Bitcoin to "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
            BlockchainType.Bitcoin to "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
            BlockchainType.Litecoin to "LUEweDxDA4WhvWiNXXSxjM9CYzHPJv4QQF",
            BlockchainType.Dash to "XjhqDGJH37VEpecoDGmtJrmEB7VoD8Lb39",
            BlockchainType.Dogecoin to "DEA5vGb2NpAwCiCp5yTE16F3DueQUVivQp",
            BlockchainType.BitcoinCash to "bitcoincash:qp3wjpa3tjlj042z2wv7hahsldgwhwy0rq9sywjpyy",
            BlockchainType.ECash to "ecash:qp3wjpa3tjlj042z2wv7hahsldgwhwy0rquas9fmzn",
            BlockchainType.Cosanta to "CRUsx3zSxTFBZqve38nb2qhTxu8X7eR7LK",
            BlockchainType.PirateCash to "PHcAXz3E8KkqeYgyhTnC8E3hxX5zGAm1nw",
            BlockchainType.Ethereum to "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045",
            BlockchainType.Tron to "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
            BlockchainType.Ton to "EQCD39VS5jcptHL8vMjEXrzGaRcCVYto7HUn4bpAOg8xqB2N",
            BlockchainType.Solana to "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
            BlockchainType.Stellar to "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
            BlockchainType.Zcash to "t1WksXp7ci6XkPNkEHNkFfzQXbRpBCQw7kW",
            BlockchainType.Zcash to "zs1yc4sgtfwwzz6xfsy2xsradzr6m4aypgxhfw2vcn3hatrh5ryqsr08sgpemlg39vdh9kfupx20py",
            BlockchainType.Zcash to "u1t23erzgkn7c6c2jn66rspl4m45lg8rn3f7mn7le4yxk7693wr7sgx472jn95s00x8kx3hct5ej4tf76k59" +
                "dfhsd809t7mzt9ldzw8f5083fw4xqvxfshl9u7ed2wyv6ypmzny0px0nvszslr5kr7fgk2zgfnlycddzqak4adsqjdzp76y7fl0k" +
                "4ygamjr43t6rpxsf6xql8g20rdk0h",
            BlockchainType.Monero to "44AFFq5kSiGBoZ4NMDwYtN18obc8AemS33DBLWs3H7otXft3XjrpDtQGv7SqSsaBYBb98uNbr2VBBEt7f2wfn3RVGQBEP3A",
            BlockchainType.Thorchain to "thor1gm00vwsfcp48enm4uv9e5dhm37jtd0ye27wrx0",
            BlockchainType.Mayachain to "maya1gm00vwsfcp48enm4uv9e5dhm37jtd0ye2fs0sl",
            BlockchainType.Beam to "1279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
        )
    }
}
