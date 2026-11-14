package cash.p.terminal.modules.fee

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FeeInfoSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun feeInfoSection_balanceHidden_hidesFeeDetails() {
        setContent(balanceHidden = true)

        composeTestRule.onNodeWithText(FEE_DETAIL).assertDoesNotExist()
    }

    @Test
    fun feeInfoSection_balanceShown_showsFeeDetails() {
        setContent(balanceHidden = false)

        composeTestRule.onNodeWithText(FEE_DETAIL).assertIsDisplayed()
    }

    private fun setContent(balanceHidden: Boolean) = composeTestRule.setContent {
        ComposeAppTheme {
            FeeInfoSection(
                tokenIn = null,
                displayBalance = null,
                balanceHidden = balanceHidden,
                feeToken = null,
                feeCoinBalance = null,
                feePrimary = "0.345 TRX",
                feeSecondary = "",
                insufficientFeeBalance = false,
                onBalanceClicked = {},
                feeDetails = { Text(FEE_DETAIL) },
            )
        }
    }

    private companion object {
        const val FEE_DETAIL = "0.345 TRX burned"
    }
}
