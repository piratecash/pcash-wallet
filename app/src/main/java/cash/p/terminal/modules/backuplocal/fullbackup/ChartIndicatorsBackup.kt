package cash.p.terminal.modules.backuplocal.fullbackup

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.modules.chart.ChartIndicatorManager
import cash.p.terminal.modules.chart.ChartIndicatorSettingsDao

/** The chart-indicator part of the Android settings backup. */
class ChartIndicatorsBackup(
    private val localStorage: ILocalStorage,
    private val chartIndicatorManager: ChartIndicatorManager,
    private val chartIndicatorSettingsDao: ChartIndicatorSettingsDao,
) {
    val enabled: Boolean get() = localStorage.chartIndicatorsEnabled

    fun indicators(): ChartIndicators = chartIndicatorSettingsDao.getAllBlocking().toChartIndicators()

    fun restore(settings: Settings) {
        settings.restoreChartIndicators(chartIndicatorManager, chartIndicatorSettingsDao::insertAll)
    }
}
