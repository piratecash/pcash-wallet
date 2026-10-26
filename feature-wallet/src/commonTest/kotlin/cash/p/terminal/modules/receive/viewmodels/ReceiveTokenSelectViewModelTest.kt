package cash.p.terminal.modules.receive.viewmodels

import cash.p.terminal.modules.receive.FullCoinsProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.policy.PlatformChainSupport
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiveTokenSelectViewModelTest {

    private val walletUseCase = mockk<WalletUseCase> {
        every { getWallet(any<Token>()) } returns null
    }
    private val account = mockk<Account>(relaxed = true) {
        every { type } returns AccountType.Mnemonic(listOf("word"), "")
    }
    private val walletManager = mockk<IWalletManager>(relaxed = true) {
        every { activeWallets } returns emptyList()
    }
    private val fullCoinsProvider = mockk<FullCoinsProvider>(relaxed = true) {
        coEvery { getItems() } returns emptyList()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        startKoin {
            modules(module {
                single { walletUseCase }
                single<PlatformChainSupport> { EthereumOnlyChainSupport }
            })
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun getCoinForReceiveType_unsupportedChain_reportsItWithoutCreatingWallet() = runTest {
        val result = viewModel().getCoinForReceiveType(fullCoin(BlockchainType.Tron))

        assertEquals(CoinForReceiveType.NotSupportedOnPlatform, result)
        coVerify(exactly = 0) { walletUseCase.createWalletIfNotExists(any()) }
    }

    @Test
    fun getCoinForReceiveType_supportedChain_createsWallet() = runTest {
        val wallet = mockk<Wallet>()
        coEvery { walletUseCase.createWalletIfNotExists(any()) } returns wallet

        val result = viewModel().getCoinForReceiveType(fullCoin(BlockchainType.Ethereum))

        assertEquals(CoinForReceiveType.Single(wallet), result)
    }

    private fun viewModel() = ReceiveTokenSelectViewModel(
        walletManager,
        account,
        fullCoinsProvider,
        mockk<IAdapterManager>(relaxed = true),
    )

    private fun fullCoin(blockchainType: BlockchainType): FullCoin {
        val coin = Coin(uid = blockchainType.uid, name = blockchainType.uid, code = blockchainType.uid)
        val token = Token(
            coin = coin,
            blockchain = Blockchain(type = blockchainType, name = blockchainType.uid, eip3091url = null),
            type = TokenType.Native,
            decimals = 8,
        )
        return FullCoin(coin = coin, tokens = listOf(token))
    }

    private object EthereumOnlyChainSupport : PlatformChainSupport {
        override fun isSupported(blockchainType: BlockchainType) = blockchainType == BlockchainType.Ethereum
        override fun isSupported(account: Account) = true
    }
}
