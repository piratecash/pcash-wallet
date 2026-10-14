package io.horizontalsystems.core

import cash.p.terminal.modules.settings.appearance.PriceChangeInterval
import kotlinx.coroutines.flow.StateFlow

interface PriceChangeIntervalProvider {
    val interval: PriceChangeInterval
    val intervalFlow: StateFlow<PriceChangeInterval>
}
