package cash.p.terminal.modules.amount

import cash.p.terminal.modules.xrate.XRateService
import io.horizontalsystems.core.CurrencyManager
import org.koin.core.scope.Scope

internal fun Scope.amountInputModeViewModel(coinUid: String): AmountInputModeViewModel {

    val xRateService = XRateService(get(), get<CurrencyManager>().baseCurrency)

    return AmountInputModeViewModel(get(), xRateService, coinUid)
}
