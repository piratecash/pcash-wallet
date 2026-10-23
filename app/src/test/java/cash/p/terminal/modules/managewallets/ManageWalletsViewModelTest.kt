package cash.p.terminal.modules.managewallets

import cash.p.terminal.core.App
import cash.p.terminal.core.storage.HardwarePublicKeyStorage
import cash.p.terminal.core.usecase.AddMoneroToTrezorAccountUseCase
import cash.p.terminal.core.R
import cash.p.terminal.ledger.domain.LedgerAppMissingException
import cash.p.terminal.ledger.domain.LedgerKey
import cash.p.terminal.ledger.domain.LedgerKeyRead
import cash.p.terminal.ledger.domain.LedgerRejectedException
import cash.p.terminal.ledger.domain.LedgerScanToAddUseCase
import cash.p.terminal.ledger.domain.LedgerSkippedApp
import cash.p.terminal.ledger.monero.LEDGER_MONERO_APP
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.modules.enablecoin.restoresettings.TokenConfig
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ledger_install_app
import cash.p.terminal.resources.ledger_update_app
import cash.p.terminal.modules.offline.OfflineOperationGate
import cash.p.terminal.tangem.domain.usecase.TangemScanUseCase
import cash.p.terminal.trezor.domain.usecase.FetchTrezorPublicKeysUseCase
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.HardwarePublicKeyType
import cash.p.terminal.wallet.entities.SecretString
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import io.horizontalsystems.core.CoreApp
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ManageWalletsViewModelTest : KoinTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val service = mockk<ManageWalletsService>(relaxed = true)
    private val accountManager = mockk<IAccountManager>()
    private val tangemScanUseCase = mockk<TangemScanUseCase>()
    private val hardwarePublicKeyStorage = mockk<HardwarePublicKeyStorage>(relaxed = true)
    private val hardwareWalletTokenPolicy = mockk<HardwareWalletTokenPolicy>()
    private val fetchTrezorPublicKeys = mockk<FetchTrezorPublicKeysUseCase>()
    private val addMonero = mockk<AddMoneroToTrezorAccountUseCase>()
    private val offlineOperationGate = mockk<OfflineOperationGate>(relaxed = true)
    private val ledgerScanToAdd = mockk<LedgerScanToAddUseCase>()
    private val itemsFlow = MutableStateFlow<List<ManageWalletsService.Item>>(emptyList())
    private val restoreSettingsRejectedFlow = MutableSharedFlow<Token>()
    private val enabledTokensFlow = MutableSharedFlow<Token>()
    private val account = trezorAccount()
    private val token = moneroToken()

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single<IAccountManager> { accountManager }
                single { tangemScanUseCase }
                single { hardwarePublicKeyStorage }
                single<HardwareWalletTokenPolicy> { hardwareWalletTokenPolicy }
                single<FetchTrezorPublicKeysUseCase> { fetchTrezorPublicKeys }
                single { addMonero }
                single { offlineOperationGate }
                single { ledgerScanToAdd }
            }
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(App.Companion)
        mockkObject(Translator)
        every { Translator.getString(Res.string.ledger_install_app, "TON") } returns INSTALL_TON
        every { Translator.getString(Res.string.ledger_install_app, LEDGER_MONERO_APP) } returns INSTALL_MONERO
        every { Translator.getString(Res.string.ledger_update_app, "TON") } returns UPDATE_TON
        val app = mockk<CoreApp> {
            every { getString(any()) } returns "Add via Trezor"
            every { getString(R.string.error_hardware_wallet_some_tokens_not_found) } returns GENERIC_NOT_FOUND
            every { getString(R.string.error_hardware_wallet_token_unavailable, any()) } answers {
                NOT_AVAILABLE_ON + (args[1] as Array<*>).single()
            }
        }
        every { App.instance } returns app
        every { accountManager.activeAccount } returns account
        every { accountManager.account(account.id) } returns account
        every { service.accountType } returns account.type
        every { service.itemsFlow } returns itemsFlow
        every { service.restoreSettingsRejectedFlow } returns restoreSettingsRejectedFlow
        every { service.enabledTokensFlow } returns enabledTokensFlow
        every { hardwareWalletTokenPolicy.isSupported(account, token) } returns true
        coEvery { hardwarePublicKeyStorage.getAllPublicKeys(account.id) } returns emptyList()
        coEvery { fetchTrezorPublicKeys(emptyList(), account.id) } returns emptyList()
        coEvery { addMonero.provision(account, any()) } returns account
    }

    @After
    fun tearDown() {
        unmockkObject(Translator)
        unmockkObject(App.Companion)
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test
    fun requestTrezorKeys_moneroPendingApproval_closesOnlyAfterEnabled() = runTest(dispatcher) {
        val viewModel = ManageWalletsViewModel(service, emptyList())
        answerMoneroHeightRequests(viewModel, MONERO_HEIGHT_CONFIG)
        viewModel.enable(token)

        viewModel.requestScanToAddTokens(closeAfterSuccess = true).join()

        assertFalse(viewModel.closeScreen)
        assertTrue(viewModel.showScanToAddButton)
        coVerify(exactly = 1) { addMonero.provision(account, MONERO_HEIGHT) }
        coVerify(exactly = 0) { addMonero(account) }
        verify(exactly = 1) { service.enable(token, MONERO_HEIGHT) }

        itemsFlow.value = listOf(
            ManageWalletsService.Item(token, enabled = true, hasInfo = false, available = true)
        )
        advanceUntilIdle()

        assertTrue(viewModel.closeScreen)
        assertFalse(viewModel.showScanToAddButton)
    }

    @Test
    fun requestScanToAddTokens_scanAlreadyRunning_reusesActiveJob() = runTest(dispatcher) {
        val provisionedAccount = CompletableDeferred<Account>()
        coEvery { addMonero.provision(account, any()) } coAnswers { provisionedAccount.await() }
        val viewModel = ManageWalletsViewModel(service, emptyList())
        answerMoneroHeightRequests(viewModel, MONERO_HEIGHT_CONFIG)
        viewModel.enable(token)

        val firstJob = viewModel.requestScanToAddTokens(closeAfterSuccess = false)
        val repeatedJob = viewModel.requestScanToAddTokens(closeAfterSuccess = true)

        assertSame(firstJob, repeatedJob)
        provisionedAccount.complete(account)
        firstJob.join()
        assertSame(
            firstJob,
            viewModel.requestScanToAddTokens(closeAfterSuccess = false),
        )
        coVerify(exactly = 1) { addMonero.provision(account, MONERO_HEIGHT) }
        verify(exactly = 1) { service.enable(token, MONERO_HEIGHT) }
    }

    @Test
    fun requestScanToAddTokens_trezorMonero_asksHeightBeforeProvisioning() = runTest(dispatcher) {
        val events = mutableListOf<String>()
        coEvery { addMonero.provision(account, any()) } coAnswers {
            events += "provision"
            account
        }
        val viewModel = ManageWalletsViewModel(service, emptyList())
        answerMoneroHeightRequests(viewModel, MONERO_HEIGHT_CONFIG, events)
        viewModel.enable(token)

        viewModel.requestScanToAddTokens(closeAfterSuccess = false).join()

        assertEquals(listOf("request", "provision"), events)
        coVerify(exactly = 1) { addMonero.provision(account, MONERO_HEIGHT) }
        verify(exactly = 1) { service.enable(token, MONERO_HEIGHT) }
        verify(exactly = 0) { service.enable(token) }
    }

    @Test
    fun requestScanToAddTokens_trezorMoneroHeightDismissed_skipsMoneroAndEnablesOthers() = runTest(dispatcher) {
        val ethToken = token(BlockchainType.Ethereum, TokenType.Native)
        every { hardwareWalletTokenPolicy.isSupported(account, ethToken) } returns true
        coEvery { fetchTrezorPublicKeys(listOf(ethToken.tokenQuery), account.id) } returns
            listOf(hardwareKey(BlockchainType.Ethereum, TokenType.Native, account.id))
        val viewModel = ManageWalletsViewModel(service, emptyList())
        answerMoneroHeightRequests(viewModel, config = null)
        viewModel.enable(token)
        viewModel.enable(ethToken)

        viewModel.requestScanToAddTokens(closeAfterSuccess = false).join()

        coVerify(exactly = 0) { addMonero.provision(any(), any()) }
        verify(exactly = 1) { service.enable(ethToken) }
        verify(exactly = 0) { service.enable(token) }
        verify(exactly = 0) { service.enable(token, any()) }
        assertFalse(viewModel.showScanToAddButton)
    }

    @Test
    fun requestScanToAddTokens_ledgerZcashCloseAfterSuccess_closesOnlyAfterHeightApproved() = runTest(dispatcher) {
        val zcashToken = token(BlockchainType.Zcash, TokenType.AddressSpecTyped(TokenType.AddressSpecType.Unified))
        val zcashKey = hardwareKey(BlockchainType.Zcash, zcashToken.type, ledgerAccount.id)
        var scanned = false
        coEvery { hardwarePublicKeyStorage.getAllPublicKeys(ledgerAccount.id) } coAnswers {
            if (scanned) listOf(zcashKey) else emptyList()
        }
        coEvery { ledgerScanToAdd.scan(any(), ledgerAccount.id) } coAnswers { scanned = true; ledgerRead() }
        val viewModel = ledgerViewModel()
        every { hardwareWalletTokenPolicy.isSupported(ledgerAccount, zcashToken) } returns true
        viewModel.enable(zcashToken)

        viewModel.requestScanToAddTokens(closeAfterSuccess = true).join()

        verify(exactly = 1) { service.enable(zcashToken) }
        assertFalse(viewModel.closeScreen)

        itemsFlow.value = listOf(
            ManageWalletsService.Item(zcashToken, enabled = true, hasInfo = false, available = true)
        )
        advanceUntilIdle()

        assertTrue(viewModel.closeScreen)
        assertFalse(viewModel.showScanToAddButton)
    }

    @Test
    fun restoreSettingsRejected_moneroPendingApproval_clearsSelectionWithoutClosing() =
        runTest(dispatcher) {
            val viewModel = ledgerMoneroViewModel()
            viewModel.enable(token)
            viewModel.requestScanToAddTokens(closeAfterSuccess = true).join()

            restoreSettingsRejectedFlow.emit(token)
            advanceUntilIdle()

            assertFalse(viewModel.closeScreen)
            assertFalse(viewModel.showScanToAddButton)
        }

    @Test
    fun restoreSettingsRejected_moneroPendingWithoutAutoClose_clearsSelection() =
        runTest(dispatcher) {
            val viewModel = ledgerMoneroViewModel()
            viewModel.enable(token)
            viewModel.requestScanToAddTokens(closeAfterSuccess = false).join()

            restoreSettingsRejectedFlow.emit(token)
            advanceUntilIdle()

            assertFalse(viewModel.closeScreen)
            assertFalse(viewModel.showScanToAddButton)
        }

    @Test
    fun enable_bep20WithStoredBscKey_enablesWithoutScan() = runTest(dispatcher) {
        val viewModel = tangemViewModel(listOf(hardwareKey(BlockchainType.BinanceSmartChain, TokenType.Native)))

        viewModel.enable(bep20Token)

        verify(exactly = 1) { service.enable(bep20Token) }
        assertFalse(viewModel.showScanToAddButton)
        confirmVerified(tangemScanUseCase)
    }

    @Test
    fun enable_bep20WithoutBscKey_queuesForScan() = runTest(dispatcher) {
        val viewModel = tangemViewModel(emptyList())

        viewModel.enable(bep20Token)

        verify(exactly = 0) { service.enable(bep20Token) }
        assertTrue(viewModel.showScanToAddButton)
    }

    @Test
    fun enable_bep20WithOnlyEthereumKey_queuesForScan() = runTest(dispatcher) {
        val viewModel = tangemViewModel(listOf(hardwareKey(BlockchainType.Ethereum, TokenType.Native)))

        viewModel.enable(bep20Token)

        verify(exactly = 0) { service.enable(bep20Token) }
        assertTrue(viewModel.showScanToAddButton)
    }

    @Test
    fun enable_bitcoinDerivationWithOtherDerivationStored_queuesForScan() = runTest(dispatcher) {
        val bip84 = TokenType.Derived(TokenType.Derivation.Bip84)
        val bip49Token = token(BlockchainType.Bitcoin, TokenType.Derived(TokenType.Derivation.Bip49))
        val viewModel = tangemViewModel(listOf(hardwareKey(BlockchainType.Bitcoin, bip84)))

        viewModel.enable(bip49Token)

        verify(exactly = 0) { service.enable(bip49Token) }
        assertTrue(viewModel.showScanToAddButton)
    }

    @Test
    fun enable_nativeWithExactKey_enablesWithoutPolicyCheck() = runTest(dispatcher) {
        val bnbToken = token(BlockchainType.BinanceSmartChain, TokenType.Native)
        val viewModel = tangemViewModel(listOf(hardwareKey(BlockchainType.BinanceSmartChain, TokenType.Native)))

        viewModel.enable(bnbToken)

        verify(exactly = 1) { service.enable(bnbToken) }
        verify(exactly = 0) { hardwareWalletTokenPolicy.isSupported(any<Account>(), any<Token>()) }
        assertFalse(viewModel.showScanToAddButton)
    }

    @Test
    fun enable_unsupportedTokenWithChainKey_showsErrorAndDoesNotEnable() = runTest(dispatcher) {
        val viewModel = tangemViewModel(listOf(hardwareKey(BlockchainType.BinanceSmartChain, TokenType.Native)))
        every { hardwareWalletTokenPolicy.isSupported(tangemAccount, bep20Token) } returns false

        viewModel.enable(bep20Token)

        assertNotNull(viewModel.errorMsg)
        verify(exactly = 0) { service.enable(bep20Token) }
        assertFalse(viewModel.showScanToAddButton)
    }

    @Test
    fun enable_unsupportedTokenOnTangem_errorNamesTangem() = runTest(dispatcher) {
        assertUnsupportedTokenError(tangemAccount, "Tangem")
    }

    @Test
    fun enable_unsupportedTokenOnTrezor_errorNamesTrezor() = runTest(dispatcher) {
        assertUnsupportedTokenError(account, "Trezor")
    }

    @Test
    fun enable_unsupportedTokenOnLedger_errorNamesLedger() = runTest(dispatcher) {
        assertUnsupportedTokenError(ledgerAccount, "Ledger")
    }

    private fun assertUnsupportedTokenError(hardwareAccount: Account, deviceName: String) {
        every { accountManager.activeAccount } returns hardwareAccount
        every { service.accountType } returns hardwareAccount.type
        every { hardwareWalletTokenPolicy.isSupported(hardwareAccount, bep20Token) } returns false
        coEvery { hardwarePublicKeyStorage.getAllPublicKeys(hardwareAccount.id) } returns emptyList()
        val viewModel = ManageWalletsViewModel(service, emptyList())

        viewModel.enable(bep20Token)

        assertEquals(NOT_AVAILABLE_ON + deviceName, viewModel.errorMsg)
        verify(exactly = 0) { service.enable(bep20Token) }
    }

    @Test
    fun enable_softwareAccount_enablesDirectly() = runTest(dispatcher) {
        val softwareAccount = Account(
            id = "software-id",
            name = "Software",
            type = AccountType.Mnemonic(List(12) { "word$it" }, ""),
            origin = AccountOrigin.Created,
            level = 0,
        )
        every { accountManager.activeAccount } returns softwareAccount
        every { service.accountType } returns softwareAccount.type
        val viewModel = ManageWalletsViewModel(service, emptyList())

        viewModel.enable(bep20Token)

        verify(exactly = 1) { service.enable(bep20Token) }
        assertFalse(viewModel.showScanToAddButton)
    }

    @Test
    fun requestScanToAddTokens_ledgerAccount_scansThroughLedgerAndEnablesToken() = runTest(dispatcher) {
        val ethKey = hardwareKey(BlockchainType.Ethereum, TokenType.Native, ledgerAccount.id)
        var scanned = false
        coEvery { hardwarePublicKeyStorage.getAllPublicKeys(ledgerAccount.id) } coAnswers {
            if (scanned) listOf(ethKey) else emptyList()
        }
        coEvery { ledgerScanToAdd.scan(any(), ledgerAccount.id) } coAnswers { scanned = true; ledgerRead() }
        val viewModel = ledgerViewModel()
        viewModel.enable(ledgerEthToken)

        viewModel.requestScanToAddTokens(closeAfterSuccess = false).join()

        coVerify(exactly = 1) { ledgerScanToAdd.scan(listOf(ledgerEthToken.tokenQuery), ledgerAccount.id) }
        verify(exactly = 1) { service.enable(ledgerEthToken) }
        assertFalse(viewModel.showScanToAddButton)
        assertNull(viewModel.errorMsg)
    }

    @Test
    fun requestScanToAddTokens_ledgerRejectedOnDevice_finishesSilently() = runTest(dispatcher) {
        coEvery { hardwarePublicKeyStorage.getAllPublicKeys(ledgerAccount.id) } returns emptyList()
        coEvery { ledgerScanToAdd.scan(any(), ledgerAccount.id) } throws LedgerRejectedException()
        val viewModel = ledgerViewModel()
        viewModel.enable(ledgerEthToken)

        val job = viewModel.requestScanToAddTokens(closeAfterSuccess = false).also { it.join() }

        assertNull(viewModel.errorMsg)
        verify(exactly = 0) { service.enable(ledgerEthToken) }
        assertNotSame(job, viewModel.requestScanToAddTokens(closeAfterSuccess = false))
    }

    @Test
    fun requestScanToAddTokens_ledgerScanCancelled_propagatesCancellationWithoutError() = runTest(dispatcher) {
        coEvery { ledgerScanToAdd.scan(any(), ledgerAccount.id) } throws CancellationException("closed")
        val viewModel = ledgerViewModel()
        viewModel.enable(ledgerEthToken)

        val job = viewModel.requestScanToAddTokens(closeAfterSuccess = false).also { it.join() }

        assertTrue(job.isCancelled)
        assertNull(viewModel.errorMsg)
        assertNotSame(job, viewModel.requestScanToAddTokens(closeAfterSuccess = false))
    }

    @Test
    fun requestScanToAddTokens_ledgerMoneroKeyForgotten_scansMoneroSoItIsProvisioned() = runTest(dispatcher) {
        coEvery { hardwarePublicKeyStorage.getAllPublicKeys(ledgerAccount.id) } returns
            listOf(hardwareKey(BlockchainType.Ethereum, TokenType.Native, ledgerAccount.id))
        coEvery { ledgerScanToAdd.scan(any(), ledgerAccount.id) } returns ledgerRead()
        val viewModel = ledgerViewModel()
        every { hardwareWalletTokenPolicy.isSupported(ledgerAccount, token) } returns true
        viewModel.enable(token)

        viewModel.requestScanToAddTokens(closeAfterSuccess = false).join()

        coVerify(exactly = 1) { ledgerScanToAdd.scan(listOf(token.tokenQuery), ledgerAccount.id) }
        coVerify(exactly = 1) { addMonero.provision(ledgerAccount) }
        verify(exactly = 1) { service.enable(token) }
    }

    @Test
    fun requestScanToAddTokens_ledgerAppNotInstalled_showsInstallMessageAndKeepsTokenAwaiting() = runTest(dispatcher) {
        coEvery { ledgerScanToAdd.scan(any(), ledgerAccount.id) } returns
            ledgerRead(LedgerSkippedApp("TON", LedgerAppMissingException("TON")))
        val viewModel = ledgerViewModel()
        viewModel.enable(ledgerEthToken)

        viewModel.requestScanToAddTokens(closeAfterSuccess = false).join()

        assertEquals(INSTALL_TON, viewModel.errorMsg)
        assertTrue(viewModel.showScanToAddButton)
        verify(exactly = 0) { service.enable(ledgerEthToken) }
    }

    @Test
    fun requestScanToAddTokens_ledgerAppTooOld_showsUpdateMessage() = runTest(dispatcher) {
        coEvery { ledgerScanToAdd.scan(any(), ledgerAccount.id) } returns
            ledgerRead(LedgerSkippedApp("TON", LedgerException.AppVersionTooOld("TON", "1.0.0", "2.0.0")))
        val viewModel = ledgerViewModel()
        viewModel.enable(ledgerEthToken)

        viewModel.requestScanToAddTokens(closeAfterSuccess = false).join()

        assertEquals(UPDATE_TON, viewModel.errorMsg)
    }

    @Test
    fun requestScanToAddTokens_ledgerAppOpenDeclined_showsGenericMessage() = runTest(dispatcher) {
        coEvery { ledgerScanToAdd.scan(any(), ledgerAccount.id) } returns
            ledgerRead(LedgerSkippedApp("TON", LedgerRejectedException()))
        val viewModel = ledgerViewModel()
        viewModel.enable(ledgerEthToken)

        viewModel.requestScanToAddTokens(closeAfterSuccess = false).join()

        assertEquals(GENERIC_NOT_FOUND, viewModel.errorMsg)
    }

    @Test
    fun requestScanToAddTokens_sameFailureTwice_clearsMessageWhileScanningThenShowsItAgain() = runTest(dispatcher) {
        val missingApp = ledgerRead(LedgerSkippedApp("TON", LedgerAppMissingException("TON")))
        val viewModel = ledgerViewModel()
        var messageWhileScanning: String? = "not captured"
        coEvery { ledgerScanToAdd.scan(any(), ledgerAccount.id) } returns missingApp andThenAnswer {
            messageWhileScanning = viewModel.errorMsg
            missingApp
        }
        viewModel.enable(ledgerEthToken)

        viewModel.requestScanToAddTokens(closeAfterSuccess = false).join()
        assertEquals(INSTALL_TON, viewModel.errorMsg)
        viewModel.requestScanToAddTokens(closeAfterSuccess = false).join()

        assertNull(messageWhileScanning)
        assertEquals(INSTALL_TON, viewModel.errorMsg)
    }

    @Test
    fun requestScanToAddTokens_ledgerMoneroAppMissing_enablesFoundTokenWithoutProvisioningMonero() =
        runTest(dispatcher) {
            val ethKey = hardwareKey(BlockchainType.Ethereum, TokenType.Native, ledgerAccount.id)
            coEvery { hardwarePublicKeyStorage.getAllPublicKeys(ledgerAccount.id) } returns listOf(ethKey)
            coEvery { ledgerScanToAdd.scan(any(), ledgerAccount.id) } returns
                ledgerRead(LedgerSkippedApp(LEDGER_MONERO_APP, LedgerAppMissingException(LEDGER_MONERO_APP)))
            val viewModel = ledgerViewModel()
            every { hardwareWalletTokenPolicy.isSupported(ledgerAccount, token) } returns true
            viewModel.enable(ledgerEthToken)
            viewModel.enable(token)

            viewModel.requestScanToAddTokens(closeAfterSuccess = false).join()

            coVerify(exactly = 0) { addMonero.provision(any()) }
            verify(exactly = 1) { service.enable(ledgerEthToken) }
            verify(exactly = 0) { service.enable(token) }
            assertTrue(viewModel.showScanToAddButton)
            assertEquals(INSTALL_MONERO, viewModel.errorMsg)
        }

    @Test
    fun requestScanToAddTokens_ledgerMoneroOpenDeclined_staysAwaitingWithGenericMessage() = runTest(dispatcher) {
        coEvery { ledgerScanToAdd.scan(any(), ledgerAccount.id) } returns
            ledgerRead(LedgerSkippedApp(LEDGER_MONERO_APP, LedgerRejectedException()))
        val viewModel = ledgerViewModel()
        every { hardwareWalletTokenPolicy.isSupported(ledgerAccount, token) } returns true
        viewModel.enable(token)

        viewModel.requestScanToAddTokens(closeAfterSuccess = false).join()

        coVerify(exactly = 0) { addMonero.provision(any()) }
        verify(exactly = 0) { service.enable(token) }
        assertTrue(viewModel.showScanToAddButton)
        assertEquals(GENERIC_NOT_FOUND, viewModel.errorMsg)
    }

    private fun TestScope.answerMoneroHeightRequests(
        viewModel: ManageWalletsViewModel,
        config: TokenConfig?,
        events: MutableList<String> = mutableListOf(),
    ) {
        backgroundScope.launch {
            viewModel.moneroRestoreHeightRequests.collect {
                events += "request"
                viewModel.onMoneroRestoreHeightConfigured(config)
            }
        }
    }

    // A Ledger asks the Monero height after provisioning, through the restore-settings dialog.
    private fun ledgerMoneroViewModel(): ManageWalletsViewModel {
        coEvery { ledgerScanToAdd.scan(any(), ledgerAccount.id) } returns ledgerRead()
        every { hardwareWalletTokenPolicy.isSupported(ledgerAccount, token) } returns true
        return ledgerViewModel()
    }

    private fun ledgerRead(vararg skipped: LedgerSkippedApp) = LedgerKeyRead(emptyList<LedgerKey>(), skipped.toList())

    private val ledgerEthToken = token(BlockchainType.Ethereum, TokenType.Native)

    private fun ledgerViewModel(): ManageWalletsViewModel {
        every { accountManager.activeAccount } returns ledgerAccount
        every { service.accountType } returns ledgerAccount.type
        every { accountManager.account(ledgerAccount.id) } returns ledgerAccount
        coEvery { addMonero.provision(ledgerAccount) } returns ledgerAccount
        every { hardwareWalletTokenPolicy.isSupported(ledgerAccount, ledgerEthToken) } returns true
        return ManageWalletsViewModel(service, emptyList())
    }

    private val ledgerAccount = Account(
        id = "ledger-id",
        name = "Ledger",
        type = AccountType.LedgerDevice(
            deviceId = "device-id",
            model = "NanoSPlus",
            walletPublicKey = "wallet-public-key",
        ),
        origin = AccountOrigin.Created,
        level = 0,
    )

    private val tangemAccount = Account(
        id = "tangem-id",
        name = "Tangem",
        type = AccountType.HardwareCard(
            cardId = "card-id",
            backupCardsCount = 0,
            walletPublicKey = "wallet-public-key",
            signedHashes = 0,
        ),
        origin = AccountOrigin.Restored,
        level = 0,
    )

    private val bep20Token = token(BlockchainType.BinanceSmartChain, TokenType.Eip20("0xtoken"))

    private fun tangemViewModel(storedKeys: List<HardwarePublicKey>): ManageWalletsViewModel {
        every { accountManager.activeAccount } returns tangemAccount
        every { service.accountType } returns tangemAccount.type
        every { hardwareWalletTokenPolicy.isSupported(tangemAccount, any<Token>()) } returns true
        coEvery { hardwarePublicKeyStorage.getAllPublicKeys(tangemAccount.id) } returns storedKeys
        return ManageWalletsViewModel(service, emptyList())
    }

    private fun hardwareKey(
        blockchainType: BlockchainType,
        tokenType: TokenType,
        accountId: String = tangemAccount.id,
    ) = HardwarePublicKey(
        accountId = accountId,
        blockchainType = blockchainType.uid,
        type = HardwarePublicKeyType.PUBLIC_KEY,
        tokenType = tokenType,
        key = SecretString("key"),
        derivationPath = "m/44'/60'/0'/0/0",
        publicKey = ByteArray(0),
        derivedPublicKey = ByteArray(0),
    )

    private fun token(blockchainType: BlockchainType, type: TokenType) = Token(
        coin = Coin(uid = "${blockchainType.uid}-${type.id}", name = "Token", code = "TKN"),
        blockchain = Blockchain(blockchainType, blockchainType.uid, null),
        type = type,
        decimals = 18,
    )

    private fun trezorAccount() = Account(
        id = "account-id",
        name = "Trezor",
        type = AccountType.TrezorDevice(
            deviceId = "device-id",
            model = "T3T1",
            firmwareVersion = "2.8.10",
            walletPublicKey = "wallet-public-key",
        ),
        origin = AccountOrigin.Restored,
        level = 0,
    )

    private fun moneroToken() = Token(
        coin = Coin(uid = "monero", name = "Monero", code = "XMR"),
        blockchain = Blockchain(BlockchainType.Monero, "Monero", null),
        type = TokenType.Native,
        decimals = 12,
    )

    private companion object {
        const val INSTALL_TON = "Install the TON app via Ledger Live"
        const val UPDATE_TON = "Update the TON app via Ledger Live"
        const val INSTALL_MONERO = "Install the Monero app via Ledger Live"
        const val NOT_AVAILABLE_ON = "Not available on "
        const val GENERIC_NOT_FOUND = "Some tokens could not be added"
        const val MONERO_HEIGHT = 3_100_000L
        val MONERO_HEIGHT_CONFIG = TokenConfig(MONERO_HEIGHT.toString(), restoreAsNew = false)
    }
}
