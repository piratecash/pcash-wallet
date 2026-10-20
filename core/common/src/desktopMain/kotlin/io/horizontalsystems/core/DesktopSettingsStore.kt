package io.horizontalsystems.core

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import cash.p.terminal.modules.settings.security.autolock.AutoLockInterval
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

/** Both contracts are synchronous, so reads and writes block on DataStore (a small local file). */
class DesktopSettingsStore(
    file: File,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : MarketSettingsStore, BaseCurrencyStorage, ILockoutStorage {

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { file }
    )

    private val _marketsTabEnabledFlow = MutableStateFlow(marketsTabEnabledStored())
    override val marketsTabEnabledFlow: StateFlow<Boolean> = _marketsTabEnabledFlow.asStateFlow()

    private val _autoLockIntervalFlow = MutableStateFlow(autoLockIntervalStored())
    val autoLockIntervalFlow: StateFlow<AutoLockInterval> = _autoLockIntervalFlow.asStateFlow()

    override var marketSearchRecentCoinUids: List<String>
        get() = read(RECENT_COIN_UIDS)?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()
        set(value) = write(RECENT_COIN_UIDS, value.joinToString(SEPARATOR))

    override var marketFavoritesSorting: String?
        get() = read(FAVORITES_SORTING)
        set(value) = write(FAVORITES_SORTING, value)

    override var marketFavoritesPeriod: String?
        get() = read(FAVORITES_PERIOD)
        set(value) = write(FAVORITES_PERIOD, value)

    override var marketFavoritesShowSignals: Boolean
        get() = read(FAVORITES_SHOW_SIGNALS) ?: false
        set(value) = write(FAVORITES_SHOW_SIGNALS, value)

    override var chartIndicatorsEnabled: Boolean
        get() = read(CHART_INDICATORS_ENABLED) ?: false
        set(value) = write(CHART_INDICATORS_ENABLED, value)

    override var marketsTabEnabled: Boolean
        get() = marketsTabEnabledStored()
        set(value) {
            write(MARKETS_TAB_ENABLED, value)
            _marketsTabEnabledFlow.value = value
        }

    override var launchPage: String?
        get() = read(LAUNCH_PAGE)
        set(value) = write(LAUNCH_PAGE, value)

    override var currentMarketTab: String?
        get() = read(CURRENT_MARKET_TAB)
        set(value) = write(CURRENT_MARKET_TAB, value)

    override var baseCurrencyCode: String?
        get() = read(BASE_CURRENCY_CODE)
        set(value) = write(BASE_CURRENCY_CODE, value)

    override var failedAttempts: Int?
        get() = read(FAILED_ATTEMPTS)
        set(value) = write(FAILED_ATTEMPTS, value)

    override var lockoutUptime: Long?
        get() = read(LOCKOUT_UPTIME)
        set(value) = write(LOCKOUT_UPTIME, value)

    var languageTag: String?
        get() = read(LANGUAGE_TAG)
        set(value) = write(LANGUAGE_TAG, value)

    var autoLockInterval: AutoLockInterval
        get() = autoLockIntervalStored()
        set(value) {
            write(AUTO_LOCK_INTERVAL, value.raw)
            _autoLockIntervalFlow.value = value
        }

    /** Drops every stored value, the cached ones included. */
    fun clearAll() {
        runBlocking { dataStore.edit { it.clear() } }
        _marketsTabEnabledFlow.value = marketsTabEnabledStored()
        _autoLockIntervalFlow.value = autoLockIntervalStored()
    }

    private fun marketsTabEnabledStored() = read(MARKETS_TAB_ENABLED) ?: true

    private fun autoLockIntervalStored() =
        read(AUTO_LOCK_INTERVAL)?.let(AutoLockInterval::fromRaw) ?: AutoLockInterval.AFTER_1_MIN

    private fun <T> read(key: Preferences.Key<T>): T? =
        runBlocking { dataStore.data.first()[key] }

    private fun <T> write(key: Preferences.Key<T>, value: T?) = runBlocking {
        dataStore.edit { if (value == null) it.remove(key) else it[key] = value }
        Unit
    }

    private companion object {
        const val SEPARATOR = ","

        val RECENT_COIN_UIDS = stringPreferencesKey("marketSearchRecentCoinUids")
        val FAVORITES_SORTING = stringPreferencesKey("marketFavoritesSorting")
        val FAVORITES_PERIOD = stringPreferencesKey("marketFavoritesPeriod")
        val FAVORITES_SHOW_SIGNALS = booleanPreferencesKey("marketFavoritesShowSignals")
        val CHART_INDICATORS_ENABLED = booleanPreferencesKey("chartIndicatorsEnabled")
        val MARKETS_TAB_ENABLED = booleanPreferencesKey("marketsTabEnabled")
        val LAUNCH_PAGE = stringPreferencesKey("launchPage")
        val CURRENT_MARKET_TAB = stringPreferencesKey("currentMarketTab")
        val BASE_CURRENCY_CODE = stringPreferencesKey("baseCurrencyCode")
        val FAILED_ATTEMPTS = intPreferencesKey("failedAttempts")
        val LOCKOUT_UPTIME = longPreferencesKey("lockoutUptime")
        val LANGUAGE_TAG = stringPreferencesKey("languageTag")
        val AUTO_LOCK_INTERVAL = stringPreferencesKey("autoLockInterval")
    }
}
