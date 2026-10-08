package cash.p.terminal.modules.receive.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.R
import cash.p.terminal.modules.main.Nav3Host
import cash.p.terminal.modules.main.PlainTestPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.ui_compose.LocalConnectionPanelState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReceiveChooseCoinFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun bchAddressFormatPage_noCoinChosen_closesWholeFlowDownToEntryPage() {
        val root = PlainTestPage()
        val entryPage = PlainTestPage()
        val navigation = HSNavigation(
            NavBackStack<HSPage>(root, entryPage, ReceiveChooseCoinPage(), BchAddressFormatPage())
        )

        // The flow's exit shows a HudHelper snackbar, which needs MainActivity's AppCompat theme.
        composeRule.activity.setTheme(R.style.Theme_AppTheme_DayNight)
        composeRule.setContent {
            CompositionLocalProvider(LocalConnectionPanelState provides mutableStateOf(false)) {
                Nav3Host(navigation, isLocked = mutableStateOf(false))
            }
        }
        composeRule.waitForIdle()

        assertEquals(listOf<HSPage>(root, entryPage), navigation.backStack.toList())
    }
}
