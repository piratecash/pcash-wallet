package cash.p.terminal.modules.settings.main

import android.app.Application
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.R
import cash.p.terminal.shared.settings.CounterType
import cash.p.terminal.shared.settings.MainSettingUiState
import cash.p.terminal.shared.settings.SettingsAction
import cash.p.terminal.shared.settings.SettingsContent
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

internal val settingsContentTestState = MainSettingUiState(
    isUpdateAvailable = true,
    currentLanguage = "English",
    baseCurrencyCode = "USD",
    appWebPageLink = "https://example.com/app",
    hasNonStandardAccount = true,
    allBackedUp = false,
    pendingRequestCount = 7,
    walletConnectSessionCount = 3,
    manageWalletShowAlert = true,
    securityCenterShowAlert = true,
    securityCenterShowNewBadge = true,
    aboutAppShowAlert = true,
    wcCounterType = CounterType.PendingRequestCounter(7),
    premiumSettingsShowAlert = true,
    isPayCoreEnabled = true,
)

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsContentTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun settingsContent_allRowsAndCompanyLogo_emitsTheirActions() {
        val emitted = mutableListOf<SettingsAction>()
        setContent(onAction = emitted::add)
        val expectedRows = listOf(
            R.string.Settings_Donate to SettingsAction.Donate,
            R.string.settings_mini_app to SettingsAction.MiniApp,
            R.string.SettingsSecurity_ManageKeys to SettingsAction.ManageWallets,
            R.string.BlockchainSettings_Title to SettingsAction.BlockchainSettings,
            R.string.Settings_WalletConnect to SettingsAction.WalletConnect,
            R.string.Settings_TonConnect to SettingsAction.TonConnect,
            R.string.BackupManager_Title to SettingsAction.BackupManager,
            R.string.Settings_SecurityCenter to SettingsAction.SecurityCenter,
            R.string.Contacts to SettingsAction.Contacts,
            R.string.Settings_Appearance to SettingsAction.Appearance,
            R.string.Settings_BaseCurrency to SettingsAction.BaseCurrency,
            R.string.Settings_Language to SettingsAction.Language,
            R.string.address_checker_title to SettingsAction.AddressChecker,
            R.string.swap_providers_title to SettingsAction.SwapProviders,
            R.string.offline_broadcast_title to SettingsAction.OfflineBroadcast,
            R.string.about_premium to SettingsAction.AboutPremium,
            R.string.premium_settings to SettingsAction.PremiumSettings,
            R.string.advanced_security to SettingsAction.AdvancedSecurity,
            R.string.software_update_title to SettingsAction.SoftwareUpdate,
            R.string.SettingsAboutApp_Title to SettingsAction.AboutApp,
            R.string.Settings_RateUs to SettingsAction.RateApp,
            R.string.Settings_ShareThisWallet to SettingsAction.ShareApp,
            R.string.SettingsContact_Title to SettingsAction.Contact(true),
        )

        expectedRows.forEach { (title, _) ->
            compose.onNodeWithText(application.getString(title)).performScrollTo().performClick()
        }
        val clickableNodes = compose.onAllNodes(hasClickAction())
        assertEquals(24, clickableNodes.fetchSemanticsNodes().size)
        clickableNodes[23].performScrollTo().performClick()

        assertEquals(expectedRows.map { it.second } + SettingsAction.CompanyWebsite, emitted)
    }

    @Test
    fun settingsContent_populatedState_showsValuesBadgesAndContactAction() {
        val emitted = mutableListOf<SettingsAction>()
        setContent(emitted::add, settingsContentTestState.copy(isPayCoreEnabled = false))

        compose.onNodeWithText("USD").assertExists()
        compose.onNodeWithText("English").assertExists()
        compose.onNodeWithText("7").assertExists()
        compose.onNodeWithText(application.getString(R.string.badge_new)).assertExists()
        compose.onNodeWithText(application.getString(R.string.SettingsContact_Title))
            .performScrollTo()
            .performClick()

        assertEquals(listOf(SettingsAction.Contact(false)), emitted)
    }

    @Test
    fun settingsContent_sessionCounter_showsSessionCount() {
        setContent({}, settingsContentTestState.copy(
            wcCounterType = CounterType.SessionCounter(3),
        ))

        compose.onNodeWithText("3").assertExists()
        compose.onNodeWithText("7").assertDoesNotExist()
    }

    @Test
    fun settingsContent_withoutCounter_showsNoWalletConnectCount() {
        setContent({}, settingsContentTestState.copy(wcCounterType = null))

        compose.onNodeWithText("3").assertDoesNotExist()
        compose.onNodeWithText("7").assertDoesNotExist()
    }

    @Test
    fun settingsContent_allAlertStatesOff_rendersWithoutNewBadge() {
        setContent({}, settingsContentTestState.copy(
            isUpdateAvailable = false,
            manageWalletShowAlert = false,
            securityCenterShowAlert = false,
            securityCenterShowNewBadge = false,
            aboutAppShowAlert = false,
            premiumSettingsShowAlert = false,
        ))

        compose.onNodeWithText(application.getString(R.string.badge_new)).assertDoesNotExist()
        compose.onNodeWithText(application.getString(R.string.SettingsAboutApp_Title))
            .performScrollTo()
            .assertExists()
    }

    @Test
    fun settingCell_counterValueAndNewBadge_showsCounterAndNewBadgeInsteadOfValue() {
        compose.setContent {
            ComposeAppTheme {
                HsSettingCell(
                    title = R.string.Settings_WalletConnect,
                    value = "session-value",
                    counterBadge = "pending-value",
                    newBadgeText = "new-value",
                    showAlert = true,
                    onClick = {},
                )
            }
        }

        compose.onNodeWithText("pending-value").assertExists()
        compose.onNodeWithText("session-value").assertDoesNotExist()
        compose.onNodeWithText("new-value").assertExists()
    }

    private fun setContent(
        onAction: (SettingsAction) -> Unit,
        uiState: MainSettingUiState = settingsContentTestState,
    ) {
        compose.setContent {
            ComposeAppTheme {
                SettingsContent(
                    uiState = uiState,
                    appVersion = "1.2.3",
                    onAction = onAction,
                    alertPainter = ColorPainter(Color.Red),
                )
            }
        }
    }

    private companion object {
        val application = ApplicationProvider.getApplicationContext<Application>()
    }
}
