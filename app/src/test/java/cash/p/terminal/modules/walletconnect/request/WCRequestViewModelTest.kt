package cash.p.terminal.modules.walletconnect.request

import cash.p.terminal.core.ServiceStateFlow
import cash.p.terminal.ledger.domain.LedgerBlindSigningRequiredException
import cash.p.terminal.ledger.domain.LedgerRejectedException
import cash.p.terminal.strings.helpers.TranslatableString
import io.horizontalsystems.core.Translator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jetbrains.compose.resources.StringResource
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WCRequestViewModelTest {

    private val wcAction = mockk<AbstractWCAction>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { wcAction.stateFlow } returns
            ServiceStateFlow(MutableStateFlow(WCActionState(runnable = true, items = emptyList())))
        every { wcAction.getTitle() } returns TranslatableString.PlainString("Sign")
        every { wcAction.getApproveButtonTitle() } returns TranslatableString.PlainString("Sign")
        mockkObject(Translator)
        every { Translator.getString(any(), *anyVararg()) } answers { firstArg<StringResource>().key }
    }

    @After
    fun tearDown() {
        unmockkObject(Translator)
        Dispatchers.resetMain()
    }

    @Test
    fun approve_rejectedOnLedger_resetsWithoutMessage() = runTest {
        val viewModel = approveFailingWith(LedgerRejectedException())

        assertNull(viewModel.uiState.errorText)
        assertTrue(viewModel.uiState.runnable)
    }

    @Test
    fun approve_blindSigningOffOnLedger_showsInstruction() = runTest {
        val viewModel = approveFailingWith(LedgerBlindSigningRequiredException("Stellar"))

        assertEquals("ledger_blind_signing_required", viewModel.uiState.errorText)
        assertTrue(viewModel.uiState.runnable)
    }

    @Test
    fun approve_genericFailure_showsItsMessage() = runTest {
        val viewModel = approveFailingWith(IllegalStateException("Network down"))

        assertEquals("Network down", viewModel.uiState.errorText)
        assertTrue(viewModel.uiState.runnable)
    }

    private suspend fun approveFailingWith(error: Exception): WCRequestViewModel {
        coEvery { wcAction.performAction() } throws error
        val viewModel = WCRequestViewModel(mockk(relaxed = true), wcAction)
        viewModel.approve().join()
        return viewModel
    }
}
