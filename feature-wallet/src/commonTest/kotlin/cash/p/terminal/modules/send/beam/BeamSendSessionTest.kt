package cash.p.terminal.modules.send.beam

import cash.p.beam.BeamOfflineSignResult
import cash.p.beam.BeamQuoteRequest
import cash.p.beam.BeamOfflineSendState
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.adapters.BeamAdapter
import cash.p.terminal.core.managers.BeamNetwork
import cash.p.terminal.core.managers.BeamSendCoordinator
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class BeamSendSessionTest {
    private val wallet = mockk<Wallet> {
        every { account.id } returns "account"
        every { account.type } returns mockk<AccountType.Mnemonic>()
        every { coin.uid } returns "beam"
        every { token.blockchainType } returns BlockchainType.Beam
        every { token.type } returns TokenType.Native
        every { token.decimals } returns 8
    }
    private val session = mockk<BeamSessionOwner.Session> {
        every { accountId } returns "account"
        every { network } returns BeamNetwork.Mainnet
    }
    private val owner = mockk<BeamSessionOwner> { every { current } returns session }
    private val adapter = mockk<BeamAdapter>(relaxUnitFun = true) {
        every { accountId } returns "account"
        every { isNetworkPaused } returns false
    }
    private val offlineMode = mockk<OfflineModeManager> {
        every { isNetworkPaused(any()) } returns false
        every { stateFlow } returns MutableStateFlow(emptyMap())
    }
    private val coordinator = mockk<BeamSendCoordinator>()
    private val quote = mockk<BeamSendCoordinator.Quote>()

    @Test
    fun sign_networkRunning_drainsWithoutCriticalLeaseBeforeSigning() = runTest(UnconfinedTestDispatcher()) {
        var drained = false
        coEvery { adapter.pauseNetworkAndAwait() } answers { drained = true }
        coEvery { coordinator.signOffline(quote) } answers {
            assertTrue(drained)
            BeamOfflineSignResult("tx", BeamOfflineSendState.Signed)
        }
        val access = BeamSendSession(wallet, session, adapter, owner, coordinator, offlineMode,
            TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler), this))
        access.sign(quote)
        coVerify(exactly = 0) { adapter.withCriticalOperation<Any>(any()) }
        coVerify(exactly = 0) { coordinator.confirmQuote(any()) }
        verify(exactly = 0) { adapter.resumeNetwork() }
        access.releaseOffline()
        verify(exactly = 1) { adapter.resumeNetwork() }
    }

    @Test
    fun quoteOffline_networkRunning_quotesOnlyOnceDrainedAndResumesOnRelease() = runTest(UnconfinedTestDispatcher()) {
        var drained = false
        val request = mockk<BeamQuoteRequest>()
        coEvery { adapter.pauseNetworkAndAwait() } answers { drained = true }
        coEvery { coordinator.quote(session, request) } answers {
            assertTrue(drained)
            quote
        }
        val access = BeamSendSession(wallet, session, adapter, owner, coordinator, offlineMode,
            TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler), this))
        assertSame(quote, access.quoteOffline(request))
        verify(exactly = 0) { adapter.resumeNetwork() }
        access.releaseOffline()
        verify(exactly = 1) { adapter.resumeNetwork() }
    }

    @Test
    fun sign_manualPauseDuringSigning_doesNotResumeNetwork() = runTest(UnconfinedTestDispatcher()) {
        coEvery { adapter.pauseNetworkAndAwait() } returns Unit
        coEvery { coordinator.signOffline(quote) } answers {
            every { offlineMode.isNetworkPaused(any()) } returns true
            BeamOfflineSignResult("tx", BeamOfflineSendState.Signed)
        }
        val access = BeamSendSession(wallet, session, adapter, owner, coordinator, offlineMode,
            TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler), this))
        access.sign(quote)
        access.releaseOffline()
        verify(exactly = 0) { adapter.resumeNetwork() }
    }

    @Test
    fun sign_alreadyPaused_doesNotResumeAfterFailure() = runTest(UnconfinedTestDispatcher()) {
        every { adapter.isNetworkPaused } returns true
        coEvery { adapter.pauseNetworkAndAwait() } returns Unit
        coEvery { coordinator.signOffline(quote) } throws IllegalStateException("interrupted")
        val access = BeamSendSession(wallet, session, adapter, owner, coordinator, offlineMode,
            TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler), this))
        assertFailsWith<IllegalStateException> { access.sign(quote) }
        access.releaseOffline()
        verify(exactly = 0) { adapter.resumeNetwork() }
    }

    @Test
    fun sign_hardwareOrWatchAccount_neverSignsOrStartsNetwork() = runTest(UnconfinedTestDispatcher()) {
        val access = BeamSendSession(wallet, session, adapter, owner, coordinator, offlineMode,
            TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler), this))
        for (type in listOf(mockk<AccountType.HardwareCard>(), mockk<AccountType.EvmAddress>())) {
            every { wallet.account.type } returns type
            assertFailsWith<IllegalStateException> { access.sign(quote) }
        }
        coVerify(exactly = 0) { coordinator.signOffline(any()) }
        coVerify(exactly = 0) { adapter.pauseNetworkAndAwait() }
        verify(exactly = 0) { adapter.resumeNetwork() }
    }
}
