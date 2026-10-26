package cash.p.terminal.core.managers

import cash.p.terminal.core.AppearanceSettingsStore
import cash.p.terminal.core.BalanceSettingsStorage
import cash.p.terminal.modules.displayoptions.DisplayDiffOptionType
import cash.p.terminal.modules.displayoptions.DisplayPricePeriod
import cash.p.terminal.modules.settings.appearance.PriceChangeInterval
import io.horizontalsystems.core.PriceChangeIntervalProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PriceManager(
    private val appearanceStorage: AppearanceSettingsStore,
    private val balanceStorage: BalanceSettingsStorage,
) : PriceChangeIntervalProvider {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var priceChangeInterval: PriceChangeInterval = appearanceStorage.priceChangeInterval
        private set

    override val interval: PriceChangeInterval
        get() = priceChangeInterval

    override val intervalFlow: StateFlow<PriceChangeInterval>
        get() = priceChangeIntervalFlow

    val displayDiffOptionType: DisplayDiffOptionType
        get() = balanceStorage.displayDiffOptionType

    val displayPricePeriod: DisplayPricePeriod
        get() = balanceStorage.displayDiffPricePeriod

    val displayDiffOptionTypeFlow: StateFlow<DisplayDiffOptionType>
        get() = balanceStorage.displayDiffOptionTypeFlow

    val displayPricePeriodFlow: StateFlow<DisplayPricePeriod>
        get() = balanceStorage.displayDiffPricePeriodFlow

    val priceChangeIntervalFlow: StateFlow<PriceChangeInterval>
        get() = appearanceStorage.priceChangeIntervalFlow

    init {
        coroutineScope.launch {
            appearanceStorage.priceChangeIntervalFlow.collect {
                priceChangeInterval = it
            }
        }
    }

}
