package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.modules.multiswap.SwapAmountDirection
import cash.p.terminal.modules.multiswap.SwapQuoteStonFi
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.network.stonfi.domain.entity.GasParams
import cash.p.terminal.network.stonfi.domain.entity.RouterInfo
import cash.p.terminal.network.stonfi.domain.entity.SimulateSwap
import cash.p.terminal.network.stonfi.domain.repository.StonFiRepository
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ton.block.AddrStd
import org.ton.boc.BagOfCells
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Base64

class StonFiProviderTest {

    @Test
    fun minimumAskUnits_exactOut_usesRequestedTargetInsteadOfSlippageMinimum() {
        val minimum = stonFiMinimumAskUnits(
            direction = SwapAmountDirection.Out,
            amount = BigDecimal("1"),
            tokenOutDecimals = 9,
            simulatedMinimum = "995000006",
        )

        assertEquals(BigInteger("1000000000"), minimum)
    }

    @Test
    fun minimumAskUnits_exactIn_usesSimulationSlippageMinimum() {
        val minimum = stonFiMinimumAskUnits(
            direction = SwapAmountDirection.In,
            amount = BigDecimal("1"),
            tokenOutDecimals = 9,
            simulatedMinimum = "995000006",
        )

        assertEquals(BigInteger("995000006"), minimum)
    }

    @Test
    fun fetchFinalQuote_w5JettonToV4r2Ton_debitsW5JettonWalletAndPaysV4r2Address() = runTest {
        val tokenIn = tonToken(TokenType.TonW5Jetton(MASTER))
        val tokenOut = tonToken(TokenType.Native)
        val walletUseCase = mockk<WalletUseCase> {
            every { getReceiveAddress(tokenIn) } returns W5_ADDRESS
            every { getReceiveAddress(tokenOut) } returns V4R2_ADDRESS
        }
        val repository = mockk<StonFiRepository> {
            coEvery { simulateSwap(any(), any(), any(), any(), any(), any(), any(), 2) } returns simulation()
            coEvery { getRouter(ROUTER) } returns router()
            coEvery { getJettonAddress(MASTER, W5_ADDRESS) } returns W5_JETTON_WALLET
        }
        val provider = StonFiProvider(repository, walletUseCase)

        val quote = provider.fetchFinalQuote(
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = BigDecimal.ONE,
            swapSettings = emptyMap(),
            sendTransactionSettings = null,
            swapQuote = mockk<SwapQuoteStonFi>(relaxed = true),
        )

        val data = quote.sendTransactionData as SendTransactionData.TonSwap
        val payload = BagOfCells(Base64.getDecoder().decode(data.payload)).first()
        assertEquals(W5_JETTON_WALLET, data.destinationAddress)
        assertEquals(
            JettonSwapParties(AddrStd.parse(W5_ADDRESS), AddrStd.parse(V4R2_ADDRESS)),
            payload.stonFiJettonSwapParties(dexVersion = 2),
        )
        coVerify(exactly = 0) { repository.getJettonAddress(MASTER, V4R2_ADDRESS) }
    }

    private fun tonToken(type: TokenType) = Token(
        coin = Coin(uid = "coin", name = "Coin", code = "COIN"),
        blockchain = Blockchain(BlockchainType.Ton, "TON", null),
        type = type,
        decimals = 9,
    )

    private fun simulation() = SimulateSwap(
        offerAddress = MASTER,
        askAddress = TON_NATIVE,
        offerJettonWallet = "",
        askJettonWallet = PTON_WALLET,
        routerAddress = ROUTER,
        poolAddress = "",
        offerUnits = BigInteger("1000000000"),
        askUnits = "500",
        slippageTolerance = "0.005",
        minAskUnits = "450",
        recommendedSlippageTolerance = "0.005",
        recommendedMinAskUnits = "450",
        swapRate = "1",
        priceImpact = "0.01",
        feeAddress = "",
        feeUnits = "0",
        feePercent = "0",
        gasParams = GasParams(BigInteger.ONE, BigInteger.ONE, BigInteger.ONE),
    )

    private fun router() = RouterInfo(
        address = ROUTER,
        majorVersion = 2,
        minorVersion = 0,
        ptonMasterAddress = TON_NATIVE,
        ptonWalletAddress = PTON_WALLET,
        ptonVersion = "2",
        routerType = "ConstantProduct",
        poolCreationEnabled = true,
    )

    private companion object {
        const val MASTER = "EQmaster"
        const val TON_NATIVE = "EQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAM9c"
        const val ROUTER = "0:1111111111111111111111111111111111111111111111111111111111111111"
        const val PTON_WALLET = "0:2222222222222222222222222222222222222222222222222222222222222222"
        const val W5_JETTON_WALLET = "0:3333333333333333333333333333333333333333333333333333333333333333"
        const val W5_ADDRESS = "0:a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5"
        const val V4R2_ADDRESS = "0:b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4"
    }
}
