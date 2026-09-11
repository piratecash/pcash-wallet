package cash.p.terminal.core.adapters.zcash

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ZcashAddressDerivationTest {

    private val deriver = ZcashAddressDeriver()

    private val phrase = ZcashKey.Phrase(PHRASE.split(" "), "")

    @Test
    fun addresses_eccVector_matchesTheAddressesTheEccSdkDerived() = runBlocking {
        val addresses = deriver.addresses(phrase)

        assertEquals(EXPECTED_UA, addresses.unified)
        assertEquals(EXPECTED_SAPLING_RECEIVER, addresses.sapling)
        assertEquals(EXPECTED_TRANSPARENT_RECEIVER, addresses.transparent)
    }

    @Test
    fun addresses_viewingKeyOfThePhrase_matchesTheAddressesOfThePhrase() = runBlocking {
        assertEquals(
            deriver.addresses(ZcashKey.Phrase(UFVK_PHRASE.split(" "), "")),
            deriver.addresses(ZcashKey.ViewingKey(UFVK)),
        )
    }

    @Test
    fun addresses_passphrase_yieldsADifferentWalletThanWithoutOne() = runBlocking {
        assertNotEquals(
            deriver.addresses(phrase),
            deriver.addresses(phrase.copy(passphrase = "pepper")),
        )
    }

    private companion object {
        /** ECC SDK regression vector, pinned in zcash-sdk-kmp too; holds no funds. */
        const val PHRASE =
            "deputy visa gentle among clean scout farm drive comfort patch skin salt ranch cool ramp" +
                " warrior drink narrow normal lunch behind salt deal person"

        @Suppress("MaxLineLength")
        const val EXPECTED_UA =
            "u1t23erzgkn7c6c2jn66rspl4m45lg8rn3f7mn7le4yxk7693wr7sgx472jn95s00x8kx3hct5ej4tf76k59dfhsd809t7mzt9ldzw8f5083fw4xqvxfshl9u7ed2wyv6ypmzny0px0nvszslr5kr7fgk2zgfnlycddzqak4adsqjdzp76y7fl0k4ygamjr43t6rpxsf6xql8g20rdk0h"

        const val EXPECTED_SAPLING_RECEIVER =
            "zs1yc4sgtfwwzz6xfsy2xsradzr6m4aypgxhfw2vcn3hatrh5ryqsr08sgpemlg39vdh9kfupx20py"

        const val EXPECTED_TRANSPARENT_RECEIVER = "t1WksXp7ci6XkPNkEHNkFfzQXbRpBCQw7kW"

        /** BIP-39 test vector; [UFVK] is its account 0 unified full viewing key. */
        const val UFVK_PHRASE =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon" +
                " abandon about"

        @Suppress("MaxLineLength")
        const val UFVK =
            "uview1qggz6nejagvka9wtm9r7xf84kkwy4cc0cgchptr98w0cyz33cj4958q5ulkd32nz2u3s0sp9yhcw7tu2n3n" +
                "lw9x6ulghyd2zgc857tnzme2zpr3vn24zhtm2rjduv9a5zxlmzz404n7l0k69gmu4tfn2g3vpcn03rhz63e3l" +
                "92fn8gra37tyly7utvgveswl20vz23pu84rc2nyqess38wvlgr2xzyhgj232ne5qutpe6ql6ghzetdy7pfzcm" +
                "dzd5gd5dnwk25fwv7nnzmnty7u5ax3nzzgr6pdc905ckpd0s9v2cvn7e03qm7r46e5ngax536ywz7zxjptymm" +
                "90px0rhvmqtwvttuy6d7degly023lqvskclk6mezyt69dwu6c4tfzrjgq4uuh5xa9m5dclgatykgtrrw268qe" +
                "5pldfkx73f2kd5yyy2tjpjql92pa6tsk2nh2h88q23nee9z379het4akl6haqmuwf9d0nl0susg4tnxyk"
    }
}
