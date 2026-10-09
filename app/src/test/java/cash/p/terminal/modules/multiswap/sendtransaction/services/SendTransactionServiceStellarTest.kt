package cash.p.terminal.modules.multiswap.sendtransaction.services

import cash.p.terminal.core.App
import cash.p.terminal.core.ISendMemoAdapter
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.core.managers.StellarKitManager
import cash.p.terminal.core.managers.StellarKitWrapper
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionServiceFactory
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.stellarkit.StellarKit
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.stellar.sdk.responses.TransactionResponse
import java.math.BigDecimal

class SendTransactionServiceStellarTest : KoinTest {

    private val account = mockk<Account>(relaxed = true)
    private val stellarKit = mockk<StellarKit>(relaxed = true)
    private val adapter = mockk<ISendMemoAdapter>(relaxed = true)
    private val walletUseCase = mockk<WalletUseCase>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val currencyManager = mockk<CurrencyManager>(relaxed = true)
    private val stellarKitManager = mockk<StellarKitManager>(relaxed = true)
    private val accountManager = mockk<IAccountManager>(relaxed = true)

    private val recipient = "GBRECIPIENT"
    private val amount = BigDecimal("12.5")
    private val issuer = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"

    private val nativeToken = stellarToken(TokenType.Native)
    private val assetToken = stellarToken(TokenType.Asset("USDC", issuer))

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single<StellarKitManager> { stellarKitManager }
                single<IAccountManager> { accountManager }
                single<WalletUseCase> { walletUseCase }
                single<IAdapterManager> { adapterManager }
                single<MarketKitWrapper> { marketKit }
                single<CurrencyManager> { currencyManager }
                single<IAppNumberFormatter> { mockk(relaxed = true) }
                single<LocallyCreatedTransactionRepository> { mockk(relaxed = true) }
                single<OfflineOperationGate> { mockk(relaxed = true) }
            }
        )
    }

    @Before
    fun setUp() {
        val response = mockk<TransactionResponse> { every { hash } returns "tx-hash" }
        coEvery { stellarKit.sendNative(any(), any(), any()) } returns response
        coEvery { stellarKit.sendAsset(any(), any(), any(), any()) } returns response
        every { stellarKit.sendFee } returns BigDecimal("0.00001")
        coEvery { stellarKitManager.getStellarKitWrapper(any()) } returns StellarKitWrapper(stellarKit)
        every { accountManager.activeAccount } returns account

        coEvery { walletUseCase.createWalletIfNotExists(any()) } returns mockk<Wallet>(relaxed = true)
        coEvery { adapterManager.awaitAdapterForWallet<ISendMemoAdapter>(any(), any()) } returns adapter
        every { adapterManager.getAdjustedBalanceData(any()) } returns null
        every { adapter.maxSpendableBalance } returns BigDecimal("100")
        every { currencyManager.baseCurrency } returns Currency("USD", "$", 2, 0)
        every { marketKit.token(any()) } returns nativeToken
        every { marketKit.coinPrice(any(), any()) } returns null

        mockkObject(App)
        every { App.currencyManager } returns currencyManager
        every { App.marketKit } returns marketKit
    }

    @After
    fun tearDown() {
        unmockkAll()
        stopKoin()
    }

    @Test
    fun sendTransaction_assetTokenRegular_sendsAssetWithAssetId() = runTest {
        send(assetToken, memo = "123456")

        coVerify { stellarKit.sendAsset("USDC:$issuer", recipient, amount, "123456") }
        coVerify(exactly = 0) { stellarKit.sendNative(any(), any(), any()) }
    }

    @Test
    fun sendTransaction_nativeTokenRegular_sendsNative() = runTest {
        send(nativeToken, memo = "123456")

        coVerify { stellarKit.sendNative(recipient, amount, "123456") }
        coVerify(exactly = 0) { stellarKit.sendAsset(any(), any(), any(), any()) }
    }

    @Test
    fun sendTransaction_blankMemo_sendsNullMemo() = runTest {
        send(nativeToken, memo = "")

        coVerify { stellarKit.sendNative(recipient, amount, null) }
    }

    @Test
    fun create_stellarAssetToken_returnsStellarService() {
        val service = SendTransactionServiceFactory.create(assetToken)

        assertTrue(service is SendTransactionServiceStellar)
    }

    private suspend fun send(token: Token, memo: String) {
        val service = SendTransactionServiceStellar(account, token)
        service.setSendTransactionData(SendTransactionData.Stellar.Regular(recipient, memo, amount))
        service.send()
    }

    private fun stellarToken(type: TokenType) = Token(
        coin = Coin(uid = "stellar-token", name = "Stellar token", code = "TKN"),
        blockchain = Blockchain(type = BlockchainType.Stellar, name = "Stellar", eip3091url = null),
        type = type,
        decimals = 7
    )
}
