package cash.p.terminal.shared.desktop.journal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.feature.logging.settings.LoginJournalSecrets
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.shared.TestWindowHost
import cash.p.terminal.shared.desktop.lock.BlankPage
import cash.p.terminal.shared.desktop.lock.ConfirmPasswordPage
import io.mockk.every
import io.mockk.mockk
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalTestApi::class)
class JournalAccessGuardTest {

    private val navigation = HSNavigation(NavBackStack<HSPage>(BlankPage()))
    private val secrets = mockk<LoginJournalSecrets>()
    private var runs = 0

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun rememberJournalAccessAction_journalPasswordSet_asksForIt() = runDesktopComposeUiTest {
        val guarded = hostGuardedAction(journalPasswordSet = true)

        runOnIdle { guarded() }

        assertIs<ConfirmJournalSecretPage>(navigation.lastOrNull())
        assertEquals(0, runs)
    }

    @Test
    fun rememberJournalAccessAction_noJournalPassword_asksForTheWalletPassword() = runDesktopComposeUiTest {
        val guarded = hostGuardedAction(journalPasswordSet = false)

        runOnIdle { guarded() }

        assertIs<ConfirmPasswordPage>(navigation.lastOrNull())
        assertEquals(0, runs)
    }

    @Test
    fun rememberJournalAccessAction_passwordConfirmed_runsTheActionOnce() = runDesktopComposeUiTest {
        val guarded = hostGuardedAction(journalPasswordSet = true)
        runOnIdle { guarded() }
        val page = assertIs<ConfirmJournalSecretPage>(navigation.lastOrNull())

        runOnIdle {
            navigation.setResult(page, ConfirmPasswordPage.Result(success = true))
            navigation.navigateUp()
        }
        waitForIdle()

        assertEquals(1, runs)
    }

    @Test
    fun rememberJournalAccessAction_cancelled_doesNotRunTheAction() = runDesktopComposeUiTest {
        val guarded = hostGuardedAction(journalPasswordSet = false)
        runOnIdle { guarded() }

        runOnIdle { navigation.navigateUp() }
        waitForIdle()

        assertEquals(0, runs)
    }

    private fun ComposeUiTest.hostGuardedAction(journalPasswordSet: Boolean): () -> Unit {
        every { secrets.isJournalSecretSet() } returns journalPasswordSet
        startKoin { modules(module { single { secrets } }) }
        val caller = GuardedCallerPage()
        navigation.slideFromRight(caller)
        setContent { TestWindowHost { caller.Content(navigation) } }
        waitForIdle()
        return caller.guarded
    }

    private inner class GuardedCallerPage : HSPage() {
        lateinit var guarded: () -> Unit

        @Composable
        override fun GetContent(navigation: HSNavigation) {
            val action = navigation.rememberJournalAccessAction { runs++ }
            SideEffect { guarded = action }
        }
    }
}
