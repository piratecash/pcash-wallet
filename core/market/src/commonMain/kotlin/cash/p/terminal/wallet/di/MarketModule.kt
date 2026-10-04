package cash.p.terminal.wallet.di

import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.SubscriptionManager
import cash.p.terminal.wallet.favorites.MarketFavoritesManager
import cash.p.terminal.wallet.managers.VirtualCoinMapper
import cash.p.terminal.wallet.providers.CryptoCompareProvider
import cash.p.terminal.wallet.providers.RetrofitUtils
import cash.p.terminal.wallet.providers.mapper.PirateCoinInfoMapper
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

internal const val MARKET_FAVORITES_DATA_STORE = "marketFavoritesDataStore"

/** User data: the name is part of the on-disk contract and must not change. */
internal const val MARKET_FAVORITES_FILE_NAME = "market_favorites.preferences_pb"

private val commonMarketModule = module {
    single { RetrofitUtils(get(named(RETROFIT_OK_HTTP_CLIENT))) }

    single { MarketFavoritesManager(get(named(MARKET_FAVORITES_DATA_STORE)), get()) }

    singleOf(::VirtualCoinMapper)
    singleOf(::MarketKitWrapper)
    singleOf(::SubscriptionManager)
    factoryOf(::CryptoCompareProvider)
    factoryOf(::PirateCoinInfoMapper)
}

val marketModule = module {
    includes(
        commonMarketModule,
        platformMarketModule(),
    )
}

internal expect fun platformMarketModule(): Module
