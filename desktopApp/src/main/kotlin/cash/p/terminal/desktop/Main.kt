package cash.p.terminal.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import cash.p.terminal.network.data.AppHeadersProvider
import cash.p.terminal.network.desktopAppDataFile
import cash.p.terminal.network.di.networkModule
import cash.p.terminal.network.pirate.di.PREMIUM_API_BASE_URL_QUALIFIER
import cash.p.terminal.network.secureDesktopAppDataDirectory
import cash.p.terminal.feature.market.di.marketFeatureModule
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.desktop_already_running
import cash.p.terminal.shared.desktop.DesktopForegroundEffect
import cash.p.terminal.shared.desktop.desktopFeatureModule
import cash.p.terminal.shared.desktop.lock.DesktopInactivityLock
import cash.p.terminal.shared.desktop.lock.DesktopRoot
import cash.p.terminal.shared.desktop.vault.SingleInstanceLock
import cash.p.terminal.shared.market.MarketCatalogSyncer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.di.marketModule
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import io.horizontalsystems.core.DesktopLanguageManager
import java.util.Locale
import kotlinx.coroutines.MainScope
import okhttp3.OkHttpClient
import org.jetbrains.compose.resources.stringResource
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val APPLICATION_NAME = "P.CASH"
private const val APPLICATION_VERSION = "desktop"
private const val INSTANCE_LOCK_FILE = ".instance.lock"

private val applicationScope = MainScope()

// Releasing the lock holder releases the lock, so it stays referenced for the whole process.
private var instanceLock: SingleInstanceLock? = null

fun main() {
    secureDesktopAppDataDirectory()
    instanceLock = SingleInstanceLock.acquire(desktopAppDataFile(INSTANCE_LOCK_FILE))
    if (instanceLock == null) {
        showAlreadyRunning()
        return
    }
    val koin = startKoin {
        modules(networkModule, marketModule, marketFeatureModule, desktopFeatureModule, desktopModule)
    }.koin
    // Applies the stored language before the first request reads Locale.getDefault() for its headers.
    koin.get<DesktopLanguageManager>()
    SingletonImageLoader.setSafe(::buildImageLoader)
    koin.get<MarketCatalogSyncer>().start(applicationScope)
    val inactivityLock = koin.get<DesktopInactivityLock>()
    inactivityLock.start(applicationScope)
    inactivityLock.watchUserInput()
    application {
        Window(onCloseRequest = ::exitApplication, title = APPLICATION_NAME) {
            DesktopForegroundEffect()
            ComposeAppTheme {
                DesktopRoot(APPLICATION_VERSION)
            }
        }
    }
}

private fun showAlreadyRunning() = application {
    DialogWindow(onCloseRequest = ::exitApplication, title = APPLICATION_NAME) {
        ComposeAppTheme {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                body_leah(text = stringResource(Res.string.desktop_already_running))
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
