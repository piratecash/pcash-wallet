package cash.p.terminal.modules.managewallets

import cash.p.terminal.core.managers.RestoreSettings
import cash.p.terminal.core.managers.UserDeletedWalletManager
import cash.p.terminal.core.usecase.OfflineModeUseCase
import cash.p.terminal.core.usecase.RescanMoneroUseCase
import cash.p.terminal.modules.enablecoin.restoresettings.RestoreSettingsService
import cash.p.terminal.modules.receive.FullCoinsProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.policy.HardwareWalletTokenPolicy
import cash.p.terminal.wallet.useCases.GetHardwarePublicKeyForWalletUseCase
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class ManageWalletsServiceTest : KoinTest {

    private val dispatcher = StandardTestDispatcher()
    private val walletManager = mockk<IWalletManager>(relaxed = true)
    private val accountManager = mockk<IAccountManager>()
    private val restoreSettingsService = mockk<RestoreSettingsService>(relaxed = true)
    private val userDeletedWalletManager = mockk<UserDeletedWalletManager>(relaxed = true)
    private val offlineModeUseCase = mockk<OfflineModeUseCase>(relaxed = true)
    private val rescanMoneroUseCase = mockk<RescanMoneroUseCase>(relaxed = true)
    private val walletFactory = mockk<WalletFactory>()
    private val hardwarePublicKeyProvider =
        mockk<GetHardwarePublicKeyForWalletUseCase>(relaxed = true)
    private val marketKit = mockk<MarketKitWrapper>(relaxed = true)
    private val hardwareWalletTokenPolicy = mockk<HardwareWalletTokenPolicy>()

    private val approveSettingsSubject =
        PublishSubject.create<RestoreSettingsService.TokenWithSettings>()
    private val rejectSettingsSubject = PublishSubject.create<Token>()
    private val activeWalletsFlow = MutableStateFlow<List<Wallet>>(emptyList())

    private val account = account()
    private val zcashTokens = zcashTokens()
    private var activeWallets = emptyList<Wallet>()
    private lateinit var service: ManageWalletsService

    @get:Rule
    val koinRule = KoinTestRule.create {
        modules(
            module {
                single { walletFactory }
                single { hardwarePublicKeyProvider }
                single<MarketKitWrapper> { marketKit }
                single { offlineModeUseCase }
                single<HardwareWalletTokenPolicy> { hardwareWalletTokenPolicy }
            }
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        activeWallets = emptyList()
        activeWalletsFlow.value = activeWallets

        every { restoreSettingsService.approveSettingsObservable } returns approveSettingsSubject
        every { restoreSettingsService.rejectApproveSettingsObservable } returns rejectSettingsSubject
        every { walletManager.activeWallets } answers { activeWallets }
        every { walletManager.activeWalletsFlow } returns activeWalletsFlow
        every { accountManager.account(account.id) } returns account

        service = ManageWalletsService(
            walletManager = walletManager,
            restoreSettingsService = restoreSettingsService,
            fullCoinsProvider = null,
            account = account,
            userDeletedWalletManager = userDeletedWalletManager,
            rescanMoneroUseCase = rescanMoneroUseCase,
            accountManager = accountManager,
        )
    }

    @After
    fun tearDown() {
        service.clear()
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test
    fun enable_zcashRestoredWithoutActiveZcash_requestsBirthdayHeight() = runTest(dispatcher) {
        val unifiedToken = zcashTokens.first {
            it.type == TokenType.AddressSpecTyped(TokenType.AddressSpecType.Unified)
        }

        service.enable(unifiedToken)
        advanceUntilIdle()

        verify {
            restoreSettingsService.approveSettings(
                token = unifiedToken,
                account = account,
                forceRequest = true,
                initialConfig = null
            )
        }
    }

    @Test
    fun disable_lastWalletOfChain_resetsOfflineModeAfterDeletion() = runTest(dispatcher) {
        val token = bitcoinToken()
        activeWallets = listOf(wallet(token))
        val offlineModeReset = signalOn(token)

        service.disable(token)
        advanceUntilSignalled(offlineModeReset)

        coVerifyOrder {
            walletManager.deleteByTokenQueryIds(account.id, setOf(token.tokenQuery.id))
            offlineModeUseCase.resetIfBlockchainRemoved(account, BlockchainType.Bitcoin)
        }
    }

    @Test
    fun itemsFlow_hardwareAccount_listsDeviceUnsupportedTokensLastAndUnavailable() = runTest(dispatcher) {
        every { hardwareWalletTokenPolicy.isSupported(account, ethereumToken) } returns false
        every { hardwareWalletTokenPolicy.isSupported(account, bscToken) } returns true
        service = serviceFor(account, FullCoin(ethereumToken.coin, listOf(ethereumToken, bscToken)))

        val items = awaitItems()

        assertEquals(listOf(bscToken, ethereumToken), items.map { it.token })
        assertEquals(listOf(true, false), items.map { it.available })
    }

    @Test
    fun itemsFlow_hardwareAccountWithTransparentZcash_checksOnlyTransparent() = runTest(dispatcher) {
        val transparent = zcashTokens.first {
            it.type == TokenType.AddressSpecTyped(TokenType.AddressSpecType.Transparent)
        }
        activeWallets = listOf(wallet(transparent))
        zcashTokens.forEach {
            every { hardwareWalletTokenPolicy.isSupported(account, it) } returns (it == transparent)
        }
        service = serviceFor(account, FullCoin(transparent.coin, zcashTokens))

        val enabledTokens = awaitItems().filter { it.enabled }.map { it.token }

        assertEquals(listOf(transparent), enabledTokens)
    }

    @Test
    fun itemsFlow_softwareAccount_listsEligibleTokensAvailableWithoutPolicy() = runTest(dispatcher) {
        val solanaAccount = account.copy(type = AccountType.SolanaAddress("address"))
        service = serviceFor(solanaAccount, FullCoin(ethereumToken.coin, listOf(ethereumToken, solanaToken)))

        val items = awaitItems()

        assertEquals(listOf(solanaToken), items.map { it.token })
        assertEquals(listOf(true), items.map { it.available })
        verify(exactly = 0) { hardwareWalletTokenPolicy.isSupported(any<Account>(), any<Token>()) }
    }

    private fun serviceFor(account: Account, fullCoin: FullCoin): ManageWalletsService {
        service.clear()
        every { accountManager.account(account.id) } returns account
        val provider = mockk<FullCoinsProvider>(relaxed = true)
        coEvery { provider.getItems() } returns listOf(fullCoin)
        return ManageWalletsService(
            walletManager = walletManager,
            restoreSettingsService = restoreSettingsService,
            fullCoinsProvider = provider,
            account = account,
            userDeletedWalletManager = userDeletedWalletManager,
            rescanMoneroUseCase = rescanMoneroUseCase,
            accountManager = accountManager,
        )
    }

    /** Items arrive after a Dispatchers.IO hop, which one drain can outrun. */
    private fun TestScope.awaitItems(): List<ManageWalletsService.Item> {
        repeat(SETTLE_ATTEMPTS) {
            advanceUntilIdle()
            service.itemsFlow.value.takeIf { it.isNotEmpty() }?.let { return it }
            Thread.sleep(SETTLE_STEP_MS)
        }
        return service.itemsFlow.value
    }

    private fun signalOn(token: Token) = CountDownLatch(1).also { signal ->
        every {
            offlineModeUseCase.resetIfBlockchainRemoved(account, token.blockchainType)
        } answers { signal.countDown() }
    }

    /** The service holds its mutex across a Dispatchers.IO hop, which one drain can outrun. */
    private fun TestScope.advanceUntilSignalled(signal: CountDownLatch) {
        repeat(SETTLE_ATTEMPTS) {
            advanceUntilIdle()
            if (signal.await(SETTLE_STEP_MS, TimeUnit.MILLISECONDS)) return
        }
        advanceUntilIdle()
    }

    private fun bitcoinToken() = Token(
        coin = Coin(uid = "bitcoin", name = "Bitcoin", code = "BTC"),
        blockchain = Blockchain(BlockchainType.Bitcoin, "Bitcoin", null),
        type = TokenType.Derived(TokenType.Derivation.Bip84),
        decimals = 8
    )

    private val ethereumToken = Token(
        coin = Coin(uid = "wrapped", name = "Wrapped", code = "WRP"),
        blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
        type = TokenType.Native,
        decimals = 18
    )

    private val bscToken = Token(
        coin = Coin(uid = "wrapped", name = "Wrapped", code = "WRP"),
        blockchain = Blockchain(BlockchainType.BinanceSmartChain, "BNB Smart Chain", null),
        type = TokenType.Eip20("0xwrapped"),
        decimals = 18
    )

    private val solanaToken = Token(
        coin = Coin(uid = "wrapped", name = "Wrapped", code = "WRP"),
        blockchain = Blockchain(BlockchainType.Solana, "Solana", null),
        type = TokenType.Native,
        decimals = 9
    )

    private fun wallet(token: Token) = mockk<Wallet> {
        every { this@mockk.token } returns token
    }

    @Test
    fun enable_trezorMoneroWithApprovedHeight_appliesHardwareRescan() = runTest(dispatcher) {
        assertApprovedMoneroHeightRescansBeforeCreatingWallet(account)
    }

    @Test
    fun enable_ledgerMoneroWithApprovedHeight_appliesHardwareRescan() = runTest(dispatcher) {
        val ledgerAccount = account.copy(
            type = AccountType.LedgerDevice("device-id", "NanoSPlus", "wallet-public-key")
        )
        every { hardwareWalletTokenPolicy.isSupported(ledgerAccount, any<Token>()) } returns true
        service = serviceFor(ledgerAccount, FullCoin(moneroToken().coin, listOf(moneroToken())))

        assertApprovedMoneroHeightRescansBeforeCreatingWallet(ledgerAccount)
    }

    @Test
    fun enable_withBirthdayHeight_savesHeightWithoutRequest() = runTest(dispatcher) {
        val token = moneroToken()
        coEvery { hardwarePublicKeyProvider(account, token) } returns null
        every { walletFactory.create(token, account, null) } returns wallet(token)
        advanceUntilIdle()

        service.enable(token, MONERO_RESTORE_HEIGHT)
        advanceUntilIdle()

        verify(timeout = 2_000) { walletFactory.create(token, account, null) }
        verify {
            restoreSettingsService.save(
                match { it.birthdayHeight == MONERO_RESTORE_HEIGHT },
                account,
                BlockchainType.Monero,
            )
        }
        verify(exactly = 0) { restoreSettingsService.approveSettings(any(), any(), any(), any()) }
    }

    private fun TestScope.assertApprovedMoneroHeightRescansBeforeCreatingWallet(account: Account) {
        val token = moneroToken()
        val settings = RestoreSettings().apply {
            birthdayHeight = MONERO_RESTORE_HEIGHT
        }
        coEvery { hardwarePublicKeyProvider(account, token) } returns null
        every { walletFactory.create(token, account, null) } returns wallet(token)
        advanceUntilIdle()

        approveSettingsSubject.onNext(
            RestoreSettingsService.TokenWithSettings(token, settings)
        )
        advanceUntilIdle()

        verify(timeout = 2_000) {
            walletFactory.create(token, account, null)
        }
        coVerifyOrder {
            rescanMoneroUseCase(account, MONERO_RESTORE_HEIGHT)
            walletFactory.create(token, account, null)
        }
    }

    @Test
    fun enable_trezorMoneroWithRejectedHeight_doesNotCreateWallet() = runTest(dispatcher) {
        val token = moneroToken()

        service.enable(token)
        advanceUntilIdle()
        rejectSettingsSubject.onNext(token)
        advanceUntilIdle()

        verify(exactly = 1) {
            restoreSettingsService.approveSettings(
                token = token,
                account = account,
                forceRequest = true,
                initialConfig = null,
            )
        }
        coVerify(exactly = 0) { rescanMoneroUseCase(any(), any()) }
        verify(exactly = 0) { walletFactory.create(any(), any(), any()) }
        coVerify(exactly = 0) { walletManager.saveSuspended(any()) }
    }

    private fun zcashTokens(): List<Token> {
        val coin = Coin(uid = "zcash", name = "Zcash", code = "ZEC")
        val blockchain = Blockchain(BlockchainType.Zcash, "Zcash", null)
        return TokenType.AddressSpecType.entries.map {
            Token(
                coin = coin,
                blockchain = blockchain,
                type = TokenType.AddressSpecTyped(it),
                decimals = 8
            )
        }
    }

    private fun moneroToken() = Token(
        coin = Coin(uid = "monero", name = "Monero", code = "XMR"),
        blockchain = Blockchain(BlockchainType.Monero, "Monero", null),
        type = TokenType.Native,
        decimals = 12,
    )

    private fun account() = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.TrezorDevice(
            deviceId = "device-id",
            model = "T3T1",
            firmwareVersion = "2.8.10",
            walletPublicKey = "wallet-public-key",
        ),
        origin = AccountOrigin.Restored,
        level = 0
    )

    private companion object {
        const val MONERO_RESTORE_HEIGHT = 3_529_956L
        const val SETTLE_ATTEMPTS = 200
        const val SETTLE_STEP_MS = 10L
    }

}
