package cash.p.terminal.featureStacking.ui.cosantaCoinScreen

import cash.p.terminal.featureStacking.ui.PirateInvestmentChartService
import cash.p.terminal.featureStacking.ui.stackingCoinScreen.StackingCoinChartViewModel
import cash.p.terminal.featureStacking.ui.staking.StackingType
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IAppNumberFormatter

internal class CosantaCoinChartViewModel(
    service: PirateInvestmentChartService,
    numberFormatter: IAppNumberFormatter,
    dispatcherProvider: DispatcherProvider,
) : StackingCoinChartViewModel(service, numberFormatter, dispatcherProvider) {
    override val coinCode: String = StackingType.COSANTA.value
}
