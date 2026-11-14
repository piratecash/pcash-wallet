package cash.p.terminal.modules.hardwarewallet

import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.usecase.CreateHardwareWalletUseCase
import cash.p.terminal.core.usecase.RescanMoneroUseCase
import cash.p.terminal.core.usecase.RescanZcashUseCase
import cash.p.terminal.modules.enablecoin.restoresettings.TokenConfig
import cash.p.terminal.tangem.domain.usecase.TangemScanUseCase
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.useCases.IGetMoneroWalletFilesNameUseCase
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HardwareWalletViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val accountManager = mockk<IAccountManager>()
    private val walletManager = mockk<IWalletManager>()
    private val getMoneroWalletFilesName = mockk<IGetMoneroWalletFilesNameUseCase>()
    private val rescanMonero = mockk<RescanMoneroUseCase>(relaxed = true)
    private val rescanZcash = mockk<RescanZcashUseCase>(relaxed = true)
    private val accountFactory = mockk<IAccountFactory> {
        every { getNextHardwareAccountName() } returns "Hardware 1"
    }

    private val ledgerAccount = account(AccountType.LedgerDevice("ledger-id", "nanoSP", "wallet-key"))
    private val trezorAccount = account(
        AccountType.TrezorDevice(
            deviceId = "trezor-id",
            model = "T3T1",
            firmwareVersion = "2.8.10",
            walletPublicKey = "wallet-key",
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onHardwareSetupFinished_ledgerWithZcashAndMonero_requestsZcashThenMonero() = runTest(dispatcher) {
        givenAccount(ledgerAccount, zcash = true, moneroFiles = true)
        val viewModel = createViewModel()
        val requests = collectRequests(viewModel)

        viewModel.onHardwareSetupFinished(created = true)
        assertEquals(listOf(RestoreHeightChain.Zcash), requests)

        viewModel.onRestoreHeightConfigured(RestoreHeightChain.Zcash, null)
        assertEquals(listOf(RestoreHeightChain.Zcash, RestoreHeightChain.Monero), requests)
        assertFalse(viewModel.success)

        viewModel.onRestoreHeightConfigured(RestoreHeightChain.Monero, null)
        assertTrue(viewModel.success)
    }

    @Test
    fun onHardwareSetupFinished_trezorWithZcash_requestsZcashOnly() = runTest(dispatcher) {
        givenAccount(trezorAccount, zcash = true, moneroFiles = true)
        val viewModel = createViewModel()
        val requests = collectRequests(viewModel)

        viewModel.onHardwareSetupFinished(created = true)
        viewModel.onRestoreHeightConfigured(RestoreHeightChain.Zcash, null)

        assertEquals(listOf(RestoreHeightChain.Zcash), requests)
        assertTrue(viewModel.success)
    }

    @Test
    fun onHardwareSetupFinished_noHeightChains_finishesImmediately() = runTest(dispatcher) {
        givenAccount(ledgerAccount, zcash = false, moneroFiles = false)
        val viewModel = createViewModel()
        val requests = collectRequests(viewModel)

        viewModel.onHardwareSetupFinished(created = true)

        assertTrue(requests.isEmpty())
        assertTrue(viewModel.success)
    }

    @Test
    fun onHardwareSetupFinished_notCreated_doesNothing() = runTest(dispatcher) {
        givenAccount(ledgerAccount, zcash = true, moneroFiles = true)
        val viewModel = createViewModel()
        val requests = collectRequests(viewModel)

        viewModel.onHardwareSetupFinished(created = false)

        assertTrue(requests.isEmpty())
        assertFalse(viewModel.success)
    }

    @Test
    fun onRestoreHeightConfigured_zcashExistingHeight_rescansZcash() = runTest(dispatcher) {
        givenAccount(trezorAccount, zcash = true, moneroFiles = false)
        val viewModel = createViewModel()
        viewModel.onHardwareSetupFinished(created = true)

        viewModel.onRestoreHeightConfigured(RestoreHeightChain.Zcash, TokenConfig("2000000", restoreAsNew = false))

        coVerify(exactly = 1) { rescanZcash(trezorAccount, 2_000_000L) }
        assertTrue(viewModel.success)
    }

    @Test
    fun onRestoreHeightConfigured_zcashNewOrDismissed_doesNotRescan() = runTest(dispatcher) {
        givenAccount(ledgerAccount, zcash = true, moneroFiles = true)
        val viewModel = createViewModel()
        viewModel.onHardwareSetupFinished(created = true)

        viewModel.onRestoreHeightConfigured(RestoreHeightChain.Zcash, TokenConfig("3424810", restoreAsNew = true))
        viewModel.onHardwareSetupFinished(created = true)
        viewModel.onRestoreHeightConfigured(RestoreHeightChain.Zcash, null)

        coVerify(exactly = 0) { rescanZcash(any(), any()) }
    }

    @Test
    fun onRestoreHeightConfigured_zcashRescanFails_stillAdvances() = runTest(dispatcher) {
        givenAccount(ledgerAccount, zcash = true, moneroFiles = true)
        coEvery { rescanZcash(any(), any()) } throws IllegalStateException("offline")
        val viewModel = createViewModel()
        val requests = collectRequests(viewModel)
        viewModel.onHardwareSetupFinished(created = true)

        viewModel.onRestoreHeightConfigured(RestoreHeightChain.Zcash, TokenConfig("2000000", restoreAsNew = false))

        assertEquals(listOf(RestoreHeightChain.Zcash, RestoreHeightChain.Monero), requests)
    }

    @Test
    fun onRestoreHeightConfigured_zcashRescanStillRunning_requestsMoneroWithoutWaiting() = runTest(dispatcher) {
        givenAccount(ledgerAccount, zcash = true, moneroFiles = true)
        coEvery { rescanZcash(any(), any()) } coAnswers { awaitCancellation() }
        val viewModel = createViewModel()
        val requests = collectRequests(viewModel)
        viewModel.onHardwareSetupFinished(created = true)

        viewModel.onRestoreHeightConfigured(RestoreHeightChain.Zcash, TokenConfig("2000000", restoreAsNew = false))

        coVerify(exactly = 1) { rescanZcash(ledgerAccount, 2_000_000L) }
        assertEquals(listOf(RestoreHeightChain.Zcash, RestoreHeightChain.Monero), requests)
    }

    @Test
    fun onRestoreHeightConfigured_moneroHeight_rescansMonero() = runTest(dispatcher) {
        givenAccount(ledgerAccount, zcash = false, moneroFiles = true)
        val viewModel = createViewModel()
        viewModel.onHardwareSetupFinished(created = true)

        viewModel.onRestoreHeightConfigured(RestoreHeightChain.Monero, TokenConfig("3100000", restoreAsNew = false))

        coVerify(exactly = 1) { rescanMonero(ledgerAccount, 3_100_000L) }
        assertTrue(viewModel.success)
    }

    private fun givenAccount(account: Account, zcash: Boolean, moneroFiles: Boolean) {
        every { accountManager.activeAccount } returns account
        val wallets = if (zcash) listOf(wallet(BlockchainType.Zcash)) else emptyList()
        coEvery { walletManager.getWallets(account) } returns wallets
        coEvery { getMoneroWalletFilesName(account) } returns if (moneroFiles) "monero-files" else null
    }

    private fun TestScope.collectRequests(viewModel: HardwareWalletViewModel): List<RestoreHeightChain> {
        val requests = mutableListOf<RestoreHeightChain>()
        backgroundScope.launch { viewModel.restoreHeightRequests.toList(requests) }
        return requests
    }

    private fun createViewModel() = HardwareWalletViewModel(
        tangemScanUseCase = mockk<TangemScanUseCase>(),
        createHardwareWalletUseCase = mockk<CreateHardwareWalletUseCase>(),
        accountManager = accountManager,
        getMoneroWalletFilesName = getMoneroWalletFilesName,
        rescanMonero = rescanMonero,
        rescanZcash = rescanZcash,
        walletManager = walletManager,
        accountFactory = accountFactory,
    )

    private fun wallet(blockchainType: BlockchainType) = mockk<Wallet> {
        every { token.blockchainType } returns blockchainType
    }

    private fun account(type: AccountType) = Account(
        id = "account-id",
        name = "Hardware",
        type = type,
        origin = AccountOrigin.Created,
        level = 0,
    )
}
