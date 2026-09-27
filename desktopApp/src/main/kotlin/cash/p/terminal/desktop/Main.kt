package cash.p.terminal.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import cash.p.terminal.network.data.AppHeadersProvider
import cash.p.terminal.network.di.networkModule
import cash.p.terminal.network.pirate.di.PREMIUM_API_BASE_URL_QUALIFIER
import cash.p.terminal.shared.PcashApp
import cash.p.terminal.shared.settings.MainSettingUiState
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import java.util.Locale
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val APPLICATION_NAME = "P.CASH"
private const val APPLICATION_VERSION = "desktop"

fun main() {
    val settingsUiState = desktopSettingsState(Locale.getDefault())
    startKoin {
        modules(networkModule, desktopModule)
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = APPLICATION_NAME,
        ) {
            ComposeAppTheme {
                PcashApp(settingsUiState, APPLICATION_VERSION)
            }
        }
    }
}

private fun desktopSettingsState(locale: Locale) = MainSettingUiState(
    isUpdateAvailable = false,
    currentLanguage = locale.displayName.ifEmpty { locale.language.ifEmpty { "English" } },
    baseCurrencyCode = "",
    appWebPageLink = "",
    hasNonStandardAccount = false,
    allBackedUp = true,
    pendingRequestCount = 0,
    walletConnectSessionCount = 0,
    manageWalletShowAlert = false,
    securityCenterShowAlert = false,
    securityCenterShowNewBadge = false,
    aboutAppShowAlert = false,
    wcCounterType = null,
    premiumSettingsShowAlert = false,
    isPayCoreEnabled = false,
)

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
