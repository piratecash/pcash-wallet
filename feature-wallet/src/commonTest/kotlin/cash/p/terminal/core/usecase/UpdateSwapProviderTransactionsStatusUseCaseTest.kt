package cash.p.terminal.core.usecase

import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.modules.multiswap.providers.buildSwapProviderTransaction
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.network.swaprepository.SwapProviderStatusRequest
import cash.p.terminal.network.swaprepository.SwapProviderTransactionStatusRepository
import io.mockk.coEvery
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
import org.koin.core.qualifier.named
import org.koin.dsl.module

class UpdateSwapProviderTransactionsStatusUseCaseTest {

    private val storage = mockk<SwapProviderTransactionsStorage>(relaxed = true)
    private val statusRepository = mockk<SwapProviderTransactionStatusRepository>()

    @Before
    fun setUp() {
        startKoin {
            modules(module {
                single(named(SwapProvider.PCASH_BACKEND)) { statusRepository }
            })
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun invoke_backendSwapTransaction_passesStoredWalletAddress() = runTest {
        val transaction = buildSwapProviderTransaction(SwapProvider.PCASH_BACKEND, "order-1")
            .copy(providerWalletAddress = WALLET_ADDRESS)
        every { storage.getAllUnfinishedByAccount(any(), any(), any()) } returns listOf(transaction)
        val request = slot<SwapProviderStatusRequest>()
        coEvery { statusRepository.getTransactionStatus(capture(request)) } returns null

        UpdateSwapProviderTransactionsStatusUseCase(storage)(transaction.accountId)

        assertEquals("order-1", request.captured.transactionId)
        assertEquals(WALLET_ADDRESS, request.captured.walletAddress)
    }

    private companion object {
        const val WALLET_ADDRESS = "0x2c7536E3605D9C16a7a3D7b1898e529396a65c23"
    }
}
