package cash.p.terminal.shared.settings

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

    private companion object {
        val state = MainSettingUiState(
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
    }
}
