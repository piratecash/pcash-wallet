package cash.p.terminal.modules.tonconnect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import cash.p.terminal.feature.wallet.SecretActionAuthorizer
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.ui_compose.components.HudMessenger
import cash.p.terminal.ui_compose.components.LocalHudMessenger
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TonConnectSendRequestScreenDesktopTest {

    // The app bar title says Confirm too.
    private val confirmButton = hasText("Confirm") and hasClickAction()
    private val navigation = HSNavigation(NavBackStack<HSPage>(RootPage()))
    private val passThroughPin = object : SecretActionAuthorizer {
        @Composable
        override fun <A> rememberAuthorization(navigation: HSNavigation, action: (A) -> Unit): (A) -> Unit = action

        @Composable
        override fun rememberHiddenTransactionsAuthorization(navigation: HSNavigation, action: () -> Unit) = action

        @Composable
        override fun <A> rememberTransferAuthorization(navigation: HSNavigation, action: (A) -> Unit): (A) -> Unit = action
    }

    @Test
    fun confirmClick_confirmEnabled_confirmsRequest() {
        val viewModel = viewModel(confirmEnabled = true)

        runScreenTest(viewModel) {
            onNode(confirmButton).performClick()
            waitForIdle()
        }

        verify(exactly = 1) { viewModel.confirm() }
    }

    @Test
    fun confirmButton_scopeClosed_isDisabled() = runScreenTest(viewModel(confirmEnabled = false)) {
        onNode(confirmButton).assertIsNotEnabled()
    }

    @Test
    fun rejectClick_rejectEnabled_rejectsRequest() {
        val viewModel = viewModel(confirmEnabled = true)

        runScreenTest(viewModel) {
            onNodeWithText("Reject").assertIsEnabled().performClick()
            waitForIdle()
        }

        verify(exactly = 1) { viewModel.reject() }
    }

    private fun viewModel(confirmEnabled: Boolean) = mockk<TonConnectSendRequestViewModel>(relaxed = true) {
        every { uiState } returns TonConnectSendRequestUiState(
            tonTransactionRecord = null,
            error = null,
            confirmEnabled = confirmEnabled,
            rejectEnabled = true,
        )
    }

    private fun runScreenTest(
        viewModel: TonConnectSendRequestViewModel,
        body: ComposeUiTest.() -> Unit,
    ) = runDesktopComposeUiTest {
        setContent {
            KoinIsolatedContext(
                koinApplication { modules(module { single<SecretActionAuthorizer> { passThroughPin } }) }
            ) {
                CompositionLocalProvider(
                    LocalHudMessenger provides mockk<HudMessenger>(relaxed = true),
                    LocalNavigationEventDispatcherOwner provides rememberNavigationEventDispatcherOwner(parent = null),
                ) {
                    ComposeAppTheme { TonConnectSendRequestScreen(navigation, viewModel) }
                }
            }
        }
        body()
    }

    private class RootPage : HSPage() {
        @Composable
        override fun GetContent(navigation: HSNavigation) = Unit
    }
}
