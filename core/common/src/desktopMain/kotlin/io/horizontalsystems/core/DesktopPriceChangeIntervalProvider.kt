package io.horizontalsystems.core

import cash.p.terminal.modules.settings.appearance.PriceChangeInterval
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DesktopPriceChangeIntervalProvider : PriceChangeIntervalProvider {
    override val interval = PriceChangeInterval.LAST_24H
    override val intervalFlow: StateFlow<PriceChangeInterval> = MutableStateFlow(interval)
}
