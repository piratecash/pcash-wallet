package cash.p.terminal.modules.coin.analytics

import cash.p.terminal.wallet.models.Analytics.TechnicalAdvice
import cash.p.terminal.wallet.models.Analytics.TechnicalAdvice.Advice
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.Translator
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.jetbrains.compose.resources.StringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class TechnicalAdviceViewItemFactoryTest {

    private val numberFormatter = mockk<IAppNumberFormatter>()
    private val factory = TechnicalAdviceViewItemFactory(numberFormatter)

    @Before
    fun setUp() {
        mockkObject(Translator)
        every { Translator.getString(any(), *anyVararg()) } answers {
            val key = firstArg<StringResource>().key
            val args = secondArg<Array<Any>>()
            if (args.isEmpty()) key else "$key(${args.joinToString(",")})"
        }
    }

    @After
    fun tearDown() {
        unmockkObject(Translator)
    }

    @Test
    fun advice_oversoldWithRsi_combinesMainTextIndicatorsAndDirection() {
        every { numberFormatter.format(BigDecimal(25), 0, 1, "", "%") } returns "25%"

        val text = factory.advice(technicalAdvice(advice = Advice.Oversold, rsi = BigDecimal(25)))

        assertEquals(
            "TechnicalAdvice_OverMain " +
                "TechnicalAdvice_OverIndicators(TechnicalAdvice_OverSold) " +
                "TechnicalAdvice_OverRsi(25%,TechnicalAdvice_OverSold) " +
                "TechnicalAdvice_OverAdvice(TechnicalAdvice_Down)",
            text
        )
    }

    @Test
    fun advice_strongSellWithoutRsi_usesOverboughtIndicatorsAndUpDirection() {
        val text = factory.advice(technicalAdvice(advice = Advice.StrongSell))

        assertEquals(
            "TechnicalAdvice_StrongIndicators(TechnicalAdvice_OverBought)  " +
                "TechnicalAdvice_StrongAdvice(TechnicalAdvice_Up)",
            text
        )
    }

    @Test
    fun advice_priceAboveEma_appendsTrendBlockWithGrowth() {
        every { numberFormatter.format(BigDecimal(8), 0, 4, "", "") } returns "8"

        val text = factory.advice(
            technicalAdvice(advice = Advice.Neutral, price = BigDecimal(10), ema = BigDecimal(8))
        )

        assertEquals(
            "\n\nTechnicalAdvice_OtherTitle\n\n" +
                "TechnicalAdvice_EmaAdvice(TechnicalAdvice_EmaAbove,8,TechnicalAdvice_EmaGrowth)",
            text.substringAfter("TechnicalAdvice_NeutralAdvice")
        )
    }

    @Test
    fun advice_negativeMacd_formatsValueWithMinusPrefixAndDownAction() {
        val macd = BigDecimal("-0.5")
        every { numberFormatter.format(macd, 0, 4, "-", "") } returns "-0.5"

        val text = factory.advice(technicalAdvice(price = BigDecimal.ONE, macd = macd))

        verify { numberFormatter.format(macd, 0, 4, "-", "") }
        assertEquals(
            "TechnicalAdvice_OtherTitle\n\n" +
                "TechnicalAdvice_MacdAdvice(TechnicalAdvice_MacdNegative,-0.5,TechnicalAdvice_Down)",
            text.substringAfter("TechnicalAdvice_NeutralAdvice\n\n")
        )
    }

    @Test
    fun advice_noPrice_hasNoTrendBlock() {
        val text = factory.advice(technicalAdvice(ema = BigDecimal(8), macd = BigDecimal.ONE))

        assertEquals(
            "TechnicalAdvice_NeutralIndicators(TechnicalAdvice_OverBought)  TechnicalAdvice_NeutralAdvice",
            text
        )
    }

    private fun technicalAdvice(
        advice: Advice? = null,
        rsi: BigDecimal? = null,
        price: BigDecimal? = null,
        ema: BigDecimal? = null,
        macd: BigDecimal? = null,
    ) = TechnicalAdvice(
        ema = ema,
        rsi = rsi,
        macd = macd,
        lower = null,
        price = price,
        upper = null,
        middle = null,
        timestamp = null,
        advice = advice,
        signalTimestamp = null,
    )
}
