package cash.p.terminal.modules.restoreaccount.restoreprivatekey

import cash.p.terminal.R
import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.AccountType
import cash.p.zcash.ZcashSdk
import cash.p.zcash.isValidKey
import io.horizontalsystems.hdwalletkit.Curve
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.horizontalsystems.hdwalletkit.HDExtendedKeyVersion
import io.horizontalsystems.hdwalletkit.HDKeychain
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.math.BigInteger
import org.junit.Test
import org.junit.After
import org.junit.Before
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RestorePrivateKeyViewModelTest {

    private val accountFactory = mockk<IAccountFactory> {
        every { getNextAccountName() } returns "Wallet 1"
    }

    @Before
    fun setUp() {
        // Translator has no Android context here; the resource id itself identifies the message.
        mockkObject(Translator)
        every { Translator.getString(any<Int>()) } answers { firstArg<Int>().toString() }
        mockkStatic("cash.p.zcash.ZcashSdkKt")
        every { ZcashSdk.isValidKey(any(), any()) } returns true
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun resolveAccountType_saplingSpendingKey_returnsSaplingAccount() {
        assertEquals(AccountType.ZCashSaplingKey(SAPLING_SPENDING_KEY), restore(SAPLING_SPENDING_KEY).accountType)
    }

    @Test
    fun resolveAccountType_saplingViewingKey_reportsViewOnly() {
        assertViewOnly(restore(SAPLING_VIEWING_KEY))
    }

    @Test
    fun resolveAccountType_unifiedViewingKey_reportsViewOnly() {
        assertViewOnly(restore(UFVK))
    }

    @Test
    fun resolveAccountType_transparentXpub_reportsViewOnly() {
        assertViewOnly(restore(accountXpub))
    }

    @Test
    fun resolveAccountType_transparentAccountXprv_returnsHdExtendedKeyAccount() {
        assertEquals(AccountType.HdExtendedKey(accountXprv), restore(accountXprv).accountType)
    }

    @Test
    fun resolveAccountType_transparentMasterXprv_returnsHdExtendedKeyAccount() {
        assertEquals(AccountType.HdExtendedKey(masterXprv), restore(masterXprv).accountType)
    }

    @Test
    fun resolveAccountType_ethereumPrivateKey_returnsEvmPrivateKeyAccount() {
        assertEquals(
            AccountType.EvmPrivateKey(BigInteger(ETHEREUM_PRIVATE_KEY, 16)),
            restore(ETHEREUM_PRIVATE_KEY).accountType
        )
    }

    @Test
    fun resolveAccountType_saplingPrefixRejectedBySdk_reportsInvalidKey() {
        every { ZcashSdk.isValidKey(any(), any()) } returns false

        assertInvalidKey(restore(SAPLING_SPENDING_KEY))
    }

    @Test
    fun resolveAccountType_garbage_reportsInvalidKey() {
        assertInvalidKey(restore("not a key at all"))
    }

    @Test
    fun resolveAccountType_blank_reportsInvalidKey() {
        assertInvalidKey(restore("   "))
    }

    private fun assertViewOnly(result: Result) {
        assertNull(result.accountType)
        assertEquals(R.string.restore_private_key_is_view_only.toString(), result.errorText)
    }

    private fun assertInvalidKey(result: Result) {
        assertNull(result.accountType)
        assertEquals(R.string.Restore_PrivateKey_InvalidKey.toString(), result.errorText)
    }

    private fun restore(input: String): Result {
        val viewModel = RestorePrivateKeyViewModel(accountFactory)
        viewModel.onEnterPrivateKey(input)
        return Result(viewModel.resolveAccountType(), viewModel.inputState?.error?.message)
    }

    private class Result(val accountType: AccountType?, val errorText: String?)

    private companion object {
        const val SAPLING_SPENDING_KEY = "secret-extended-key-main1qsaplingspendingkey"
        const val SAPLING_VIEWING_KEY = "zxviews1qsaplingviewingkey"
        const val UFVK = "uview1qunifiedviewingkey"
        const val ETHEREUM_PRIVATE_KEY = "1111111111111111111111111111111111111111111111111111111111111111"

        private val keychain = HDKeychain(ByteArray(64) { (it + 1).toByte() }, Curve.Secp256K1)
        private val accountKey = keychain.getKeyByPath("m/44'/0'/0'")

        val masterXprv = HDExtendedKey(keychain.hdKey, HDExtendedKeyVersion.xprv).serializePrivate()
        val accountXprv = HDExtendedKey(accountKey, HDExtendedKeyVersion.xprv).serializePrivate()
        val accountXpub = HDExtendedKey(accountKey, HDExtendedKeyVersion.xprv).serializePublic()
    }
}
