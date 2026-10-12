package cash.p.terminal.shared.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.modules.basecurrency.BaseCurrencySettingsPage
import cash.p.terminal.modules.market.MarketScreen
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.shared.settings.MainSettingUiState
import cash.p.terminal.shared.settings.SettingsAction
import cash.p.terminal.shared.settings.SettingsContent
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.ILanguageManager
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

/** Root of the desktop back stack: the main tabs; pages opened from them stack above it. */
class DesktopMainPage(private val appVersion: String) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        var selectedDestination by rememberSaveable { mutableStateOf(MainDestination.Balance) }
        MainNavigation(
            selectedDestination = selectedDestination,
            onDestinationSelect = { selectedDestination = it },
            modifier = Modifier.fillMaxSize(),
        ) { destination ->
            when (destination) {
                MainDestination.Market -> MarketScreen(navigation, PaddingValues())
                MainDestination.Settings -> SettingsPane(navigation, appVersion)
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(MainDestinationTitle(destination))
                }
            }
        }
    }
}

@Composable
private fun SettingsPane(navigation: HSNavigation, appVersion: String) {
    val uiState = rememberSettingsUiState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeAppTheme.colors.backgroundBase),
    ) {
        Text(
            text = MainDestinationTitle(MainDestination.Settings),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            style = ComposeAppTheme.typography.title3,
            color = ComposeAppTheme.colors.textPrimary,
        )
        SettingsContent(
            uiState = uiState,
            appVersion = appVersion,
            onAction = { action ->
                if (action == SettingsAction.BaseCurrency) {
                    navigation.slideFromRight(BaseCurrencySettingsPage())
                }
            },
            alertPainter = null,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun rememberSettingsUiState(): MainSettingUiState {
    val currencyManager = koinInject<CurrencyManager>()
    val locale = koinInject<ILanguageManager>().currentLocale
    val baseCurrencyCode by remember(currencyManager) {
        currencyManager.baseCurrencyUpdatedFlow.map { currencyManager.baseCurrency.code }
    }.collectAsStateWithLifecycle(currencyManager.baseCurrency.code)
    return MainSettingUiState(
        isUpdateAvailable = false,
        currentLanguage = locale.displayName.ifEmpty { locale.language.ifEmpty { "English" } },
        baseCurrencyCode = baseCurrencyCode,
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
}
