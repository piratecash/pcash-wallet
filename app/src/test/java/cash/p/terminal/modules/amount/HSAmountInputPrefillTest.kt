package cash.p.terminal.modules.amount

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.modules.address.AmountUnique
import cash.p.terminal.modules.main.PlainTestPage
import cash.p.terminal.modules.main.ScreenTestPage
import cash.p.terminal.modules.main.TestNav3Host
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HSAmountInputPrefillTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val prefill = AmountUnique(BigDecimal("0.002"))
    private val amounts = mutableListOf<BigDecimal?>()

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single<IClipboardManager> { mockk(relaxed = true) }
                    viewModel { params -> AmountInputViewModel2(params[0], params[1], params[2], params[3]) }
                }
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun amountUnique_editedThenCallerCoveredAndShownAgain_keepsEditedAmount() {
        val caller = ScreenTestPage {
            HSAmountInput(
                availableBalance = BigDecimal.ONE,
                coinCode = "BTC",
                coinDecimal = 8,
                fiatDecimal = 2,
                onClickHint = {},
                onValueChange = { amounts += it },
                inputType = AmountInputType.COIN,
                rate = null,
                amountUnique = prefill,
            )
        }
        val navigation = HSNavigation(NavBackStack<HSPage>(PlainTestPage(), caller))
        composeRule.setContent { TestNav3Host(navigation) }
        composeRule.onNode(hasSetTextAction() and hasText("0.002")).performTextReplacement("0.001")

        composeRule.runOnUiThread { navigation.slideFromRight(PlainTestPage()) }
        composeRule.waitForIdle()
        assertFalse(caller.composed)
        composeRule.runOnUiThread { navigation.navigateUp() }
        composeRule.waitForIdle()

        assertEquals(0, BigDecimal("0.001").compareTo(amounts.last()))
        composeRule.onNode(hasSetTextAction() and hasText("0.001")).assertExists()
    }
}
