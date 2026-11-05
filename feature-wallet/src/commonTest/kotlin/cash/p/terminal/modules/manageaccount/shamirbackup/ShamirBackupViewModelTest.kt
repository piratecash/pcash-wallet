package cash.p.terminal.modules.manageaccount.shamirbackup

import cash.p.terminal.core.managers.RandomProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.slip39.EncryptedMasterSecret
import cash.p.terminal.wallet.slip39.Slip39Shamir
import io.mockk.every
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
import java.security.SecureRandom
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ShamirBackupViewModelTest {

    private val accountManager = mockk<IAccountManager>(relaxed = true)
    private val accountSecret = EncryptedMasterSecret(
        identifier = ORIGINAL_IDENTIFIER,
        extendable = false,
        iterationExponent = 1,
        ciphertext = ByteArray(16) { (it * 7 + 3).toByte() },
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_defaultScheme_isTwoOfThree() {
        assertEquals(ShamirScheme(threshold = 2, total = 3), createViewModel().uiState.scheme)
    }

    @Test
    fun onSelectThreshold_aboveTotal_keepsScheme() {
        val viewModel = createViewModel()

        viewModel.onSelectThreshold(4)

        assertEquals(ShamirScheme(threshold = 2, total = 3), viewModel.uiState.scheme)
    }

    @Test
    fun onSelectThreshold_belowTwo_keepsScheme() {
        val viewModel = createViewModel()

        viewModel.onSelectThreshold(1)

        assertEquals(2, viewModel.uiState.scheme.threshold)
    }

    @Test
    fun onSelectTotal_belowThreshold_lowersThreshold() {
        val viewModel = createViewModel()
        viewModel.onSelectPreset(ShamirScheme(threshold = 4, total = 7))

        viewModel.onSelectTotal(3)

        assertEquals(ShamirScheme(threshold = 3, total = 3), viewModel.uiState.scheme)
    }

    @Test
    fun onSelectTotal_outsideTwoToSixteen_keepsScheme() {
        val viewModel = createViewModel()

        viewModel.onSelectTotal(17)
        viewModel.onSelectTotal(1)

        assertEquals(3, viewModel.uiState.scheme.total)
    }

    @Test
    fun onSelectPreset_threeOfFive_appliesBothValues() {
        val viewModel = createViewModel()

        viewModel.onSelectPreset(ShamirScheme(threshold = 3, total = 5))

        assertEquals(ShamirScheme(threshold = 3, total = 5), viewModel.uiState.scheme)
    }

    @Test
    fun onShowShares_anyThresholdSubset_combinesToAccountSecret() {
        val viewModel = createViewModel()
        viewModel.onSelectPreset(ShamirScheme(threshold = 3, total = 5))

        viewModel.onShowShares()

        val shares = viewModel.uiState.shares
        assertEquals(5, shares.size)
        shares.forEach { assertEquals(20, it.size) }
        subsets(shares.indices.toList(), 3).forEach { subset ->
            val secret = combine(subset.map(shares::get))
            assertContentEquals(accountSecret.ciphertext, secret.ciphertext)
            assertEquals(accountSecret.iterationExponent, secret.iterationExponent)
        }
    }

    @Test
    fun onShowShares_nonExtendableSet_keepsIdentifier() {
        val viewModel = createViewModel()

        viewModel.onShowShares()

        val secret = combine(viewModel.uiState.shares.take(2))
        assertEquals(ORIGINAL_IDENTIFIER, secret.identifier)
        assertFalse(secret.extendable)
    }

    @Test
    fun onShowShares_extendableSet_takesNewIdentifier() {
        val extendable = EncryptedMasterSecret(ORIGINAL_IDENTIFIER, true, 1, accountSecret.ciphertext)
        val viewModel = createViewModel(secret = extendable)

        viewModel.onShowShares()

        val secret = combine(viewModel.uiState.shares.take(2))
        assertEquals(NEW_IDENTIFIER, secret.identifier)
        assertTrue(secret.extendable)
        assertContentEquals(accountSecret.ciphertext, secret.ciphertext)
    }

    @Test
    fun onShowShares_calledAgain_keepsSameShares() {
        val viewModel = createViewModel()
        viewModel.onShowShares()
        val first = viewModel.uiState.shares

        viewModel.onShowShares()

        assertEquals(first, viewModel.uiState.shares)
    }

    @Test
    fun onShowShares_newRun_generatesNewShares() {
        val first = createViewModel().apply { onShowShares() }.uiState.shares

        val second = createViewModel().apply { onShowShares() }.uiState.shares

        assertNotEquals(first, second)
    }

    @Test
    fun onSelectPreset_afterSharesShown_dropsSharesAndProgress() {
        val viewModel = createViewModel()
        viewModel.onShowShares()
        viewModel.confirmCurrentShare()

        viewModel.onSelectPreset(ShamirScheme(threshold = 3, total = 5))

        assertTrue(viewModel.uiState.shares.isEmpty())
        assertEquals(0, viewModel.uiState.confirmedCount)
    }

    @Test
    fun showPassphraseWarning_passphraseSet_isTrue() {
        assertTrue(createViewModel(passphrase = "TREZOR").uiState.showPassphraseWarning)
    }

    @Test
    fun showPassphraseWarning_noPassphrase_isFalse() {
        assertFalse(createViewModel().uiState.showPassphraseWarning)
    }

    @Test
    fun onStartCheck_notWrittenDown_startsNoCheck() {
        val viewModel = createViewModel()
        viewModel.onShowShares()

        viewModel.onStartCheck()

        assertNull(viewModel.uiState.check)
    }

    @Test
    fun onStartCheck_writtenDown_checksCurrentShareWords() {
        val viewModel = createViewModel()
        viewModel.onShowShares()
        viewModel.onWrittenDownChange(true)

        viewModel.onStartCheck()

        val share = viewModel.uiState.shares.first()
        viewModel.uiState.check?.questions?.forEach { assertEquals(share[it.position - 1], it.word) }
            ?: error("No check started")
    }

    @Test
    fun onSelectWord_firstShareChecked_unlocksOnlyTheNextShare() {
        val viewModel = createViewModel()
        viewModel.onShowShares()

        viewModel.confirmCurrentShare()

        assertEquals(1, viewModel.uiState.confirmedCount)
        assertFalse(viewModel.uiState.writtenDown)
        assertFalse(viewModel.uiState.allConfirmed)
        verify(exactly = 0) { accountManager.markAsBackedUp(any()) }
    }

    @Test
    fun onSelectWord_everyShareChecked_marksAccountBackedUp() {
        val viewModel = createViewModel()
        viewModel.onShowShares()

        repeat(viewModel.uiState.shares.size) { viewModel.confirmCurrentShare() }

        assertTrue(viewModel.uiState.allConfirmed)
        verify(exactly = 1) { accountManager.markAsBackedUp(ACCOUNT_ID) }
    }

    @Test
    fun onShowShares_accountDeleted_closesFlowWithoutShares() {
        every { accountManager.account(ACCOUNT_ID) } returns null
        val viewModel = ShamirBackupViewModel(ACCOUNT_ID, accountManager, RandomProvider(), FixedIdentifierRandom())

        viewModel.onShowShares()

        assertTrue(viewModel.uiState.accountMissing)
        assertTrue(viewModel.uiState.shares.isEmpty())
    }

    private fun createViewModel(
        secret: EncryptedMasterSecret = accountSecret,
        passphrase: String = "",
    ): ShamirBackupViewModel {
        val canonicalShare = Slip39Shamir.split(secret, threshold = 1, count = 1, SecureRandom()).single()
        every { accountManager.account(ACCOUNT_ID) } returns Account(
            id = ACCOUNT_ID,
            name = "Shamir",
            type = AccountType.Slip39(canonicalShare, passphrase, seed = ByteArray(16)),
            origin = AccountOrigin.Restored,
            level = 0
        )
        return ShamirBackupViewModel(ACCOUNT_ID, accountManager, RandomProvider(), FixedIdentifierRandom())
    }

    private fun ShamirBackupViewModel.confirmCurrentShare() {
        onWrittenDownChange(true)
        onStartCheck()
        while (uiState.check?.isComplete == false) {
            val question = uiState.check?.current ?: return
            onSelectWord(question.options.indexOf(question.word))
        }
    }

    private fun combine(shares: List<List<String>>) =
        Slip39Shamir.combine(shares.map { Slip39Shamir.parseShare(it.joinToString(" ")) })

    private fun subsets(items: List<Int>, size: Int): List<List<Int>> = when {
        size == 0 -> listOf(emptyList())
        items.size < size -> emptyList()
        else -> subsets(items.drop(1), size - 1).map { listOf(items.first()) + it } + subsets(items.drop(1), size)
    }

    // Shares still need real randomness; only the identifier is pinned.
    private class FixedIdentifierRandom : SecureRandom() {
        override fun nextInt(bound: Int): Int = NEW_IDENTIFIER
    }

    private companion object {
        const val ACCOUNT_ID = "slip39"
        const val ORIGINAL_IDENTIFIER = 7_000
        const val NEW_IDENTIFIER = 1_234
    }
}
