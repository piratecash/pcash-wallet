package cash.p.terminal.shared.market

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.wallet.MarketKitWrapper
import io.horizontalsystems.core.AppForegroundState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MarketCatalogSyncerTest {

    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val inForeground = MutableStateFlow(true)
    private val foreground = object : AppForegroundState {
        override val inForeground = this@MarketCatalogSyncerTest.inForeground
    }

    @Test
    fun start_foregroundAtStartAndRegained_syncsTwice() = runTest {
        startSyncer()
        runCurrent()

        inForeground.value = false
        runCurrent()
        inForeground.value = true
        runCurrent()

        coVerify(exactly = 2) { marketKit.sync(false) }
    }

    @Test
    fun start_tonW5NativeTokenMissing_forcesSync() = runTest {
        every { marketKit.isTonW5NativeTokenMissing() } returns true

        startSyncer()
        runCurrent()

        coVerify(exactly = 1) { marketKit.sync(true) }
    }

    @Test
    fun start_inBackground_doesNotSync() = runTest {
        inForeground.value = false

        startSyncer()
        runCurrent()

        coVerify(exactly = 0) { marketKit.sync(any()) }
    }

    @Test
    fun start_syncFails_keepsSyncingOnNextForeground() = runTest {
        coEvery { marketKit.sync(any()) } throws IllegalStateException("offline") andThen Unit
        startSyncer()
        runCurrent()

        inForeground.value = false
        runCurrent()
        inForeground.value = true
        runCurrent()

        coVerify(exactly = 2) { marketKit.sync(false) }
    }

    private fun TestScope.startSyncer() {
        val dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler), backgroundScope)
        MarketCatalogSyncer(marketKit, foreground, dispatchers).start(backgroundScope)
    }
}
