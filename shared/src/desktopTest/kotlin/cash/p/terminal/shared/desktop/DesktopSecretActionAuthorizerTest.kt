package cash.p.terminal.shared.desktop

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
    private var actions = 0
    private val systemLocale: Locale = Locale.getDefault()

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
        Locale.setDefault(systemLocale)
    }

    @Test
    fun authorize_passwordConfirmed_runsTheActionOnce() {
        val page = authorize()

        navigation.setResult(page, ConfirmPasswordPage.Result(success = true))
        navigation.navigateUp()

        assertEquals(1, actions)
    }

    @Test
    fun authorize_confirmationCancelled_doesNotRunTheAction() {
        authorize()

        navigation.navigateUp()

        assertEquals(0, actions)
    }

    @Test
    fun authorize_passwordAcceptedOnThePage_runsTheActionAndClosesIt() = runDesktopComposeUiTest {
        val check = submitPasswordOnThePage()

        check.complete(UnlockOutcome.Success)
        waitForIdle()

        assertEquals(1, actions)
        assertIs<BlankPage>(navigation.lastOrNull())
    }

    @Test
    fun authorize_cancelledWhileThePasswordIsChecked_doesNotRunTheAction() = runDesktopComposeUiTest {
        val check = submitPasswordOnThePage()

        runOnIdle { navigation.navigateUp() }
        check.complete(UnlockOutcome.Success)
        waitForIdle()

        assertEquals(0, actions)
    }

    /** Leaves the check pending until the returned deferred completes. */
    private fun ComposeUiTest.submitPasswordOnThePage(): CompletableDeferred<UnlockOutcome> {
        val check = CompletableDeferred<UnlockOutcome>()
        startConfirmPasswordGraph(check)
        val page = authorize()
        // The page stays composed after its pop, as it does while animating out.
        setContent { TestWindowHost { ComposeAppTheme { page.GetContent(navigation) } } }
        onNode(hasSetTextAction()).performTextInput(PASSWORD)
        onNodeWithText(CONFIRM).performClick()
        waitForIdle()
        return check
    }

    private fun authorize(): ConfirmPasswordPage {
        DesktopSecretActionAuthorizer().authorize(navigation) { actions++ }
        assertEquals(0, actions)
        return assertIs<ConfirmPasswordPage>(navigation.lastOrNull())
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
    }
}
