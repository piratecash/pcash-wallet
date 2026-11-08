package cash.p.terminal.shared.desktop.lock

import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.modules.pin.DuressSecretController
import cash.p.terminal.modules.pin.LevelOutcome
import cash.p.terminal.wallet.PassphraseValidator
import cash.p.terminal.wallet.managers.UserManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SecretPasswordViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val controller = mockk<DuressSecretController>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onSubmitClick_duressWithChosenWallets_createsTheLevelThenMovesTheWallets() = runTest(dispatcher) {
        coEvery { controller.setDuressSecret(any()) } returns LevelOutcome.SUCCESS
        val viewModel = duressViewModel(listOf("a", "b"))

        viewModel.submit()

        assertTrue(viewModel.uiState.finished)
        verify { userManager.allowAccountsForDuress(listOf("a", "b")) }
    }

    @Test
    fun onSubmitClick_duressWithNothingToChoose_createsTheLevelOnly() = runTest(dispatcher) {
        coEvery { controller.setDuressSecret(any()) } returns LevelOutcome.SUCCESS
        val viewModel = duressViewModel(emptyList())

        viewModel.submit()

        assertTrue(viewModel.uiState.finished)
        verify(exactly = 0) { userManager.allowAccountsForDuress(any()) }
    }

    @Test
    fun onSubmitClick_duressPasswordInUse_movesNothingAndShowsTheOutcome() = runTest(dispatcher) {
        coEvery { controller.setDuressSecret(any()) } returns LevelOutcome.PASSWORD_IN_USE
        val viewModel = duressViewModel(listOf("a"))

        viewModel.submit()

        assertEquals(LevelOutcome.PASSWORD_IN_USE, viewModel.uiState.outcome)
        verify(exactly = 0) { userManager.allowAccountsForDuress(any()) }
    }

    @Test
    fun onSubmitClick_nestedDuressLimitReached_showsTheLimit() = runTest(dispatcher) {
        coEvery { controller.setDuressSecret(any()) } returns LevelOutcome.LIMIT_REACHED
        val viewModel = duressViewModel(listOf("a"))

        viewModel.submit()

        assertEquals(LevelOutcome.LIMIT_REACHED, viewModel.uiState.outcome)
        assertFalse(viewModel.uiState.finished)
    }

    @Test
    fun onSubmitClick_movingTheWalletsFails_removesTheNewLevelAndFails() = runTest(dispatcher) {
        coEvery { controller.setDuressSecret(any()) } returns LevelOutcome.SUCCESS
        every { userManager.allowAccountsForDuress(any()) } throws IllegalStateException("disk")
        val viewModel = duressViewModel(listOf("a"))

        viewModel.submit()

        assertEquals(LevelOutcome.FAILED, viewModel.uiState.outcome)
        coVerify { controller.disableDuress() }
    }

    @Test
    fun onSubmitClick_hiddenWallet_createsItAndFinishes() = runTest(dispatcher) {
        coEvery { controller.createHiddenWallet(any()) } returns LevelOutcome.SUCCESS
        val viewModel = HiddenWalletPasswordViewModel(controller, PassphraseValidator())

        viewModel.submit()

        assertTrue(viewModel.uiState.finished)
    }

    @Test
    fun onSubmitClick_hiddenWalletLimitReached_showsTheLimit() = runTest(dispatcher) {
        coEvery { controller.createHiddenWallet(any()) } returns LevelOutcome.LIMIT_REACHED
        val viewModel = HiddenWalletPasswordViewModel(controller, PassphraseValidator())

        viewModel.submit()

        assertEquals(LevelOutcome.LIMIT_REACHED, viewModel.uiState.outcome)
    }

    @Test
    fun onSubmitClick_duressCancelledWhileSettingTheSecret_stillMovesTheWallets() = runTest(dispatcher) {
        val secretSet = CompletableDeferred<Unit>()
        coEvery { controller.setDuressSecret(any()) } coAnswers {
            secretSet.await()
            LevelOutcome.SUCCESS
        }
        val viewModel = duressViewModel(listOf("a", "b"))
        viewModel.onPasswordChange(PASSWORD)
        viewModel.onConfirmationChange(PASSWORD)
        viewModel.onSubmitClick()
        runCurrent()

        viewModel.viewModelScope.cancel()
        secretSet.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        verify { userManager.allowAccountsForDuress(listOf("a", "b")) }
        coVerify(exactly = 0) { controller.disableDuress() }
    }

    @Test
    fun onSubmitClick_hiddenWalletCancelledWhileCreating_runsTheCreationToTheEnd() = runTest(dispatcher) {
        val created = CompletableDeferred<Unit>()
        var finished = false
        coEvery { controller.createHiddenWallet(any()) } coAnswers {
            created.await()
            finished = true
            LevelOutcome.SUCCESS
        }
        val viewModel = HiddenWalletPasswordViewModel(controller, PassphraseValidator())
        viewModel.onPasswordChange(PASSWORD)
        viewModel.onConfirmationChange(PASSWORD)
        viewModel.onSubmitClick()
        runCurrent()

        viewModel.viewModelScope.cancel()
        created.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(finished)
    }

    @Test
    fun onSubmitClick_weakPassword_isNotSubmitted() = runTest(dispatcher) {
        val viewModel = HiddenWalletPasswordViewModel(controller, PassphraseValidator())
        viewModel.onPasswordChange("weak")
        viewModel.onConfirmationChange("weak")

        viewModel.onSubmitClick()
        runCurrent()

        coVerify(exactly = 0) { controller.createHiddenWallet(any()) }
    }

    @Test
    fun onSubmitClick_confirmationDiffers_isNotSubmitted() = runTest(dispatcher) {
        val viewModel = HiddenWalletPasswordViewModel(controller, PassphraseValidator())
        viewModel.onPasswordChange(PASSWORD)
        viewModel.onConfirmationChange(PASSWORD + "x")

        viewModel.onSubmitClick()
        runCurrent()

        assertTrue(viewModel.uiState.mismatch)
        coVerify(exactly = 0) { controller.createHiddenWallet(any()) }
    }

    private fun duressViewModel(accountIds: List<String>) = DuressPasswordViewModel(
        accountIds, controller, userManager,
        TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        PassphraseValidator(),
    )

    private fun SecretPasswordViewModel.submit() {
        onPasswordChange(PASSWORD)
        onConfirmationChange(PASSWORD)
        onSubmitClick()
        dispatcher.scheduler.advanceUntilIdle()
    }

    private companion object {
        const val PASSWORD = "Duress-password1"
    }
}
