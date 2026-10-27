package cash.p.terminal.modules.manageaccount.recoveryphrase

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.R
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.modules.main.PlainTestPage
import cash.p.terminal.modules.main.TestNav3Host
import cash.p.terminal.modules.manageaccount.safetyrules.SafetyRulesPage
import cash.p.terminal.modules.manageaccount.safetyrules.SafetyRulesViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertTrue
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

// The rule's host is enough here: the lost reveal was a write into a disposed composition's state, not a launch.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RecoveryPhraseRevealTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val recoveryPhraseViewModel = mockk<RecoveryPhraseViewModel>(relaxed = true) {
        every { wordsNumbered } returns listOf(RecoveryPhraseModule.WordNumbered(WORD, 1))
        every { safetyRulesAgreed } returns false
        every { passphrase } returns null
        every { qrGenerationError } returns false
        every { encryptedSeedQrContent } returns ""
    }
    private val navigation = HSNavigation(
        NavBackStack<HSPage>(
            PlainTestPage(),
            RecoveryPhrasePage(
                RecoveryPhrasePage.Input(mockk(relaxed = true), RecoveryPhrasePage.RecoveryPhraseType.Mnemonic)
            )
        )
    )

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    viewModel { recoveryPhraseViewModel }
                    single { mockk<IClipboardManager>(relaxed = true) }
                    viewModel { params -> SafetyRulesViewModel(params.get(), params.get(), mockk(relaxed = true)) }
                }
            )
        }
        composeRule.setContent {
            TestNav3Host(navigation)
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun showPhrase_safetyRulesAgreedAfterCallerCovered_showsPhrase() {
        composeRule.onNodeWithText(WORD).assertDoesNotExist()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.RecoveryPhrase_ShowPhrase)).performClick()
        composeRule.waitForIdle()
        val safetyRulesPage = navigation.backStack.last()
        assertTrue(safetyRulesPage is SafetyRulesPage)

        composeRule.runOnUiThread {
            navigation.setResult(safetyRulesPage, SafetyRulesPage.Result.AGREED)
            navigation.navigateUp()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(WORD).assertExists()
    }

    private companion object {
        const val WORD = "abandon"
    }
}
