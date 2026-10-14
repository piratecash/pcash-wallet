package cash.p.terminal.core.managers

import cash.p.terminal.core.ILocalStorage
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
    private val storage: ILocalStorage
) : PriceChangeIntervalProvider {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var priceChangeInterval: PriceChangeInterval = storage.priceChangeInterval
        private set

    override val interval: PriceChangeInterval
        get() = priceChangeInterval

    override val intervalFlow: StateFlow<PriceChangeInterval>
        get() = priceChangeIntervalFlow

    val displayDiffOptionType: DisplayDiffOptionType
        get() = storage.displayDiffOptionType

    val displayPricePeriod: DisplayPricePeriod
        get() = storage.displayDiffPricePeriod

    val displayDiffOptionTypeFlow: StateFlow<DisplayDiffOptionType>
        get() = storage.displayDiffOptionTypeFlow

    val displayPricePeriodFlow: StateFlow<DisplayPricePeriod>
        get() = storage.displayDiffPricePeriodFlow

    val priceChangeIntervalFlow: StateFlow<PriceChangeInterval>
        get() = storage.priceChangeIntervalFlow

    init {
        coroutineScope.launch {
            storage.priceChangeIntervalFlow.collect {
                priceChangeInterval = it
            }
        }
    }

}
