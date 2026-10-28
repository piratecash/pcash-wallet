package cash.p.terminal.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cash.p.terminal.network.data.AppHeadersProvider
import cash.p.terminal.network.desktopAppDataFile
import cash.p.terminal.network.di.networkModule
import cash.p.terminal.network.pirate.di.PREMIUM_API_BASE_URL_QUALIFIER
import cash.p.terminal.network.secureDesktopAppDataDirectory
import cash.p.terminal.feature.market.di.marketFeatureModule
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.desktop_already_running
import cash.p.terminal.shared.desktop.DesktopAppExit
import cash.p.terminal.shared.desktop.DesktopBalanceAutoHide
import cash.p.terminal.shared.desktop.DesktopForegroundEffect
import cash.p.terminal.shared.desktop.DesktopLaunchArguments
import cash.p.terminal.shared.desktop.DesktopWalletSession
import cash.p.terminal.shared.desktop.DesktopWipeCoordinator
import cash.p.terminal.shared.desktop.desktopFeatureModule
import cash.p.terminal.shared.desktop.rememberDesktopDarkTheme
import cash.p.terminal.shared.desktop.lock.DesktopInactivityLock
import cash.p.terminal.shared.desktop.lock.DesktopRoot
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.SingleInstanceLock
import cash.p.terminal.shared.market.MarketCatalogSyncer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.di.marketModule
import co.touchlab.kermit.Logger
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import io.horizontalsystems.core.DesktopLanguageManager
import io.horizontalsystems.dashlibnative.DashjBlsLibrary
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.sqlcipher.SqlCipherDriver
import java.io.File
import java.util.Locale
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.jetbrains.compose.resources.stringResource
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val APPLICATION_NAME = "P.CASH"
private const val APPLICATION_VERSION = "desktop"
private const val INSTANCE_LOCK_FILE = ".instance.lock"
private const val BEAM_NATIVE_PATH_PROPERTY = "beam.sdk.native.path"

private val applicationScope = MainScope()

// Releasing the lock holder releases the lock, so it stays referenced for the whole process.
private var instanceLock: SingleInstanceLock? = null

fun main(args: Array<String>) {
    val launchArguments = DesktopLaunchArguments.parse(args)
    // The packager re-signs natives inside jars, so SQLCipher, BEAM and JNA load their copies from the app resources.
    System.getProperty("compose.application.resources.dir")?.let {
        System.setProperty(SqlCipherDriver.NATIVE_DIR_PROPERTY, it)
        System.setProperty(BEAM_NATIVE_PATH_PROPERTY, File(it, System.mapLibraryName("beam_sdk_kmp")).path)
        System.setProperty("jna.boot.library.path", it)
        System.setProperty("jna.library.path", it)
        // Never unpack a jar's copy into a temp directory: only the packaged natives may load.
        System.setProperty("jna.nounpack", "true")
    }
    secureDesktopAppDataDirectory()
    launchArguments.awaitPreviousInstance()
    instanceLock = SingleInstanceLock.acquire(desktopAppDataFile(INSTANCE_LOCK_FILE))
    if (instanceLock == null) {
        showAlreadyRunning()
        return
    }
    // Registers the crypto provider the kits sign and derive keys with, before anything touches a key.
    EthereumKit.init()
    // Without the native, Dash InstantSend lock votes are rejected silently.
    if (!DashjBlsLibrary.available) Logger.withTag("Main").w { "Dash BLS native is unavailable on this platform" }
    val koin = startKoin {
        modules(networkModule, marketModule, marketFeatureModule, desktopFeatureModule, desktopModule)
    }.koin
    // Applies the stored language before the first request reads Locale.getDefault() for its headers.
    koin.get<DesktopLanguageManager>()
    SingletonImageLoader.setSafe(::buildImageLoader)
    koin.get<MarketCatalogSyncer>().start(applicationScope)
    koin.get<DesktopWalletSession>().start(applicationScope)
    koin.get<DesktopBalanceAutoHide>().start(applicationScope)
    koin.get<DesktopWipeCoordinator>().start(applicationScope)
    val inactivityLock = koin.get<DesktopInactivityLock>()
    inactivityLock.start(applicationScope)
    inactivityLock.watchUserInput()
    val vault = koin.get<DesktopVault>()
    val appExit = koin.get<DesktopAppExit>()
    application {
        val quit: () -> Unit = {
            applicationScope.launch {
                try {
                    vault.close()
                } finally {
                    exitApplication()
                }
            }
        }
        val windowState = rememberWindowState()
        Window(onCloseRequest = quit, state = windowState, title = APPLICATION_NAME) {
            LaunchedEffect(appExit) {
                appExit.requested.first { it }
                quit()
            }
            DesktopForegroundEffect(windowState.isMinimized)
            ComposeAppTheme(darkTheme = rememberDesktopDarkTheme()) {
                DesktopRoot(APPLICATION_VERSION, launchArguments.wipeFailed)
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
