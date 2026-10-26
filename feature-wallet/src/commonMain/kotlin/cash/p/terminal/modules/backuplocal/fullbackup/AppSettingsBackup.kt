package cash.p.terminal.modules.backuplocal.fullbackup

import cash.p.terminal.modules.chart.ChartIndicatorManager
import cash.p.terminal.modules.chart.ChartIndicatorSetting
import cash.p.terminal.modules.contacts.ContactsBackup

/** The app-settings and contacts part of a full backup, which each platform owns differently. */
interface AppSettingsBackup : ContactsBackup {
    /** `null` when this platform writes no settings into a backup. */
    fun currentSettings(cachedKey: ByteArray, passphrase: String): Settings?
    suspend fun restore(settings: Settings, passphrase: String)
    fun customRpcCount(): Int?
}

fun Settings.restoreChartIndicators(
    chartIndicatorManager: ChartIndicatorManager,
    insertAll: (List<ChartIndicatorSetting>) -> Unit,
) {
    if (chartIndicatorsEnabled) {
        chartIndicatorManager.enable()
    } else {
        chartIndicatorManager.disable()
    }

    insertAll(chartIndicators.toChartIndicatorSettings())
}

/** Backed-up indicators laid over the defaults; a backup may carry fewer of each type. */
fun ChartIndicators.toChartIndicatorSettings(): List<ChartIndicatorSetting> {
    val defaultChartSettings = ChartIndicatorSetting.defaults()

    val rsiDefaults =
        defaultChartSettings.filter { it.type == ChartIndicatorSetting.IndicatorType.RSI }
    val rsiChartSettings = rsiDefaults.sortedBy { it.index }.take(rsi.size).map { default ->
        val imported = rsi[default.index - 1]
        default.copy(
            extraData = mapOf(
                "period" to imported.period.toString()
            ),
            enabled = imported.enabled
        )
    }

    val maDefaults =
        defaultChartSettings.filter { it.type == ChartIndicatorSetting.IndicatorType.MA }
    val maChartSettings = maDefaults.sortedBy { it.index }.take(ma.size).map { default ->
        val imported = ma[default.index - 1]
        default.copy(
            extraData = mapOf(
                "period" to imported.period.toString(),
                "maType" to imported.type.uppercase(),
            ),
            enabled = imported.enabled
        )
    }

    val macdDefaults =
        defaultChartSettings.filter { it.type == ChartIndicatorSetting.IndicatorType.MACD }
    val macdChartSettings = macdDefaults.sortedBy { it.index }.take(macd.size).map { default ->
        val imported = macd[default.index - 1]
        default.copy(
            extraData = mapOf(
                "fast" to imported.fast.toString(),
                "slow" to imported.slow.toString(),
                "signal" to imported.signal.toString(),
            ),
            enabled = imported.enabled
        )
    }

    return rsiChartSettings + maChartSettings + macdChartSettings
}

/** Slot order matters: [toChartIndicatorSettings] restores by position, and storage returns rows unordered. */
fun List<ChartIndicatorSetting>.toChartIndicators(): ChartIndicators {
    val bySlot = sortedBy { it.index }
    val rsi = bySlot.filter { it.type == ChartIndicatorSetting.IndicatorType.RSI }
        .map { setting -> RsiBackup(period = setting.getTypedDataRsi().period, enabled = setting.enabled) }
    val ma = bySlot.filter { it.type == ChartIndicatorSetting.IndicatorType.MA }
        .map { setting ->
            val data = setting.getTypedDataMA()
            MaBackup(type = data.maType.lowercase(), period = data.period, enabled = setting.enabled)
        }
    val macd = bySlot.filter { it.type == ChartIndicatorSetting.IndicatorType.MACD }
        .map { setting ->
            val data = setting.getTypedDataMacd()
            MacdBackup(fast = data.fast, slow = data.slow, signal = data.signal, enabled = setting.enabled)
        }
    return ChartIndicators(rsi, ma, macd)
}
