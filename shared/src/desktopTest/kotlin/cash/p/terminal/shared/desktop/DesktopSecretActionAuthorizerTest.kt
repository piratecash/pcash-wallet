package cash.p.terminal.shared.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.shared.TestWindowHost
import cash.p.terminal.shared.desktop.lock.BlankPage
import cash.p.terminal.shared.desktop.lock.ConfirmPasswordPage
import cash.p.terminal.shared.desktop.lock.ConfirmPasswordViewModel
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.UnlockOutcome
import cash.p.terminal.shared.main.DesktopHudMessenger
import cash.p.terminal.shared.main.DesktopNavHost
import cash.p.terminal.ui_compose.components.UrlOpener
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.ICurrentDateProvider
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import java.util.Date
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class DesktopSecretActionAuthorizerTest {

    private val navigation = HSNavigation(NavBackStack<HSPage>(BlankPage()))
    private val arguments = mutableListOf<String>()
    private val systemLocale: Locale = Locale.getDefault()

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
        Locale.setDefault(systemLocale)
    }

    @Test
    fun rememberAuthorization_passwordAcceptedOnThePage_runsTheActionAndClosesIt() = runDesktopComposeUiTest {
        val check = submitPasswordOnThePage()

        check.complete(UnlockOutcome.Success)
        waitForIdle()

        assertEquals(listOf(ARGUMENT), arguments)
        assertIs<AuthorizationCallerPage>(navigation.lastOrNull())
    }

    @Test
    fun rememberAuthorization_cancelledWhileThePasswordIsChecked_doesNotRunTheAction() = runDesktopComposeUiTest {
        val check = submitPasswordOnThePage()

        runOnIdle { navigation.navigateUp() }
        check.complete(UnlockOutcome.Success)
        waitForIdle()

        assertEquals(emptyList(), arguments)
    }

    @Test
    fun rememberAuthorization_passwordConfirmed_runsTheActionOnceWithItsArgument() = runDesktopComposeUiTest {
        val page = rememberAuthorizationAndLaunch()

        runOnIdle {
            navigation.setResult(page, ConfirmPasswordPage.Result(success = true))
            navigation.navigateUp()
        }
        waitForIdle()

        assertEquals(listOf(ARGUMENT), arguments)
    }

    @Test
    fun rememberAuthorization_confirmationCancelled_doesNotRunTheAction() = runDesktopComposeUiTest {
        rememberAuthorizationAndLaunch()

        runOnIdle { navigation.navigateUp() }
        waitForIdle()

        assertEquals(emptyList(), arguments)
    }

    /** Hosts a caller that remembers the authorization and launches it with [ARGUMENT]. */
    private fun ComposeUiTest.rememberAuthorizationAndLaunch(): ConfirmPasswordPage {
        startConfirmPasswordGraph(CompletableDeferred())
        val caller = AuthorizationCallerPage()
        navigation.slideFromRight(caller)
        setContent {
            TestWindowHost {
                ComposeAppTheme {
                    DesktopNavHost(navigation, remember { DesktopHudMessenger() }, NoUrlOpener)
                }
            }
        }
        runOnIdle { caller.authorize(ARGUMENT) }
        waitForIdle()
        assertEquals(emptyList(), arguments)
        return assertIs<ConfirmPasswordPage>(navigation.lastOrNull())
    }

    private inner class AuthorizationCallerPage : HSPage() {
        lateinit var authorize: (String) -> Unit

        @Composable
        override fun GetContent(navigation: HSNavigation) {
            val authorization = DesktopSecretActionAuthorizer().rememberAuthorization<String>(navigation) {
                arguments += it
            }
            SideEffect { authorize = authorization }
        }
    }

    private object NoUrlOpener : UrlOpener {
        override fun open(url: String) = Unit
    }

    /** Leaves the check pending until the returned deferred completes. */
    private fun ComposeUiTest.submitPasswordOnThePage(): CompletableDeferred<UnlockOutcome> {
        val check = CompletableDeferred<UnlockOutcome>()
        startConfirmPasswordGraph(check)
        val caller = AuthorizationCallerPage()
        navigation.slideFromRight(caller)
        var page by mutableStateOf<HSPage?>(null)
        // The page stays composed after its pop, as it does while animating out.
        setContent {
            TestWindowHost {
                ComposeAppTheme {
                    caller.Content(navigation)
                    page?.Content(navigation)
                }
            }
        }
        runOnIdle {
            caller.authorize(ARGUMENT)
            page = assertIs<ConfirmPasswordPage>(navigation.lastOrNull())
        }
        onNode(hasSetTextAction()).performTextInput(PASSWORD)
        onNodeWithText(CONFIRM).performClick()
        waitForIdle()
        return check
    }

    private fun startConfirmPasswordGraph(check: CompletableDeferred<UnlockOutcome>) {
        Locale.setDefault(Locale.ENGLISH)
        // The check resumes inside complete(), so waitForIdle() covers everything it triggers.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val vault = mockk<DesktopVault> {
            coEvery { lockedOutUntil() } returns null
            coEvery { verifyPassword(any()) } coAnswers { check.await() }
        }
        startKoin {
            modules(
                module {
                    single { vault }
                    single<ICurrentDateProvider> {
                        object : ICurrentDateProvider {
                            override val currentDate: Date get() = Date()
                        }
                    }
                    viewModelOf(::ConfirmPasswordViewModel)
                }
            )
        }
    }

    private companion object {
        const val PASSWORD = "Correct-horse1"
        const val CONFIRM = "Confirm"
        const val ARGUMENT = "argument"
    }
}
