package cash.p.terminal.modules.chart

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.modules.chart.ChartIndicatorSetting.IndicatorType
import io.horizontalsystems.chartview.models.ChartIndicator
import io.horizontalsystems.core.MarketSettingsStore
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChartIndicatorManagerTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(dispatcher, TestScope(dispatcher))
    private val settings = mockk<MarketSettingsStore>(relaxed = true)

    private fun manager(storage: ChartIndicatorStorage = FakeStorage()) =
        ChartIndicatorManager(storage, settings, dispatcherProvider)

    private fun series(vararg values: Float) =
        LinkedHashMap(values.mapIndexed { index, value -> (index + 1).toLong() to value }.toMap())

    private fun assertLine(expected: Map<Long, Float>, actual: Map<Long, Float>) {
        assertEquals(expected.keys.toList(), actual.keys.toList())
        expected.forEach { (timestamp, value) -> assertEquals(value, actual.getValue(timestamp), 1e-3f) }
    }

    private fun maSetting(id: String, period: Int, maType: String, enabled: Boolean = true) = ChartIndicatorSetting(
        id = id,
        type = IndicatorType.MA,
        extraData = mapOf("period" to period.toString(), "maType" to maType, "color" to "42"),
        defaultData = emptyMap(),
        enabled = enabled,
    )

    @Test
    fun init_emptyStorage_insertsDefaultIndicators() {
        val storage = FakeStorage()

        manager(storage)

        assertEquals(listOf("ma1", "ma2", "ma3", "rsi", "macd"), storage.items.value.map { it.id })
        assertEquals(listOf("ma1", "ma2", "ma3", "rsi"), storage.enabled().map { it.id })
    }

    @Test
    fun init_storedIndicators_keepsThemUntouched() {
        val stored = listOf(maSetting("custom", 5, "SMA"))
        val storage = FakeStorage(stored)

        manager(storage)

        assertEquals(stored, storage.items.value)
    }

    @Test
    fun calculateSMA_period3_averagesEachWindow() {
        val result = ChartIndicatorManager.calculateSMA(series(1f, 2f, 3f, 4f, 5f), 3)

        assertLine(mapOf(3L to 2f, 4L to 3f, 5L to 4f), result)
    }

    @Test
    fun calculateEMA_period3_seedsWithSmaThenSmooths() {
        val result = ChartIndicatorManager.calculateEMA(series(1f, 2f, 3f, 4f, 5f), 3)

        assertLine(mapOf(3L to 2f, 4L to 3f, 5L to 4f), result)
    }

    @Test
    fun calculateWMA_period3_weightsRecentPointsHigher() {
        val result = ChartIndicatorManager.calculateWMA(series(1f, 2f, 3f, 4f, 5f), 3)

        assertLine(mapOf(3L to 14f / 6, 4L to 20f / 6, 5L to 26f / 6), result)
    }

    @Test
    fun calculateRsi_period2_seedsWithAverageThenSmooths() {
        val result = ChartIndicatorManager.calculateRsi(
            series(10f, 11f, 10f, 12f, 11f),
            ChartIndicatorDataRsi(period = 2),
            startTimestamp = 0,
        )

        assertLine(mapOf(3L to 50f, 4L to 100f - 100f / 6, 5L to 50f), result.points)
    }

    @Test
    fun calculateRsi_startTimestampInsideSeries_dropsEarlierPoints() {
        val result = ChartIndicatorManager.calculateRsi(
            series(10f, 11f, 10f, 12f, 11f),
            ChartIndicatorDataRsi(period = 2),
            startTimestamp = 4,
        )

        assertEquals(listOf(4L, 5L), result.points.keys.toList())
    }

    @Test
    fun calculateMacd_linearSeries_hasConstantMacdAndZeroHistogram() {
        val result = ChartIndicatorManager.calculateMacd(
            series(1f, 2f, 3f, 4f, 5f, 6f),
            ChartIndicatorDataMacd(fast = 2, slow = 3, signal = 2),
            startTimestamp = 0,
        )

        assertLine(mapOf(3L to 0.5f, 4L to 0.5f, 5L to 0.5f, 6L to 0.5f), result.macdLine)
        assertLine(mapOf(4L to 0.5f, 5L to 0.5f, 6L to 0.5f), result.signalLine)
        assertLine(mapOf(4L to 0f, 5L to 0f, 6L to 0f), result.histogram)
    }

    @Test
    fun calculateIndicators_enabledMovingAverage_returnsLineKeyedBySettingId() {
        val storage = FakeStorage(listOf(maSetting("ma-a", 3, "SMA")))

        val result = manager(storage).calculateIndicators(series(1f, 2f, 3f, 4f, 5f), startTimestamp = 4)

        val indicator = result.getValue("ma-a") as ChartIndicator.MovingAverage
        assertLine(mapOf(4L to 3f, 5L to 4f), indicator.line)
        assertEquals(42L, indicator.color)
    }

    @Test
    fun calculateIndicators_fewerPointsThanPeriod_skipsIndicator() {
        val storage = FakeStorage(listOf(maSetting("ma-a", 10, "SMA")))

        val result = manager(storage).calculateIndicators(series(1f, 2f, 3f), startTimestamp = 0)

        assertTrue(result.isEmpty())
    }

    @Test
    fun calculateIndicators_disabledIndicator_isNotCalculated() {
        val storage = FakeStorage(listOf(maSetting("ma-a", 3, "SMA", enabled = false)))

        val result = manager(storage).calculateIndicators(series(1f, 2f, 3f, 4f), startTimestamp = 0)

        assertTrue(result.isEmpty())
    }

    @Test
    fun getPointsCount_defaultIndicators_returnsLongestEnabledPeriod() {
        assertEquals(50, manager().getPointsCount())
    }

    @Test
    fun getPointsCount_afterDisablingLongest_returnsNextLongest() {
        val manager = manager()

        manager.disableIndicator("ma3")

        assertEquals(25, manager.getPointsCount())
    }

    @Test
    fun enable_emitsEnabledAndPersistsFlag() = runTest(dispatcher) {
        val manager = manager()
        val emitted = mutableListOf<Boolean>()
        val job = launch { manager.isEnabledFlow.toList(emitted) }

        manager.enable()
        manager.disable()

        verify { settings.chartIndicatorsEnabled = true }
        verify { settings.chartIndicatorsEnabled = false }
        assertEquals(listOf(true, false), emitted)
        assertFalse(job.isCompleted)
        job.cancel()
    }

    private class FakeStorage(initial: List<ChartIndicatorSetting> = emptyList()) : ChartIndicatorStorage {
        val items = MutableStateFlow(initial)

        override fun allFlow(): Flow<List<ChartIndicatorSetting>> = items
        override fun count() = items.value.size
        override fun insertAll(settings: List<ChartIndicatorSetting>) {
            items.value = settings
        }

        override fun enabled() = items.value.filter { it.enabled }
        override fun get(id: String) = items.value.firstOrNull { it.id == id }
        override fun enable(id: String) = setEnabled(id, true)
        override fun disable(id: String) = setEnabled(id, false)
        override fun update(setting: ChartIndicatorSetting) {
            items.value = items.value.map { if (it.id == setting.id) setting else it }
        }

        private fun setEnabled(id: String, enabled: Boolean) {
            items.value = items.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
        }
    }
}
