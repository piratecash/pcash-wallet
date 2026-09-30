package cash.p.terminal.core.adapters.zcash

import cash.p.zcash.Addresses
import cash.p.zcash.ZcashNetwork
import cash.p.zcash.ZcashSdk
import cash.p.zcash.deriveAddresses
import cash.p.zcash.deriveAddressesFromKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertSame

/**
 * A phrase is derived through the seed; every standalone key — viewing or spending, transparent
 * or shielded — is handed to the SDK as-is, which is what lets an imported key have an address.
 */
class ZcashAddressDeriverTest {

    private val deriver = ZcashAddressDeriver()

    @Before
    fun stubSdk() {
        mockkStatic(ZCASH_SDK_EXTENSIONS)
        coEvery { ZcashSdk.deriveAddresses(any(), any(), any(), any()) } returns ADDRESSES
        coEvery { ZcashSdk.deriveAddressesFromKey(any(), any()) } returns ADDRESSES
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun addresses_phrase_derivesFromTheSeed() = runTest {
        val words = List(12) { "abandon" }

        assertSame(ADDRESSES, deriver.addresses(ZcashKey.Phrase(words, "pass")))

        coVerify {
            ZcashSdk.deriveAddresses(words.joinToString(" "), ZcashNetwork.MAIN, 0, "pass")
        }
    }

    @Test
    fun addresses_emptyPassphrase_forwardsItVerbatim() = runTest {
        deriver.addresses(ZcashKey.Phrase(List(12) { "abandon" }, ""))

        coVerify { ZcashSdk.deriveAddresses(any(), ZcashNetwork.MAIN, 0, "") }
    }

    @Test
    fun addresses_viewingKey_derivesFromTheKey() = runTest {
        assertSame(ADDRESSES, deriver.addresses(ZcashKey.ViewingKey(UFVK)))

        coVerify { ZcashSdk.deriveAddressesFromKey(UFVK, ZcashNetwork.MAIN) }
    }

    @Test
    fun addresses_spendingKey_derivesFromTheKey() = runTest {
        assertSame(ADDRESSES, deriver.addresses(ZcashKey.SpendingKey(SAPLING_SPENDING_KEY)))

        coVerify { ZcashSdk.deriveAddressesFromKey(SAPLING_SPENDING_KEY, ZcashNetwork.MAIN) }
    }

    private companion object {
        const val ZCASH_SDK_EXTENSIONS = "cash.p.zcash.ZcashSdkKt"
        const val UFVK = "uview1qunifiedviewingkey"
        const val SAPLING_SPENDING_KEY = "secret-extended-key-main1qsaplingspendingkey"

        val ADDRESSES = Addresses(
            unified = "u1test",
            sapling = "zs1test",
            orchard = null,
            transparent = "t1test",
            diversifierIndex = 0,
        )
    }
}
