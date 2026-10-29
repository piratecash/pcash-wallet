package cash.p.terminal.modules.settings.appcache

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.modules.settings.appcache.AppCacheModule.CacheItemViewItem
import cash.p.terminal.modules.settings.appcache.AppCacheModule.CacheType
import cash.p.terminal.modules.settings.appcache.AppCacheModule.UiState
import cash.p.terminal.wallet.storage.MarketDatabase
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppCacheViewModel(
    private val platformCacheCleaner: PlatformCacheCleaner,
    private val dispatcherProvider: DispatcherProvider,
    private val marketDatabase: MarketDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        UiState(
            cacheItems = CacheType.entries.map { type ->
                CacheItemViewItem(
                    type = type,
                    title = type.title,
                    description = type.description
                )
            }
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun clearAllCaches() {
        viewModelScope.launch(dispatcherProvider.io) {
            _uiState.value = _uiState.value.copy(isClearing = true)

            platformCacheCleaner.clearHttp()
            platformCacheCleaner.clearImages()
            platformCacheCleaner.clearDatabase()
            clearMarketPricesCache()

            delay(300)

            _uiState.value = _uiState.value.copy(isClearing = false)
        }
    }

    fun clearCache(type: CacheType) {
        viewModelScope.launch(dispatcherProvider.io) {
            _uiState.value = _uiState.value.copy(clearingItemId = type)

            when (type) {
                CacheType.HTTP -> platformCacheCleaner.clearHttp()
                CacheType.IMAGE -> platformCacheCleaner.clearImages()
                CacheType.DATABASE -> platformCacheCleaner.clearDatabase()
                CacheType.MARKET_PRICES -> clearMarketPricesCache()
            }

            delay(300)
            _uiState.value = _uiState.value.copy(clearingItemId = null)
        }
    }

    private suspend fun clearMarketPricesCache() {
        withContext(dispatcherProvider.io) {
            runCatching {
                marketDatabase.coinPriceDao().deleteAll()
                marketDatabase.coinHistoricalPriceDao().deleteAll()
            }
        }
    }
}
