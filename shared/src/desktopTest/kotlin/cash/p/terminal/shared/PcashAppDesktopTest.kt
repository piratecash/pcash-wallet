package cash.p.terminal.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cash.p.terminal.shared.settings.MainSettingUiState
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui_compose.theme.darkPalette
import cash.p.terminal.ui_compose.theme.lightPalette
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PcashAppDesktopTest {

    @Test
    fun settings_adaptsToViewportAndKeepsSelection() = runDesktopComposeUiTest(
        width = WIDE.width,
        height = NARROW.height,
    ) {
        var viewport by mutableStateOf(WIDE)
        var darkTheme by mutableStateOf(false)
        val windowInfo = object : WindowInfo {
            override val isWindowFocused = true
            override val containerSize: IntSize
                get() = viewport
        }

        setContent {
            CompositionLocalProvider(LocalWindowInfo provides windowInfo) {
                ComposeAppTheme(darkTheme = darkTheme) {
                    Box(Modifier.size(viewport.width.dp, viewport.height.dp)) {
                        PcashApp(SETTINGS_STATE, "desktop")
                    }
                }
            }
        }

        val wideCenters = destinationCenters()
        assertTrue(axisSpread(wideCenters, vertical = true) > axisSpread(wideCenters, vertical = false))

        onNodeWithContentDescription("Settings").performClick()
        onNodeWithText("Donate").performClick()
        onNodeWithText("P.CASH DESKTOP").performScrollTo().assertExists()
        val company = onNodeWithText("PirateCash and Cosanta Foundation")
        company.performScrollTo().assertExists()
        val companyBounds = company.fetchSemanticsNode().boundsInRoot
        onRoot().performTouchInput {
            click(Offset(companyBounds.center.x, companyBounds.top - 28.dp.toPx()))
        }
        onNodeWithText("Settings").assertExists()

        onNodeWithContentDescription("Balance").performClick()
        onNodeWithText("Balance").assertExists()
        onNodeWithContentDescription("Settings").performClick()
        onNodeWithText("Donate").assertExists()

        runOnIdle {
            viewport = NARROW
            darkTheme = true
        }
        onNodeWithText("Donate").assertExists()
        val narrowCenters = destinationCenters()
        assertTrue(axisSpread(narrowCenters, vertical = false) > axisSpread(narrowCenters, vertical = true))
        assertTrue(rootCornerColor() == darkPalette.tyler)

        runOnIdle { darkTheme = false }
        assertTrue(rootCornerColor() == lightPalette.tyler)
    }

    private fun ComposeUiTest.destinationCenters(): List<Offset> =
        DESTINATIONS.map { title ->
            onNodeWithContentDescription(title).fetchSemanticsNode().boundsInRoot.center
        }

    private fun axisSpread(centers: List<Offset>, vertical: Boolean): Float {
        val values = centers.map { if (vertical) it.y else it.x }
        return values.max() - values.min()
    }

    private fun ComposeUiTest.rootCornerColor() =
        onRoot().captureToImage().toPixelMap()[NARROW.width - 1, 0]

    private companion object {
        val WIDE = IntSize(800, 600)
        val NARROW = IntSize(420, 740)
        val DESTINATIONS = listOf("Balance", "Transactions", "Markets", "Settings")
        val SETTINGS_STATE = MainSettingUiState(
            isUpdateAvailable = false,
            currentLanguage = "English",
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
    }
}
