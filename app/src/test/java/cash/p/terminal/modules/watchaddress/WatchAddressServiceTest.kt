package cash.p.terminal.modules.watchaddress

import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.RestoreSettings
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.WalletActivator
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.zcash.Pool
import cash.p.zcash.PoolSet
import cash.p.zcash.ZcashSdk
import cash.p.zcash.keyPools
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.Curve
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.horizontalsystems.hdwalletkit.HDExtendedKeyVersion
import io.horizontalsystems.hdwalletkit.HDKeychain
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WatchAddressServiceTest {

    private val marketKit = mockk<MarketKitWrapper> {
        every { tokens(any<List<TokenQuery>>()) } answers {
            firstArg<List<TokenQuery>>().map { query -> tokenOf(query) }
        }
    }

    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val walletActivator = mockk<WalletActivator>(relaxed = true)
    private val account = mockk<Account>()
    private val accountFactory = mockk<IAccountFactory> {
        every { watchAccount(any(), any()) } returns account
    }
    private val restoreSettingsManager = mockk<RestoreSettingsManager>(relaxed = true)

    private val service = WatchAddressService(
        accountManager = accountManager,
        walletActivator = walletActivator,
        accountFactory = accountFactory,
        marketKit = marketKit,
        evmBlockchainManager = mockk<EvmBlockchainManager>(),
        restoreSettingsManager = restoreSettingsManager,
    )

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun tokens_saplingKey_requestsShieldedOnly() {
        assertEquals(
            listOf(TokenType.AddressSpecType.Shielded),
            zcashSpecsOf(AccountType.ZCashSaplingKey(SAPLING_KEY))
        )
    }

    @Test
    fun tokens_fullUfvk_requestsWholeGroupInCanonicalOrder() {
        givenKeyPools(PoolSet.of(Pool.TRANSPARENT, Pool.SAPLING, Pool.ORCHARD))

        assertEquals(
            TokenType.AddressSpecType.entries,
            zcashSpecsOf(AccountType.ZCashUfvKey(UFVK))
        )
    }

    @Test
    fun tokens_saplingOnlyUfvk_requestsShieldedOnly() {
        givenKeyPools(PoolSet.of(Pool.SAPLING))

        assertEquals(
            listOf(TokenType.AddressSpecType.Shielded),
            zcashSpecsOf(AccountType.ZCashUfvKey(UFVK))
        )
    }

    @Test
    fun tokens_transparentAccountXpub_requestsZcashTransparentAlongsideBitcoin() {
        val tokens = service.tokens(AccountType.HdExtendedKey(accountXpub))

        assertEquals(
            listOf(TokenType.AddressSpecType.Transparent),
            tokens.zcashSpecs()
        )
        assertTrue(tokens.any { it.blockchainType == BlockchainType.Bitcoin })
    }

    @Test
    fun tokens_litecoinExtendedKey_requestsNoZcashQuery() {
        assertEquals(emptyList(), zcashSpecsOf(AccountType.HdExtendedKey(accountLtpv)))
    }

    @Test
    fun watchAll_birthdayHeightGiven_savesItForZcashBeforeActivatingTokens() {
        val settings = slot<RestoreSettings>()

        service.watchAll(
            accountType = AccountType.ZCashSaplingKey(SAPLING_KEY),
            name = "Watch 1",
            zcashBirthdayHeight = BIRTHDAY_HEIGHT
        )

        verifyOrder {
            accountManager.save(account)
            restoreSettingsManager.save(capture(settings), account, BlockchainType.Zcash)
            walletActivator.activateTokens(account, any())
        }
        assertEquals(BIRTHDAY_HEIGHT, settings.captured.birthdayHeight)
    }

    @Test
    fun watchTokens_noBirthdayHeight_savesNoRestoreSettings() {
        service.watchTokens(
            accountType = AccountType.ZCashUfvKey(UFVK),
            tokens = emptyList(),
            name = "Watch 1"
        )

        verify { walletActivator.activateTokens(account, emptyList()) }
        verify(exactly = 0) { restoreSettingsManager.save(any(), any(), any()) }
    }

    private fun zcashSpecsOf(accountType: AccountType) = service.tokens(accountType).zcashSpecs()

    private fun List<Token>.zcashSpecs() = filter { it.blockchainType == BlockchainType.Zcash }
        .map { (it.type as TokenType.AddressSpecTyped).type }

    private fun givenKeyPools(pools: PoolSet) {
        mockkStatic(ZCASH_SDK_EXTENSIONS)
        every { ZcashSdk.keyPools(any(), any()) } returns pools
    }

    private fun tokenOf(query: TokenQuery) = Token(
        coin = Coin(uid = query.blockchainType.uid, name = "", code = ""),
        blockchain = Blockchain(query.blockchainType, "", null),
        type = query.tokenType,
        decimals = 8
    )

    private companion object {
        const val ZCASH_SDK_EXTENSIONS = "cash.p.zcash.ZcashSdkKt"
        const val SAPLING_KEY = "secret-extended-key-main1qsaplingspendingkey"
        const val UFVK = "uview1qunifiedviewingkey"
        const val BIRTHDAY_HEIGHT = 3_100_000L

        private val accountKey = HDKeychain(ByteArray(64) { (it + 1).toByte() }, Curve.Secp256K1)
            .getKeyByPath("m/44'/0'/0'")

        val accountXpub = HDExtendedKey(accountKey, HDExtendedKeyVersion.xprv).serializePublic()
        val accountLtpv = HDExtendedKey(accountKey, HDExtendedKeyVersion.Ltpv).serializePrivate()
    }
}
