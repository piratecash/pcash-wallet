package cash.p.terminal.modules.multiswap.sendtransaction.services

import cash.p.terminal.core.App
import cash.p.terminal.core.installEthereumCryptoProviderForTest
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.EvmKitManager
import cash.p.terminal.core.managers.EvmKitWrapper
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.core.managers.PendingTransactionRegistrar
import cash.p.terminal.modules.evmfee.GasData
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.modules.send.evm.settings.SendEvmSettingsService
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.ethereumkit.decorations.TransactionDecoration
import io.horizontalsystems.ethereumkit.models.Address as EvmAddress
import io.horizontalsystems.ethereumkit.models.FullTransaction
import io.horizontalsystems.ethereumkit.models.GasPrice
import io.horizontalsystems.ethereumkit.models.RawTransaction
import io.horizontalsystems.ethereumkit.models.Signature
import io.horizontalsystems.ethereumkit.models.SignedRawTransaction
import io.horizontalsystems.ethereumkit.models.Transaction
import io.horizontalsystems.ethereumkit.models.TransactionData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import io.reactivex.Single
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger

/** The signature, not the broadcast result, identifies the transaction being sent. */
@OptIn(ExperimentalCoroutinesApi::class)
class SendTransactionServiceEvmTest : KoinTest {

    private val dispatcher = StandardTestDispatcher()

    private val pendingRegistrar = mockk<PendingTransactionRegistrar>(relaxed = true)
    private val locallyCreatedTransactionRepository =
        mockk<LocallyCreatedTransactionRepository>(relaxed = true)
    private val walletUseCase = mockk<WalletUseCase>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val numberFormatter = mockk<IAppNumberFormatter>(relaxed = true)
    private val currencyManager = mockk<CurrencyManager>(relaxed = true)

    private val evmKit = mockk<EthereumKit>(relaxed = true)
    private val signer = mockk<Signer>(relaxed = true)

    private val walletFactory = WalletFactory(object : HardwareWalletTokenPolicy {
        override fun isSupported(blockchainType: BlockchainType, tokenType: TokenType) = true
    })
    private val testAccount = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.Mnemonic(List(12) { "word$it" }, ""),
        origin = AccountOrigin.Created,
        level = 0,
        isBackedUp = true,
    )
    private val bnbToken = Token(
        coin = Coin(uid = "binance-coin", name = "BNB", code = "BNB"),
        blockchain = Blockchain(
            type = BlockchainType.BinanceSmartChain,
            name = "BNB Smart Chain",
            eip3091url = null,
        ),
        type = TokenType.Native,
        decimals = 18,
    )
    private lateinit var testWallet: Wallet

    private val toAddress = EvmAddress("0xafcc12e4040615e7afe9fb4330eb3d9120acac05")
    private val ownAddress = EvmAddress("0x1111111111111111111111111111111111111111")
    private val signedHash = ByteArray(32) { 0x5d }
    private val signedHashString = "0x" + signedHash.joinToString("") { "%02x".format(it) }

    private val transactionData = TransactionData(to = toAddress, value = BigInteger.ONE, input = ByteArray(0))
    private val rawTransaction = RawTransaction(
        gasPrice = GasPrice.Legacy(1_000_000_000),
        gasLimit = 21_000,
        to = toAddress,
        value = BigInteger.ONE,
        nonce = 7,
    )
    private val signature = Signature(v = 27, r = ByteArray(32) { 1 }, s = ByteArray(32) { 2 })

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single<PendingTransactionRegistrar> { pendingRegistrar }
                single { locallyCreatedTransactionRepository }
                single<WalletUseCase> { walletUseCase }
                single<IAdapterManager> { adapterManager }
                single<MarketKitWrapper> { marketKit }
                single<IAppNumberFormatter> { numberFormatter }
                single<CurrencyManager> { currencyManager }
                single<OfflineOperationGate> { mockk(relaxed = true) }
            }
        )
    }

    @Before
    fun setUp() {
        installEthereumCryptoProviderForTest()
        Dispatchers.setMain(dispatcher)
        testWallet = checkNotNull(walletFactory.create(bnbToken, testAccount, null))

        every { marketKit.token(any()) } returns bnbToken
        coEvery { walletUseCase.createWalletIfNotExists(bnbToken) } returns testWallet

        every { evmKit.receiveAddress } returns ownAddress
        every { evmKit.rawTransaction(transactionData, any(), any(), any()) } returns
            Single.just(rawTransaction)
        coEvery { signer.signature(rawTransaction) } returns signature
        every { evmKit.signedRawTransaction(rawTransaction, signature) } returns
            SignedRawTransaction(raw = ByteArray(4) { 9 }, hash = signedHash)

        coEvery { pendingRegistrar.register(any()) } returns PENDING_TX_ID

        mockkObject(App)
        val evmKitWrapper = EvmKitWrapper(
            evmKit = evmKit,
            nftKit = null,
            blockchainType = BlockchainType.BinanceSmartChain,
            signer = signer,
            merkleTransactionAdapter = null,
        )
        val evmKitManager = mockk<EvmKitManager>(relaxed = true) {
            every { this@mockk.evmKitWrapper } returns evmKitWrapper
        }
        every { App.evmBlockchainManager } returns mockk<EvmBlockchainManager>(relaxed = true) {
            every { getEvmKitManager(BlockchainType.BinanceSmartChain) } returns evmKitManager
            every { getBaseToken(BlockchainType.BinanceSmartChain) } returns bnbToken
        }
        every { App.marketKit } returns marketKit
        every { App.currencyManager } returns currencyManager
        every { App.coinManager } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
        stopKoin()
    }

    @Test
    fun send_broadcastResponseLost_marksTransactionLocallyCreated() = runTest(dispatcher) {
        every { evmKit.send(rawTransaction, signature) } returns
            Single.error(IOException("connection reset"))

        val service = createService()

        var thrown: Throwable? = null
        try {
            service.send()
            advanceUntilIdle()
        } catch (e: Throwable) {
            thrown = e
        }

        assertTrue(thrown is IOException)
        coVerifyOrder {
            locallyCreatedTransactionRepository.markCreated(testWallet, signedHashString)
            evmKit.send(rawTransaction, signature)
        }
        coVerify { pendingRegistrar.deleteFailed(PENDING_TX_ID) }
    }

    @Test
    fun send_broadcastReturnsDifferentTransaction_marksSignedHash() = runTest(dispatcher) {
        // The SDK resolves the broadcast result through an unordered `hash IN (...)` query and takes
        // the first row, so it can hand back a different transaction than the one just submitted.
        val foreignHash = ByteArray(32) { 0x7a }
        every { evmKit.send(rawTransaction, signature) } returns Single.just(
            FullTransaction(
                transaction = Transaction(
                    hash = foreignHash,
                    timestamp = 1_700_000_000L,
                    isFailed = false,
                ),
                decoration = mockk<TransactionDecoration>(relaxed = true),
                extra = emptyMap(),
            )
        )

        val service = createService()
        service.send()
        advanceUntilIdle()

        val foreignHashString = "0x" + foreignHash.joinToString("") { "%02x".format(it) }
        coVerify { locallyCreatedTransactionRepository.markCreated(testWallet, signedHashString) }
        coVerify { pendingRegistrar.updateTxId(PENDING_TX_ID, signedHashString) }
        coVerify(exactly = 0) {
            locallyCreatedTransactionRepository.markCreated(testWallet, foreignHashString)
        }
        coVerify(exactly = 0) { pendingRegistrar.updateTxId(PENDING_TX_ID, foreignHashString) }
    }

    private fun createService(): SendTransactionServiceEvm {
        val service = spyk(SendTransactionServiceEvm(bnbToken), recordPrivateCalls = true)
        every { service["requireValidTransaction"]() } returns SendEvmSettingsService.Transaction(
            transactionData = transactionData,
            gasData = GasData(gasLimit = 21_000, gasPrice = GasPrice.Legacy(1_000_000_000)),
            nonce = 7,
            default = true,
        )
        // Only reachable through the fee pipeline, which needs a live EvmKit; without it the
        // service would skip pending-draft registration and leave updateTxId untested.
        service.setPrivateField("sendAmount", BigDecimal.ONE)
        return service
    }

    private fun Any.setPrivateField(name: String, value: Any?) {
        var type: Class<*>? = javaClass
        while (type != null) {
            val field = type.declaredFields.firstOrNull { it.name == name }
            if (field != null) {
                field.isAccessible = true
                field.set(this, value)
                return
            }
            type = type.superclass
        }
        error("Field $name not found on $javaClass")
    }

    private companion object {
        const val PENDING_TX_ID = "pending-tx-id"
    }
}
