package cash.p.terminal.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import cash.p.terminal.network.data.AppHeadersProvider
import cash.p.terminal.network.di.networkModule
import cash.p.terminal.network.pirate.di.PREMIUM_API_BASE_URL_QUALIFIER
import cash.p.terminal.feature.market.di.marketFeatureModule
import cash.p.terminal.shared.PcashApp
import cash.p.terminal.shared.desktop.DesktopForegroundEffect
import cash.p.terminal.shared.desktop.desktopFeatureModule
import cash.p.terminal.shared.market.MarketCatalogSyncer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.di.marketModule
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import java.util.Locale
import kotlinx.coroutines.MainScope
import okhttp3.OkHttpClient
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val APPLICATION_NAME = "P.CASH"
private const val APPLICATION_VERSION = "desktop"

private val applicationScope = MainScope()

fun main() {
    val koin = startKoin {
        modules(networkModule, marketModule, marketFeatureModule, desktopFeatureModule, desktopModule)
    }.koin
    SingletonImageLoader.setSafe(::buildImageLoader)
    koin.get<MarketCatalogSyncer>().start(applicationScope)
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = APPLICATION_NAME,
        ) {
            DesktopForegroundEffect()
            ComposeAppTheme {
                PcashApp(APPLICATION_VERSION)
            }
        }
    }
}

private fun buildImageLoader(context: PlatformContext): ImageLoader {
    val okHttpClient = OkHttpClient.Builder()
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            // Error responses must not be cached, or a placeholder icon sticks forever.
            if (response.isSuccessful) response else response.newBuilder().header("Cache-Control", "no-store").build()
        }
        .build()
    return ImageLoader.Builder(context)
        .crossfade(true)
        .components {
            add(SvgDecoder.Factory())
            add(OkHttpNetworkFetcherFactory(okHttpClient))
        }
        .build()
}

private val desktopModule = module {
    single<AppHeadersProvider> {
        object : AppHeadersProvider {
            override val appVersion = APPLICATION_VERSION
            override val currentLanguage: String
                get() = Locale.getDefault().language.ifEmpty { "en" }
            override val appSignature: String? = null
        }
    }
    single(named(PREMIUM_API_BASE_URL_QUALIFIER)) { "https://p.cash/api/" }
}
