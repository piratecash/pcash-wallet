package cash.p.terminal.core.managers

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.EnabledWallet
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.entities.TonWalletVersion
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.models.AccountAddress
import io.horizontalsystems.tonkit.models.Action
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.EventInfo
import io.horizontalsystems.tonkit.models.Jetton
import io.horizontalsystems.tonkit.models.JettonTransfer
import io.horizontalsystems.tonkit.models.JettonVerificationType
import io.horizontalsystems.tonkit.models.SyncState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TonAccountManagerTest {

    private val tonBlockchain = Blockchain(BlockchainType.Ton, "TON", null)

    private val account = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.Mnemonic(emptyList(), ""),
        origin = AccountOrigin.Created,
        level = 0,
        isBackedUp = true,
    )

    private lateinit var accountManager: IAccountManager
    private lateinit var walletManager: IWalletManager
    private lateinit var tonKitManager: TonKitManager
    private lateinit var tokenAutoEnableManager: TokenAutoEnableManager
    private lateinit var userDeletedWalletManager: UserDeletedWalletManager
    private lateinit var marketKit: MarketKitWrapper

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        accountManager = mockk(relaxed = true) {
            every { activeAccount } returns account
        }
        walletManager = mockk(relaxed = true) {
            every { activeWallets } returns emptyList()
        }
        tonKitManager = mockk(relaxed = true)
        tokenAutoEnableManager = mockk(relaxed = true) {
            every { isAutoEnabled(any(), any()) } returns true
        }
        userDeletedWalletManager = mockk(relaxed = true) {
            coEvery { isDeletedByUser(any(), any()) } returns false
        }
        marketKit = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun handle_jettonNotInMarketKit_doesNotSaveEnabledWallet() = runTest {
        val jetton = mockJetton(addressBase64 = TEST_ADDRESS_1, name = "Scam", symbol = "SCM", decimals = 6)
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns emptyList()

        val manager = createManager()
        manager.handle(setOf(jetton), account, TonWalletVersion.V4R2)

        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
    }

    @Test
    fun handle_jettonInMarketKit_savesEnabledWalletWithMarketKitMetadata() = runTest {
        val jetton = mockJetton(addressBase64 = TEST_ADDRESS_1, name = "OnchainName", symbol = "OCN", decimals = 0)
        val tokenType = TokenType.Jetton(jetton.address.toUserFriendly(true))
        val knownToken = token(tokenType, "Tether USD", "USDT", "img-url", 6)
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns listOf(knownToken)

        val savedSlot = slot<List<EnabledWallet>>()
        coEvery { walletManager.saveEnabledWallets(capture(savedSlot)) } returns Unit

        val manager = createManager()
        manager.handle(setOf(jetton), account, TonWalletVersion.V4R2)

        coVerify(exactly = 1) { walletManager.saveEnabledWallets(any()) }
        val saved = savedSlot.captured
        assertEquals(1, saved.size)
        val wallet = saved.single()
        assertEquals(TokenQuery(BlockchainType.Ton, tokenType).id, wallet.tokenQueryId)
        assertEquals("Tether USD", wallet.coinName)
        assertEquals("USDT", wallet.coinCode)
        assertEquals(6, wallet.coinDecimals)
        assertEquals("img-url", wallet.coinImage)
    }

    @Test
    fun handle_emptyJettons_doesNotCallWalletManager() = runTest {
        val manager = createManager()
        manager.handle(emptySet(), account, TonWalletVersion.V4R2)

        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
    }

    @Test
    fun handle_jettonAlreadyEnabled_doesNotSaveDuplicate() = runTest {
        val jetton = mockJetton(addressBase64 = TEST_ADDRESS_1, name = "OnchainName", symbol = "OCN", decimals = 0)
        val tokenType = TokenType.Jetton(jetton.address.toUserFriendly(true))
        val existingWallet = mockk<Wallet> {
            every { token } returns token(tokenType, "Tether USD", "USDT", "img-url", 6)
        }
        every { walletManager.activeWallets } returns listOf(existingWallet)

        val manager = createManager()
        manager.handle(setOf(jetton), account, TonWalletVersion.V4R2)

        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
    }

    @Test
    fun handle_jettonDeletedByUser_doesNotResave() = runTest {
        val jetton = mockJetton(addressBase64 = TEST_ADDRESS_1, name = "OnchainName", symbol = "OCN", decimals = 0)
        val tokenType = TokenType.Jetton(jetton.address.toUserFriendly(true))
        val knownToken = token(tokenType, "Tether USD", "USDT", "img-url", 6)
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns listOf(knownToken)
        coEvery {
            userDeletedWalletManager.isDeletedByUser(account.id, TokenQuery(BlockchainType.Ton, tokenType).id)
        } returns true

        val manager = createManager()
        manager.handle(setOf(jetton), account, TonWalletVersion.V4R2)

        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
    }

    @Test
    fun handle_w5Version_savesW5JettonWallet() = runTest {
        val jetton = mockJetton(addressBase64 = TEST_ADDRESS_1, name = "OnchainName", symbol = "OCN", decimals = 0)
        val tokenType = TokenType.TonW5Jetton(jetton.address.toUserFriendly(true))
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns
            listOf(token(tokenType, "Tether USD", "USDT", null, 6))
        val savedSlot = slot<List<EnabledWallet>>()
        coEvery { walletManager.saveEnabledWallets(capture(savedSlot)) } returns Unit

        createManager().handle(setOf(jetton), account, TonWalletVersion.W5)

        assertEquals(listOf(TokenQuery(BlockchainType.Ton, tokenType).id), savedSlot.captured.map { it.tokenQueryId })
    }

    @Test
    fun start_w5HistoryEmittedBeforeSubscription_enablesStoredJetton() = runTest {
        val tonKit = tonKit(liveEvents = MutableSharedFlow())
        coEvery { tonKit.events(any(), null, any()) } returns listOf(jettonReceivedEvent(jetton(TEST_ADDRESS_1)))

        val saved = startRestored(TonWalletVersion.W5, tonKit, expectedSaves = 1)

        assertEquals(listOf(w5JettonQueryId(TEST_ADDRESS_1)), saved)
    }

    @Test
    fun start_w5StoredHistoryTwoPages_enablesJettonsFromBothPages() = runTest {
        val tonKit = tonKit(liveEvents = MutableSharedFlow())
        val firstPageLastLt = 500L
        coEvery { tonKit.events(any(), null, any()) } answers {
            List(thirdArg<Int>()) { jettonReceivedEvent(jetton(TEST_ADDRESS_1), lt = firstPageLastLt + it) }.reversed()
        }
        coEvery { tonKit.events(any(), firstPageLastLt, any()) } returns
            listOf(jettonReceivedEvent(jetton(TEST_ADDRESS_2), lt = 1))

        val saved = startRestored(TonWalletVersion.W5, tonKit, expectedSaves = 2)

        assertEquals(listOf(w5JettonQueryId(TEST_ADDRESS_1), w5JettonQueryId(TEST_ADDRESS_2)), saved)
    }

    @Test
    fun start_restoredAccountV4R2InitialHistory_skipsJettonDiscoveryAndStoredScan() = runTest {
        val processed = CompletableDeferred<Unit>()
        val tonKit = tonKit(
            liveEvents = flow {
                emit(EventInfo(listOf(jettonReceivedEvent(jetton(TEST_ADDRESS_1))), initial = true))
                processed.complete(Unit)
            }
        )

        startRestored(TonWalletVersion.V4R2, tonKit, expectedSaves = 0)
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { processed.await() }
            // The stored scan would run in parallel with the live collector; give it time to show up.
            delay(200)
        }

        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
        coVerify(exactly = 0) { tonKit.events(any(), any(), any()) }
    }

    private fun tonKit(liveEvents: Flow<EventInfo>) = mockk<TonKit> {
        every { receiveAddress } returns Address.parse(OWN_ADDRESS)
        every { eventFlow(any()) } returns liveEvents
        every { eventSyncStateFlow } returns MutableStateFlow(SyncState.Synced)
        coEvery { events(any(), any(), any()) } returns emptyList()
    }

    // A restored account without auto-enable skips the initial V4R2 batch; W5 is enabled explicitly.
    private suspend fun startRestored(version: TonWalletVersion, tonKit: TonKit, expectedSaves: Int): List<String> {
        every { accountManager.activeAccount } returns account.copy(origin = AccountOrigin.Restored)
        every { tokenAutoEnableManager.isAutoEnabled(any(), any()) } returns false
        every { marketKit.tokens(any<List<TokenQuery>>()) } answers {
            firstArg<List<TokenQuery>>().map { token(it.tokenType, "Tether USD", "USDT", null, 6) }
        }
        val saved = CopyOnWriteArrayList<String>()
        val allSaved = CompletableDeferred<Unit>()
        coEvery { walletManager.saveEnabledWallets(any()) } answers {
            saved += firstArg<List<EnabledWallet>>().map { it.tokenQueryId }
            if (saved.size >= expectedSaves) allSaved.complete(Unit)
        }
        every { tonKitManager.startedKitsFlow } returns
            MutableStateFlow(mapOf(version to TonKitWrapper(tonKit, mockk(relaxed = true), version)))

        createManager().start()

        if (expectedSaves > 0) {
            withContext(Dispatchers.Default) { withTimeout(5_000) { allSaved.await() } }
        }
        return saved.toList()
    }

    private fun jetton(addressBase64: String) =
        mockJetton(addressBase64 = addressBase64, name = "Tether USD", symbol = "USDT", decimals = 6)

    private fun w5JettonQueryId(addressBase64: String) =
        TokenQuery(BlockchainType.Ton, TokenType.TonW5Jetton(Address.parse(addressBase64).toUserFriendly(true))).id

    private fun jettonReceivedEvent(jetton: Jetton, lt: Long = 1) = Event(
        id = "event-$lt",
        lt = lt,
        timestamp = 1,
        scam = false,
        inProgress = false,
        extra = 0,
        actions = listOf(
            Action(
                type = Action.Type.JettonTransfer,
                status = Action.Status.OK,
                tonTransfer = null,
                jettonTransfer = JettonTransfer(
                    sender = null,
                    recipient = AccountAddress(Address.parse(OWN_ADDRESS), null, isScam = false, isWallet = true),
                    sendersWallet = Address.parse(TEST_ADDRESS_1),
                    recipientsWallet = Address.parse(TEST_ADDRESS_1),
                    amount = BigInteger.ONE,
                    comment = null,
                    jetton = jetton,
                ),
                jettonBurn = null,
                jettonMint = null,
                contractDeploy = null,
                jettonSwap = null,
                smartContractExec = null,
            )
        ),
    )

    private fun createManager() = TonAccountManager(
        accountManager = accountManager,
        walletManager = walletManager,
        tonKitManager = tonKitManager,
        tokenAutoEnableManager = tokenAutoEnableManager,
        userDeletedWalletManager = userDeletedWalletManager,
        marketKit = marketKit,
    )

    private fun mockJetton(
        addressBase64: String,
        name: String,
        symbol: String,
        decimals: Int,
        verification: JettonVerificationType = JettonVerificationType.NONE,
    ): Jetton = Jetton(
        address = Address.parse(addressBase64),
        name = name,
        symbol = symbol,
        decimals = decimals,
        image = null,
        verification = verification,
    )

    private fun token(
        type: TokenType,
        coinName: String,
        coinCode: String,
        coinImage: String?,
        decimals: Int,
    ) = Token(
        coin = Coin(
            uid = "$coinCode-uid",
            name = coinName,
            code = coinCode,
            image = coinImage,
        ),
        blockchain = tonBlockchain,
        type = type,
        decimals = decimals,
    )

    companion object {
        // Valid base64url TON addresses for tests.
        private const val TEST_ADDRESS_1 =
            "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs"
        private const val TEST_ADDRESS_2 =
            "EQBynBO23ywHy_CgarY9NK9FTz0yDsG82PtcbSTQgGoXwiuA"
        private const val OWN_ADDRESS = "UQAzWZa6nM5mJev91wGc7VCSfBoIsYRqKJpV78N8Add9-RKY"
    }
}
