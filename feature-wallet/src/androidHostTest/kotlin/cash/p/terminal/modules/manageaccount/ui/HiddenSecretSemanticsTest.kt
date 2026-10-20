package cash.p.terminal.modules.manageaccount.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import cash.p.terminal.modules.manageaccount.recoveryphrase.RecoveryPhraseModule
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Assertions use the merged tree on purpose: it is what accessibility services read.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HiddenSecretSemanticsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun seedPhraseList_hidden_secretAbsentFromSemantics() {
        setSeedPhraseList(hidden = true)

        composeTestRule.onNodeWithText(SECRET, substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Tap to show recovery phrase", substring = true).assertExists()
    }

    @Test
    fun seedPhraseList_shown_secretInSemantics() {
        setSeedPhraseList(hidden = false)

        composeTestRule.onNodeWithText(SECRET, substring = true).assertExists()
    }

    @Test
    fun hidableContent_hidden_secretAbsentFromSemantics() {
        setHidableContent(hideScreenText = HIDE_TEXT)

        composeTestRule.onNodeWithText(SECRET, substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText(HIDE_TEXT, substring = true).assertExists()
    }

    @Test
    fun hidableContent_notHidable_secretInSemantics() {
        setHidableContent(hideScreenText = null)

        composeTestRule.onNodeWithText(SECRET, substring = true).assertExists()
    }

    private fun setSeedPhraseList(hidden: Boolean) {
        composeTestRule.setContent {
            ComposeAppTheme {
                SeedPhraseList(
                    wordsNumbered = listOf(RecoveryPhraseModule.WordNumbered(SECRET, 1)),
                    hidden = hidden,
                    onClick = {},
                )
            }
        }
    }

    private fun setHidableContent(hideScreenText: String?) {
        composeTestRule.setContent {
            ComposeAppTheme {
                HidableContent(content = SECRET, hideScreenText = hideScreenText)
            }
        }
    }

    private companion object {
        const val SECRET = "zqxsecretword"
        const val HIDE_TEXT = "Tap to show key"
    }
}
