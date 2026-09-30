package cash.p.terminal.modules.multiswap

import cash.p.terminal.core.App
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.multiswap.providers.OffChainSwapProviderSupport
import cash.p.terminal.modules.multiswap.providers.SwapProviderTransactionFactory
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SwapTransactionServiceFactory
import cash.p.terminal.modules.multiswap.sendtransaction.services.SendTransactionServiceBeam
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.Currency
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** A provider that lists native BEAM gets it sent like any other chain: data mapping and service dispatch. */
class BeamSwapDispatchTest {

    private val nativeBeam = mockk<Token>(relaxed = true) {
        every { blockchainType } returns BlockchainType.Beam
        every { type } returns TokenType.Native
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun createTransactionService_nativeBeam_dispatchesToTheBeamService() {
        mockkObject(App)
        every { App.currencyManager } returns mockk<CurrencyManager> {
            every { baseCurrency } returns Currency("USD", "$", 2, 0)
        }

        val service = SwapTransactionServiceFactory.create(nativeBeam, mockk<IMultiSwapProvider>())

        assertTrue(service is SendTransactionServiceBeam)
    }

    @Test
    fun buildTransactionData_nativeBeam_carriesDepositAmountAndMemo() {
        val support = OffChainSwapProviderSupport(
            walletUseCase = mockk<WalletUseCase>(relaxed = true),
            accountManager = mockk<IAccountManager>(relaxed = true),
            swapProviderTransactionsStorage = mockk<SwapProviderTransactionsStorage>(relaxed = true),
            marketKit = mockk<MarketKitWrapper>(relaxed = true),
            adapterManager = mockk<IAdapterManager>(relaxed = true),
            swapProviderTransactionFactory = mockk<SwapProviderTransactionFactory>(relaxed = true),
        )

        val data = support.buildTransactionData(
            tokenIn = nativeBeam,
            amountIn = BigDecimal.ONE,
            depositAddress = "remote-deposit",
            memo = "extra-id",
        )

        assertEquals(SendTransactionData.Beam("remote-deposit", BigDecimal.ONE, "extra-id"), data)
    }
}
