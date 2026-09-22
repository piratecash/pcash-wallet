package cash.p.terminal.modules.receive.viewmodels

import androidx.lifecycle.ViewModelStore
import cash.p.beam.BeamAddress
import cash.p.beam.BeamAddressType
import cash.p.beam.BeamNetwork
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Flowable
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.math.BigDecimal
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiveAddressViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val testDispatcherProvider = TestDispatcherProvider(dispatcher, testScope)

    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val adapter = mockk<IReceiveAdapter>(relaxed = true)
    private val initializationInProgress = MutableStateFlow(false)

    private val account = mockk<Account> {
        every { isWatchAccount } returns false
    }
    private val coin = mockk<Coin> {
        every { code } returns "ZEC"
    }
    private val blockchain = mockk<Blockchain> {
        every { name } returns "Zcash"
    }
    private val token = mockk<Token>(relaxed = true) {
        every { this@mockk.blockchain } returns this@ReceiveAddressViewModelTest.blockchain
        every { blockchainType } returns BlockchainType.Zcash
        every { type } returns TokenType.Native
    }
    private val wallet = mockk<Wallet>(relaxed = true) {
        every { this@mockk.account } returns this@ReceiveAddressViewModelTest.account
        every { this@mockk.coin } returns this@ReceiveAddressViewModelTest.coin
        every { this@mockk.token } returns this@ReceiveAddressViewModelTest.token
    }

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { adapterManager.adaptersReadyObservable } returns Flowable.empty()
        every { adapterManager.initializationInProgressFlow } returns initializationInProgress
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns null
        coEvery { adapterManager.getReceiveAddressForWallet(wallet) } returns null
        every { adapter.receiveAddress } returns ADAPTER_ADDRESS
        every { adapter.isMainNet } returns true
        every { adapter.isAddressHistorySupported } returns false
        every { adapter.usedAddresses(any()) } returns emptyList()
        coEvery { adapter.isAddressActive(any()) } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        wallet: Wallet = this.wallet,
        beamAddressProvider: BeamReceiveAddressProvider? = null,
    ) = ReceiveAddressViewModel(
        wallet = wallet,
        adapterManager = adapterManager,
        dispatcherProvider = testDispatcherProvider,
        beamAddressProvider = beamAddressProvider,
    )

    @Test
    fun setData_adapterMissingWhileInitializing_showsLoading() = runTest(dispatcher) {
        initializationInProgress.value = true

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ViewState.Loading, viewModel.uiState.viewState)
    }

    @Test
    fun setData_adapterMissingAfterInitFinished_showsError() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.viewState is ViewState.Error)
    }

    @Test
    fun setData_adapterMissingButFallbackAddressAvailable_showsSuccessWithAddress() =
        runTest(dispatcher) {
            coEvery { adapterManager.getReceiveAddressForWallet(wallet) } returns FALLBACK_ADDRESS

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(ViewState.Success, viewModel.uiState.viewState)
            assertEquals(FALLBACK_ADDRESS, viewModel.uiState.address)
            assertEquals(null, viewModel.uiState.beamAddressType)
        }

    @Test
    fun setData_isAddressActiveThrows_keepsSuccessAndAssumesActive() = runTest(dispatcher) {
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
        coEvery { adapter.isAddressActive(any()) } throws IllegalStateException("no network")

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ViewState.Success, viewModel.uiState.viewState)
        assertEquals(ADAPTER_ADDRESS, viewModel.uiState.address)
        assertEquals(false, viewModel.uiState.showTronAlert)
    }

    @Test
    fun setData_isAddressActiveReturnsFalse_keepsSuccessAndShowsNotActive() = runTest(dispatcher) {
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
        coEvery { adapter.isAddressActive(any()) } returns false

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ViewState.Success, viewModel.uiState.viewState)
        assertEquals(true, viewModel.uiState.showTronAlert)
    }

    @Test
    fun initializationFinished_adapterAppears_replacesFallbackWithAdapterAddress() =
        runTest(dispatcher) {
            initializationInProgress.value = true
            coEvery { adapterManager.getReceiveAddressForWallet(wallet) } returns FALLBACK_ADDRESS

            val viewModel = createViewModel()
            advanceUntilIdle()
            assertEquals(FALLBACK_ADDRESS, viewModel.uiState.address)

            every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
            initializationInProgress.value = false
            advanceUntilIdle()

            assertEquals(ViewState.Success, viewModel.uiState.viewState)
            assertEquals(ADAPTER_ADDRESS, viewModel.uiState.address)
        }

    @Test
    fun beamReceiveToken_neverBecomesGenericAmountUri() = runTest(dispatcher) {
        every { token.blockchainType } returns BlockchainType.Beam
        every { adapterManager.getReceiveAdapterForWallet(wallet) } returns adapter
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setAmount(BigDecimal.ONE)

        assertEquals(ADAPTER_ADDRESS, viewModel.uiState.uri)
        assertEquals(null, viewModel.uiState.amount)
    }

    @Test
    fun init_beamBeforeSync_requestsPublicOfflineAddress() = runTest(dispatcher) {
        every { token.blockchainType } returns BlockchainType.Beam
        val provider = beamProvider()
        coEvery {
            provider.receiveAddress(account, BeamAddressType.PublicOffline)
        } returns beamAddress(PUBLIC_ADDRESS, BeamAddressType.PublicOffline)

        val viewModel = createViewModel(beamAddressProvider = provider)
        advanceUntilIdle()

        assertEquals(BeamAddressType.PublicOffline, viewModel.uiState.beamAddressType)
        assertEquals(PUBLIC_ADDRESS, viewModel.uiState.address)
        assertEquals(ViewState.Success, viewModel.uiState.viewState)
    }

    @Test
    fun init_beamSessionInvalidatedBeforePublication_doesNotExposeToken() = runTest(dispatcher) {
        every { token.blockchainType } returns BlockchainType.Beam
        val provider = beamProvider()
        coEvery { provider.receiveAddress(account, BeamAddressType.PublicOffline) } returns
            BeamReceiveAddress(
                BeamAddress(PUBLIC_ADDRESS, BeamAddressType.PublicOffline, BeamNetwork.Mainnet)
            ) { false }

        val viewModel = createViewModel(beamAddressProvider = provider)
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.address)
        assertEquals("", viewModel.uiState.uri)
        assertTrue(viewModel.uiState.viewState is ViewState.Error)
    }

    @Test
    fun onBeamAddressTypeSelect_staleCompletion_keepsSelectedTypeAddress() = runTest(dispatcher) {
        every { token.blockchainType } returns BlockchainType.Beam
        val publicResult = CompletableDeferred<BeamReceiveAddress>()
        val offlineResult = CompletableDeferred<BeamReceiveAddress>()
        val provider = beamProvider()
        coEvery {
            provider.receiveAddress(account, BeamAddressType.PublicOffline)
        } coAnswers { publicResult.await() }
        coEvery {
            provider.receiveAddress(account, BeamAddressType.Offline)
        } coAnswers { offlineResult.await() }
        val viewModel = createViewModel(beamAddressProvider = provider)

        viewModel.onBeamAddressTypeSelect(BeamAddressType.Offline)
        assertEquals(BeamAddressType.Offline, viewModel.uiState.beamAddressType)
        assertEquals("", viewModel.uiState.address)
        assertEquals(ViewState.Loading, viewModel.uiState.viewState)

        offlineResult.complete(beamAddress(OFFLINE_ADDRESS, BeamAddressType.Offline))
        advanceUntilIdle()
        publicResult.complete(beamAddress(PUBLIC_ADDRESS, BeamAddressType.PublicOffline))
        advanceUntilIdle()

        assertEquals(BeamAddressType.Offline, viewModel.uiState.beamAddressType)
        assertEquals(OFFLINE_ADDRESS, viewModel.uiState.address)
        assertEquals(OFFLINE_ADDRESS, viewModel.uiState.uri)
    }

    @Test
    fun onBeamAddressTypeSelect_ownerChanged_retryKeepsSelectedType() = runTest(dispatcher) {
        every { token.blockchainType } returns BlockchainType.Beam
        val provider = beamProvider()
        coEvery {
            provider.receiveAddress(account, BeamAddressType.PublicOffline)
        } returns beamAddress(PUBLIC_ADDRESS, BeamAddressType.PublicOffline)
        coEvery {
            provider.receiveAddress(account, BeamAddressType.MaxPrivacy)
        } throws CancellationException("BEAM account request was superseded")
        val viewModel = createViewModel(beamAddressProvider = provider)
        advanceUntilIdle()

        viewModel.onBeamAddressTypeSelect(BeamAddressType.MaxPrivacy)
        advanceUntilIdle()
        assertEquals(BeamAddressType.MaxPrivacy, viewModel.uiState.beamAddressType)
        assertEquals("", viewModel.uiState.address)
        assertTrue(viewModel.uiState.viewState is ViewState.Error)

        coEvery {
            provider.receiveAddress(account, BeamAddressType.MaxPrivacy)
        } returns beamAddress(MAX_PRIVACY_ADDRESS, BeamAddressType.MaxPrivacy)
        viewModel.onErrorClick()
        advanceUntilIdle()

        assertEquals(MAX_PRIVACY_ADDRESS, viewModel.uiState.address)
        assertEquals(ViewState.Success, viewModel.uiState.viewState)
        coVerify(exactly = 2) {
            provider.receiveAddress(account, BeamAddressType.MaxPrivacy)
        }
    }

    @Test
    fun init_sameMnemonicDifferentAccounts_keepsAddressRequestsIsolated() = runTest(dispatcher) {
        every { token.blockchainType } returns BlockchainType.Beam
        val mnemonic = AccountType.Mnemonic(List(12) { "word$it" }, "")
        val firstAccount = account("first-account", mnemonic)
        val secondAccount = account("second-account", mnemonic)
        val provider = beamProvider()
        coEvery {
            provider.receiveAddress(any(), BeamAddressType.PublicOffline)
        } coAnswers {
            beamAddress("${firstArg<Account>().id}-address", BeamAddressType.PublicOffline)
        }

        val first = createViewModel(walletFor(firstAccount), provider)
        val second = createViewModel(walletFor(secondAccount), provider)
        advanceUntilIdle()

        assertEquals("first-account-address", first.uiState.address)
        assertEquals("second-account-address", second.uiState.address)
        coVerify(exactly = 1) {
            provider.receiveAddress(firstAccount, BeamAddressType.PublicOffline)
        }
        coVerify(exactly = 1) {
            provider.receiveAddress(secondAccount, BeamAddressType.PublicOffline)
        }
    }

    @Test
    fun clear_beamRequestCompletesLate_doesNotExposeAddress() = runTest(dispatcher) {
        every { token.blockchainType } returns BlockchainType.Beam
        val result = CompletableDeferred<BeamReceiveAddress>()
        val provider = beamProvider()
        coEvery {
            provider.receiveAddress(account, BeamAddressType.PublicOffline)
        } coAnswers { result.await() }
        val viewModel = createViewModel(beamAddressProvider = provider)
        val store = ViewModelStore().also { it.put("beam-receive", viewModel) }

        store.clear()
        result.complete(beamAddress(PUBLIC_ADDRESS, BeamAddressType.PublicOffline))
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.address)
        assertEquals("", viewModel.uiState.uri)
    }

    private fun walletFor(account: Account) = mockk<Wallet>(relaxed = true) {
        every { this@mockk.account } returns account
        every { this@mockk.coin } returns this@ReceiveAddressViewModelTest.coin
        every { this@mockk.token } returns this@ReceiveAddressViewModelTest.token
    }

    private fun account(id: String, type: AccountType) = Account(
        id = id,
        name = id,
        type = type,
        origin = AccountOrigin.Created,
        level = 0,
    )

    private fun beamAddress(token: String, type: BeamAddressType) =
        BeamReceiveAddress(BeamAddress(token, type, BeamNetwork.Mainnet)) { true }

    private fun beamProvider() = mockk<BeamReceiveAddressProvider> {
        every { changes(any()) } returns flowOf(Unit)
    }

    private companion object {
        const val ADAPTER_ADDRESS = "u1adapteraddress"
        const val FALLBACK_ADDRESS = "u1fallbackaddress"
        const val PUBLIC_ADDRESS = "public-offline-address"
        const val OFFLINE_ADDRESS = "offline-address"
        const val MAX_PRIVACY_ADDRESS = "max-privacy-address"
    }
}
