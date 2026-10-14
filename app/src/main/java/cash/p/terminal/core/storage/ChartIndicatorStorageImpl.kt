package cash.p.terminal.core.storage

import cash.p.terminal.modules.chart.ChartIndicatorSetting
import cash.p.terminal.modules.chart.ChartIndicatorStorage
import kotlinx.coroutines.flow.Flow

class ChartIndicatorStorageImpl(appDatabase: AppDatabase) : ChartIndicatorStorage {
    private val dao = appDatabase.chartIndicatorSettingsDao()

    override fun allFlow(): Flow<List<ChartIndicatorSetting>> = dao.getAll()
    override fun count(): Int = dao.getCount()
    override fun insertAll(settings: List<ChartIndicatorSetting>) = dao.insertAll(settings)
    override fun enabled(): List<ChartIndicatorSetting> = dao.getEnabled()
    override fun get(id: String): ChartIndicatorSetting? = dao.get(id)
    override fun enable(id: String) = dao.enableIndicator(id)
    override fun disable(id: String) = dao.disableIndicator(id)
    override fun update(setting: ChartIndicatorSetting) = dao.update(setting)
}
