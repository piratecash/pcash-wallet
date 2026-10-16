package cash.p.terminal.core

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import cash.p.zcash.Pool
import cash.p.zcash.PoolSet
import cash.p.zcash.ZcashSdk
import cash.p.zcash.keyPools
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.Curve
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.horizontalsystems.hdwalletkit.HDExtendedKeyVersion
import io.horizontalsystems.hdwalletkit.HDKeychain
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.Test
import org.junit.After
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZcashAddressSpecsTest {

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun zcashAddressSpecs_mnemonic_ownsEverySpec() {
        assertEquals(AddressSpecType.entries.toSet(), mnemonic.zcashAddressSpecs())
        assertTrue(BlockchainType.Zcash.supports(mnemonic))
    }

    @Test
    fun zcashAddressSpecs_accountXprv_ownsTransparentOnly() {
        val accountType = AccountType.HdExtendedKey(accountXprv)

        assertEquals(setOf(AddressSpecType.Transparent), accountType.zcashAddressSpecs())
        assertTrue(BlockchainType.Zcash.supports(accountType))
    }

    @Test
    fun zcashAddressSpecs_accountXpub_ownsTransparentOnly() {
        val accountType = AccountType.HdExtendedKey(accountXpub)

        assertEquals(setOf(AddressSpecType.Transparent), accountType.zcashAddressSpecs())
        assertTrue(BlockchainType.Zcash.supports(accountType))
    }

    @Test
    fun zcashAddressSpecs_masterXprv_ownsNothing() {
        val accountType = AccountType.HdExtendedKey(masterXprv)

        assertEquals(emptySet(), accountType.zcashAddressSpecs())
        assertFalse(BlockchainType.Zcash.supports(accountType))
    }

    @Test
    fun zcashAddressSpecs_accountZprv_ownsNothing() {
        val accountType = AccountType.HdExtendedKey(accountZprv)

        assertEquals(emptySet(), accountType.zcashAddressSpecs())
        assertFalse(BlockchainType.Zcash.supports(accountType))
    }

    @Test
    fun zcashAddressSpecs_litecoinAccountKey_ownsNothing() {
        val accountType = AccountType.HdExtendedKey(accountLtpv)

        assertEquals(emptySet(), accountType.zcashAddressSpecs())
        assertFalse(BlockchainType.Zcash.supports(accountType))
    }

    @Test
    fun zcashAddressSpecs_saplingKey_ownsShieldedOnly() {
        val accountType = AccountType.ZCashSaplingKey(SAPLING_SPENDING_KEY)

        assertEquals(setOf(AddressSpecType.Shielded), accountType.zcashAddressSpecs())
        assertTrue(BlockchainType.Zcash.supports(accountType))
    }

    @Test
    fun zcashAddressSpecs_evmAddress_ownsNothing() {
        val accountType = AccountType.EvmAddress("0x01")

        assertEquals(emptySet(), accountType.zcashAddressSpecs())
        assertFalse(BlockchainType.Zcash.supports(accountType))
    }

    @Test
    fun zcashAddressSpecs_ufvk_mapsSdkPoolsToSpecs() {
        givenKeyPools(PoolSet.of(Pool.TRANSPARENT, Pool.ORCHARD))
        val accountType = AccountType.ZCashUfvKey(UFVK)

        assertEquals(
            setOf(AddressSpecType.Transparent, AddressSpecType.Unified),
            accountType.zcashAddressSpecs()
        )
    }

    @Test
    fun zcashAddressSpecs_ufvkTheSdkRejects_ownsNothing() {
        mockkStatic(ZCASH_SDK_EXTENSIONS)
        every { ZcashSdk.keyPools(any(), any()) } throws IllegalArgumentException("not a key")
        val accountType = AccountType.ZCashUfvKey(UFVK)

        assertEquals(emptySet(), accountType.zcashAddressSpecs())
        assertFalse(BlockchainType.Zcash.supports(accountType))
    }

    @Test
    fun tokenSupports_accountXprv_offersTransparentTokenOnly() {
        val accountType = AccountType.HdExtendedKey(accountXprv)

        assertTrue(zecToken(AddressSpecType.Transparent).supports(accountType))
        assertFalse(zecToken(AddressSpecType.Shielded).supports(accountType))
        assertFalse(zecToken(AddressSpecType.Unified).supports(accountType))
    }

    @Test
    fun tokenSupports_saplingKey_offersShieldedTokenOnly() {
        val accountType = AccountType.ZCashSaplingKey(SAPLING_SPENDING_KEY)

        assertTrue(zecToken(AddressSpecType.Shielded).supports(accountType))
        assertFalse(zecToken(AddressSpecType.Transparent).supports(accountType))
        assertFalse(zecToken(AddressSpecType.Unified).supports(accountType))
    }

    @Test
    fun tokenSupports_mnemonic_offersEverySpecTokenButNotLegacyNative() {
        AddressSpecType.entries.forEach { spec ->
            assertTrue(zecToken(spec).supports(mnemonic), "$spec should be offered")
        }
        assertFalse(legacyNativeZecToken.supports(mnemonic))
    }

    private fun givenKeyPools(pools: PoolSet) {
        mockkStatic(ZCASH_SDK_EXTENSIONS)
        every { ZcashSdk.keyPools(any(), any()) } returns pools
    }

    private fun zecToken(spec: AddressSpecType) = zecToken(TokenType.AddressSpecTyped(spec))

    private val legacyNativeZecToken get() = zecToken(TokenType.Native)

    private fun zecToken(tokenType: TokenType) = mockk<Token> {
        every { this@mockk.blockchainType } returns BlockchainType.Zcash
        every { this@mockk.type } returns tokenType
    }

    private val mnemonic = AccountType.Mnemonic(List(12) { "abandon" }, passphrase = "")

    private companion object {
        const val ZCASH_SDK_EXTENSIONS = "cash.p.zcash.ZcashSdkKt"
        const val SAPLING_SPENDING_KEY = "secret-extended-key-main1qsaplingspendingkey"
        const val UFVK = "uview1qunifiedviewingkey"

        private val keychain = HDKeychain(ByteArray(64) { (it + 1).toByte() }, Curve.Secp256K1)
        private val masterKey = keychain.hdKey
        private val accountKey = keychain.getKeyByPath("m/44'/0'/0'")

        val masterXprv = HDExtendedKey(masterKey, HDExtendedKeyVersion.xprv).serializePrivate()
        val accountXprv = HDExtendedKey(accountKey, HDExtendedKeyVersion.xprv).serializePrivate()
        val accountXpub = HDExtendedKey(accountKey, HDExtendedKeyVersion.xprv).serializePublic()
        val accountZprv = HDExtendedKey(accountKey, HDExtendedKeyVersion.zprv).serializePrivate()
        val accountLtpv = HDExtendedKey(accountKey, HDExtendedKeyVersion.Ltpv).serializePrivate()
    }
}
