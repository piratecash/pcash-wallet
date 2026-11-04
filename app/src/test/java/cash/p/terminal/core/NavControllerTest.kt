package cash.p.terminal.core

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.core.managers.TransactionHiddenManager
import cash.p.terminal.modules.main.AndroidRecomposerNavHost
import cash.p.terminal.modules.main.PlainTestPage
import cash.p.terminal.modules.main.ScreenTestPage
import cash.p.terminal.modules.pin.ConfirmPinPage
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.pin.SetPinPage
import cash.p.terminal.modules.pin.core.UptimeProvider
import cash.p.terminal.modules.pin.hiddenwallet.HiddenWalletPinPolicy
import cash.p.terminal.modules.premium.about.AboutPremiumPage
import cash.p.terminal.modules.premium.about.AboutPremiumUiState
import cash.p.terminal.modules.premium.about.AboutPremiumViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.premium.domain.usecase.PremiumType
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.core.ILockoutStorage
import io.horizontalsystems.core.IPinComponent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.PinSet_Title

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NavControllerTest {

    private val pinComponent = mockk<IPinComponent>(relaxed = true)
    private val checkPremiumUseCase = mockk<CheckPremiumUseCase>()
    private val received = mutableListOf<Any>()
    private lateinit var navigation: HSNavigation
    private lateinit var host: AndroidRecomposerNavHost

    @After
    fun tearDown() {
        host.close()
        stopKoin()
    }

    @Test
    fun rememberAuthorizedAction_pinNotSet_runsActionAtOnce() {
        val authorize = show { rememberAuthorizedAction<String>(input = null) { received += it } }

        authorize("arg")

        assertEquals(listOf("arg"), received)
        assertTrue(navigation.backStack.last() is ScreenTestPage)
    }

    @Test
    fun rememberAuthorizedAction_pinConfirmed_runsActionOnceWithClickTimeArg() {
        every { pinComponent.isPinSet } returns true
        val authorize = show { rememberAuthorizedAction<String>(input = null) { received += it } }

        authorize("arg")
        host.advanceFrames()
        finishTopPage<ConfirmPinPage>(ConfirmPinPage.Result(true))

        assertEquals(listOf("arg"), received)
    }

    @Test
    fun rememberAuthorizedAction_pinNotConfirmed_doesNotRunAction() {
        every { pinComponent.isPinSet } returns true
        val authorize = show { rememberAuthorizedAction<String>(input = null) { received += it } }

        authorize("arg")
        host.advanceFrames()
        finishTopPage<ConfirmPinPage>(ConfirmPinPage.Result(false))

        assertTrue(received.isEmpty())
    }

    @Test
    fun rememberEnsurePinSetPremiumAction_noPremiumAndNoPin_setsAfterPremiumThenPin() {
        every { checkPremiumUseCase.getPremiumType() } returns PremiumType.NONE
        val toggle = show {
            rememberEnsurePinSetPremiumAction(Res.string.PinSet_Title, PinType.REGULAR) { received += it }
        }

        toggle(true)
        host.advanceFrames()
        finishTopPage<AboutPremiumPage>(AboutPremiumPage.Result())
        finishTopPage<SetPinPage>(SetPinPage.Result(true))

        assertEquals(listOf(true), received)
    }

    @Test
    fun rememberEnsurePinSetPremiumAction_disabled_setsAtOnce() {
        every { checkPremiumUseCase.getPremiumType() } returns PremiumType.NONE
        val toggle = show {
            rememberEnsurePinSetPremiumAction(Res.string.PinSet_Title, PinType.REGULAR) { received += it }
        }

        toggle(false)

        assertEquals(listOf(false), received)
        assertTrue(navigation.backStack.last() is ScreenTestPage)
    }

    /** Hosts [remember] on a page of its own and returns the lambda of its latest composition. */
    private fun <T> show(remember: @Composable HSNavigation.() -> T): T {
        startKoin { modules(testModule()) }
        var latest: T? = null
        val caller = ScreenTestPage { navigation ->
            val value = navigation.remember()
            SideEffect { latest = value }
        }
        navigation = HSNavigation(NavBackStack<HSPage>(PlainTestPage(), caller))
        host = AndroidRecomposerNavHost(navigation)
        return checkNotNull(latest)
    }

    private inline fun <reified P : HSPage> finishTopPage(result: Any) {
        val page = navigation.backStack.last()
        assertEquals(P::class, page::class)
        navigation.setResult(page, result)
        navigation.navigateUp()
        host.advanceFrames()
    }

    private fun testModule() = module {
        single { pinComponent }
        single { checkPremiumUseCase }
        single { mockk<ILockoutStorage>(relaxed = true) }
        single { mockk<UptimeProvider>(relaxed = true) }
        single { mockk<ILocalStorage>(relaxed = true) }
        single { mockk<TransactionHiddenManager>(relaxed = true) }
        single { mockk<UserManager>(relaxed = true) }
        factory { mockk<HiddenWalletPinPolicy>(relaxed = true) }
        viewModel {
            mockk<AboutPremiumViewModel>(relaxed = true) {
                every { uiState } returns AboutPremiumUiState()
                every { uiEvents } returns emptyFlow()
            }
        }
    }
}
