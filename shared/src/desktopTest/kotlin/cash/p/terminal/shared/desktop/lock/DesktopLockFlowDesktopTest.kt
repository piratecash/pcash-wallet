package cash.p.terminal.shared.desktop.lock

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import cash.p.terminal.shared.DesktopTestGraph
import cash.p.terminal.shared.TestWindowHost
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.DesktopVaultFiles
import cash.p.terminal.shared.desktop.vault.WipeOutcome
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import org.koin.core.context.GlobalContext

@OptIn(ExperimentalTestApi::class)
class DesktopLockFlowDesktopTest {

    private val systemLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun setUp() {
        Locale.setDefault(Locale.ENGLISH)
        DesktopTestGraph.start()
    }

    @AfterTest
    fun tearDown() {
        Locale.setDefault(systemLocale)
        DesktopTestGraph.stop()
    }

    @Test
    // Three Argon2id derivations with the production parameters: the default test timeout is too tight.
    fun firstRun_passwordCreated_opensTheAppAndUnlocksItAgainAfterALock() = runDesktopComposeUiTest(
        testTimeout = TEST_TIMEOUT,
    ) {
        createPasswordAndLock()

        onAllNodes(hasSetTextAction())[0].performTextInput(WRONG_PASSWORD)
        onNodeWithText(UNLOCK).performClick()
        awaitText(WRONG_PASSWORD_ERROR)

        onAllNodes(hasSetTextAction())[0].performTextClearance()
        onAllNodes(hasSetTextAction())[0].performTextInput(PASSWORD)
        onNodeWithText(UNLOCK).performClick()
        awaitAppShown()
    }

    @Test
    fun unlock_enterInThePasswordField_unlocks() = runDesktopComposeUiTest(testTimeout = TEST_TIMEOUT) {
        createPasswordAndLock()

        onAllNodes(hasSetTextAction())[0].performTextInput(PASSWORD)
        onAllNodes(hasSetTextAction())[0].performKeyInput { pressKey(Key.Enter) }
        awaitAppShown()
    }

    @Test
    fun unlock_dataUnreadable_cancelOnResetReturnsToUnlock() = runDesktopComposeUiTest(
        testTimeout = TEST_TIMEOUT,
    ) {
        createPasswordAndLock()
        GlobalContext.get().get<DesktopVaultFiles>().vaultFile.writeText("damaged")

        onAllNodes(hasSetTextAction())[0].performTextInput(PASSWORD)
        onNodeWithText(UNLOCK).performClick()
        awaitText(DATA_UNREADABLE)

        onNodeWithText(CANCEL).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText(UNLOCK_TITLE).assertExists()
        onNodeWithText(DATA_UNREADABLE).assertDoesNotExist()
    }

    @Test
    fun forgotPassword_checkbox_gatesDeletionAndExposesItsState() = runDesktopComposeUiTest {
        var wipes = 0
        val vault = mockk<DesktopVault> {
            coEvery { wipe() } coAnswers {
                wipes++
                WipeOutcome.SUCCESS
            }
        }
        setContent {
            TestWindowHost {
                ComposeAppTheme {
                    ForgotPasswordScreen(
                        dataUnreadable = false,
                        onCancelClick = {},
                        viewModel = ForgotPasswordViewModel(vault),
                    )
                }
            }
        }

        onNodeWithText(DELETE_DATA).performScrollTo().performClick()
        drainMainDispatcher()
        assertEquals(0, wipes)

        onNodeWithText(UNDERSTAND).assert(hasRole(Role.Checkbox)).assertIsOff()
        onNodeWithText(UNDERSTAND).performScrollTo().performClick()
        onNodeWithText(UNDERSTAND).assertIsOn()
        onNodeWithText(DELETE_DATA).performClick()
        drainMainDispatcher()
        assertEquals(1, wipes)
    }

    private fun ComposeUiTest.createPasswordAndLock() {
        setContent {
            TestWindowHost {
                ComposeAppTheme { DesktopRoot(appVersion = "test") }
            }
        }

        finishIntro()
        onNodeWithText(CREATE_TITLE).assertExists()
        onAllNodes(hasSetTextAction())[0].performTextInput(PASSWORD)
        onAllNodes(hasSetTextAction())[1].performTextInput(PASSWORD)
        onNodeWithText(UNDERSTAND).performScrollTo().performClick()
        onNodeWithText(CREATE).performScrollTo().performClick()
        awaitAppShown()

        runBlocking { vault().lock() }
        awaitText(UNLOCK_TITLE)
    }

    // The ViewModel launches on Main (the AWT thread), so a click's effect lands only once it runs.
    private fun hasRole(role: Role) =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun drainMainDispatcher() = runBlocking(Dispatchers.Main) { }

    private fun vault(): DesktopVault = GlobalContext.get().get()

    private fun ComposeUiTest.awaitAppShown() = waitUntil(timeoutMillis = OPERATION_TIMEOUT_MS) {
        onAllNodesWithContentDescription(BALANCE_TAB).fetchSemanticsNodes().isNotEmpty()
    }

    private fun ComposeUiTest.awaitText(text: String) = waitUntil(timeoutMillis = OPERATION_TIMEOUT_MS) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private companion object {
        val TEST_TIMEOUT = 5.minutes
        const val OPERATION_TIMEOUT_MS = 60_000L
        const val PASSWORD = "Correct-horse1"
        const val WRONG_PASSWORD = "Battery-staple2"
        const val CREATE_TITLE = "Create a password"
        const val CREATE = "Create"
        const val UNDERSTAND = "I Understand"
        const val UNLOCK_TITLE = "Enter your password"
        const val UNLOCK = "Unlock"
        const val WRONG_PASSWORD_ERROR = "Wrong password"
        const val DELETE_DATA = "Delete data"
        const val DATA_UNREADABLE = "Could not open your data"
        const val CANCEL = "Cancel"
        const val BALANCE_TAB = "Balance"
    }
}
