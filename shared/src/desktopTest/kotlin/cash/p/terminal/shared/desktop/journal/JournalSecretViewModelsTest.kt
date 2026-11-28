package cash.p.terminal.shared.desktop.journal

import cash.p.terminal.modules.pin.LevelOutcome
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.UnlockOutcome
import cash.p.terminal.wallet.PassphraseValidator
import io.horizontalsystems.core.ICurrentDateProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class JournalSecretViewModelsTest {

    private val dispatcher = StandardTestDispatcher()
    private val secrets = mockk<DesktopLoginJournalSecrets>()
    private val vault = mockk<DesktopVault>()
    private val dateProvider = object : ICurrentDateProvider {
        override val currentDate: Date get() = Date(NOW + dispatcher.scheduler.currentTime)
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { vault.lockedOutUntil() } returns null
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun check_journalPasswordCorrect_confirmsAndClearsTheForm() = runTest(dispatcher) {
        coEvery { secrets.verifyJournalPassword(any()) } returns JournalSecretOutcome.SUCCESS
        val viewModel = checkViewModel(JournalSecretKind.JOURNAL_PASSWORD)

        viewModel.submit()

        assertTrue(viewModel.confirmed)
        assertEquals("", viewModel.uiState.password)
    }

    @Test
    fun check_journalPasswordWrong_staysOpenForAnotherTry() = runTest(dispatcher) {
        coEvery { secrets.verifyJournalPassword(any()) } returns JournalSecretOutcome.WRONG_PASSWORD
        val viewModel = checkViewModel(JournalSecretKind.JOURNAL_PASSWORD)

        viewModel.submit()

        assertFalse(viewModel.confirmed)
        assertEquals(UnlockOutcome.WrongPassword(attemptsLeft = null), viewModel.uiState.outcome)
        assertTrue(viewModel.uiState.canSubmit)
    }

    @Test
    fun check_lockedOut_blocksTheFormUntilTheSharedLockoutEnds() = runTest(dispatcher) {
        val until = Date(NOW + LOCKOUT_MS)
        coEvery { secrets.verifyJournalPassword(any()) } returns JournalSecretOutcome.LOCKED_OUT
        coEvery { vault.lockedOutUntil() } returnsMany listOf(null, until)
        val viewModel = checkViewModel(JournalSecretKind.JOURNAL_PASSWORD)

        viewModel.submit()

        assertFalse(viewModel.confirmed)
        assertEquals(UnlockOutcome.LockedOut(until), viewModel.uiState.outcome)
        assertFalse(viewModel.uiState.canSubmit)
    }

    @Test
    fun check_deleteContactsPassword_disablesThroughTheOperationNotTheJournalCheck() = runTest(dispatcher) {
        coEvery { secrets.disableDeleteContactsPassword(any()) } returns JournalSecretOutcome.SUCCESS
        val viewModel = checkViewModel(JournalSecretKind.DELETE_CONTACTS)

        viewModel.submit()

        assertTrue(viewModel.confirmed)
        coVerify(exactly = 1) { secrets.disableDeleteContactsPassword(any()) }
        coVerify(exactly = 0) { secrets.verifyJournalPassword(any()) }
    }

    @Test
    fun check_operationFailed_showsAFailureAndDoesNotConfirm() = runTest(dispatcher) {
        coEvery { secrets.disableDeleteContactsPassword(any()) } returns JournalSecretOutcome.FAILED
        val viewModel = checkViewModel(JournalSecretKind.DELETE_CONTACTS)

        viewModel.submit()

        assertFalse(viewModel.confirmed)
        assertEquals(UnlockOutcome.Failed, viewModel.uiState.outcome)
    }

    @Test
    fun save_journalPasswordFree_finishesTheForm() = runTest(dispatcher) {
        coEvery { secrets.setJournalPassword(any()) } returns JournalSecretOutcome.SUCCESS
        val viewModel = passwordViewModel(JournalSecretKind.JOURNAL_PASSWORD)

        viewModel.submit()

        assertTrue(viewModel.uiState.finished)
        coVerify(exactly = 0) { secrets.setDeleteContactsPassword(any()) }
    }

    @Test
    fun save_passwordInUse_showsItAndDoesNotFinish() = runTest(dispatcher) {
        coEvery { secrets.setDeleteContactsPassword(any()) } returns JournalSecretOutcome.PASSWORD_IN_USE
        val viewModel = passwordViewModel(JournalSecretKind.DELETE_CONTACTS)

        viewModel.submit()

        assertEquals(LevelOutcome.PASSWORD_IN_USE, viewModel.uiState.outcome)
        assertFalse(viewModel.uiState.finished)
    }

    @Test
    fun save_operationFailed_showsAFailure() = runTest(dispatcher) {
        coEvery { secrets.setJournalPassword(any()) } returns JournalSecretOutcome.FAILED
        val viewModel = passwordViewModel(JournalSecretKind.JOURNAL_PASSWORD)

        viewModel.submit()

        assertEquals(LevelOutcome.FAILED, viewModel.uiState.outcome)
    }

    private fun checkViewModel(kind: JournalSecretKind) =
        JournalSecretCheckViewModel(kind, secrets, vault, dateProvider)

    private fun JournalSecretCheckViewModel.submit() {
        onPasswordChange(PASSWORD)
        onSubmitClick()
        dispatcher.scheduler.runCurrent()
    }

    private fun passwordViewModel(kind: JournalSecretKind) =
        JournalSecretPasswordViewModel(kind, secrets, PassphraseValidator())

    private fun JournalSecretPasswordViewModel.submit() {
        onPasswordChange(PASSWORD)
        onConfirmationChange(PASSWORD)
        onSubmitClick()
        dispatcher.scheduler.runCurrent()
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val LOCKOUT_MS = 60_000L
        const val PASSWORD = "Correct-horse1"
    }
}
