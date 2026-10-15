package cash.p.terminal.modules.managewallets

import cash.p.terminal.core.App
import cash.p.terminal.core.storage.HardwarePublicKeyStorage
import cash.p.terminal.core.usecase.AddMoneroToTrezorAccountUseCase
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
            }
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(App.Companion)
        val app = mockk<CoreApp> {
            every { getString(any()) } returns "Add via Trezor"
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
        coEvery { addMonero.provision(account) } returns account
    }

    @After
    fun tearDown() {
        unmockkObject(App.Companion)
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test
    fun requestTrezorKeys_moneroPendingApproval_closesOnlyAfterEnabled() = runTest(dispatcher) {
        val viewModel = ManageWalletsViewModel(service, emptyList())
        viewModel.enable(token)

        viewModel.requestScanToAddTokens(closeAfterSuccess = true).join()

        assertFalse(viewModel.closeScreen)
        assertTrue(viewModel.showScanToAddButton)
        coVerify(exactly = 1) { addMonero.provision(account) }
        coVerify(exactly = 0) { addMonero(account) }
        verify(exactly = 1) { service.enable(token) }

        itemsFlow.value = listOf(
            ManageWalletsService.Item(token, enabled = true, hasInfo = false)
        )
        advanceUntilIdle()

        assertTrue(viewModel.closeScreen)
        assertFalse(viewModel.showScanToAddButton)
    }

    @Test
    fun requestScanToAddTokens_scanAlreadyRunning_reusesActiveJob() = runTest(dispatcher) {
        val provisionedAccount = CompletableDeferred<Account>()
        coEvery { addMonero.provision(account) } coAnswers { provisionedAccount.await() }
        val viewModel = ManageWalletsViewModel(service, emptyList())
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
        coVerify(exactly = 1) { addMonero.provision(account) }
        verify(exactly = 1) { service.enable(token) }
    }

    @Test
    fun restoreSettingsRejected_moneroPendingApproval_clearsSelectionWithoutClosing() =
        runTest(dispatcher) {
            val viewModel = ManageWalletsViewModel(service, emptyList())
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
            val viewModel = ManageWalletsViewModel(service, emptyList())
            viewModel.enable(token)
            viewModel.requestScanToAddTokens(closeAfterSuccess = false).join()

            restoreSettingsRejectedFlow.emit(token)
            advanceUntilIdle()

            assertFalse(viewModel.closeScreen)
            assertFalse(viewModel.showScanToAddButton)
        }

    @Test
    fun enable_bep20WithStoredBscKey_enablesWithoutScan() = runTest(dispatcher) {
        val viewModel = tangemViewModel(listOf(tangemKey(BlockchainType.BinanceSmartChain, TokenType.Native)))

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
        val viewModel = tangemViewModel(listOf(tangemKey(BlockchainType.Ethereum, TokenType.Native)))

        viewModel.enable(bep20Token)

        verify(exactly = 0) { service.enable(bep20Token) }
        assertTrue(viewModel.showScanToAddButton)
    }

    @Test
    fun enable_bitcoinDerivationWithOtherDerivationStored_queuesForScan() = runTest(dispatcher) {
        val bip84 = TokenType.Derived(TokenType.Derivation.Bip84)
        val bip49Token = token(BlockchainType.Bitcoin, TokenType.Derived(TokenType.Derivation.Bip49))
        val viewModel = tangemViewModel(listOf(tangemKey(BlockchainType.Bitcoin, bip84)))

        viewModel.enable(bip49Token)

        verify(exactly = 0) { service.enable(bip49Token) }
        assertTrue(viewModel.showScanToAddButton)
    }

    @Test
    fun enable_nativeWithExactKey_enablesWithoutPolicyCheck() = runTest(dispatcher) {
        val bnbToken = token(BlockchainType.BinanceSmartChain, TokenType.Native)
        val viewModel = tangemViewModel(listOf(tangemKey(BlockchainType.BinanceSmartChain, TokenType.Native)))

        viewModel.enable(bnbToken)

        verify(exactly = 1) { service.enable(bnbToken) }
        verify(exactly = 0) { hardwareWalletTokenPolicy.isSupported(any<Account>(), any<Token>()) }
        assertFalse(viewModel.showScanToAddButton)
    }

    @Test
    fun enable_unsupportedTokenWithChainKey_showsErrorAndDoesNotEnable() = runTest(dispatcher) {
        val viewModel = tangemViewModel(listOf(tangemKey(BlockchainType.BinanceSmartChain, TokenType.Native)))
        every { hardwareWalletTokenPolicy.isSupported(tangemAccount, bep20Token) } returns false

        viewModel.enable(bep20Token)

        assertNotNull(viewModel.errorMsg)
        verify(exactly = 0) { service.enable(bep20Token) }
        assertFalse(viewModel.showScanToAddButton)
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

    private fun tangemKey(blockchainType: BlockchainType, tokenType: TokenType) = HardwarePublicKey(
        accountId = tangemAccount.id,
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
}
