package cash.p.terminal.modules.receive.ui

import cash.p.terminal.modules.receive.viewmodels.ReceiveSharedViewModel
import io.mockk.mockk
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.core.context.stopKoin
import org.koin.core.context.startKoin
import org.junit.After
import org.junit.Before
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.R
import cash.p.terminal.modules.main.Nav3Host
import cash.p.terminal.modules.main.PlainTestPage
import cash.p.terminal.ledger.domain.LedgerUserOperations
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.ui_compose.LocalConnectionPanelState
import kotlinx.coroutines.flow.MutableStateFlow
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

    @Before
    fun startKoinWithSharedViewModel() {
        startKoin {
            modules(module { viewModel { ReceiveSharedViewModel(mockk(relaxed = true), mockk(relaxed = true)) } })
        }
    }

    @After
    fun stopKoinAfterTest() = stopKoin()

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
                Nav3Host(
                    navigation,
                    isLocked = mutableStateOf(false),
                    ledgerUserOperations = LedgerUserOperations(),
                    ledgerOpeningApp = MutableStateFlow(null),
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(listOf<HSPage>(root, entryPage), navigation.backStack.toList())
    }
}
