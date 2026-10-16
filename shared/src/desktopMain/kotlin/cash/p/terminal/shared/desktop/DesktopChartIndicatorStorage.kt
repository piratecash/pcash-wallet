package cash.p.terminal.shared.desktop

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import cash.p.terminal.modules.chart.ChartIndicatorSetting
import cash.p.terminal.modules.chart.ChartIndicatorStorage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.File

/** The contract is synchronous, so calls block on DataStore (one small local file). */
class DesktopChartIndicatorStorage(
    file: File,
    private val gson: Gson,
    dispatcherProvider: DispatcherProvider,
) : ChartIndicatorStorage {

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(SupervisorJob() + dispatcherProvider.io),
        produceFile = { file },
    )

    override fun allFlow(): Flow<List<ChartIndicatorSetting>> = dataStore.data.map { it.settings() }

    override fun count(): Int = all().size

    override fun insertAll(settings: List<ChartIndicatorSetting>) =
        edit { current -> current.filter { old -> settings.none { it.id == old.id } } + settings }

    override fun enabled(): List<ChartIndicatorSetting> = all().filter { it.enabled }

    override fun get(id: String): ChartIndicatorSetting? = all().firstOrNull { it.id == id }

    override fun enable(id: String) = setEnabled(id, true)

    override fun disable(id: String) = setEnabled(id, false)

    override fun update(setting: ChartIndicatorSetting) =
        edit { current -> current.map { if (it.id == setting.id) setting else it } }

    private fun setEnabled(id: String, enabled: Boolean) =
        edit { current -> current.map { if (it.id == id) it.copy(enabled = enabled) else it } }

    internal fun all(): List<ChartIndicatorSetting> = runBlocking { dataStore.data.first().settings() }

    private fun edit(transform: (List<ChartIndicatorSetting>) -> List<ChartIndicatorSetting>) {
        runBlocking {
            dataStore.edit { it[KEY] = gson.toJson(transform(it.settings())) }
        }
    }

    private fun Preferences.settings(): List<ChartIndicatorSetting> =
        this[KEY]?.let { gson.fromJson<List<ChartIndicatorSetting>>(it, LIST_TYPE) }.orEmpty()

    private companion object {
        val KEY = stringPreferencesKey("chartIndicators")
        val LIST_TYPE = object : TypeToken<List<ChartIndicatorSetting>>() {}.type
    }
}
