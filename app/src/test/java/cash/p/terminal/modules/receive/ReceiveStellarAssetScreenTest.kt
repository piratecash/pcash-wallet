package cash.p.terminal.modules.receive

import android.app.Application
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.R
import cash.p.terminal.modules.activatetoken.ActivateTokenPage
import cash.p.terminal.modules.main.AndroidRecomposerNavHost
import cash.p.terminal.modules.main.PlainTestPage
import cash.p.terminal.modules.main.ScreenTestPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.wallet.Wallet
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReceiveStellarAssetScreenTest {

    private val wallet = mockk<Wallet>(relaxed = true)
    private val viewModel = mockk<ReceiveStellarAssetViewModel>(relaxed = true) {
        every { uiState } returns ReceiveStellarAssetUiState(
            viewState = ViewState.Loading,
            uri = "",
            address = "",
            mainNet = true,
            blockchainName = "Stellar",
            watchAccount = false,
            amount = null,
            activationRequired = true,
            coinCode = "USDC",
            trustlineEstablished = null,
        )
    }
    private val activateTokenViewModel = mockk<ActivateTokenViewModel>(relaxed = true) {
        every { uiState } returns ActivateTokenUiState(
            token = mockk(relaxed = true),
            currency = mockk(relaxed = true),
            activateEnabled = false,
            error = null,
            feeCoinValue = null,
            feeFiatValue = null,
        )
    }
    private val caller = ScreenTestPage { navigation ->
        ReceiveStellarAssetScreen(navigation, wallet, receiveEntryPoint = null, viewModel = viewModel)
    }
    private val navigation = HSNavigation(NavBackStack<HSPage>(PlainTestPage(), caller))
    private lateinit var host: AndroidRecomposerNavHost

    @Before
    fun setUp() {
        mockkConstructor(ActivateTokenViewModel.Factory::class)
        every {
            anyConstructed<ActivateTokenViewModel.Factory>().create(ActivateTokenViewModel::class.java)
        } returns activateTokenViewModel
        host = AndroidRecomposerNavHost(navigation)
    }

    @After
    fun tearDown() {
        host.close()
        unmockkConstructor(ActivateTokenViewModel.Factory::class)
    }

    @Test
    fun activate_tokenActivated_reportsActivationResult() {
        host.clickOnText(R.string.Button_Activate)
        val activateTokenPage = navigation.backStack.last()
        assertTrue(activateTokenPage is ActivateTokenPage)
        assertFalse(caller.composed)

        navigation.setResult(activateTokenPage, ActivateTokenPage.Result(activated = true))
        navigation.navigateUp()
        host.advanceFrames()

        verify { viewModel.onActivationResult(true) }
    }
}
