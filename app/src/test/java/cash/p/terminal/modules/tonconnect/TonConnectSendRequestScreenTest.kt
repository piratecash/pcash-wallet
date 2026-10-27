package cash.p.terminal.modules.tonconnect

import android.app.Application
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.R
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.managers.TransactionHiddenManager
import cash.p.terminal.modules.main.AndroidRecomposerNavHost
import cash.p.terminal.modules.main.PlainTestPage
import cash.p.terminal.modules.main.ScreenTestPage
import cash.p.terminal.modules.pin.ConfirmPinPage
import cash.p.terminal.modules.pin.core.UptimeProvider
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import io.horizontalsystems.core.ILockoutStorage
import io.horizontalsystems.core.IPinComponent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TonConnectSendRequestScreenTest {

    private val pinComponent = mockk<IPinComponent>(relaxed = true) {
        every { isPinSet } returns true
    }
    private val viewModel = mockk<TonConnectSendRequestViewModel>(relaxed = true) {
        every { uiState } returns TonConnectSendRequestUiState(
            tonTransactionRecord = null,
            error = null,
            confirmEnabled = true,
            rejectEnabled = true,
        )
    }
    private val caller = ScreenTestPage { navigation -> TonConnectSendRequestScreen(navigation, viewModel) }
    private val navigation = HSNavigation(NavBackStack<HSPage>(PlainTestPage(), caller))
    private lateinit var host: AndroidRecomposerNavHost

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single { pinComponent }
                    single { mockk<ILockoutStorage>(relaxed = true) }
                    single { mockk<UptimeProvider>(relaxed = true) }
                    single { mockk<ILocalStorage>(relaxed = true) }
                    single { mockk<TransactionHiddenManager>(relaxed = true) }
                }
            )
        }
        host = AndroidRecomposerNavHost(navigation)
    }

    @After
    fun tearDown() {
        host.close()
        stopKoin()
    }

    @Test
    fun confirm_pinSetAndPinConfirmed_sendsRequest() {
        host.clickOnText(R.string.Button_Confirm)
        val confirmPinPage = navigation.backStack.last()
        assertTrue(confirmPinPage is ConfirmPinPage)
        assertFalse(caller.composed)

        navigation.setResult(confirmPinPage, ConfirmPinPage.Result(true))
        navigation.navigateUp()
        host.advanceFrames()

        verify { viewModel.confirm() }
    }
}
