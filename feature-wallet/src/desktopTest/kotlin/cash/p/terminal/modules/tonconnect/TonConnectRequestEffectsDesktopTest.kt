package cash.p.terminal.modules.tonconnect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import cash.p.terminal.core.managers.TonConnectManager
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import io.horizontalsystems.tonkit.models.SignTransaction
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertIs

@OptIn(ExperimentalTestApi::class)
class TonConnectRequestEffectsDesktopTest {

    private val request = mockk<SignTransaction>()
    private val accounts = MutableSharedFlow<List<Account>>(replay = 1)
    private var accountVisible = false
    private val tonConnectManager = mockk<TonConnectManager>(relaxed = true) {
        every { pendingSendRequest } returns MutableStateFlow(request)
        every { takeShowableSendRequest() } answers { request.takeIf { accountVisible } }
        every { dappRequestFlow } returns emptyFlow()
    }
    private val accountManager = mockk<IAccountManager>(relaxed = true) {
        every { accountsFlow } returns this@TonConnectRequestEffectsDesktopTest.accounts
    }
    private val navigation = HSNavigation(NavBackStack<HSPage>(RootPage()))

    @Test
    fun pendingRequest_accountBecomesVisible_opensSignSheet() = runDesktopComposeUiTest {
        setContent {
            KoinIsolatedContext(
                koinApplication { modules(module { single { tonConnectManager }; single { accountManager } }) }
            ) {
                CompositionLocalProvider(
                    LocalNavigationEventDispatcherOwner provides rememberNavigationEventDispatcherOwner(parent = null),
                ) {
                    TonConnectRequestEffects(navigation) { _, _ -> }
                }
            }
        }
        waitForIdle()
        assertIs<RootPage>(navigation.backStack.last())

        accountVisible = true
        accounts.tryEmit(listOf(mockk()))
        waitForIdle()

        assertIs<TonConnectSendRequestPage>(navigation.backStack.last())
    }

    private class RootPage : HSPage() {
        @Composable
        override fun GetContent(navigation: HSNavigation) = Unit
    }
}
