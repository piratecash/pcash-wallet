package cash.p.terminal.modules.unlinkaccount

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountDeletionBlockedException
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.MnemonicDerivation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnlinkAccountViewModelTest {
    private val account = Account(
        id = "account", name = "Account", type = AccountType.EvmAddress("address"),
        origin = AccountOrigin.Created, level = 0, isBackedUp = false, isFileBackedUp = false,
    )
    private val manager = mockk<IAccountManager>(relaxed = true)

    @Test
    fun mnemonicAccount_requiresBothConfirmationsBeforeUnlinkIsEnabled() {
        val viewModel = UnlinkAccountViewModel(
            account.copy(type = AccountType.Mnemonic(emptyList(), "", MnemonicDerivation.Legacy)),
            manager,
        )

        assertEquals(
            listOf(ConfirmationType.ConfirmationRemove, ConfirmationType.ConfirmationLos),
            viewModel.confirmations.map(ConfirmationItem::confirmationType),
        )
        assertFalse(viewModel.unlinkEnabled)

        viewModel.confirmations.toList().forEach(viewModel::toggleConfirm)

        assertTrue(viewModel.unlinkEnabled)
    }

    @Test
    fun onUnlink_blocked_showsBlockedStateAndKeepsScreenOpen() = runTest {
        coEvery { manager.delete(account.id) } throws AccountDeletionBlockedException()
        val viewModel = UnlinkAccountViewModel(account, manager)

        viewModel.onUnlink().join()

        coVerify(exactly = 1) { manager.delete("account") }
        assertTrue(viewModel.deletionState.blocked)
        assertFalse(viewModel.closeScreen)
    }

    @Test
    fun onUnlink_allowed_closesOnlyAfterDeletionCompletes() = runTest {
        val viewModel = UnlinkAccountViewModel(account, manager)
        coEvery { manager.delete(account.id) } answers { assertFalse(viewModel.closeScreen) }

        viewModel.onUnlink().join()

        assertFalse(viewModel.deletionState.blocked)
        assertTrue(viewModel.closeScreen)
    }
}
