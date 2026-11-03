package cash.p.terminal.modules.send

import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.entities.CoinValue
import cash.p.terminal.resources.Error_InsufficientBalanceForFee
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Send_Error_FetchFeeRateFailed
import cash.p.terminal.resources.Send_Error_FetchFeeRateFailed_Description
import cash.p.terminal.resources.Send_Error_LowFee
import cash.p.terminal.resources.Send_Error_LowFee_Description
import cash.p.terminal.resources.Send_Error_MaximumAmount
import cash.p.terminal.resources.Send_Error_MinimumAmount
import cash.p.terminal.resources.Send_Warning_LowFee
import cash.p.terminal.resources.Send_Warning_LowFee_Description
import cash.p.terminal.resources.Swap_ErrorInsufficientBalance
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.CurrencyValue
import cash.p.terminal.strings.helpers.TranslatableString
import java.math.BigDecimal

private val numberFormatter: IAppNumberFormatter
    get() = getKoinInstance()

object SendModule {

    data class AmountData(val primary: AmountInfo.CoinValueInfo, val secondary: AmountInfo.CurrencyValueInfo?)

    sealed class AmountInfo {
        abstract val approximate: Boolean

        data class CoinValueInfo(val coinValue: CoinValue, override val approximate: Boolean = false) : AmountInfo()
        data class CurrencyValueInfo(val currencyValue: CurrencyValue, override val approximate: Boolean = false) :
            AmountInfo()

        val value: BigDecimal
            get() = when (this) {
                is CoinValueInfo -> coinValue.value
                is CurrencyValueInfo -> currencyValue.value
            }

        val decimal: Int
            get() = when (this) {
                is CoinValueInfo -> coinValue.decimal
                is CurrencyValueInfo -> currencyValue.currency.decimal
            }

        fun getFormatted(): String {
            val prefix = if (approximate) "~" else ""
            return prefix + when (this) {
                is CoinValueInfo -> coinValue.getFormattedFull()
                is CurrencyValueInfo -> numberFormatter.formatFiatFull(
                    currencyValue.value, currencyValue.currency.symbol
                )
            }
        }

        fun getFormattedPlain(): String {
            val prefix = if (approximate) "~" else ""
            return prefix + when (this) {
                is CoinValueInfo -> {
                    numberFormatter.formatCoinFull(value, coinValue.coin.code, coinValue.decimal)
                }

                is CurrencyValueInfo -> {
                    numberFormatter.formatFiatFull(currencyValue.value, currencyValue.currency.symbol)
                }
            }
        }

    }

}



object SendErrorFetchFeeRateFailed : HSCaution(
    TranslatableString.ResString(Res.string.Send_Error_FetchFeeRateFailed),
    Type.Error,
    TranslatableString.ResString(Res.string.Send_Error_FetchFeeRateFailed_Description)
)

object SendWarningRiskOfGettingStuck : HSCaution(
    TranslatableString.ResString(Res.string.Send_Warning_LowFee),
    Type.Warning,
    TranslatableString.ResString(Res.string.Send_Warning_LowFee_Description)
)

object SendErrorLowFee : HSCaution(
    TranslatableString.ResString(Res.string.Send_Error_LowFee),
    Type.Error,
    TranslatableString.ResString(Res.string.Send_Error_LowFee_Description)
)

class SendErrorInsufficientBalance(coinCode: Any, balance: Any) : HSCaution(
    TranslatableString.ResString(Res.string.Swap_ErrorInsufficientBalance),
    Type.Error,
    TranslatableString.ResString(
        Res.string.Error_InsufficientBalanceForFee,
        coinCode,
        balance
    )
)

class SendErrorMinimumSendAmount(amount: Any) : HSCaution(
    TranslatableString.ResString(Res.string.Send_Error_MinimumAmount, amount)
)

class SendErrorMaximumSendAmount(amount: Any) : HSCaution(
    TranslatableString.ResString(Res.string.Send_Error_MaximumAmount, amount)
)
