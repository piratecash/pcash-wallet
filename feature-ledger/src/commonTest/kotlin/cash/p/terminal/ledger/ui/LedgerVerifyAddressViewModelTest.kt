package cash.p.terminal.ledger.ui

import cash.p.terminal.ledger.domain.LedgerAddressVerification
import cash.p.terminal.ledger.domain.LedgerRejectedException
import cash.p.terminal.wallet.Wallet
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerVerifyAddressViewModelTest {

    private val wallet = mockk<Wallet>(relaxed = true)
    private val verification = mockk<LedgerAddressVerification> {
        every { isAvailable(any()) } returns true
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun verify_rejectedOnDevice_showsWarning() {
        coEvery { verification.verify(any(), any()) } throws LedgerRejectedException()
        val viewModel = LedgerVerifyAddressViewModel(wallet, verification)

        viewModel.verify("address")

        assertIs<LedgerRejectedException>(viewModel.error)
        assertFalse(viewModel.verifying)
    }

    @Test
    fun verify_coroutineCancelled_staysSilent() {
        coEvery { verification.verify(any(), any()) } throws CancellationException()
        val viewModel = LedgerVerifyAddressViewModel(wallet, verification)

        viewModel.verify("address")

        assertNull(viewModel.error)
        assertFalse(viewModel.verifying)
    }
}
