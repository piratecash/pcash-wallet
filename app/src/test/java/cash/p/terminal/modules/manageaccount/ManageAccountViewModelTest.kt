package cash.p.terminal.modules.manageaccount

import cash.p.terminal.core.utils.MoneroGoldenSeeds
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
class ManageAccountViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val accountManager = mockk<IAccountManager>(relaxed = true) {
        every { accountsFlow } returns MutableSharedFlow()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun getMoneroSpendKey_moneroAccount_returnsKeyDerivedFromThatAccount() {
        val first = createViewModel(moneroAccount("first", MoneroGoldenSeeds.SEED_0))
        val second = createViewModel(moneroAccount("second", MoneroGoldenSeeds.SEED_1))

        assertEquals(MoneroGoldenSeeds.SPEND_KEY_0, first.getMoneroSpendKey())
        assertEquals(MoneroGoldenSeeds.VIEW_KEY_0, first.getMoneroViewKey())
        assertEquals(MoneroGoldenSeeds.SPEND_KEY_1, second.getMoneroSpendKey())
        assertEquals(MoneroGoldenSeeds.VIEW_KEY_1, second.getMoneroViewKey())
    }

    @Test
    fun getKeyActions_moneroAccountWithValidWords_showsViewAndSpendKey() = runTest(dispatcher) {
        val viewModel = createViewModel(moneroAccount("first", MoneroGoldenSeeds.SEED_0))
        advanceUntilIdle()

        assertEquals(
            listOf(
                ManageAccountModule.KeyAction.RecoveryPhrase,
                ManageAccountModule.KeyAction.ViewKey,
                ManageAccountModule.KeyAction.SpendKey
            ),
            viewModel.viewState.keyActions
        )
    }

    @Test
    fun getKeyActions_moneroAccountWithUnderivableWords_hidesViewAndSpendKey() = runTest(dispatcher) {
        val viewModel = createViewModel(moneroAccount("first", words = emptyList()))
        advanceUntilIdle()

        assertEquals(
            listOf(ManageAccountModule.KeyAction.RecoveryPhrase),
            viewModel.viewState.keyActions
        )
    }

    @Test
    fun getMoneroViewKey_underivableWords_returnsNull() {
        val viewModel = createViewModel(moneroAccount("first", words = emptyList()))

        assertNull(viewModel.getMoneroViewKey())
        assertNull(viewModel.getMoneroSpendKey())
    }

    @Test
    fun getMoneroViewKey_nonMoneroAccount_returnsNull() {
        val viewModel = createViewModel(
            account("evm", AccountType.EvmPrivateKey(BigInteger.ONE))
        )

        assertNull(viewModel.getMoneroViewKey())
        assertNull(viewModel.getMoneroSpendKey())
    }

    private fun createViewModel(account: Account) = ManageAccountViewModel(account, accountManager)

    private fun moneroAccount(id: String, words: List<String>) = account(
        id = id,
        type = AccountType.MnemonicMonero(
            words = words,
            password = "password",
            height = 1,
            walletInnerName = "wallet-$id"
        )
    )

    private fun account(id: String, type: AccountType) = Account(
        id = id,
        name = "Account $id",
        type = type,
        origin = AccountOrigin.Restored,
        level = 0,
        isBackedUp = true
    )
}
