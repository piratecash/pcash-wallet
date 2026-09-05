package cash.p.terminal.modules.balance.token

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TokenNotSyncedSectionTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun tokenNotSyncedSection_retryHidden_showsBannerWithoutRetry() {
        setContent(showRetry = false)

        composeTestRule.onNodeWithText("Data is not synchronized.").assertExists()
        composeTestRule.onNodeWithText("Try Again").assertDoesNotExist()
    }

    @Test
    fun tokenNotSyncedSection_retryVisible_showsRetryAndCallsOnRetry() {
        var retryCount = 0
        setContent(showRetry = true, onRetry = { retryCount++ })

        composeTestRule.onNodeWithText("Try Again").assertExists().performClick()

        assertEquals(1, retryCount)
    }

    private fun setContent(
        showRetry: Boolean,
        onRetry: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            ComposeAppTheme {
                TokenNotSyncedSection(
                    onBlockchainStatusClick = {},
                    onRetry = onRetry,
                    showRetry = showRetry,
                )
            }
        }
    }
}
