package cash.p.terminal.modules.settings.appcache

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.settings_app_cache_database_description
import cash.p.terminal.resources.settings_app_cache_database_title
import cash.p.terminal.resources.settings_app_cache_http_description
import cash.p.terminal.resources.settings_app_cache_http_title
import cash.p.terminal.resources.settings_app_cache_image_description
import cash.p.terminal.resources.settings_app_cache_image_title
import cash.p.terminal.resources.settings_app_cache_market_description
import cash.p.terminal.resources.settings_app_cache_market_title
import org.jetbrains.compose.resources.StringResource

object AppCacheModule {

    data class UiState(
        val cacheItems: List<CacheItemViewItem> = emptyList(),
        val isClearing: Boolean = false,
        val clearingItemId: CacheType? = null
    )

    data class CacheItemViewItem(
        val type: CacheType,
        val title: StringResource,
        val description: StringResource
    )

    enum class CacheType(
        val title: StringResource,
        val description: StringResource
    ) {
        HTTP(
            Res.string.settings_app_cache_http_title,
            Res.string.settings_app_cache_http_description
        ),
        IMAGE(
            Res.string.settings_app_cache_image_title,
            Res.string.settings_app_cache_image_description
        ),
        DATABASE(
            Res.string.settings_app_cache_database_title,
            Res.string.settings_app_cache_database_description
        ),
        MARKET_PRICES(
            Res.string.settings_app_cache_market_title,
            Res.string.settings_app_cache_market_description
        )
    }
}
