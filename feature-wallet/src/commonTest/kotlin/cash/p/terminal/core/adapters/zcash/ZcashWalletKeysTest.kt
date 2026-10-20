package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.hdwalletkit.Curve
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.horizontalsystems.hdwalletkit.HDExtendedKeyVersion
import io.horizontalsystems.hdwalletkit.HDKeychain
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which imported key can spend and which can only watch — the classification every Zcash
 * consumer relies on, from the receive address to the signer.
 */
class ZcashWalletKeysTest {

    @Test
    fun zcashKey_mnemonic_isPhrase() {
        val words = List(12) { "abandon" }

        assertEquals(
            ZcashKey.Phrase(words, "pass"),
            walletOf(AccountType.Mnemonic(words, "pass")).zcashKey(),
        )
    }

    @Test
    fun zcashKey_unifiedViewingKey_isViewingKey() {
        assertEquals(
            ZcashKey.ViewingKey(UFVK),
            walletOf(AccountType.ZCashUfvKey(UFVK)).zcashKey(),
        )
    }

    @Test
    fun zcashKey_saplingViewingKey_isViewingKey() {
        assertEquals(
            ZcashKey.ViewingKey(SAPLING_VIEWING_KEY),
            walletOf(AccountType.ZCashSaplingKey(SAPLING_VIEWING_KEY)).zcashKey(),
        )
    }

    @Test
    fun zcashKey_saplingSpendingKey_isSpendingKey() {
        assertEquals(
            ZcashKey.SpendingKey(SAPLING_SPENDING_KEY),
            walletOf(AccountType.ZCashSaplingKey(SAPLING_SPENDING_KEY)).zcashKey(),
        )
    }

    @Test
    fun zcashKey_accountXpub_isViewingKey() {
        assertEquals(
            ZcashKey.ViewingKey(accountXpub),
            walletOf(AccountType.HdExtendedKey(accountXpub)).zcashKey(),
        )
    }

    @Test
    fun zcashKey_accountXprv_isSpendingKey() {
        assertEquals(
            ZcashKey.SpendingKey(accountXprv),
            walletOf(AccountType.HdExtendedKey(accountXprv)).zcashKey(),
        )
    }

    @Test
    fun zcashKey_addressOnlyAccount_isNull() {
        assertNull(walletOf(AccountType.EvmAddress("0x01")).zcashKey())
    }

    private fun walletOf(accountType: AccountType) = mockk<Wallet>(relaxed = true) {
        every { account } returns Account(
            id = "account-id",
            name = "Test",
            type = accountType,
            origin = AccountOrigin.Created,
            level = 0,
        )
    }

    private companion object {
        const val UFVK = "uview1qunifiedviewingkey"
        const val SAPLING_VIEWING_KEY = "zxviews1qsaplingviewingkey"
        const val SAPLING_SPENDING_KEY = "secret-extended-key-main1qsaplingspendingkey"

        private val accountKey =
            HDKeychain(ByteArray(64) { (it + 1).toByte() }, Curve.Secp256K1)
                .getKeyByPath("m/44'/0'/0'")

        val accountXprv: String =
            HDExtendedKey(accountKey, HDExtendedKeyVersion.xprv).serializePrivate()
        val accountXpub: String =
            HDExtendedKey(accountKey, HDExtendedKeyVersion.xprv).serializePublic()
    }
}
