package cash.p.terminal.modules.coin.analytics

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.TechnicalAdvice_Down
import cash.p.terminal.resources.TechnicalAdvice_EmaAbove
import cash.p.terminal.resources.TechnicalAdvice_EmaAdvice
import cash.p.terminal.resources.TechnicalAdvice_EmaBelow
import cash.p.terminal.resources.TechnicalAdvice_EmaDecrease
import cash.p.terminal.resources.TechnicalAdvice_EmaGrowth
import cash.p.terminal.resources.TechnicalAdvice_Indicators_Buy
import cash.p.terminal.resources.TechnicalAdvice_Indicators_Neutral
import cash.p.terminal.resources.TechnicalAdvice_Indicators_RiskyToTrade
import cash.p.terminal.resources.TechnicalAdvice_Indicators_Sell
import cash.p.terminal.resources.TechnicalAdvice_Indicators_StrongBuy
import cash.p.terminal.resources.TechnicalAdvice_Indicators_StrongSell
import cash.p.terminal.resources.TechnicalAdvice_MacdAdvice
import cash.p.terminal.resources.TechnicalAdvice_MacdNegative
import cash.p.terminal.resources.TechnicalAdvice_MacdPositive
import cash.p.terminal.resources.TechnicalAdvice_NeutralAdvice
import cash.p.terminal.resources.TechnicalAdvice_NeutralIndicators
import cash.p.terminal.resources.TechnicalAdvice_OtherTitle
import cash.p.terminal.resources.TechnicalAdvice_OverAdvice
import cash.p.terminal.resources.TechnicalAdvice_OverBought
import cash.p.terminal.resources.TechnicalAdvice_OverIndicators
import cash.p.terminal.resources.TechnicalAdvice_OverIndicators_SignalDate
import cash.p.terminal.resources.TechnicalAdvice_OverMain
import cash.p.terminal.resources.TechnicalAdvice_OverRsi
import cash.p.terminal.resources.TechnicalAdvice_OverSold
import cash.p.terminal.resources.TechnicalAdvice_StableRsi
import cash.p.terminal.resources.TechnicalAdvice_StrongAdvice
import cash.p.terminal.resources.TechnicalAdvice_StrongIndicators
import cash.p.terminal.resources.TechnicalAdvice_StrongRsi
import cash.p.terminal.resources.TechnicalAdvice_Up
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.helpers.DateHelper
import cash.p.terminal.wallet.models.Analytics.TechnicalAdvice
import cash.p.terminal.wallet.models.Analytics.TechnicalAdvice.Advice
import java.math.BigDecimal
import java.util.Date

class TechnicalAdviceViewItemFactory(private val numberFormatter: IAppNumberFormatter) {

    fun advice(technicalAdvice: TechnicalAdvice): String {
        val mainAdvice = mainAdvice(technicalAdvice)
        val trendAdvice = trendAdvice(technicalAdvice)

        val combinedAdvice = buildString {
            append(mainAdvice)
            if (trendAdvice != null) {
                append("\n\n")
                append(trendAdvice)
            }
        }

        return combinedAdvice
    }

    private fun mainAdvice(technicalAdvice: TechnicalAdvice): String {
        val (overtype, direction, rsiLine) = when (technicalAdvice.advice ?: Advice.Neutral) {
            Advice.Oversold, Advice.StrongBuy, Advice.Buy -> Triple(
                Translator.getString(Res.string.TechnicalAdvice_OverSold),
                Translator.getString(Res.string.TechnicalAdvice_Down),
                "30%"
            )

            Advice.Overbought, Advice.StrongSell, Advice.Sell, Advice.Neutral -> Triple(
                Translator.getString(Res.string.TechnicalAdvice_OverBought),
                Translator.getString(Res.string.TechnicalAdvice_Up),
                "70%"
            )
        }

        val rsiValue = technicalAdvice.rsi?.let {
            numberFormatter.format(it, 0, 1, suffix = "%")
        }

        val signalTimeString = technicalAdvice.signalTimestamp?.let { timestamp ->
            val date = Date(timestamp * 1000)
            DateHelper.shortDate(date)
        }?.let { date ->
            Translator.getString(
                Res.string.TechnicalAdvice_OverIndicators_SignalDate, date
            )
        }

        val adviceString = when (technicalAdvice.advice) {
            Advice.Oversold, Advice.Overbought -> {
                val advice = Translator.getString(Res.string.TechnicalAdvice_OverMain)

                val rsi = rsiValue?.let { value ->
                    Translator.getString(
                        Res.string.TechnicalAdvice_OverRsi, value, overtype
                    )
                } ?: ""

                val indicators = listOf(
                    Translator.getString(
                        Res.string.TechnicalAdvice_OverIndicators, overtype
                    ),
                    rsi
                ).joinToString(" ")

                val combinedIndicators = if (signalTimeString != null) {
                    listOf(
                        signalTimeString,
                        indicators,
                    ).joinToString(" ")
                } else {
                    indicators
                }

                val resultAdvice = Translator.getString(
                    Res.string.TechnicalAdvice_OverAdvice,
                    direction
                )

                listOf(advice, combinedIndicators, resultAdvice).joinToString(" ")
            }

            Advice.StrongBuy, Advice.StrongSell -> {
                val rsi = rsiValue?.let {
                    Translator.getString(
                        Res.string.TechnicalAdvice_StrongRsi,
                        it,
                        overtype
                    )
                } ?: ""

                val indicators = listOf(
                    Translator.getString(
                        Res.string.TechnicalAdvice_StrongIndicators, overtype
                    ),
                    rsi
                ).joinToString(" ")

                val combinedIndicators = if (signalTimeString != null) {
                    listOf(
                        signalTimeString,
                        indicators,
                    ).joinToString(" ")
                } else {
                    indicators
                }

                listOf(
                    combinedIndicators,
                    Translator.getString(
                        Res.string.TechnicalAdvice_StrongAdvice, direction
                    )
                ).joinToString(" ")
            }

            Advice.Buy, Advice.Sell -> {
                val rsi = rsiValue?.let {
                    Translator.getString(
                        Res.string.TechnicalAdvice_StableRsi,
                        it,
                        rsiLine
                    )
                } ?: ""

                val indicators = listOf(
                    Translator.getString(
                        Res.string.TechnicalAdvice_StrongIndicators, overtype
                    ),
                    rsi
                ).joinToString(" ")

                val combinedIndicators = if (signalTimeString != null) {
                    listOf(
                        signalTimeString,
                        indicators
                    ).joinToString(" ")
                } else {
                    indicators
                }

                listOf(
                    combinedIndicators,
                    Translator.getString(
                        Res.string.TechnicalAdvice_StrongAdvice, direction
                    )
                ).joinToString(" ")
            }

            Advice.Neutral, null -> {
                val rsi =
                    rsiValue?.let {
                        Translator.getString(
                            Res.string.TechnicalAdvice_StableRsi, it
                        )
                    }
                        ?: ""

                val indicators = listOf(
                    Translator.getString(
                        Res.string.TechnicalAdvice_NeutralIndicators, overtype
                    ),
                    rsi
                ).joinToString(" ")

                val combinedIndicators = if (signalTimeString != null) {
                    listOf(
                        signalTimeString,
                        indicators
                    ).joinToString(" ")
                } else {
                    indicators
                }

                listOf(
                    combinedIndicators,
                    Translator.getString(Res.string.TechnicalAdvice_NeutralAdvice)
                ).joinToString(" ")
            }
        }

        return adviceString
    }

    private fun trendAdvice(technicalAdvice: TechnicalAdvice): String? {
        val price = technicalAdvice.price ?: return null

        val emaAdvice = technicalAdvice.ema?.let { ema ->
            val direction = Translator.getString(
                if (price >= ema) Res.string.TechnicalAdvice_EmaAbove else Res.string.TechnicalAdvice_EmaBelow
            )
            val action = Translator.getString(
                if (price >= ema) Res.string.TechnicalAdvice_EmaGrowth else Res.string.TechnicalAdvice_EmaDecrease
            )
            val emaValue = numberFormatter.format(ema, 0, 4)
            Translator.getString(
                Res.string.TechnicalAdvice_EmaAdvice, direction, emaValue, action
            )
        }

        val macdAdvice = technicalAdvice.macd?.let { macd ->
            val isPositive = macd >= BigDecimal.ZERO
            val directionRes = if (isPositive) {
                Res.string.TechnicalAdvice_MacdPositive
            } else {
                Res.string.TechnicalAdvice_MacdNegative
            }
            val actionRes = if (isPositive) {
                Res.string.TechnicalAdvice_Up
            } else {
                Res.string.TechnicalAdvice_Down
            }
            val direction = Translator.getString(directionRes)
            val action = Translator.getString(actionRes)
            val sign = if (isPositive) "" else "-"
            val macdValue = numberFormatter.format(macd, 0, 4, prefix = sign)
            Translator.getString(
                Res.string.TechnicalAdvice_MacdAdvice, direction, macdValue, action
            )
        }

        val advices = listOf(emaAdvice, macdAdvice).filterNotNull()
        if (advices.isEmpty()) {
            return null
        }

        return buildString {
            append(Translator.getString(Res.string.TechnicalAdvice_OtherTitle))
            advices.forEach {
                append("\n\n")
                append(it)
            }
        }
    }
}

val Advice.title: String
    get() {
        return when (this) {
            Advice.Oversold -> Translator.getString(
                Res.string.TechnicalAdvice_Indicators_RiskyToTrade
            )

            Advice.StrongSell -> Translator.getString(
                Res.string.TechnicalAdvice_Indicators_StrongSell
            )

            Advice.Sell -> Translator.getString(
                Res.string.TechnicalAdvice_Indicators_Sell
            )

            Advice.Neutral -> Translator.getString(
                Res.string.TechnicalAdvice_Indicators_Neutral
            )

            Advice.Buy -> Translator.getString(Res.string.TechnicalAdvice_Indicators_Buy)
            Advice.StrongBuy -> Translator.getString(
                Res.string.TechnicalAdvice_Indicators_StrongBuy
            )

            Advice.Overbought -> Translator.getString(
                Res.string.TechnicalAdvice_Indicators_RiskyToTrade
            )
        }
    }

val Advice.sliderIndex: Int
    get() {
        return when (this) {
            Advice.Oversold -> 0
            Advice.StrongSell -> 3
            Advice.Sell -> 2
            Advice.Neutral -> 1
            Advice.Buy -> 2
            Advice.StrongBuy -> 3
            Advice.Overbought -> 0
        }
    }
