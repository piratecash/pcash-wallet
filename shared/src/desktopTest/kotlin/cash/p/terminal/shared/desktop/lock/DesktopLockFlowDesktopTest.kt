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
import cash.p.terminal.modules.pin.DuressSecretController
import cash.p.terminal.modules.pin.LevelOutcome
import cash.p.terminal.shared.DesktopTestGraph
import cash.p.terminal.shared.TestWindowHost
import cash.p.terminal.shared.desktop.DesktopHiddenWalletEntry
import cash.p.terminal.shared.desktop.DesktopWipeCoordinator
import cash.p.terminal.shared.desktop.WipeProgress
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.DesktopVaultFiles
import cash.p.terminal.shared.main.DesktopHudMessenger
import cash.p.terminal.shared.main.HudMessage
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
    fun lockScreen_hiddenWalletEntryRunning_hidesTheUnlockFormUntilItEnds() = runDesktopComposeUiTest(
        testTimeout = TEST_TIMEOUT,
    ) {
        createPasswordAndLock()
        val entry = GlobalContext.get().get<DesktopHiddenWalletEntry>()
        val finish = CompletableDeferred<Unit>()
        val running = CoroutineScope(Dispatchers.Default).launch { entry.whileEntering { finish.await() } }

        waitUntil(timeoutMillis = OPERATION_TIMEOUT_MS) {
            onAllNodesWithText(UNLOCK_TITLE).fetchSemanticsNodes().isEmpty()
        }
        onNodeWithText(UNLOCK).assertDoesNotExist()

        finish.complete(Unit)
        awaitText(UNLOCK_TITLE)
    }

    @Test
    fun hiddenWalletEntry_lockedRightAfterEntering_showsTheUnlockFormAndOpensCreateAccountAfterUnlock() =
        runDesktopComposeUiTest(testTimeout = TEST_TIMEOUT) {
            createPasswordAndLock()
            onAllNodes(hasSetTextAction())[0].performTextInput(PASSWORD)
            onNodeWithText(UNLOCK).performClick()
            awaitAppShown()
            val hud = collectHud()

            assertEquals(LevelOutcome.SUCCESS, enterHiddenWalletAndRelock())
            awaitText(UNLOCK_TITLE)
            assertTrue(hud.none { it?.text == CREATED }, "created while the screen was locked")

            onAllNodes(hasSetTextAction())[0].performTextInput(HIDDEN_PASSWORD)
            onNodeWithText(UNLOCK).performClick()
            awaitText(TERMS_AGREE)
            waitUntil(timeoutMillis = OPERATION_TIMEOUT_MS) { hud.any { it?.text == CREATED } }
            assertEquals(1, hud.count { it?.text == CREATED })
        }

    @Test
    fun hiddenWalletEntry_relockedThenOtherWalletUnlocked_revealsNothingNowOrAfterTheHiddenUnlock() =
        runDesktopComposeUiTest(testTimeout = TEST_TIMEOUT) {
            createPasswordAndLock()
            onAllNodes(hasSetTextAction())[0].performTextInput(PASSWORD)
            onNodeWithText(UNLOCK).performClick()
            awaitAppShown()
            val hud = collectHud()
            enterHiddenWalletAndRelock()
            awaitText(UNLOCK_TITLE)

            onAllNodes(hasSetTextAction())[0].performTextInput(PASSWORD)
            onNodeWithText(UNLOCK).performClick()
            awaitAppOrTerms()
            assertNothingRevealed(hud)

            runBlocking { vault().lock() }
            awaitText(UNLOCK_TITLE)
            onAllNodes(hasSetTextAction())[0].performTextInput(HIDDEN_PASSWORD)
            onNodeWithText(UNLOCK).performClick()
            awaitAppOrTerms()
            assertNothingRevealed(hud)
        }

    /**
     * Off the test thread: the entry waits for the app to release its wallet, which needs the compose clock.
     * The entry stays open across the relock, so the app cannot act on the new session in between.
     */
    /** Subscribed before it returns: the messenger replays nothing. */
    private fun collectHud(): List<HudMessage?> {
        val hud = CopyOnWriteArrayList<HudMessage?>()
        val messages = GlobalContext.get().get<DesktopHudMessenger>().messages
        CoroutineScope(Dispatchers.Default).launch(start = CoroutineStart.UNDISPATCHED) { messages.collect { hud += it } }
        return hud
    }

    /**
     * The lock lands while the frame clock is stopped, so the entered wallet's screen is still composed
     * when the entry ends, as it is when the machine is slow.
     */
    private fun ComposeUiTest.enterHiddenWalletAndRelock(): LevelOutcome? {
        var outcome: LevelOutcome? = null
        val entry = GlobalContext.get().get<DesktopHiddenWalletEntry>()
        val entered = CompletableDeferred<Unit>()
        val clockStopped = CompletableDeferred<Unit>()
        val job = CoroutineScope(Dispatchers.Default).launch {
            entry.whileEntering {
                outcome = GlobalContext.get().get<DuressSecretController>().createHiddenWallet(HIDDEN_PASSWORD.toCharArray())
                entered.complete(Unit)
                clockStopped.await()
                vault().lock()
            }
        }
        waitUntil(timeoutMillis = OPERATION_TIMEOUT_MS) { entered.isCompleted }
        mainClock.autoAdvance = false
        clockStopped.complete(Unit)
        waitUntil(timeoutMillis = OPERATION_TIMEOUT_MS) { job.isCompleted }
        drainMainDispatcher()
        mainClock.autoAdvance = true
        return outcome
    }

    // The app is up first: only then is it too late for the page to open.
    private fun ComposeUiTest.assertNothingRevealed(hud: List<HudMessage?>) {
        Thread.sleep(SETTLE_MS)
        waitForIdle()
        onNodeWithText(TERMS_AGREE).assertDoesNotExist()
        assertTrue(hud.none { it?.text == CREATED })
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
    fun unlock_legacyFormat_showsTheOldFormatNoticeWithTheResetOffered() = runDesktopComposeUiTest(
        testTimeout = TEST_TIMEOUT,
    ) {
        createPasswordAndLock()
        GlobalContext.get().get<DesktopVaultFiles>().vaultFile.writeBytes("PCKV".toByteArray() + 1)

        onAllNodes(hasSetTextAction())[0].performTextInput(PASSWORD)
        onNodeWithText(UNLOCK).performClick()
        awaitText(LEGACY_FORMAT)

        onNodeWithText(DELETE_DATA).performScrollTo().assertExists()
        onNodeWithText(DATA_UNREADABLE).assertDoesNotExist()
        onNodeWithText(CANCEL).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText(UNLOCK_TITLE).assertExists()
    }

    @Test
    fun forgotPassword_checkbox_gatesDeletionAndExposesItsState() = runDesktopComposeUiTest {
        val wipeCoordinator = mockk<DesktopWipeCoordinator>(relaxed = true) {
            every { progress } returns MutableStateFlow(WipeProgress.Idle)
        }
        val viewModel = ForgotPasswordViewModel(wipeCoordinator)
        setContent {
            TestWindowHost {
                ComposeAppTheme {
                    ForgotPasswordScreen(
                        dataUnreadable = false,
                        onCancelClick = {},
                        viewModel = viewModel,
                    )
                }
            }
        }

        onNodeWithText(DELETE_DATA).performScrollTo().performClick()
        drainMainDispatcher()
        verify(exactly = 0) { wipeCoordinator.wipe() }

        onNodeWithText(UNDERSTAND).assert(hasRole(Role.Checkbox)).assertIsOff()
        onNodeWithText(UNDERSTAND).performScrollTo().performClick()
        onNodeWithText(UNDERSTAND).assertIsOn()
        onNodeWithText(DELETE_DATA).performClick()
        drainMainDispatcher()
        verify(exactly = 1) { wipeCoordinator.wipe() }
    }

    private fun ComposeUiTest.createPasswordAndLock() {
        setContent {
            TestWindowHost {
                ComposeAppTheme { DesktopRoot(wipeFailed = false) }
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

    private fun ComposeUiTest.awaitAppOrTerms() = waitUntil(timeoutMillis = OPERATION_TIMEOUT_MS) {
        onAllNodesWithContentDescription(BALANCE_TAB).fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithText(TERMS_AGREE).fetchSemanticsNodes().isNotEmpty()
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
        const val LEGACY_FORMAT = "The data was created by an older version of the app and cannot be opened"
        const val CANCEL = "Cancel"
        const val BALANCE_TAB = "Balance"
        const val SETTLE_MS = 1_000L
        const val HIDDEN_PASSWORD = "Hidden-pass9word"
        const val CREATED = "Created"
        const val TERMS_AGREE = "I Agree"
    }
}
