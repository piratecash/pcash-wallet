package cash.p.terminal.modules.evmfee

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.adapters.Eip20Adapter
import cash.p.terminal.core.managers.EvmLabelManager
import cash.p.terminal.core.managers.StackingManager
import cash.p.terminal.data.repository.EvmTransactionRepository
import cash.p.terminal.modules.evmfee.eip1559.Eip1559GasPriceService
import cash.p.terminal.modules.evmfee.legacy.LegacyGasPriceService
import cash.p.terminal.wallet.WalletFactory
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.erc20kit.core.Erc20Kit
import io.horizontalsystems.ethereumkit.api.models.AccountState
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.core.LegacyGasPriceProvider
import io.horizontalsystems.ethereumkit.core.eip1559.Eip1559GasPriceProvider
import io.horizontalsystems.ethereumkit.core.eip1559.FeeHistory
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.TransactionData
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkAll
import io.reactivex.Flowable
import io.reactivex.Single
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

/** "100%" of a native coin is balance minus this reserve; the confirmation must accept exactly that amount. */
class NativeTransferFeeEstimateTest {

    private val evmKit = mockk<EthereumKit>()
    private var feeService: EvmFeeService? = null

    @Before
    fun setUp() {
        EthereumKit.init()
        mockkConstructor(Eip1559GasPriceProvider::class, LegacyGasPriceProvider::class)
        every {
            anyConstructed<Eip1559GasPriceProvider>().feeHistorySingle(any(), any(), any())
        } returns Single.just(feeHistory)
        every { anyConstructed<LegacyGasPriceProvider>().gasPriceSingle() } returns Single.just(LEGACY_GAS_PRICE)
        every { evmKit.accountState } returns AccountState(BALANCE, 0)
        every { evmKit.receiveAddress } returns Address(OWN_ADDRESS)
        every { evmKit.estimateGas(any<TransactionData>(), any()) } returns Single.just(TRANSFER_GAS)
    }

    @After
    fun tearDown() {
        feeService?.clear()
        unmockkAll()
    }

    @Test
    fun estimateNativeTransferFee_eip1559Chain_confirmationAcceptsBalanceMinusReserve() = runBlocking {
        every { evmKit.chain } returns Chain.Ethereum

        val reserve = estimateNativeTransferFee(evmKit, BlockchainType.Ethereum)

        val gasPriceService = Eip1559GasPriceService(Eip1559GasPriceProvider(evmKit), Flowable.empty())
        assertEquals(emptyList<Throwable>(), confirmationErrors(BlockchainType.Ethereum, gasPriceService, reserve))
    }

    @Test
    fun estimateNativeTransferFee_estimatedGasAboveDefault_confirmationAcceptsBalanceMinusReserve() = runBlocking {
        every { evmKit.chain } returns Chain.ArbitrumOne
        every { evmKit.estimateGas(any<TransactionData>(), any()) } returns Single.just(ARBITRUM_TRANSFER_GAS)

        val reserve = estimateNativeTransferFee(evmKit, BlockchainType.ArbitrumOne)

        val gasPriceService = LegacyGasPriceService(LegacyGasPriceProvider(evmKit))
        assertEquals(emptyList<Throwable>(), confirmationErrors(BlockchainType.ArbitrumOne, gasPriceService, reserve))
    }

    @Test
    fun estimateNativeTransferFee_optimism_reservesL1FeeAndConfirmationAccepts() = runBlocking {
        every { evmKit.chain } returns Chain.Optimism
        every { evmKit.call(any(), any(), any()) } returns Single.just(L1_FEE.toUint256())

        val reserve = estimateNativeTransferFee(evmKit, BlockchainType.Optimism)

        assertEquals(BigInteger.valueOf(TRANSFER_GAS * LEGACY_GAS_PRICE) + L1_FEE, reserve)
        val gasPriceService = LegacyGasPriceService(LegacyGasPriceProvider(evmKit))
        assertEquals(emptyList<Throwable>(), confirmationErrors(BlockchainType.Optimism, gasPriceService, reserve))
    }

    @Test
    fun estimateTransferCost_eip20_estimatesForeignRecipientWithGasReserve() = runBlocking {
        every { evmKit.chain } returns Chain.BinanceSmartChain
        every { evmKit.estimateGas(any<TransactionData>(), any()) } returns Single.just(TOKEN_TRANSFER_GAS)
        val recipient = slot<Address>()
        val eip20Kit = mockk<Erc20Kit> {
            every { buildTransferTransactionData(capture(recipient), any()) } returns tokenTransfer
        }

        val cost = eip20Adapter(eip20Kit).estimateTransferCost()

        assertNotEquals(Address(OWN_ADDRESS), recipient.captured)
        assertEquals(0, BigDecimal("0.00018").compareTo(cost.fee))
        assertEquals(0, BigDecimal("0.000198").compareTo(cost.requiredBalance))
    }

    @Test
    fun estimateGasData_optimismTokenTransfer_feeExcludesGasReserve() = runBlocking {
        every { evmKit.chain } returns Chain.Optimism
        every { evmKit.call(any(), any(), any()) } returns Single.just(L1_FEE.toUint256())
        every { evmKit.estimateGas(any<TransactionData>(), any()) } returns Single.just(TOKEN_TRANSFER_GAS)

        val gasData = estimateGasData(evmKit, BlockchainType.Optimism, tokenTransfer)

        assertEquals(BigInteger.valueOf(TOKEN_TRANSFER_GAS * LEGACY_GAS_PRICE) + L1_FEE, gasData.estimatedFee)
        assertEquals(
            BigInteger.valueOf(TOKEN_TRANSFER_GAS_LIMIT * LEGACY_GAS_PRICE) + L1_FEE,
            gasData.fee,
        )
    }

    private fun eip20Adapter(eip20Kit: Erc20Kit) = Eip20Adapter(
        eip20Kit = eip20Kit,
        evmTransactionRepository = mockk<EvmTransactionRepository> {
            every { evmKitWrapper.evmKit } returns evmKit
            every { getBlockchainType() } returns BlockchainType.BinanceSmartChain
        },
        contractAddress = RECIPIENT,
        baseToken = wallet.token,
        coinManager = mockk<ICoinManager>(relaxed = true),
        wallet = wallet,
        evmLabelManager = mockk<EvmLabelManager>(relaxed = true),
        stackingManager = mockk<StackingManager>(relaxed = true),
    )

    // The same fee service and gas data service the send confirmation builds.
    private suspend fun confirmationErrors(
        blockchainType: BlockchainType,
        gasPriceService: IEvmGasPriceService,
        reserve: BigInteger,
    ): List<Throwable>? {
        val gasDataService = EvmCommonGasDataService.instance(evmKit, blockchainType)
        val service = EvmFeeService(evmKit, gasPriceService, gasDataService).also { feeService = it }
        gasPriceService.start()
        service.start()
        val data = TransactionData(Address(RECIPIENT), BALANCE - reserve, byteArrayOf())

        service.setTransactionData(data)

        return withTimeout(TIMEOUT_MS) {
            service.transactionStatusFlow.first { it.dataOrNull?.estimatedFor === data }
        }.dataOrNull?.errors
    }

    private fun BigInteger.toUint256(): ByteArray {
        val bytes = toByteArray()
        return ByteArray(32 - bytes.size) + bytes
    }

    private companion object {
        const val OWN_ADDRESS = "0x1111111111111111111111111111111111111111"
        const val RECIPIENT = "0x2222222222222222222222222222222222222222"
        const val TRANSFER_GAS = 21_000L
        const val TOKEN_TRANSFER_GAS = 60_000L
        const val TOKEN_TRANSFER_GAS_LIMIT = 66_000L
        const val ARBITRUM_TRANSFER_GAS = 32_000L
        const val LEGACY_GAS_PRICE = 3_000_000_000L
        const val TIMEOUT_MS = 5_000L
        val BALANCE: BigInteger = BigInteger.TEN.pow(17)
        val L1_FEE: BigInteger = BigInteger.valueOf(40_000_000_000_000L)
        val tokenTransfer = TransactionData(Address(RECIPIENT), BigInteger.ZERO, byteArrayOf(1))
        val wallet = WalletFactory.previewWallet()
        val feeHistory = FeeHistory(
            baseFeePerGas = listOf(90_000_000_000L, 120_000_000_000L, 100_000_000_000L),
            gasUsedRatio = listOf(0.5, 0.5),
            oldestBlock = 1L,
            reward = listOf(listOf(2_000_000_000L), listOf(4_000_000_000L)),
        )
    }
}
