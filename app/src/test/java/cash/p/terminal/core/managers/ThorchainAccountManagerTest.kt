package cash.p.terminal.core.managers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.EnabledWallet
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.thorchainkit.ThorchainKit
import io.horizontalsystems.thorchainkit.network.Network
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThorchainAccountManagerTest {

    private val blockchain = Blockchain(BlockchainType.Thorchain, "THORChain", null)
    private val account = account(AccountOrigin.Created)
    private val tcy = TokenType.ThorchainAsset(TCY_DENOM)

    private val accountManager = mockk<IAccountManager>(relaxed = true) {
        every { activeAccount } returns account
    }
    private val walletManager = mockk<IWalletManager>(relaxed = true) {
        every { activeWallets } returns emptyList()
    }
    private val tokenAutoEnableManager = mockk<TokenAutoEnableManager> {
        every { isAutoEnabled(any(), any()) } returns false
    }
    private val userDeletedWalletManager = mockk<UserDeletedWalletManager> {
        coEvery { isDeletedByUser(any(), any()) } returns false
    }
    private val marketKit = mockk<MarketKitWrapper>()
    private val thorchainKitManager = mockk<ThorchainKitManager> {
        every { blockchainType } returns BlockchainType.Thorchain
    }
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        managerScope.cancel()
    }

    @Test
    fun handle_nativeAndZeroBalances_enablesOnlyHeldTokens() = runTest {
        // The catalog knows every denom, so only the balance filter can keep native and zero rows out.
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns listOf(
            token(tcy, "TCY"),
            token(TokenType.ThorchainAsset(NATIVE_DENOM), "RUNE"),
            token(TokenType.ThorchainAsset(RUJI_DENOM), "RUJI"),
        )
        val saved = slot<List<EnabledWallet>>()
        coEvery { walletManager.saveEnabledWallets(capture(saved)) } returns Unit

        createManager().handle(
            mapOf(NATIVE_DENOM to BigInteger.TEN, TCY_DENOM to BigInteger.ONE, RUJI_DENOM to BigInteger.ZERO),
            NATIVE_DENOM,
            account,
            initial = false,
        )

        assertEquals(listOf(TokenQuery(BlockchainType.Thorchain, tcy).id), saved.captured.map { it.tokenQueryId })
    }

    @Test
    fun handle_tokenDeletedByUser_doesNotSave() = runTest {
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns listOf(token(tcy, "TCY"))
        coEvery {
            userDeletedWalletManager.isDeletedByUser(account.id, TokenQuery(BlockchainType.Thorchain, tcy).id)
        } returns true

        createManager().handle(mapOf(TCY_DENOM to BigInteger.ONE), NATIVE_DENOM, account, initial = false)

        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
    }

    @Test
    fun handle_initialSnapshotOfRestoredAccountWithoutAutoEnable_doesNotSave() = runTest {
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns listOf(token(tcy, "TCY"))

        createManager().handle(
            mapOf(TCY_DENOM to BigInteger.ONE),
            NATIVE_DENOM,
            account(AccountOrigin.Restored),
            initial = true,
        )

        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
    }

    @Test
    fun start_emissionHandlingFails_nextEmissionStillHandled() = runBlocking {
        val balancesFlow = MutableStateFlow(mapOf(TCY_DENOM to BigInteger.ONE))
        val kit = mockk<ThorchainKit> {
            every { this@mockk.balancesFlow } returns balancesFlow
            every { nativeDenom } returns NATIVE_DENOM
            every { network } returns Network.Mainnet
        }
        every { thorchainKitManager.kitStartedFlow } returns MutableStateFlow(true)
        every { thorchainKitManager.thorchainKitWrapper } returns ThorchainKitWrapper(kit)
        val firstLookupFailed = CompletableDeferred<Unit>()
        val lookups = AtomicInteger()
        every { marketKit.tokens(any<List<TokenQuery>>()) } answers {
            if (lookups.getAndIncrement() == 0) {
                firstLookupFailed.complete(Unit)
                error("catalog unavailable")
            }
            listOf(token(tcy, "TCY"))
        }
        val saved = CompletableDeferred<Unit>()
        coEvery { walletManager.saveEnabledWallets(any()) } answers { saved.complete(Unit) }

        createManager().start()
        withTimeout(TIMEOUT_MS) { firstLookupFailed.await() }
        balancesFlow.value = mapOf(TCY_DENOM to BigInteger.TWO)

        withTimeout(TIMEOUT_MS) { saved.await() }
    }

    @Test
    fun start_restoredAccountGatedSnapshotThenUnrelatedChange_enablesOnlyNewlyReceivedToken() = runBlocking {
        val restored = account(AccountOrigin.Restored)
        every { accountManager.activeAccount } returns restored
        val balancesFlow = startWithBalances(mapOf(NATIVE_DENOM to 100.toBigInteger(), TCY_DENOM to BigInteger.TEN))
        val saves = captureSaves()

        balancesFlow.value = mapOf(NATIVE_DENOM to 101.toBigInteger(), TCY_DENOM to BigInteger.TEN)
        balancesFlow.value = mapOf(
            NATIVE_DENOM to 101.toBigInteger(),
            TCY_DENOM to BigInteger.TEN,
            RUJI_DENOM to 5.toBigInteger(),
        )

        val ruji = TokenType.ThorchainAsset(RUJI_DENOM)
        assertEquals(listOf(TokenQuery(BlockchainType.Thorchain, ruji).id), withTimeout(TIMEOUT_MS) { saves.receive() })
        assertTrue(saves.isEmpty)
    }

    @Test
    fun start_restoredAccountDrainedThenReceives_enablesReceivedToken() = runBlocking {
        every { accountManager.activeAccount } returns account(AccountOrigin.Restored)
        val balancesFlow = startWithBalances(mapOf(NATIVE_DENOM to 100.toBigInteger(), TCY_DENOM to BigInteger.TEN))
        val saves = captureSaves()

        balancesFlow.value = emptyMap()
        balancesFlow.value = mapOf(TCY_DENOM to 5.toBigInteger())

        assertEquals(listOf(TokenQuery(BlockchainType.Thorchain, tcy).id), withTimeout(TIMEOUT_MS) { saves.receive() })
        assertTrue(saves.isEmpty)
    }

    @Test
    fun start_createdAccountFirstSnapshot_enablesHeldToken() = runBlocking {
        val saves = captureSaves()

        startWithBalances(mapOf(TCY_DENOM to BigInteger.TEN))

        assertEquals(listOf(TokenQuery(BlockchainType.Thorchain, tcy).id), withTimeout(TIMEOUT_MS) { saves.receive() })
    }

    private fun startWithBalances(initial: Map<String, BigInteger>): MutableStateFlow<Map<String, BigInteger>> {
        val balancesFlow = MutableStateFlow(initial)
        val kit = mockk<ThorchainKit> {
            every { this@mockk.balancesFlow } returns balancesFlow
            every { nativeDenom } returns NATIVE_DENOM
            every { network } returns Network.Mainnet
        }
        every { thorchainKitManager.kitStartedFlow } returns MutableStateFlow(true)
        every { thorchainKitManager.thorchainKitWrapper } returns ThorchainKitWrapper(kit)
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns listOf(
            token(tcy, "TCY"),
            token(TokenType.ThorchainAsset(RUJI_DENOM), "RUJI"),
        )
        createManager(Dispatchers.Unconfined).start()
        return balancesFlow
    }

    private fun captureSaves(): Channel<List<String>> {
        val saves = Channel<List<String>>(Channel.UNLIMITED)
        coEvery { walletManager.saveEnabledWallets(any()) } answers {
            saves.trySend(firstArg<List<EnabledWallet>>().map { it.tokenQueryId })
        }
        return saves
    }

    private fun createManager(dispatcher: CoroutineDispatcher = Dispatchers.IO) = ThorchainAccountManager(
        accountManager = accountManager,
        walletManager = walletManager,
        thorchainKitManager = thorchainKitManager,
        tokenAutoEnableManager = tokenAutoEnableManager,
        userDeletedWalletManager = userDeletedWalletManager,
        marketKit = marketKit,
        dispatcherProvider = TestDispatcherProvider(dispatcher, managerScope),
    )

    private fun token(type: TokenType, code: String) = Token(
        coin = Coin(uid = "$code-uid", name = code, code = code),
        blockchain = blockchain,
        type = type,
        decimals = 8,
    )

    private fun account(origin: AccountOrigin) = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.Mnemonic(emptyList(), ""),
        origin = origin,
        level = 0,
        isBackedUp = true,
    )

    private companion object {
        const val NATIVE_DENOM = "rune"
        const val TCY_DENOM = "tcy"
        const val RUJI_DENOM = "x/ruji"
        const val TIMEOUT_MS = 5_000L
    }
}
