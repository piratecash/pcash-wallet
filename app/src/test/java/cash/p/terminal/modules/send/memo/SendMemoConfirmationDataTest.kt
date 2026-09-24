package cash.p.terminal.modules.send.memo

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.send.SendConfirmationData
import cash.p.terminal.wallet.entities.Coin
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SendMemoConfirmationDataTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val lifecycleOwner = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }
    private var liveFee by mutableStateOf(FEE)
    private var loads = 0
    private lateinit var shown: SendConfirmationData

    @Test
    fun rememberConfirmationData_feeChangesWhileResumed_showsNewFee() {
        setContent()

        composeTestRule.runOnIdle { liveFee = RAISED_FEE }

        composeTestRule.runOnIdle { assertEquals(RAISED_FEE, shown.fee) }
    }

    @Test
    fun rememberConfirmationData_resumedAfterPause_reloads() {
        setContent()

        composeTestRule.runOnIdle { lifecycleOwner.registry.currentState = Lifecycle.State.STARTED }
        composeTestRule.runOnIdle { lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED }

        composeTestRule.runOnIdle { assertEquals(2, loads) }
    }

    private fun setContent() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                shown = rememberConfirmationData(liveFee) {
                    loads++
                    confirmationData(liveFee)
                }
            }
        }
    }

    private fun confirmationData(fee: BigDecimal) = SendConfirmationData(
        amount = BigDecimal.ONE,
        fee = fee,
        address = Address("thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws"),
        contact = null,
        coin = Coin(uid = "tcy", name = "TCY", code = "TCY"),
        feeCoin = Coin(uid = "thorchain", name = "THORChain", code = "RUNE"),
        memo = null,
    )

    private companion object {
        val FEE: BigDecimal = BigDecimal("0.02")
        val RAISED_FEE: BigDecimal = BigDecimal("0.03")
    }
}
