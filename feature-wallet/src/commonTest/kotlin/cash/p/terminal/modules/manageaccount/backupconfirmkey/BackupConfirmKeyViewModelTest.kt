package cash.p.terminal.modules.manageaccount.backupconfirmkey

import cash.p.terminal.core.managers.RandomProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BackupConfirmKeyViewModelTest {

    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val account = Account(
        id = "slip39",
        name = "Shamir",
        type = AccountType.Slip39(List(20) { "word$it" }, passphrase = "", seed = ByteArray(16)),
        origin = AccountOrigin.Restored,
        level = 0
    )
    private val viewModel by lazy { BackupConfirmKeyViewModel(account, accountManager, RandomProvider()) }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_slip39Share_asksThreeWordsOfTheShare() {
        val questions = viewModel.uiState.check.questions

        assertEquals(3, questions.size)
        questions.forEach { assertEquals("word${it.position - 1}", it.word) }
    }

    @Test
    fun onSelectWord_allRequestedWords_marksAccountBackedUp() {
        repeat(viewModel.uiState.check.questions.size) { viewModel.selectCorrectWord() }

        assertTrue(viewModel.uiState.confirmed)
        verify(exactly = 1) { accountManager.markAsBackedUp(account.id) }
    }

    @Test
    fun onSelectWord_wrongWord_keepsProgressWithoutMarkingBackedUp() {
        viewModel.selectCorrectWord()
        val question = requireNotNull(viewModel.uiState.check.current)

        viewModel.onSelectWord(question.options.indexOfFirst { it != question.word })

        assertEquals(1, viewModel.uiState.check.answered)
        assertFalse(viewModel.uiState.confirmed)
        verify(exactly = 0) { accountManager.markAsBackedUp(any()) }
    }

    private fun BackupConfirmKeyViewModel.selectCorrectWord() {
        val question = requireNotNull(uiState.check.current)
        onSelectWord(question.options.indexOf(question.word))
    }
}
