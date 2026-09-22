package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.wallet.AccountType
import cash.p.zcash.ZcashNetwork
import cash.p.zcash.ZcashSdk
import cash.p.zcash.deriveSaplingViewingKey
import cash.p.zcash.deriveSpendingKey
import cash.p.zcash.deriveTransparentAccountKey
import cash.p.zcash.deriveUfvk
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZcashKeyExporterTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val exporter =
        ZcashKeyExporter(TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)))

    @Before
    fun stubSdk() {
        mockkStatic(ZCASH_SDK_EXTENSIONS)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun export_transparentWithPassphrase_forwardsPassphrase() = runTest {
        coEvery { ZcashSdk.deriveTransparentAccountKey(any(), any(), any()) } returns TRANSPARENT_KEY

        exporter.export(AccountType.Mnemonic(WORDS, "pass"), ZcashPrivateKeyType.Transparent)

        coVerify {
            ZcashSdk.deriveTransparentAccountKey(WORDS.joinToString(" "), ZcashNetwork.MAIN, "pass")
        }
    }

    @Test
    fun export_transparentEmptyPassphrase_forwardsItVerbatim() = runTest {
        coEvery { ZcashSdk.deriveTransparentAccountKey(any(), any(), any()) } returns TRANSPARENT_KEY

        exporter.export(AccountType.Mnemonic(WORDS, ""), ZcashPrivateKeyType.Transparent)

        coVerify { ZcashSdk.deriveTransparentAccountKey(any(), ZcashNetwork.MAIN, "") }
    }

    @Test
    fun export_transparentDerivationThrows_returnsNull() = runTest {
        coEvery { ZcashSdk.deriveTransparentAccountKey(any(), any(), any()) } throws
                IllegalStateException("native failure")

        assertNull(exporter.export(AccountType.Mnemonic(WORDS, ""), ZcashPrivateKeyType.Transparent))
    }

    @Test
    fun export_shieldedWithPassphrase_forwardsPassphrase() = runTest {
        coEvery { ZcashSdk.deriveSpendingKey(any(), any(), any(), any()) } returns usk()

        exporter.export(AccountType.Mnemonic(WORDS, "pass"), ZcashPrivateKeyType.Shielded)

        coVerify {
            ZcashSdk.deriveSpendingKey(WORDS.joinToString(" "), ZcashNetwork.MAIN, 0, "pass")
        }
    }

    @Test
    fun export_shieldedEmptyPassphrase_forwardsItVerbatim() = runTest {
        coEvery { ZcashSdk.deriveSpendingKey(any(), any(), any(), any()) } returns usk()

        exporter.export(AccountType.Mnemonic(WORDS, ""), ZcashPrivateKeyType.Shielded)

        coVerify { ZcashSdk.deriveSpendingKey(any(), ZcashNetwork.MAIN, 0, "") }
    }

    @Test
    fun export_shieldedDerivationSucceeds_returnsSaplingKey() = runTest {
        coEvery { ZcashSdk.deriveSpendingKey(any(), any(), any(), any()) } returns usk()

        val key = exporter.export(AccountType.Mnemonic(WORDS, ""), ZcashPrivateKeyType.Shielded)

        assertTrue(key?.startsWith("secret-extended-key-main1") == true)
    }

    @Test
    fun export_shieldedDerivationThrows_returnsNull() = runTest {
        coEvery { ZcashSdk.deriveSpendingKey(any(), any(), any(), any()) } throws
                IllegalStateException("native failure")

        assertNull(exporter.export(AccountType.Mnemonic(WORDS, ""), ZcashPrivateKeyType.Shielded))
    }

    @Test
    fun export_saplingSpendingShielded_returnsStoredKey() = runTest {
        val key = exporter.export(AccountType.ZCashSaplingKey(SAPLING_SPENDING_KEY), ZcashPrivateKeyType.Shielded)

        assertEquals(SAPLING_SPENDING_KEY, key)
    }

    @Test
    fun export_saplingSpendingTransparent_returnsNull() = runTest {
        val key = exporter.export(AccountType.ZCashSaplingKey(SAPLING_SPENDING_KEY), ZcashPrivateKeyType.Transparent)

        assertNull(key)
    }

    @Test
    fun privateKeyTypes_mnemonic_returnsAllEntries() {
        assertEquals(ZcashPrivateKeyType.entries, exporter.privateKeyTypes(AccountType.Mnemonic(WORDS, "")))
    }

    @Test
    fun privateKeyTypes_saplingSpending_returnsShieldedOnly() {
        assertEquals(
            listOf(ZcashPrivateKeyType.Shielded),
            exporter.privateKeyTypes(AccountType.ZCashSaplingKey(SAPLING_SPENDING_KEY))
        )
    }

    @Test
    fun privateKeyTypes_saplingViewingOnly_returnsEmpty() {
        assertTrue(exporter.privateKeyTypes(AccountType.ZCashSaplingKey(SAPLING_VIEWING_KEY)).isEmpty())
    }

    @Test
    fun privateKeyTypes_ufvk_returnsEmpty() {
        assertTrue(exporter.privateKeyTypes(AccountType.ZCashUfvKey(UFVK)).isEmpty())
    }

    @Test
    fun privateKeyTypes_nonZecType_returnsEmpty() {
        assertTrue(exporter.privateKeyTypes(AccountType.EvmPrivateKey(BigInteger.ONE)).isEmpty())
    }

    @Test
    fun viewingKey_ufvkAccount_returnsOwnKeyWithoutSdkCall() = runTest {
        val key = exporter.viewingKey(AccountType.ZCashUfvKey(UFVK))

        assertEquals(UFVK, key)
        coVerify(exactly = 0) { ZcashSdk.deriveSaplingViewingKey(any(), any()) }
    }

    @Test
    fun viewingKey_saplingViewingOnlyAccount_returnsOwnKeyWithoutSdkCall() = runTest {
        val key = exporter.viewingKey(AccountType.ZCashSaplingKey(SAPLING_VIEWING_KEY))

        assertEquals(SAPLING_VIEWING_KEY, key)
        coVerify(exactly = 0) { ZcashSdk.deriveSaplingViewingKey(any(), any()) }
    }

    @Test
    fun viewingKey_saplingSpendingAccount_derivesExactlyOnce() = runTest {
        coEvery { ZcashSdk.deriveSaplingViewingKey(any(), any()) } returns SAPLING_VIEWING_KEY

        val key = exporter.viewingKey(AccountType.ZCashSaplingKey(SAPLING_SPENDING_KEY))

        assertEquals(SAPLING_VIEWING_KEY, key)
        coVerify(exactly = 1) { ZcashSdk.deriveSaplingViewingKey(SAPLING_SPENDING_KEY, ZcashNetwork.MAIN) }
    }

    @Test
    fun viewingKey_sdkReturnsNull_returnsNull() = runTest {
        coEvery { ZcashSdk.deriveSaplingViewingKey(any(), any()) } returns null

        assertNull(exporter.viewingKey(AccountType.ZCashSaplingKey(SAPLING_SPENDING_KEY)))
    }

    @Test
    fun viewingKey_sdkThrows_returnsNullInsteadOfThrowing() = runTest {
        coEvery { ZcashSdk.deriveSaplingViewingKey(any(), any()) } throws IllegalStateException("native failure")

        assertNull(exporter.viewingKey(AccountType.ZCashSaplingKey(SAPLING_SPENDING_KEY)))
    }

    @Test
    fun viewingKey_mnemonic_returnsDerivedUfvk() = runTest {
        coEvery { ZcashSdk.deriveUfvk(any(), any(), any()) } returns UFVK

        assertEquals(UFVK, exporter.viewingKey(AccountType.Mnemonic(WORDS, "")))
    }

    @Test
    fun viewingKey_mnemonicWithPassphrase_forwardsPhraseAndPassphraseVerbatim() = runTest {
        coEvery { ZcashSdk.deriveUfvk(any(), any(), any()) } returns UFVK

        exporter.viewingKey(AccountType.Mnemonic(WORDS, "  "))

        coVerify { ZcashSdk.deriveUfvk(WORDS.joinToString(" "), ZcashNetwork.MAIN, "  ") }
    }

    @Test
    fun viewingKey_mnemonicDerivationThrows_returnsNull() = runTest {
        coEvery { ZcashSdk.deriveUfvk(any(), any(), any()) } throws IllegalStateException("native failure")

        assertNull(exporter.viewingKey(AccountType.Mnemonic(WORDS, "")))
    }

    @Test
    fun viewingKey_blankWordMnemonic_returnsNullWithoutCallingTheSdk() = runTest {
        assertNull(exporter.viewingKey(AccountType.Mnemonic(listOf(""), "")))

        coVerify(exactly = 0) { ZcashSdk.deriveUfvk(any(), any(), any()) }
    }

    @Test
    fun supportsViewingKey_keyBearingTypes_areTheOnlySupportedOnes() {
        assertTrue(exporter.supportsViewingKey(AccountType.ZCashUfvKey(UFVK)))
        assertTrue(exporter.supportsViewingKey(AccountType.ZCashSaplingKey(SAPLING_SPENDING_KEY)))
        assertTrue(exporter.supportsViewingKey(AccountType.Mnemonic(WORDS, "")))

        assertFalse(exporter.supportsViewingKey(AccountType.Mnemonic(listOf(""), "")))
        assertFalse(exporter.supportsViewingKey(AccountType.HdExtendedKey(HD_EXTENDED_KEY)))
        assertFalse(exporter.supportsViewingKey(AccountType.EvmPrivateKey(BigInteger.ONE)))
    }

    @Test
    fun viewingKey_hdExtendedKey_returnsNull() = runTest {
        assertNull(exporter.viewingKey(AccountType.HdExtendedKey(HD_EXTENDED_KEY)))
    }

    @Test
    fun viewingKey_evmPrivateKey_returnsNull() = runTest {
        assertNull(exporter.viewingKey(AccountType.EvmPrivateKey(BigInteger.ONE)))
    }

    private companion object {
        const val ZCASH_SDK_EXTENSIONS = "cash.p.zcash.ZcashSdkKt"
        const val TRANSPARENT_KEY = "xprv-transparent-test"
        const val SAPLING_SPENDING_KEY = "secret-extended-key-main1qsaplingspendingkey"
        const val SAPLING_VIEWING_KEY = "zxviews1qsaplingviewingkey"
        const val UFVK = "uview1qtest"
        const val HD_EXTENDED_KEY = "zxviewtestpub1qxprvhdextendedkey"

        val WORDS = List(12) { "abandon" }

        /** Minimal NU5 unified-spending-key envelope carrying only a Sapling component. */
        fun usk(): ByteArray =
            byteArrayOf(0xB4.toByte(), 0xD0.toByte(), 0xD6.toByte(), 0xC2.toByte()) +
                    byteArrayOf(0x02, 169.toByte()) + ByteArray(169)
    }
}
