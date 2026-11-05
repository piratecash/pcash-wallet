package cash.p.terminal.modules.walletconnect.session

import android.os.Looper
import androidx.lifecycle.Observer
import cash.p.terminal.core.managers.ConnectivityManager
import cash.p.terminal.modules.walletconnect.WCDelegate
import cash.p.terminal.modules.walletconnect.WCManager
import cash.p.terminal.modules.walletconnect.WCSessionManager
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.wallet.Account
import com.reown.walletkit.client.Wallet
import com.reown.walletkit.client.WalletKit
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class WCSessionViewModelTest {

    private val proposal = mockk<Wallet.Model.SessionProposal>(relaxed = true) {
        every { proposerPublicKey } returns KEY
    }
    private val sessionManager = mockk<WCSessionManager>(relaxed = true)
    private val wcManager = mockk<WCManager>(relaxed = true)
    private val connectivityManager = mockk<ConnectivityManager> {
        every { isConnected } returns MutableStateFlow(true)
    }
    private val account = mockk<Account>(relaxed = true)

    private var closeCount = 0
    private val shownErrors = mutableListOf<String?>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(WCDelegate, WalletKit, Translator)
        // WalletKit methods may be @JvmStatic, which mockkObject does not intercept.
        mockkStatic(WalletKit::class)
        every { Translator.getString(any()) } returns TRANSLATED
        every { Translator.getString(any(), *anyVararg()) } returns TRANSLATED
        every { WCDelegate.connectionAvailableEvent } returns MutableStateFlow(true)
        every { WCDelegate.sessionProposalEvent } returns (proposal to mockk(relaxed = true))
        every { WCDelegate.sessionProposalEvent = any() } just runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): WCSessionViewModel {
        val viewModel = WCSessionViewModel(
            sessionManager = sessionManager,
            connectivityManager = connectivityManager,
            account = account,
            topic = null,
            wcManager = wcManager,
        )
        viewModel.closeLiveEvent.observeForever(Observer { closeCount++ })
        viewModel.showErrorLiveEvent.observeForever(Observer { shownErrors += it })
        return viewModel
    }

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()

    private fun otherProposal() = mockk<Wallet.Model.SessionProposal> {
        every { proposerPublicKey } returns "other-key"
    }

    @Test
    fun rejectProposal_proposalMissingOtherPresent_closesWithoutCrash() {
        every { WalletKit.getSessionProposals() } returns listOf(otherProposal())
        val viewModel = createViewModel()

        viewModel.rejectProposal()
        idleMainLooper()

        assertEquals(1, closeCount)
        verify(exactly = 0) { WalletKit.rejectSession(any(), any(), any()) }
    }

    @Test
    fun rejectProposal_proposalListEmpty_closes() {
        every { WalletKit.getSessionProposals() } returns emptyList()
        val viewModel = createViewModel()

        viewModel.rejectProposal()
        idleMainLooper()

        assertEquals(1, closeCount)
    }

    @Test
    fun rejectProposal_rejectSessionError_closes() {
        every { WalletKit.getSessionProposals() } returns listOf(proposal)
        val onError = slot<(Wallet.Model.Error) -> Unit>()
        every { WalletKit.rejectSession(any(), any(), capture(onError)) } answers {
            onError.captured(Wallet.Model.Error(IllegalStateException("relay down")))
        }
        val viewModel = createViewModel()

        viewModel.rejectProposal()
        idleMainLooper()

        assertEquals(1, closeCount)
    }

    @Test
    fun rejectProposal_proposalPresent_callsRejectSessionAndCloses() {
        every { WalletKit.getSessionProposals() } returns listOf(proposal)
        val onSuccess = slot<(Wallet.Params.SessionReject) -> Unit>()
        every { WalletKit.rejectSession(any(), capture(onSuccess), any()) } answers {
            onSuccess.captured(firstArg())
        }
        val viewModel = createViewModel()

        viewModel.rejectProposal()
        idleMainLooper()

        verify { WalletKit.rejectSession(match { it.proposerPublicKey == KEY }, any(), any()) }
        assertEquals(1, closeCount)
    }

    @Test
    fun connect_proposalMissing_invalidatesAndHidesConnect() {
        every { WalletKit.getSessionProposals() } returns emptyList()
        val viewModel = createViewModel()

        viewModel.connect()
        idleMainLooper()

        val state = viewModel.uiState
        assertEquals(WCButtonState.Hidden, state.buttonStates?.connect)
        assertEquals(WCButtonState.Enabled, state.buttonStates?.cancel)
        assertEquals(TRANSLATED, state.showError)
        assertTrue(shownErrors.isEmpty())
    }

    @Test
    fun connect_approveSessionError_postsErrorAndKeepsConnect() {
        every { WalletKit.getSessionProposals() } returns listOf(proposal)
        every { WalletKit.generateApprovedNamespaces(any(), any()) } returns emptyMap()
        val onError = slot<(Wallet.Model.Error) -> Unit>()
        every { WalletKit.approveSession(any(), any(), capture(onError)) } answers {
            onError.captured(Wallet.Model.Error(IllegalStateException("relay down")))
        }
        val viewModel = createViewModel()

        viewModel.connect()
        idleMainLooper()

        assertEquals(listOf<String?>("relay down"), shownErrors)
        assertEquals(WCButtonState.Enabled, viewModel.uiState.buttonStates?.connect)
        assertNull(viewModel.uiState.showError)
    }

    private companion object {
        const val KEY = "proposal-key"
        const val TRANSLATED = "translated"
    }
}
