package cash.p.terminal.modules.transactionInfo

import cash.p.terminal.R
import cash.p.terminal.core.managers.AddressLabelManager
import cash.p.terminal.core.managers.BeamSendCoordinator.Reason
import cash.p.terminal.core.managers.BeamSendCoordinator.SendException
import cash.p.terminal.core.managers.PendingTransactionRepository
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionInfoViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val record = mockk<TransactionRecord>(relaxed = true)
    private val service = mockk<TransactionInfoService>(relaxed = true)
    private val factory = mockk<TransactionInfoViewItemFactory>(relaxed = true)
    private val contactsRepository = mockk<ContactsRepository>(relaxed = true)
    private val balanceHiddenManager = mockk<IBalanceHiddenManager>(relaxed = true)
    private val pendingTransactionRepository = mockk<PendingTransactionRepository>(relaxed = true)
    private val poisonAddressManager = mockk<PoisonAddressManager>(relaxed = true)
    private val addressLabelManager = mockk<AddressLabelManager>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { service.transactionRecord } returns record
        every { service.transactionInfoItemFlow } returns emptyFlow()
        every { contactsRepository.contactsFlow } returns MutableStateFlow(emptyList())
        every { poisonAddressManager.poisonDbChangedFlow } returns emptyFlow()
        every { addressLabelManager.labelsChangedFlow } returns emptyFlow()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun cancelBeamTransaction_coreRefuses_reportsThatItIsTooLate() = runTest(dispatcher) {
        coEvery { service.cancelBeam(TRANSACTION_ID) } returns false
        val viewModel = createViewModel()

        viewModel.cancelBeamTransaction(TRANSACTION_ID)
        advanceUntilIdle()

        assertEquals(R.string.beam_cancel_too_late, viewModel.beamCancelMessage)
    }

    @Test
    fun cancelBeamTransaction_sessionNotReady_reportsThatTheWalletIsNotReady() = runTest(dispatcher) {
        coEvery { service.cancelBeam(TRANSACTION_ID) } throws SendException(Reason.NotReady)
        val viewModel = createViewModel()

        viewModel.cancelBeamTransaction(TRANSACTION_ID)
        advanceUntilIdle()

        assertEquals(R.string.beam_send_not_ready, viewModel.beamCancelMessage)
    }

    @Test
    fun cancelBeamTransaction_networkUnavailable_reportsThatTheWalletIsNotReady() = runTest(dispatcher) {
        coEvery { service.cancelBeam(TRANSACTION_ID) } throws IllegalStateException("BEAM network unavailable")
        val viewModel = createViewModel()

        viewModel.cancelBeamTransaction(TRANSACTION_ID)
        advanceUntilIdle()

        assertEquals(R.string.beam_send_not_ready, viewModel.beamCancelMessage)
    }

    @Test
    fun cancelBeamTransaction_walletFailure_reportsTheGenericFailure() = runTest(dispatcher) {
        coEvery { service.cancelBeam(TRANSACTION_ID) } throws SendException(Reason.ExternalFailure)
        val viewModel = createViewModel()

        viewModel.cancelBeamTransaction(TRANSACTION_ID)
        advanceUntilIdle()

        assertEquals(R.string.beam_wallet_operation_failed, viewModel.beamCancelMessage)
    }

    @Test
    fun cancelBeamTransaction_cancelled_reportsNothingAndClearsAPreviousMessage() = runTest(dispatcher) {
        coEvery { service.cancelBeam(TRANSACTION_ID) } returns false
        val viewModel = createViewModel()
        viewModel.cancelBeamTransaction(TRANSACTION_ID)
        advanceUntilIdle()
        viewModel.onBeamCancelMessageShown()
        assertNull(viewModel.beamCancelMessage)

        coEvery { service.cancelBeam(TRANSACTION_ID) } returns true
        viewModel.cancelBeamTransaction(TRANSACTION_ID)
        advanceUntilIdle()

        assertNull(viewModel.beamCancelMessage)
    }

    private fun createViewModel() = TransactionInfoViewModel(
        service = service,
        factory = factory,
        contactsRepository = contactsRepository,
        balanceHiddenManager = balanceHiddenManager,
        pendingTransactionRepository = pendingTransactionRepository,
        poisonAddressManager = poisonAddressManager,
        addressLabelManager = addressLabelManager,
    )

    private companion object {
        const val TRANSACTION_ID = "11111111111111111111111111111111"
    }
}
