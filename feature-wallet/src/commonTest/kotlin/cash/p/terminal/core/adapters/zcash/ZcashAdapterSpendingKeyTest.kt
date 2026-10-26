package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.OfflineZcashSignRequest
import cash.p.terminal.core.UnsupportedException
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import cash.p.zcash.PreparedTransaction
import cash.p.zcash.TransactionPlan
import cash.p.zcash.ZcashNetwork
import cash.p.zcash.ZcashSdk
import cash.p.zcash.deriveSpendingKey
import cash.p.zcash.importSpendingKey
import cash.p.zcash.transactionId
import io.horizontalsystems.hdwalletkit.Curve
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.horizontalsystems.hdwalletkit.HDExtendedKeyVersion
import io.horizontalsystems.hdwalletkit.HDKeychain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * Where the spending key comes from for each kind of account: derived from a phrase, imported
 * from a private key, or absent — in which case the spend must be refused, not attempted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZcashAdapterSpendingKeyTest : ZcashAdapterTestFixture() {

    /** The very buffer handed to the signer, so its wiping can be observed afterwards. */
    private var signingKey: ByteArray? = null

    override fun stubWallet() {
        coEvery { zcashWallet.prepare(any(), any(), any()) } returns PREPARED
        coEvery { zcashWallet.plan(any()) } returns PLAN
        coEvery { zcashWallet.sign(any(), any(), any()) } coAnswers {
            signingKey = thirdArg()
            PREPARED
        }
        coEvery { zcashWallet.extract(any()) } returns RAW
    }

    @Before
    fun stubSdkKeys() {
        mockkStatic(ZCASH_SDK_EXTENSIONS)
        coEvery { ZcashSdk.deriveSpendingKey(any(), any(), any(), any()) } returns derivedKey()
        every { ZcashSdk.importSpendingKey(any(), any()) } returns derivedKey()
        coEvery { ZcashSdk.transactionId(any()) } returns TX_ID
    }

    @Test
    fun signOffline_mnemonicAccount_derivesTheKeyFromThePhrase() = runTest(dispatcher) {
        startAdapter(AccountType.Mnemonic(WORDS, "pass"))

        adapter.signOffline(OfflineZcashSignRequest(AMOUNT, RECIPIENT, memo = ""))

        coVerify {
            ZcashSdk.deriveSpendingKey(WORDS.joinToString(" "), ZcashNetwork.MAIN, 0, "pass")
        }
    }

    @Test
    fun signOffline_emptyPassphrase_forwardsItVerbatim() = runTest(dispatcher) {
        startAdapter(AccountType.Mnemonic(WORDS, ""))

        adapter.signOffline(OfflineZcashSignRequest(AMOUNT, RECIPIENT, memo = ""))

        coVerify {
            ZcashSdk.deriveSpendingKey(WORDS.joinToString(" "), ZcashNetwork.MAIN, 0, "")
        }
    }

    @Test
    fun signOffline_saplingSpendingKeyAccount_importsTheKey() = runTest(dispatcher) {
        startAdapter(AccountType.ZCashSaplingKey(SAPLING_SPENDING_KEY))

        adapter.signOffline(OfflineZcashSignRequest(AMOUNT, RECIPIENT, memo = ""))

        coVerify { ZcashSdk.importSpendingKey(SAPLING_SPENDING_KEY, ZcashNetwork.MAIN) }
    }

    @Test
    fun signOffline_transparentPrivateKeyAccount_importsTheKey() = runTest(dispatcher) {
        startAdapter(AccountType.HdExtendedKey(accountXprv))

        adapter.signOffline(OfflineZcashSignRequest(AMOUNT, RECIPIENT, memo = ""))

        coVerify { ZcashSdk.importSpendingKey(accountXprv, ZcashNetwork.MAIN) }
    }

    @Test
    fun signOffline_importedKey_wipesTheKeyAfterSigning() = runTest(dispatcher) {
        startAdapter(AccountType.ZCashSaplingKey(SAPLING_SPENDING_KEY))

        adapter.signOffline(OfflineZcashSignRequest(AMOUNT, RECIPIENT, memo = ""))

        assertArrayEquals(ByteArray(KEY_SIZE), signingKey)
    }

    @Test
    fun signOffline_unifiedViewingKeyAccount_isRefused() = runTest(dispatcher) {
        assertSpendingRefused(AccountType.ZCashUfvKey(UFVK))
    }

    @Test
    fun signOffline_saplingViewingKeyAccount_isRefused() = runTest(dispatcher) {
        assertSpendingRefused(AccountType.ZCashSaplingKey(SAPLING_VIEWING_KEY))
    }

    @Test
    fun signOffline_transparentPublicKeyAccount_isRefused() = runTest(dispatcher) {
        assertSpendingRefused(AccountType.HdExtendedKey(accountXpub))
    }

    private suspend fun TestScope.assertSpendingRefused(accountType: AccountType) {
        startAdapter(accountType)

        val failure = try {
            adapter.signOffline(OfflineZcashSignRequest(AMOUNT, RECIPIENT, memo = ""))
            null
        } catch (e: Throwable) {
            e
        }

        assertTrue("watch-only spending must be refused, got $failure", failure is UnsupportedException)
        coVerify(exactly = 0) { zcashWallet.sign(any(), any(), any()) }
    }

    private fun TestScope.startAdapter(accountType: AccountType) {
        every { wallet.account } returns Account(
            id = ACCOUNT_ID,
            name = "Test",
            type = accountType,
            origin = AccountOrigin.Created,
            level = 0,
        )
        adapter = createAdapter(AddressSpecType.Transparent)
        adapter.start()
        advanceUntilIdle()
    }

    private companion object {
        const val ZCASH_SDK_EXTENSIONS = "cash.p.zcash.ZcashSdkKt"
        const val KEY_SIZE = 32
        const val RECIPIENT = "t1RecipientAddress"
        const val UFVK = "uview1qunifiedviewingkey"
        const val SAPLING_VIEWING_KEY = "zxviews1qsaplingviewingkey"
        const val SAPLING_SPENDING_KEY = "secret-extended-key-main1qsaplingspendingkey"
        const val TX_ID =
            "b7c4a1f0d3e2b5a698877665544332211ffeeddccbbaa99887766554433221100"

        val WORDS = List(24) { "abandon" }
        val AMOUNT: BigDecimal = BigDecimal("0.1")
        val RAW = byteArrayOf(1, 2, 3, 4)
        val PREPARED = PreparedTransaction(byteArrayOf(9, 9, 9, 9))
        val PLAN = TransactionPlan(
            height = 3_428_200,
            inputs = emptyList(),
            outputs = emptyList(),
            fee = 10_000L,
            canSign = true,
            canBroadcast = true,
        )

        /** Non-zero on purpose: a wiped buffer must be distinguishable from a fresh one. */
        fun derivedKey() = ByteArray(KEY_SIZE) { 7 }

        private val hdKeychain = HDKeychain(ByteArray(64) { (it + 1).toByte() }, Curve.Secp256K1)
        private val accountKey = hdKeychain.getKeyByPath("m/44'/0'/0'")

        val accountXprv: String =
            HDExtendedKey(accountKey, HDExtendedKeyVersion.xprv).serializePrivate()
        val accountXpub: String =
            HDExtendedKey(accountKey, HDExtendedKeyVersion.xprv).serializePublic()
    }
}
