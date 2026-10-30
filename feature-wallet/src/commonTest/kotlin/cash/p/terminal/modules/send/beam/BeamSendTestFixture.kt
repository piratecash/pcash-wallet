package cash.p.terminal.modules.send.beam

import androidx.lifecycle.viewModelScope
import cash.p.beam.BeamAddressType
import cash.p.beam.BeamBalance
import cash.p.beam.BeamOfflineSigningState
import cash.p.beam.BeamSendQuote
import cash.p.beam.BeamWalletState
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.BeamSendCoordinator
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.wallet.Wallet
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** Shared collaborators for the BEAM send ViewModel tests; each class drives it from its own @Before/@After. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class BeamSendTestFixture {
    protected val dispatcher = UnconfinedTestDispatcher()
    internal val access = mockk<BeamSendSession>()
    protected val session = mockk<BeamSessionOwner.Session>()
    protected val wallet = mockk<Wallet>()
    protected val operations = mockk<BeamOfflineOperations>()
    protected val repository = mockk<OfflineSignedTransactionRepository>(relaxed = true)
    protected val rates = mockk<XRateService>()
    protected val state = MutableStateFlow<BeamWalletState>(BeamWalletState.Ready(10))
    protected val balance = MutableStateFlow(BeamBalance(500_000_000, isAuthoritative = true))
    protected val context = MutableStateFlow<BeamOfflineSigningState>(BeamOfflineSigningState.Ready("context", 10, 20))
    protected val quote = BeamSendQuote(100_000_000, 29, 100_000_029, 29, 0, 0, 1, 0,
        BeamAddressType.Offline, "version", "context", "rules")
    internal lateinit var model: BeamSendViewModel

    protected fun setUpFixture() {
        Dispatchers.setMain(dispatcher)
        mockkObject(BeamRecipient)
        every { BeamRecipient.type(any()) } answers {
            BeamAddressType.entries.firstOrNull { it.name == firstArg<String>() }
        }
        every { wallet.coin.uid } returns "beam"
        every { wallet.account.id } returns "account"
        every { access.session } returns session
        every { access.current } returns true
        every { access.canSign } returns true
        every { access.releaseOffline() } just Runs
        every { session.wallet.state } returns state
        every { session.wallet.balance } returns balance
        every { session.wallet.offlineSigningState } returns context
        every { rates.getRate(any()) } returns null
        every { rates.getRateFlow(any()) } returns emptyFlow()
        coEvery { access.quote(any()) } answers { BeamSendCoordinator.Quote(session, firstArg(), quote) }
        coEvery { access.quoteOffline(any()) } answers { BeamSendCoordinator.Quote(session, firstArg(), quote) }
        model = createModel()
        model.onRecipientChanged("Offline")
        model.onAmountChanged("1")
    }

    private fun createModel() = BeamSendViewModel(wallet, access, operations,
        TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)), mockk(), repository, rates)

    protected fun tearDownFixture() {
        model.viewModelScope.cancel()
        unmockkObject(BeamRecipient)
        Dispatchers.resetMain()
    }
}
