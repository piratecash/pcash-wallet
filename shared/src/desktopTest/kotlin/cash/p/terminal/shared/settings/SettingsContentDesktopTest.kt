package cash.p.terminal.shared.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SettingsContentDesktopTest {

    @Test
    fun settingsContent_lightAndDark_rendersResourcesAndHandlesClicks() = runComposeUiTest {
        var darkTheme by mutableStateOf(false)
        val actions = mutableListOf<SettingsAction>()

        setContent {
            ComposeAppTheme(darkTheme = darkTheme) {
                SettingsContent(
                    uiState = state,
                    appVersion = "1.2.3",
                    onAction = actions::add,
                    alertPainter = ColorPainter(Color.Red),
                )
            }
        }

        onNodeWithText("Donate").performClick()
        onNodeWithText("P.CASH 1.2.3").performScrollTo().assertExists()
        onNodeWithText("PirateCash and Cosanta Foundation").assertExists()
        assertEquals(listOf<SettingsAction>(SettingsAction.Donate), actions)

        runOnIdle { darkTheme = true }
        onNodeWithText("About App").performScrollTo().performClick()
        onNodeWithText("P.CASH 1.2.3").performScrollTo().assertExists()
        assertEquals(
            listOf<SettingsAction>(SettingsAction.Donate, SettingsAction.AboutApp),
            actions,
        )
    }

    @Test
    fun settingsContent_hiddenActions_rowsAreNotDisplayed() = runComposeUiTest {
        setContent { ComposeAppTheme { SettingsContent(hiddenActions = hiddenRows.keys) } }

        hiddenRows.values.forEach { title -> onNodeWithText(title).assertDoesNotExist() }
        onNodeWithText("About App").assertExists()
        onNodeWithText("Tell Friends").assertExists()
        onNodeWithText("Donate").assertExists()
    }

    @Test
    fun settingsContent_noHiddenActions_allRowsAreDisplayed() = runComposeUiTest {
        setContent { ComposeAppTheme { SettingsContent() } }

        hiddenRows.values.forEach { title -> onNodeWithText(title).performScrollTo().assertExists() }
    }

    @Composable
    private fun SettingsContent(hiddenActions: Set<SettingsAction> = emptySet()) = SettingsContent(
        uiState = state,
        appVersion = "1.2.3",
        onAction = {},
        alertPainter = ColorPainter(Color.Red),
        hiddenActions = hiddenActions,
    )

    @Test
    fun appShareText_introAndLink_endsWithNewline() {
        assertEquals("Be Free\nhttps://p.cash/\n", appShareText("Be Free", "https://p.cash/"))
    }

    private companion object {
        val hiddenRows = mapOf(
            SettingsAction.MiniApp to "Mini App",
            SettingsAction.WalletConnect to "WalletConnect",
            SettingsAction.TonConnect to "Ton Connect",
            SettingsAction.SoftwareUpdate to "Software Updates",
            SettingsAction.AdvancedSecurity to "Advanced Security",
            SettingsAction.RateApp to "Rate Us",
        )

        val state = MainSettingUiState(
            isUpdateAvailable = true,
            currentLanguage = "English",
            baseCurrencyCode = "USD",
            appWebPageLink = "https://example.com/app",
            hasNonStandardAccount = true,
            allBackedUp = false,
            pendingRequestCount = 7,
            walletConnectSessionCount = 3,
            securityCenterShowAlert = true,
            securityCenterShowNewBadge = true,
            aboutAppShowAlert = true,
            wcCounterType = CounterType.PendingRequestCounter(7),
            premiumSettingsShowAlert = true,
        )
    }
}
