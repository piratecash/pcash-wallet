package cash.p.terminal.modules.addtoken

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.managers.UserDeletedWalletManager
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.customCoinUid
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.GetHardwarePublicKeyForWalletUseCase
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class AddTokenServiceTonW5Test {

    private val account = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.Mnemonic(List(12) { "abandon" }, ""),
        origin = AccountOrigin.Created,
        level = 0,
    )
    private val tonBlockchain = Blockchain(BlockchainType.Ton, "TON", null)
    private val jettonToken = token(TokenType.Jetton(JETTON_MASTER))

    private val walletManager = mockk<IWalletManager>(relaxed = true)
    private val userDeletedWalletManager = mockk<UserDeletedWalletManager>(relaxed = true)
    private val coinManager = mockk<ICoinManager>(relaxed = true) {
        every { getToken(any()) } returns null
    }
    private val walletFactory = mockk<WalletFactory> {
        every { create(any(), account, any()) } answers {
            val createdToken = firstArg<Token>()
            mockk<Wallet> { every { token } returns createdToken }
        }
    }

    @Before
    fun setUp() {
        startKoin {
            modules(module {
                single { walletFactory }
                single { mockk<GetHardwarePublicKeyForWalletUseCase>(relaxed = true) }
            })
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun addToken_v4r2JettonWithW5Enabled_addsJettonToBothVersions() = runTest {
        every { walletManager.activeWallets } returns listOf(wallet(token(TokenType.TonW5Native)))
        val saved = slot<List<Wallet>>()
        every { walletManager.save(capture(saved)) } returns Unit

        createService().addToken(AddTokenService.TokenInfo(jettonToken, inCoinList = false))

        val w5Jetton = TokenType.TonW5Jetton(JETTON_MASTER)
        assertEquals(listOf(jettonToken.type, w5Jetton), saved.captured.map { it.token.type })
        coVerify(exactly = 1) { userDeletedWalletManager.unmarkAsDeleted(account.id, token(w5Jetton).tokenQuery.id) }
    }

    @Test
    fun addToken_v4r2JettonWithoutW5_addsOnlyJetton() = runTest {
        every { walletManager.activeWallets } returns listOf(wallet(token(TokenType.Native)))
        val saved = slot<List<Wallet>>()
        every { walletManager.save(capture(saved)) } returns Unit

        createService().addToken(AddTokenService.TokenInfo(jettonToken, inCoinList = false))

        assertEquals(listOf(jettonToken.type), saved.captured.map { it.token.type })
    }

    @Test
    fun addToken_w5TwinInCatalog_usesCatalogToken() = runTest {
        every { walletManager.activeWallets } returns listOf(wallet(token(TokenType.TonW5Native)))
        val catalogTwin = token(TokenType.TonW5Jetton(JETTON_MASTER)).copy(decimals = 9)
        every { coinManager.getToken(catalogTwin.tokenQuery) } returns catalogTwin
        val saved = slot<List<Wallet>>()
        every { walletManager.save(capture(saved)) } returns Unit

        createService().addToken(AddTokenService.TokenInfo(jettonToken, inCoinList = true))

        assertEquals(catalogTwin, saved.captured.last().token)
    }

    @Test
    fun addToken_customJettonW5Enabled_twinCoinUidMatchesStoredQuery() = runTest {
        val customV4r2 = jettonToken.copy(coin = jettonToken.coin.copy(uid = jettonToken.tokenQuery.customCoinUid))
        every { walletManager.activeWallets } returns listOf(wallet(token(TokenType.TonW5Native)))
        val saved = slot<List<Wallet>>()
        every { walletManager.save(capture(saved)) } returns Unit

        createService().addToken(AddTokenService.TokenInfo(customV4r2, inCoinList = false))

        val w5Query = token(TokenType.TonW5Jetton(JETTON_MASTER)).tokenQuery
        assertEquals(customV4r2.tokenQuery.customCoinUid, saved.captured.first().token.coin.uid)
        assertEquals(w5Query.customCoinUid, saved.captured.last().token.coin.uid)
    }

    private fun createService() = AddTokenService(
        coinManager = coinManager,
        walletManager = walletManager,
        accountManager = mockk<IAccountManager>(relaxed = true) {
            every { activeAccount } returns account
        },
        userDeletedWalletManager = userDeletedWalletManager,
        evmSyncSourceManager = mockk(relaxed = true),
        marketKit = mockk(relaxed = true),
    )

    private fun wallet(token: Token) = mockk<Wallet> { every { this@mockk.token } returns token }

    private fun token(type: TokenType) = Token(
        coin = Coin(uid = "tether", name = "Tether USD", code = "USDT"),
        blockchain = tonBlockchain,
        type = type,
        decimals = 6,
    )

    private companion object {
        const val JETTON_MASTER = "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs"
    }
}
