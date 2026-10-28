package cash.p.terminal.modules.fee

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.navigation.HSNavigation
import io.horizontalsystems.core.entities.CurrencyValue
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.amount.AmountInputType
import java.math.BigDecimal
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Send_Fee
import cash.p.terminal.resources.Send_Fee_Info
import cash.p.terminal.core.getKoinInstance
import io.horizontalsystems.core.IAppNumberFormatter

@Composable
fun HSFeeRaw(
    title: String = stringResource(Res.string.Send_Fee),
    info: String = stringResource(Res.string.Send_Fee_Info),
    coinCode: String,
    coinDecimal: Int,
    fee: BigDecimal?,
    amountInputType: AmountInputType,
    rate: CurrencyValue?,
    navigation: HSNavigation,
    viewState: ViewState? = null
) {

    var formatted by remember { mutableStateOf<FeeItem?>(null) }

    LaunchedEffect(fee, amountInputType, rate) {
        formatted = getFormatted(fee, rate, coinCode, coinDecimal, amountInputType)
    }

    FeeCell(
        title = title,
        info = info,
        value = formatted,
        viewState = viewState,
    )
}

private fun getFormatted(
    fee: BigDecimal?,
    rate: CurrencyValue?,
    coinCode: String,
    coinDecimal: Int,
    amountInputType: AmountInputType
): FeeItem? {

    if (fee == null) return null

    val coinAmount = getKoinInstance<IAppNumberFormatter>().formatCoinFull(fee, coinCode, coinDecimal)
    val currencyAmount = rate?.let {
        it.copy(value = fee.times(it.value)).getFormattedFull()
    }

    return if (amountInputType == AmountInputType.CURRENCY && currencyAmount != null) {
        FeeItem(currencyAmount, coinAmount)
    } else {
        FeeItem(coinAmount, currencyAmount)
    }
}
