package cash.p.terminal.modules.restoreaccount

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.R
import cash.p.terminal.feature.wallet.WalletPlatformPages
import cash.p.terminal.modules.main.PlainTestPage
import cash.p.terminal.modules.main.TestNav3Host
import cash.p.terminal.modules.main.ScreenTestPage
import cash.p.terminal.modules.restoreaccount.restoremenu.RestoreMenuViewModel
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestoreMnemonicViewModel
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestorePhrase
import cash.p.terminal.modules.restoreaccount.restoremnemonicnonstandard.RestoreMnemonicNonStandardViewModel
import cash.p.terminal.modules.restoreaccount.restoremnemonicnonstandard.RestorePhraseNonStandard
import cash.p.terminal.navigation.AppPages
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.QrScannerResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The rule's host is enough here: the lost scan was a write into a disposed composition's state, not a launch.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RestorePhraseQrScanTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scanner = PlainTestPage()
    private val mnemonicInputs = mutableListOf<String>()

    // Flow-scoped in production: they outlive the caller's composition.
    private val restoreMenuViewModel = RestoreMenuViewModel()
    private val restoreViewModel = RestoreViewModel()

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single<AppPages> { mockk { every { qrScanner(any()) } returns scanner } }
                    single { mockk<WalletPlatformPages> { every { isQrScannerAvailable } returns true } }
                    viewModel { recordingMnemonicViewModel() }
                    viewModel {
                        RestoreMnemonicNonStandardViewModel(
                            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
                        )
                    }
                }
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun restorePhrase_scanResultAfterCallerCovered_showsScannedPhrase() {
        assertScannedPhraseShown(clearFirst = false) { navigation ->
            RestorePhrase(navigation, restoreMenuViewModel, restoreViewModel, {}, {}, {}, {})
        }
    }

    @Test
    fun restorePhrase_prefilledThenClearedAndScanned_viewModelKeepsScannedPhrase() {
        assertScannedPhraseShown(clearFirst = true) { navigation ->
            RestorePhrase(
                navigation, restoreMenuViewModel, restoreViewModel, {}, {}, {}, {},
                prefillWords = PREFILLED_PHRASE.split(" "),
            )
        }

        assertEquals(PHRASE, mnemonicInputs.last())
    }

    @Test
    fun restorePhraseNonStandard_scanResultAfterCallerCovered_showsScannedPhrase() {
        assertScannedPhraseShown(clearFirst = false) { navigation ->
            RestorePhraseNonStandard(navigation, restoreViewModel, {}, {})
        }
    }

    private fun assertScannedPhraseShown(clearFirst: Boolean, screen: @Composable (HSNavigation) -> Unit) {
        val caller = ScreenTestPage(screen)
        val navigation = HSNavigation(NavBackStack<HSPage>(PlainTestPage(), caller))
        composeRule.setContent {
            TestNav3Host(navigation)
        }
        if (clearFirst) composeRule.onNode(hasSetTextAction() and hasText(PREFILLED_PHRASE)).performTextClearance()

        clickScanQr()
        assertSame(scanner, navigation.backStack.last())
        assertFalse(caller.composed)
        composeRule.runOnUiThread {
            navigation.setResult(scanner, QrScannerResult(PHRASE))
            navigation.navigateUp()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(PHRASE).assertExists()
    }

    private fun recordingMnemonicViewModel() = spyk(
        RestoreMnemonicViewModel(
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        )
    ) {
        every { onEnterMnemonicPhrase(any(), any()) } answers {
            mnemonicInputs += firstArg<String>()
            callOriginal()
        }
        every { applyMnemonicPhrase(any(), any(), any(), any()) } answers {
            mnemonicInputs += firstArg<List<String>>().joinToString(" ")
            callOriginal()
        }
    }

    // The scan button has no label: it is the clickable laid out right before Paste.
    private fun clickScanQr() {
        val paste = hasText(composeRule.activity.getString(R.string.Send_Button_Paste))
        val clickables = composeRule.onAllNodes(hasClickAction())
        val pasteIndex = clickables.fetchSemanticsNodes().indexOfFirst { paste.matches(it) }
        clickables[pasteIndex - 1].performClick()
        // The scanner opens after the focus-clearing delay.
        composeRule.mainClock.advanceTimeBy(SCAN_OPEN_DELAY_MS)
        composeRule.waitForIdle()
    }

    private companion object {
        const val SCAN_OPEN_DELAY_MS = 200L
        const val PREFILLED_PHRASE = "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong"
        const val PHRASE = "abandon ability able about above absent absorb abstract absurd abuse access accident"
    }
}
