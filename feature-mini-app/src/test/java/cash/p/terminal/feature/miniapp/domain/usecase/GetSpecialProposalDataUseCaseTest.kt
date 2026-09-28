package cash.p.terminal.feature.miniapp.domain.usecase

import cash.p.terminal.feature.miniapp.data.api.MiniAppApi
import cash.p.terminal.feature.miniapp.data.api.ProfileResponseDto
import cash.p.terminal.network.binance.api.BinanceApi
import cash.p.terminal.network.binance.data.TokenBalance
import cash.p.terminal.network.pirate.domain.enity.CalculatorData
import cash.p.terminal.network.pirate.domain.enity.CalculatorItemData
import cash.p.terminal.network.pirate.domain.enity.PeriodType
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.premium.data.config.PremiumConfig
import cash.p.terminal.premium.domain.usecase.GetBnbAddressUseCase
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.MarketKitWrapper
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Currency
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

class GetSpecialProposalDataUseCaseTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = object : DispatcherProvider {
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
        override val main: CoroutineDispatcher = dispatcher
        override val applicationScope: CoroutineScope = CoroutineScope(dispatcher)
    }

    private val currencyManager = mockk<CurrencyManager>()
    private val numberFormatter = mockk<IAppNumberFormatter>(relaxed = true)
    private val miniAppApi = mockk<MiniAppApi>()
    private val piratePlaceRepository = mockk<PiratePlaceRepository>()
    private val marketKitWrapper = mockk<MarketKitWrapper>()
    private val binanceApi = mockk<BinanceApi>()
    private val getBnbAddressUseCase = mockk<GetBnbAddressUseCase>()
    private val accountManager = mockk<IAccountManager>()

    private val useCase = GetSpecialProposalDataUseCase(
        currencyManager = currencyManager,
        numberFormatter = numberFormatter,
        miniAppApi = miniAppApi,
        piratePlaceRepository = piratePlaceRepository,
        marketKitWrapper = marketKitWrapper,
        binanceApi = binanceApi,
        getBnbAddressUseCase = getBnbAddressUseCase,
        accountManager = accountManager,
        dispatcherProvider = dispatcherProvider
    )

    /**
     * The calculator is asked for the income of a concrete stake, so its answer scales with
     * the requested amount. ROI is a rate and must stay [YEARLY_RATE] whatever the stake is.
     */
    private fun stubBalances(pirate: BigDecimal, cosa: BigDecimal) {
        every { accountManager.account(ACCOUNT_ID) } returns mockk<Account>()
        coEvery { getBnbAddressUseCase.getAddress(any(), any()) } returns ADDRESS
        coEvery {
            binanceApi.getTokenBalance(PremiumConfig.PIRATE_CONTRACT_ADDRESS, ADDRESS)
        } returns TokenBalance(pirate)
        coEvery {
            binanceApi.getTokenBalance(PremiumConfig.COSANTA_CONTRACT_ADDRESS, ADDRESS)
        } returns TokenBalance(cosa)
        coEvery { miniAppApi.getUserProfile(any(), any()) } returns ProfileResponseDto(
            balance = "0",
            isPremium = false
        )
        every { marketKitWrapper.coinPrice(any(), any()) } returns null
        every { currencyManager.baseCurrency } returns Currency("USD", "$", 2, 0)
        coEvery { piratePlaceRepository.getCalculatorData(any(), any()) } answers {
            val stake = secondArg<Double>()
            CalculatorData(
                items = listOf(
                    CalculatorItemData(PeriodType.YEAR, stake * YEARLY_RATE, emptyMap()),
                    CalculatorItemData(PeriodType.MONTH, stake * YEARLY_RATE / 12, emptyMap())
                )
            )
        }
    }

    private suspend fun proposal() = useCase(ACCOUNT_ID, "jwt", "https://example.com/", "USD")

    @Test
    fun invoke_balancesBelowMinimum_roiIsYearlyRate() = runTest(dispatcher) {
        stubBalances(pirate = BigDecimal.ZERO, cosa = BigDecimal.ZERO)

        val result = proposal()

        assertEquals(EXPECTED_ROI, result.pirateRoi)
        assertEquals(EXPECTED_ROI, result.cosaRoi)
    }

    @Test
    fun invoke_cosaBalanceAboveMinimum_roiStaysYearlyRate() = runTest(dispatcher) {
        stubBalances(
            pirate = BigDecimal.ZERO,
            cosa = BigDecimal(PremiumConfig.MIN_PREMIUM_AMOUNT_COSANTA * 10)
        )

        assertEquals(EXPECTED_ROI, proposal().cosaRoi)
    }

    @Test
    fun invoke_pirateBalanceAboveMinimum_roiStaysYearlyRate() = runTest(dispatcher) {
        stubBalances(
            pirate = BigDecimal(PremiumConfig.MIN_PREMIUM_AMOUNT_PIRATE * 10),
            cosa = BigDecimal.ZERO
        )

        assertEquals(EXPECTED_ROI, proposal().pirateRoi)
    }

    private companion object {
        const val ACCOUNT_ID = "account-id"
        const val ADDRESS = "0x0000000000000000000000000000000000000001"
        const val YEARLY_RATE = 4.0
        const val EXPECTED_ROI = "400.0%"
    }
}
