package io.horizontalsystems.chartview.chart

import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

class TestDispatcherProvider(
    dispatcher: CoroutineDispatcher,
    override val applicationScope: CoroutineScope,
) : DispatcherProvider {
    override val io = dispatcher
    override val default = dispatcher
    override val main = dispatcher
}
