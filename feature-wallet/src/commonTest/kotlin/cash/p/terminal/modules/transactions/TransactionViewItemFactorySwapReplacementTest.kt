package cash.p.terminal.modules.transactions

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.TransactionSettingsStorage
import cash.p.terminal.core.adapters.BitcoinReplacementLink
import cash.p.terminal.core.adapters.BitcoinReplacementRegistry
import cash.p.terminal.core.managers.AddressMetadataManager
import cash.p.terminal.core.managers.BalanceHiddenManager
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.core.storage.PendingMultiSwapStorage
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.usecase.RelinkSwapToReplacementUseCase
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import cash.p.terminal.modules.balance.token.addresspoisoning.AddressPoisoningViewMode
import cash.p.terminal.modules.multiswap.providers.AndroidUnstoppableProviderTitles
import cash.p.terminal.modules.transactions.poison_status.PoisonStatus
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.wallet.WalletFactory
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.helpers.DateHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.math.BigDecimal

class TransactionViewItemFactorySwapReplacementTest {

    private companion object {
        const val UID_A = "uid-A"
        const val UID_B = "uid-B"
        const val HASH_A = "aaaa"
        const val HASH_B = "bbbb"
        const val SWAP_DATE = 1_000L
        const val TIMESTAMP = 1_779_766_789L
    }

    private val wallet = WalletFactory.previewWallet()
    private var storedLinks = emptySet<String>()
    private val settingsStorage = mockk<TransactionSettingsStorage> {
        every { addressPoisoningViewMode } returns AddressPoisoningViewMode.COMPACT
        every { bitcoinReplacementLinks } answers { storedLinks }
        every { bitcoinReplacementLinks = any() } answers { storedLinks = firstArg() }
    }
    private val registry = BitcoinReplacementRegistry(settingsStorage)
    private val swapStorage = mockk<SwapProviderTransactionsStorage>(relaxed = true) {
        every { getByOutgoingRecordUid(any()) } returns null
        every { getByTokenIn(any(), any(), any()) } returns null
    }
    private val balanceHiddenManager = mockk<BalanceHiddenManager> {
        every { balanceHidden } returns false
        every { isTransactionInfoHidden(any()) } returns false
    }
    private val numberFormatter = mockk<IAppNumberFormatter> {
        every { formatCoinShort(any(), any(), any()) } returns "1"
        every { formatFiatShort(any(), any(), any()) } returns "$0"
    }
    private val poisonAddressManager = mockk<PoisonAddressManager> {
        coEvery { getPoisonStatus(any<TransactionRecord>()) } returns PoisonStatus.BLOCKCHAIN
    }

    private lateinit var factory: TransactionViewItemFactory

    @Before
    fun setUp() {
        mockkObject(DateHelper)
        mockkObject(Translator)
        every { DateHelper.getOnlyTime(any()) } returns "12:00"
        every { DateHelper.shortDate(any(), any(), any()) } returns "Apr 6"

        val relink = RelinkSwapToReplacementUseCase(
            swapStorage,
            mockk<PendingMultiSwapStorage>(relaxed = true),
            UnconfinedTestDispatcher().let { TestDispatcherProvider(it, TestScope(it)) },
        )
        startKoin {
            modules(module {
                single { registry }
                single { relink }
            })
        }
        factory = TransactionViewItemFactory(
            addressMetadataManager = mockk<AddressMetadataManager>(relaxed = true),
            balanceHiddenManager = balanceHiddenManager,
            swapProviderTransactionsStorage = swapStorage,
            swapTransactionMatcher = mockk(relaxed = true),
            numberFormatter = numberFormatter,
            marketKit = mockk(relaxed = true),
            localStorage = settingsStorage,
            poisonAddressManager = poisonAddressManager,
            unstoppableProviderTitles = AndroidUnstoppableProviderTitles(),
        )
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkAll()
    }

    @Test
    fun convertToViewItemCached_replacementRestoredWithNewUid_findsSwapThroughPreviousUid() = runTest {
        rememberSpeedUp()
        val swap = swap(transactionId = "changelly-order-id", outgoingRecordUid = UID_B)
        every { swapStorage.getByOutgoingRecordUid(UID_B) } returns swap
        val restored = record(uid = "uid-B-restored", hash = HASH_B, chainUids = listOf(UID_A, UID_B))

        val viewItem = factory.convertToViewItemCached(item(restored))

        assertEquals(swap.transactionId, viewItem.changeNowTransactionId)
        verify { swapStorage.relinkOutgoingRecord(SWAP_DATE, "uid-B-restored", "changelly-order-id", null) }
    }

    @Test
    fun convertToViewItemCached_originalMinedAfterSpeedUp_findsSwapStoredUnderReplacement() = runTest {
        rememberSpeedUp()
        every { swapStorage.getByOutgoingRecordUid(UID_B) } returns
            swap(HASH_B, UID_B, depositTransactionHash = HASH_B, provider = SwapProvider.THORCHAIN)
        val original = record(uid = UID_A, hash = HASH_A, chainUids = listOf(UID_B))

        val viewItem = factory.convertToViewItemCached(item(original))

        assertEquals(HASH_A, viewItem.changeNowTransactionId)
        verify { swapStorage.relinkOutgoingRecord(SWAP_DATE, UID_A, HASH_A, HASH_A) }
    }

    @Test
    fun convertToViewItemCached_recordWithoutChain_looksUpOnlyItsOwnUid() = runTest {
        val plain = record(uid = "uid-plain", hash = "cccc", chainUids = emptyList())

        factory.convertToViewItemCached(item(plain))

        verify(exactly = 1) { swapStorage.getByOutgoingRecordUid(any()) }
        verify(exactly = 1) { swapStorage.getByOutgoingRecordUid("uid-plain") }
        verify(exactly = 0) { settingsStorage.bitcoinReplacementLinks }
        verify(exactly = 0) { swapStorage.relinkOutgoingRecord(any(), any(), any(), any()) }
    }

    private suspend fun rememberSpeedUp() = registry.remember(
        wallet.account.id,
        wallet.token.blockchainType.uid,
        BitcoinReplacementLink(HASH_A, UID_A, HASH_B, UID_B),
    )

    private fun record(uid: String, hash: String, chainUids: List<String>) = BitcoinTransactionRecord(
        token = wallet.token,
        amount = BigDecimal("-0.01"),
        to = listOf("bc1-deposit"),
        from = null,
        changeAddresses = emptyList(),
        uid = uid,
        transactionHash = hash,
        transactionIndex = 0,
        blockHeight = null,
        confirmationsThreshold = 1,
        timestamp = TIMESTAMP,
        failed = false,
        memo = null,
        source = wallet.transactionSource,
        transactionRecordType = TransactionRecordType.BITCOIN_OUTGOING,
        fee = null,
        lockInfo = null,
        conflictingHash = null,
        showRawTransaction = false,
        replacementChainUids = chainUids,
    )

    private fun item(record: TransactionRecord) = TransactionItem(
        record = record,
        currencyValue = null,
        lastBlockInfo = null,
        nftMetadata = emptyMap(),
    )

    private fun swap(
        transactionId: String,
        outgoingRecordUid: String,
        depositTransactionHash: String? = null,
        provider: SwapProvider = SwapProvider.PCASH_BACKEND,
    ) = SwapProviderTransaction(
        date = SWAP_DATE,
        outgoingRecordUid = outgoingRecordUid,
        transactionId = transactionId,
        status = "waiting",
        provider = provider,
        coinUidIn = "bitcoin",
        blockchainTypeIn = "bitcoin",
        amountIn = BigDecimal("0.01"),
        addressIn = "bc1-deposit",
        coinUidOut = "tether",
        blockchainTypeOut = "tron",
        amountOut = BigDecimal("1000"),
        addressOut = "T-recipient",
        depositTransactionHash = depositTransactionHash,
    )
}
