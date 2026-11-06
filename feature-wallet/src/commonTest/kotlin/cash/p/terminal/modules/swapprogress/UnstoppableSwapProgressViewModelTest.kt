package cash.p.terminal.modules.swapprogress

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.usecase.UpdateSwapProviderTransactionsStatusUseCase
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.modules.transactionInfo.TransactionInfoViewItem
import cash.p.terminal.modules.transactions.TransactionStatus
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableProviderContacts
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableProviderInfo
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableTrack
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableTrackLeg
import cash.p.terminal.network.unstoppable.domain.repository.UnstoppableRepository
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UnstoppableSwapProgressViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val pollMs = UnstoppableSwapProgressViewModel.POLL_INTERVAL.inWholeMilliseconds
    private val swapFlow = MutableStateFlow<SwapProviderTransaction?>(null)
    private val trackFlow = MutableStateFlow<UnstoppableTrack?>(null)
    private val storage = mockk<SwapProviderTransactionsStorage> {
        every { observeByDate(DATE) } returns swapFlow
    }
    private val repository = mockk<UnstoppableRepository> {
        every { observeTrack("uuid") } returns trackFlow
        coEvery { getProviders() } returns emptyList()
    }
    private val updateUseCase = mockk<UpdateSwapProviderTransactionsStatusUseCase> {
        coEvery { updateTransactionStatusByDate(DATE) } returns TransactionStatusEnum.WAITING
    }
    private val hiddenFlow = MutableStateFlow(false)
    private val balanceHiddenManager = mockk<IBalanceHiddenManager> {
        every { transactionInfoHiddenFlow("tx-uid", "wallet-1") } returns hiddenFlow
    }
    private val sectionsByTrack = mutableMapOf<UnstoppableTrack?, List<List<TransactionInfoViewItem>>>()
    private val stepsFactory = mockk<UnstoppableSwapStepsFactory> {
        every { sections(any(), any(), any(), any()) } answers {
            sectionsByTrack[secondArg<UnstoppableTrack?>()].orEmpty()
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun swap(status: String = "waiting", subProviderId: String? = "PEGASUS") = SwapProviderTransaction(
        date = DATE,
        outgoingRecordUid = null,
        transactionId = "uuid",
        status = status,
        provider = SwapProvider.UNSTOPPABLE,
        coinUidIn = "litecoin",
        blockchainTypeIn = "litecoin",
        amountIn = BigDecimal("0.15"),
        addressIn = "in",
        coinUidOut = "ethereum",
        blockchainTypeOut = "ethereum",
        amountOut = BigDecimal("0.0036"),
        addressOut = "out",
        accountId = "account-1",
        subProviderId = subProviderId,
        depositTransactionHash = "deposit-hash",
    )

    private fun track(toAmount: String) =
        UnstoppableTrack(TransactionStatusEnum.WAITING, false, null, BigDecimal(toAmount), emptyList())

    private fun sectionsOf(status: TransactionStatus) = listOf(listOf(TransactionInfoViewItem.Status(status)))

    // runTest drains the shared scheduler at the end, so a still-running poll loop would never finish.
    private fun TestScope.createViewModel(swap: SwapProviderTransaction): UnstoppableSwapProgressViewModel {
        swapFlow.value = swap
        return UnstoppableSwapProgressViewModel(
            swapDate = DATE,
            transactionUid = "tx-uid",
            walletUid = "wallet-1",
            storage = storage,
            unstoppableRepository = repository,
            updateSwapProviderTransactionsStatusUseCase = updateUseCase,
            stepsFactory = stepsFactory,
            balanceHiddenManager = balanceHiddenManager,
            dispatcherProvider = TestDispatcherProvider(dispatcher, this),
        )
    }

    @Test
    fun onResume_unfinishedSwap_refreshesNowAndEveryPollIntervalUntilPause() = runTest(dispatcher) {
        val viewModel = createViewModel(swap())

        viewModel.onResume()
        runCurrent()
        coVerify(exactly = 1) { updateUseCase.updateTransactionStatusByDate(DATE) }

        advanceTimeBy(pollMs - 1)
        runCurrent()
        coVerify(exactly = 1) { updateUseCase.updateTransactionStatusByDate(DATE) }

        advanceTimeBy(1)
        runCurrent()
        coVerify(exactly = 2) { updateUseCase.updateTransactionStatusByDate(DATE) }

        viewModel.onPause()
        advanceTimeBy(3 * pollMs)
        runCurrent()
        coVerify(exactly = 2) { updateUseCase.updateTransactionStatusByDate(DATE) }
    }

    @Test
    fun onResume_finishedSwap_pollsTrackUntilPauseAndRendersLaterFields() = runTest(dispatcher) {
        val partial = track("1")
        val full = track("2")
        val fullSections = sectionsOf(TransactionStatus.Completed)
        sectionsByTrack[full] = fullSections
        val responses = ArrayDeque(listOf(partial, full, full))
        coEvery { repository.track("uuid", "deposit-hash") } coAnswers {
            responses.removeFirst().also { trackFlow.value = it }
        }
        val viewModel = createViewModel(swap(status = "finished"))

        viewModel.onResume()
        advanceTimeBy(2 * pollMs)
        runCurrent()
        viewModel.onPause()
        advanceTimeBy(3 * pollMs)
        runCurrent()

        coVerify(exactly = 3) { repository.track("uuid", "deposit-hash") }
        coVerify(exactly = 0) { updateUseCase.updateTransactionStatusByDate(any()) }
        assertEquals(fullSections, viewModel.uiState.sections)
    }

    @Test
    fun onResume_finishedSwapTrackFails_keepsPollingAndRendersNextTrack() = runTest(dispatcher) {
        val later = track("2")
        val laterSections = sectionsOf(TransactionStatus.Completed)
        sectionsByTrack[later] = laterSections
        coEvery { repository.track("uuid", "deposit-hash") } coAnswers { throw object : Throwable("backend") {} } coAndThen {
            trackFlow.value = later
            later
        }
        val viewModel = createViewModel(swap(status = "finished"))

        viewModel.onResume()
        advanceTimeBy(pollMs)
        runCurrent()
        viewModel.onPause()

        coVerify(exactly = 2) { repository.track("uuid", "deposit-hash") }
        assertEquals(laterSections, viewModel.uiState.sections)
    }

    private fun settledTrack(depositHash: String?) = UnstoppableTrack(
        TransactionStatusEnum.FINISHED, false, null, BigDecimal("0.0036"),
        listOf(
            UnstoppableTrackLeg(
                UnstoppableTrackLeg.Type.NativeSend, UnstoppableTrackLeg.Status.Completed, depositHash, "litecoin",
                "LTC.LTC", BigDecimal("0.15"), "LTC.LTC", BigDecimal("0.15"), "deposit-address",
            ),
        ),
    )

    @Test
    fun onResume_finishedSwapTrackSettled_fetchesOnceThenStops() = runTest(dispatcher) {
        coEvery { repository.track("uuid", "deposit-hash") } coAnswers {
            settledTrack(depositHash = null).also { trackFlow.value = it }
        }
        val viewModel = createViewModel(swap(status = "finished"))

        viewModel.onResume()
        advanceTimeBy(3 * pollMs)
        runCurrent()
        viewModel.onPause()

        coVerify(exactly = 1) { repository.track("uuid", "deposit-hash") }
    }

    @Test
    fun onResume_unfinishedSwapTrackSettled_keepsRefreshingStatus() = runTest(dispatcher) {
        trackFlow.value = settledTrack(depositHash = "deposit-hash")
        val viewModel = createViewModel(swap())

        viewModel.onResume()
        advanceTimeBy(pollMs)
        runCurrent()
        viewModel.onPause()

        coVerify(exactly = 2) { updateUseCase.updateTransactionStatusByDate(DATE) }
    }

    @Test
    fun uiState_trackEmitted_sectionsRebuiltOnEachNewTrack() = runTest(dispatcher) {
        val first = track("1")
        val second = track("2")
        sectionsByTrack[first] = sectionsOf(TransactionStatus.Pending)
        sectionsByTrack[second] = sectionsOf(TransactionStatus.Completed)
        trackFlow.value = first
        val viewModel = createViewModel(swap())

        runCurrent()
        assertFalse(viewModel.uiState.loading)
        assertEquals(sectionsByTrack[first], viewModel.uiState.sections)

        trackFlow.value = second
        runCurrent()
        assertEquals(sectionsByTrack[second], viewModel.uiState.sections)
    }

    @Test
    fun uiState_noTrackBeforeFirstRefresh_staysLoading() = runTest(dispatcher) {
        val viewModel = createViewModel(swap())

        runCurrent()

        assertTrue(viewModel.uiState.loading)
    }

    @Test
    fun uiState_firstRefreshFailsWithoutTrack_showsFallbackInsteadOfSpinner() = runTest(dispatcher) {
        coEvery { updateUseCase.updateTransactionStatusByDate(DATE) } throws object : Throwable("backend") {}
        sectionsByTrack[null] = sectionsOf(TransactionStatus.Pending)
        val viewModel = createViewModel(swap())

        viewModel.onResume()
        runCurrent()

        assertFalse(viewModel.uiState.loading)
        assertEquals(sectionsByTrack[null], viewModel.uiState.sections)
        viewModel.onPause()
    }

    @Test
    fun contacts_providerMatchedBySubProviderIdIgnoringCase_passedToFactory() = runTest(dispatcher) {
        coEvery { repository.getProviders() } returns listOf(
            UnstoppableProviderInfo(
                "OTHER", null, emptyList(), null, null,
                UnstoppableProviderContacts("other@example.com", null, null, null),
            ),
            UnstoppableProviderInfo(
                "pegasus", null, emptyList(), null, null,
                UnstoppableProviderContacts("support@pegasus.io", "@pegasus", null, null),
            ),
        )
        val track = track("1")
        trackFlow.value = track
        createViewModel(swap(subProviderId = "PEGASUS"))

        runCurrent()

        verify {
            stepsFactory.sections(
                any(), track,
                listOf(
                    SupportContact.Telegram("https://t.me/pegasus"),
                    SupportContact.Email("support@pegasus.io"),
                ),
                false,
            )
        }
    }

    @Test
    fun contacts_providerWithoutContacts_passesEmptyList() = runTest(dispatcher) {
        coEvery { repository.getProviders() } returns listOf(
            UnstoppableProviderInfo("PEGASUS", null, emptyList(), null, null, null),
        )
        val track = track("1")
        trackFlow.value = track
        createViewModel(swap())

        runCurrent()

        verify { stepsFactory.sections(any(), track, emptyList(), false) }
    }

    @Test
    fun contacts_providersNeverRespond_sectionsStillRenderFromTrack() = runTest(dispatcher) {
        coEvery { repository.getProviders() } coAnswers { awaitCancellation() }
        val track = track("1")
        trackFlow.value = track
        sectionsByTrack[track] = sectionsOf(TransactionStatus.Completed)
        val viewModel = createViewModel(swap())

        viewModel.onResume()
        runCurrent()

        assertFalse(viewModel.uiState.loading)
        assertEquals(sectionsByTrack[track], viewModel.uiState.sections)
        viewModel.onPause()
    }

    @Test
    fun contacts_firstLoadFailsWithNonException_retriedOnNextTick() = runTest(dispatcher) {
        coEvery { repository.getProviders() } throws object : Throwable("backend") {} andThen listOf(
            UnstoppableProviderInfo(
                "PEGASUS", null, emptyList(), null, null,
                UnstoppableProviderContacts("support@pegasus.io", null, null, null),
            ),
        )
        val track = track("1")
        trackFlow.value = track
        val viewModel = createViewModel(swap())

        viewModel.onResume()
        runCurrent()
        verify(exactly = 0) { stepsFactory.sections(any(), any(), listOf(SupportContact.Email("support@pegasus.io")), any()) }

        advanceTimeBy(pollMs)
        runCurrent()

        verify { stepsFactory.sections(any(), track, listOf(SupportContact.Email("support@pegasus.io")), false) }
        viewModel.onPause()
    }

    @Test
    fun toggleBalanceVisibility_called_togglesTransactionInfoHidden() = runTest(dispatcher) {
        every { balanceHiddenManager.toggleTransactionInfoHidden("tx-uid") } returns Unit
        val viewModel = createViewModel(swap())

        viewModel.toggleBalanceVisibility()

        verify { balanceHiddenManager.toggleTransactionInfoHidden("tx-uid") }
    }

    @Test
    fun hiddenFlow_toggledAfterFirstRender_rebuildsSectionsMasked() = runTest(dispatcher) {
        val track = track("1")
        trackFlow.value = track
        val viewModel = createViewModel(swap())
        viewModel.onResume()
        runCurrent()
        assertFalse(viewModel.uiState.hideAmount)
        verify { stepsFactory.sections(any(), track, any(), false) }

        hiddenFlow.value = true
        runCurrent()

        assertTrue(viewModel.uiState.hideAmount)
        verify { stepsFactory.sections(any(), track, any(), true) }
        viewModel.onPause()
    }

    private companion object {
        const val DATE = 1L
    }
}
