package cash.p.terminal.modules.chart

import kotlinx.coroutines.flow.Flow

interface ChartIndicatorStorage {
    fun allFlow(): Flow<List<ChartIndicatorSetting>>
    fun count(): Int
    fun insertAll(settings: List<ChartIndicatorSetting>)
    fun enabled(): List<ChartIndicatorSetting>
    fun get(id: String): ChartIndicatorSetting?
    fun enable(id: String)
    fun disable(id: String)
    fun update(setting: ChartIndicatorSetting)
}
