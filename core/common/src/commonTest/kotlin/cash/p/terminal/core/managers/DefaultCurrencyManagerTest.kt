package cash.p.terminal.core.managers

import io.horizontalsystems.core.BaseCurrencyStorage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultCurrencyManagerTest {

    private class FakeStorage(override var baseCurrencyCode: String? = null) : BaseCurrencyStorage

    @Test
    fun init_noStoredCode_baseCurrencyIsUsd() {
        assertEquals("USD", DefaultCurrencyManager(FakeStorage()).baseCurrency.code)
    }

    @Test
    fun init_unknownStoredCode_baseCurrencyIsUsd() {
        assertEquals("USD", DefaultCurrencyManager(FakeStorage("XXX")).baseCurrency.code)
    }

    @Test
    fun init_knownStoredCode_baseCurrencyIsThatCurrency() {
        assertEquals("EUR", DefaultCurrencyManager(FakeStorage("EUR")).baseCurrency.code)
    }

    @Test
    fun setBaseCurrencyCode_knownCode_writesCodeToStorage() {
        val storage = FakeStorage()

        DefaultCurrencyManager(storage).setBaseCurrencyCode("JPY")

        assertEquals("JPY", storage.baseCurrencyCode)
    }

    @Test
    fun setBaseCurrencyCode_unknownCode_fallsBackToUsdAndStoresIt() {
        val storage = FakeStorage("EUR")
        val manager = DefaultCurrencyManager(storage)

        manager.setBaseCurrencyCode("XXX")

        assertEquals("USD", manager.baseCurrency.code)
        assertEquals("USD", storage.baseCurrencyCode)
    }

    @Test
    fun setBaseCurrencyCode_signalsRxSubscribersAndFlow() = runBlocking {
        val manager = DefaultCurrencyManager(FakeStorage())
        var rxSignals = 0
        manager.baseCurrencyUpdatedSignal.subscribe { rxSignals++ }
        val flowUpdate = async(start = CoroutineStart.UNDISPATCHED) {
            manager.baseCurrencyUpdatedFlow.first()
        }

        manager.setBaseCurrencyCode("GBP")

        withTimeout(5_000) { flowUpdate.await() }
        assertEquals(1, rxSignals)
    }
}
