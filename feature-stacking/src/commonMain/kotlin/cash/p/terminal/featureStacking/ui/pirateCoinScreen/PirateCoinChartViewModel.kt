package cash.p.terminal.featureStacking.ui.pirateCoinScreen

import cash.p.terminal.featureStacking.ui.PirateInvestmentChartService
import cash.p.terminal.featureStacking.ui.stackingCoinScreen.StackingCoinChartViewModel
import cash.p.terminal.featureStacking.ui.staking.StackingType
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IAppNumberFormatter

internal class PirateCoinChartViewModel(
    service: PirateInvestmentChartService,
    numberFormatter: IAppNumberFormatter,
    dispatcherProvider: DispatcherProvider,
) : StackingCoinChartViewModel(service, numberFormatter, dispatcherProvider) {
    override val coinCode: String = StackingType.PCASH.value
}
