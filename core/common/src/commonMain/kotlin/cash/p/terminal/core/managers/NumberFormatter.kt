package cash.p.terminal.core.managers

import cash.p.terminal.resources.CoinPage_MarketCap_Billion
import cash.p.terminal.resources.CoinPage_MarketCap_Million
import cash.p.terminal.resources.CoinPage_MarketCap_Quadrillion
import cash.p.terminal.resources.CoinPage_MarketCap_Thousand
import cash.p.terminal.resources.CoinPage_MarketCap_Trillion
import cash.p.terminal.resources.LargeNumberFormat
import cash.p.terminal.resources.Res
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.ILanguageManager
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.entities.Value
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class NumberFormatter(
    private val languageManager: ILanguageManager
) : IAppNumberFormatter {

    private var formatters = ConcurrentHashMap<String, NumberFormat>()
    private val numberRounding = NumberRounding()

    override fun format(
        value: Number,
        minimumFractionDigits: Int,
        maximumFractionDigits: Int,
        prefix: String,
        suffix: String
    ): String {
        val bigDecimalValue = when (value) {
            is Double -> value.toBigDecimal()
            is Float -> value.toBigDecimal()
            is BigDecimal -> value
            else -> throw UnsupportedOperationException()
        }

        val formatter = getFormatter(
            languageManager.currentLocale,
            minimumFractionDigits,
            maximumFractionDigits
        )

        val mostLowValue = BigDecimal(BigInteger.ONE, maximumFractionDigits)

        return if (bigDecimalValue > BigDecimal.ZERO && bigDecimalValue < mostLowValue) {
            "< " + prefix + formatter.format(mostLowValue) + suffix
        } else {
            prefix + formatter.format(bigDecimalValue) + suffix
        }.fixDecimalSeparator(languageManager.currentLocale)
    }

    override fun formatCoinNotRounded(value: BigDecimal, code: String?, coinDecimals: Int): String {
        val rounded = BigDecimalRounded.Regular(value)
        return formatRounded(rounded = rounded, prefix = null, suffix = code?.let { " $it" })
    }

    override fun formatCoinFull(value: BigDecimal, code: String?, coinDecimals: Int): String {
        val rounded = numberRounding.getRoundedCoinFull(value, coinDecimals)
        return formatRounded(rounded = rounded, prefix = null, suffix = code?.let { " $it" })
    }

    override fun formatCoinShort(value: BigDecimal, code: String?, coinDecimals: Int): String {
        val rounded = numberRounding.getRoundedCoinShort(value, coinDecimals)
        return formatRounded(rounded = rounded, prefix = null, suffix = code?.let { " $it" })
    }

    override fun formatNumberShort(value: BigDecimal, maximumFractionDigits: Int): String {
        val rounded = numberRounding.getRoundedShort(value, maximumFractionDigits)
        return formatRounded(rounded = rounded, prefix = null, suffix = null)
    }

    override fun formatFiatFull(value: BigDecimal, symbol: String): String {
        val rounded = numberRounding.getRoundedCurrencyFull(value)
        return formatRounded(rounded = rounded, prefix = symbol, suffix = null)
    }

    override fun formatFiatShort(
        value: BigDecimal,
        symbol: String,
        currencyDecimals: Int
    ): String {
        val rounded = numberRounding.getRoundedCurrencyShort(value, currencyDecimals)
        return formatRounded(rounded = rounded, prefix = symbol, suffix = null)
    }

    private fun formatRounded(
        rounded: BigDecimalRounded,
        prefix: String?,
        suffix: String?
    ): String {
        val formatter = getFormatter(languageManager.currentLocale, 0, Int.MAX_VALUE)
        var formattedNumber = formatter.format(rounded.value)

        prefix?.let {
            formattedNumber = "$prefix$formattedNumber"
        }

        if (rounded is BigDecimalRounded.LessThen) {
            formattedNumber = "< $formattedNumber"
        }

        when ((rounded as? BigDecimalRounded.Large)?.name) {
            LargeNumberName.Thousand -> Res.string.CoinPage_MarketCap_Thousand
            LargeNumberName.Million -> Res.string.CoinPage_MarketCap_Million
            LargeNumberName.Billion -> Res.string.CoinPage_MarketCap_Billion
            LargeNumberName.Trillion -> Res.string.CoinPage_MarketCap_Trillion
            LargeNumberName.Quadrillion -> Res.string.CoinPage_MarketCap_Quadrillion
            else -> null
        }?.let {
            formattedNumber =  Translator.getString(
                Res.string.LargeNumberFormat,
                formattedNumber,
                Translator.getString(it)
            )
        }

        suffix?.let {
            formattedNumber = "$formattedNumber$suffix"
        }

        return formattedNumber.fixDecimalSeparator(languageManager.currentLocale)
    }

    private fun getFormatter(
        locale: Locale,
        minimumFractionDigits: Int,
        maximumFractionDigits: Int
    ): NumberFormat {
        val formatterId = "${locale.language}-$minimumFractionDigits-$maximumFractionDigits"

        return formatters.getOrPut(formatterId) {
            NumberFormat.getInstance(locale).apply {
                this.roundingMode = RoundingMode.FLOOR

                this.minimumFractionDigits = minimumFractionDigits
                this.maximumFractionDigits = maximumFractionDigits
            }
        }
    }

    override fun formatValueAsDiff(value: Value): String =
        when (value) {
            is Value.Currency -> {
                val currencyValue = value.currencyValue
                val formatted = formatFiatShort(
                    currencyValue.value.abs(),
                    currencyValue.currency.symbol,
                    currencyValue.currency.decimal
                )
                sign(value.currencyValue.value) + formatted
            }

            is Value.Percent -> {
                format(value.percent.abs(), 0, 2, sign(value.percent), "%")
            }
        }

    private fun sign(value: BigDecimal): String {
        return when (value.signum()) {
            1 -> "+"
            -1 -> "-"
            else -> ""
        }
    }

    /***
     * 12 310,123 -> 12 310.123 for "uk" and "ru" locales
     */
    private fun String.fixDecimalSeparator(locale: Locale): String {
        return if(locale.language == "uk" || locale.language == "ru") {
            this.replace(',', '.')
        } else {
            this
        }
    }
}
