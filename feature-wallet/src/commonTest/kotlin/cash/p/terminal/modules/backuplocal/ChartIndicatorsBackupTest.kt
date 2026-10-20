package cash.p.terminal.modules.backuplocal

import cash.p.terminal.modules.backuplocal.fullbackup.toChartIndicatorSettings
import cash.p.terminal.modules.backuplocal.fullbackup.toChartIndicators
import cash.p.terminal.modules.chart.ChartIndicatorSetting
import cash.p.terminal.modules.chart.ChartIndicatorSetting.IndicatorType
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartIndicatorsBackupTest {

    @Test
    fun toChartIndicators_storageOutOfSlotOrder_exportsBySlot() {
        val defaults = ChartIndicatorSetting.defaults().associateBy { it.id }
        val stored = listOf(
            ma(defaults, "ma2", maType = "EMA", period = 30, enabled = false),
            ma(defaults, "ma3", maType = "WMA", period = 100, enabled = true),
            ma(defaults, "ma1", maType = "SMA", period = 7, enabled = true),
            requireNotNull(defaults["rsi"]).copy(extraData = mapOf("period" to "14"), enabled = false),
            requireNotNull(defaults["macd"]).copy(
                extraData = mapOf("fast" to "10", "slow" to "20", "signal" to "5"),
                enabled = true,
            ),
        )

        val exported = stored.toChartIndicators()

        assertEquals(listOf(7, 30, 100), exported.ma.map { it.period })
        assertEquals(
            stored.map { it.slotState() }.sorted(),
            exported.toChartIndicatorSettings().map { it.slotState() }.sorted(),
        )
    }

    private fun ma(
        defaults: Map<String, ChartIndicatorSetting>,
        id: String,
        maType: String,
        period: Int,
        enabled: Boolean,
    ) = requireNotNull(defaults[id]).copy(
        extraData = mapOf("period" to period.toString(), "maType" to maType),
        enabled = enabled,
    )

    private fun ChartIndicatorSetting.slotState(): String = when (type) {
        IndicatorType.MA -> getTypedDataMA().let { "$id ${it.maType} ${it.period} $enabled" }
        IndicatorType.RSI -> "$id ${getTypedDataRsi().period} $enabled"
        IndicatorType.MACD -> getTypedDataMacd().let { "$id ${it.fast} ${it.slow} ${it.signal} $enabled" }
    }
}
