package cash.p.terminal.core.factories

import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.EvmKitManager
import cash.p.terminal.core.managers.StackingManager
import cash.p.terminal.data.repository.EvmTransactionRepository
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class AdapterFactoryEip20LinkTest {

    private val account = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.EvmAddress("0x"),
        origin = AccountOrigin.Created,
        level = 0,
    )

    private val evmKitManager = mockk<EvmKitManager>(relaxed = true)
    private val evmTransactionRepository = mockk<EvmTransactionRepository>(relaxed = true)
    private val evmBlockchainManager = mockk<EvmBlockchainManager> {
        every { getBlockchain(any<Token>()) } returns
            Blockchain(BlockchainType.Ethereum, BlockchainType.Ethereum.uid, null)
        every { getBaseToken(BlockchainType.Ethereum) } returns mockk(relaxed = true)
        every { getEvmKitManager(BlockchainType.Ethereum) } returns evmKitManager
    }

    private val wallet = mockk<Wallet>(relaxed = true) {
        every { account } returns this@AdapterFactoryEip20LinkTest.account
        every { token.type } returns TokenType.Eip20(CONTRACT)
        every { token.blockchainType } returns BlockchainType.Ethereum
    }

    private val factory = AdapterFactory(
        context = mockk(relaxed = true),
        btcBlockchainManager = mockk(relaxed = true),
        evmBlockchainManager = evmBlockchainManager,
        evmSyncSourceManager = mockk(relaxed = true),
        solanaKitManager = mockk(relaxed = true),
        tronKitManager = mockk(relaxed = true),
        tonKitManager = mockk(relaxed = true),
        stellarKitManager = mockk(relaxed = true),
        thorchainKitManagers = mockk(relaxed = true),
        moneroKitManager = mockk(relaxed = true),
        backgroundManager = mockk(relaxed = true),
        restoreSettingsManager = mockk(relaxed = true),
        coinManager = mockk(relaxed = true),
        evmLabelManager = mockk(relaxed = true),
        localStorage = mockk(relaxed = true),
        masterNodesRepository = mockk(relaxed = true),
        getBnbAddressUseCase = mockk(relaxed = true),
        feeRateProvider = mockk(relaxed = true),
        dispatcherProvider = mockk(relaxed = true),
        walletManager = mockk(relaxed = true),
    )

    @Before
    fun setUp() {
        startKoin {
            modules(module {
                factory { evmTransactionRepository }
                single { mockk<StackingManager>(relaxed = true) }
            })
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun getAdapterOrNull_erc20KitBuildFails_unlinksEvmKitAndReturnsNull() = runTest {
        coEvery { evmTransactionRepository.buildErc20Kit(any(), CONTRACT) } throws IllegalStateException("open failed")

        val adapter = factory.getAdapterOrNull(wallet)

        assertNull(adapter)
        coVerify(exactly = 1) { evmKitManager.unlink(account) }
    }

    @Test
    fun getAdapterOrNull_cancelledWhileBuildingErc20Kit_unlinksEvmKitAndStaysCancelled() = runTest {
        coEvery { evmTransactionRepository.buildErc20Kit(any(), CONTRACT) } coAnswers { awaitCancellation() }

        val job = launch(start = CoroutineStart.UNDISPATCHED) { factory.getAdapterOrNull(wallet) }
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        coVerify(exactly = 1) { evmKitManager.unlink(account) }
    }

    private companion object {
        const val CONTRACT = "0xdac17f958d2ee523a2206206994597c13d831ec7"
    }
}
