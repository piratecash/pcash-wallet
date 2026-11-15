package cash.p.terminal.modules.transactions

import cash.p.terminal.core.TransactionSettingsStorage
import cash.p.terminal.core.managers.AddressLabelManager
import cash.p.terminal.core.managers.AddressMetadataManager
import cash.p.terminal.core.managers.BalanceHiddenManager
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.utils.SwapTransactionMatcher
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.LiquidityOperation
import cash.p.terminal.entities.transactionrecords.LiquidityOperation.Direction
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.entities.transactionrecords.evm.TransferEvent
import cash.p.terminal.entities.transactionrecords.ton.TonTransactionRecord
import cash.p.terminal.modules.balance.token.addresspoisoning.AddressPoisoningViewMode
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.multiswap.providers.AndroidUnstoppableProviderTitles
import cash.p.terminal.modules.transactions.poison_status.PoisonStatus
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Transactions_Multiple
import cash.p.terminal.resources.transactions_liquidity_add_failed
import cash.p.terminal.resources.transactions_liquidity_added
import cash.p.terminal.resources.transactions_liquidity_remove_failed
import cash.p.terminal.resources.transactions_liquidity_removed
import cash.p.terminal.ui_compose.ColorName
import cash.p.terminal.ui_compose.ColoredValue
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.helpers.DateHelper
import io.horizontalsystems.tonkit.models.Event
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.StringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class TransactionViewItemFactoryLiquidityTest {

    private val addressLabelManager = mockk<AddressLabelManager>()
    private val contactsRepository = mockk<ContactsRepository>()
    private val balanceHiddenManager = mockk<BalanceHiddenManager>()
    private val localStorage = mockk<TransactionSettingsStorage>()
    private val poisonAddressManager = mockk<PoisonAddressManager>()
    private val appNumberFormatter = mockk<IAppNumberFormatter>()
    private val swapProviderTransactionsStorage = mockk<SwapProviderTransactionsStorage>(relaxed = true)
    private val swapTransactionMatcher = mockk<SwapTransactionMatcher>(relaxed = true)

    private lateinit var factory: TransactionViewItemFactory

    @Before
    fun setUp() {
        mockkObject(DateHelper)
        mockkObject(Translator)
        every { DateHelper.getOnlyTime(any()) } returns "12:00"
        every { DateHelper.shortDate(any(), any(), any()) } returns "Apr 6"
        every { appNumberFormatter.formatCoinShort(any(), any(), any()) } answers {
            "${secondArg<String?>().orEmpty()}:${firstArg<BigDecimal>().stripTrailingZeros().toPlainString()}"
        }
        every { appNumberFormatter.formatFiatShort(any(), any(), any()) } returns "$0"
        every { addressLabelManager.label(any(), any()) } returns null
        every { contactsRepository.getContactsFiltered(any(), addressQuery = any()) } returns emptyList()
        every { balanceHiddenManager.balanceHidden } returns false
        every { balanceHiddenManager.isTransactionInfoHidden(any()) } returns false
        every { balanceHiddenManager.isTransactionInfoHiddenForWallet(any(), any()) } returns false
        every { localStorage.addressPoisoningViewMode } returns AddressPoisoningViewMode.COMPACT
        every { swapTransactionMatcher.findMatchingSwap(any()) } returns null
        every { swapProviderTransactionsStorage.getByOutgoingRecordUid(any()) } returns null
        every { swapProviderTransactionsStorage.getByTokenIn(any(), any(), any()) } returns null
        coEvery { poisonAddressManager.getPoisonStatus(any<TransactionRecord>()) } returns PoisonStatus.BLOCKCHAIN
        TITLES.forEach { (resource, title) -> every { Translator.getString(resource) } returns title }
        every { Translator.getString(Res.string.Transactions_Multiple) } returns "Multiple"

        factory = TransactionViewItemFactory(
            addressMetadataManager = AddressMetadataManager(contactsRepository, addressLabelManager),
            balanceHiddenManager = balanceHiddenManager,
            swapProviderTransactionsStorage = swapProviderTransactionsStorage,
            swapTransactionMatcher = swapTransactionMatcher,
            numberFormatter = appNumberFormatter,
            marketKit = mockk(relaxed = true),
            localStorage = localStorage,
            poisonAddressManager = poisonAddressManager,
            unstoppableProviderTitles = AndroidUnstoppableProviderTitles(),
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun convertToViewItemCached_evmLiquidityOperations_useFourTitles() = runTest {
        val titles = listOf(
            LiquidityOperation(Direction.Add, failed = false),
            LiquidityOperation(Direction.Remove, failed = false),
            LiquidityOperation(Direction.Add, failed = true),
            LiquidityOperation(Direction.Remove, failed = true),
        ).map { factory.convertToViewItemCached(item(evmRecord(it))).title }

        assertEquals(
            listOf("Added", "Withdrawn", "Failed to add", "Failed to withdraw"),
            titles
        )
    }

    @Test
    fun convertToViewItemCached_evmAddWithTwoOutgoing_showsBothAmountsInOutgoingColor() = runTest {
        val record = evmRecord(
            LiquidityOperation(Direction.Add, failed = false),
            outgoing = listOf(tokenValue("AAA", "-1"), tokenValue("BBB", "-2")),
        )

        val viewItem = factory.convertToViewItemCached(item(record))

        assertEquals(ColoredValue("-AAA:1", ColorName.Lucian), viewItem.primaryValue)
        assertEquals(ColoredValue("-BBB:2", ColorName.Lucian), viewItem.secondaryValue)
    }

    @Test
    fun convertToViewItemCached_evmRemoveWithTwoIncoming_showsBothAmountsInIncomingColor() = runTest {
        val record = evmRecord(
            LiquidityOperation(Direction.Remove, failed = false),
            incoming = listOf(tokenValue("AAA", "1"), tokenValue("BBB", "2")),
        )

        val viewItem = factory.convertToViewItemCached(item(record))

        assertEquals(ColoredValue("+AAA:1", ColorName.Remus), viewItem.primaryValue)
        assertEquals(ColoredValue("+BBB:2", ColorName.Remus), viewItem.secondaryValue)
    }

    @Test
    fun convertToViewItemCached_evmContractCallWithoutOperation_keepsMultipleSummary() = runTest {
        val record = evmRecord(
            operation = null,
            outgoing = listOf(tokenValue("AAA", "-1"), tokenValue("BBB", "-2")),
        )

        val viewItem = factory.convertToViewItemCached(item(record))

        assertEquals("AAA, BBB", viewItem.primaryValue?.value)
        assertEquals("Multiple", viewItem.secondaryValue?.value)
    }

    @Test
    fun convertToViewItemCached_tonV2Deposit_showsTitleProtocolAndBothAmounts() = runTest {
        val record = tonRecord(
            LiquidityOperation(Direction.Add, failed = false),
            TonTransactionRecord.Action.Type.LiquidityDeposit(
                deposits = listOf(
                    TonTransactionRecord.Action.Type.LiquidityDeposit.Deposit(tokenValue("TON", "-2"), "vault1"),
                    TonTransactionRecord.Action.Type.LiquidityDeposit.Deposit(jettonValue("XYZ", "-5"), "vault2"),
                ),
                protocol = "stonfi",
            ),
        )

        val viewItem = factory.convertToViewItemCached(item(record))

        assertEquals("Added", viewItem.title)
        assertEquals("stonfi", viewItem.subtitle)
        assertEquals(ColoredValue("-TON:2", ColorName.Lucian), viewItem.primaryValue)
        assertEquals(ColoredValue("-XYZ:5", ColorName.Lucian), viewItem.secondaryValue)
    }

    @Test
    fun convertToViewItemCached_tonMultiActionWithdrawal_showsMultipleAndMergedIncoming() = runTest {
        val record = tonRecord(
            LiquidityOperation(Direction.Remove, failed = false),
            burn(),
            receive(tokenValue("TON", "1")),
            receive(jettonValue("XYZ", "4")),
        )

        val viewItem = factory.convertToViewItemCached(item(record))

        assertEquals("Withdrawn", viewItem.title)
        assertEquals("Multiple", viewItem.subtitle)
        assertEquals(ColoredValue("+TON:1", ColorName.Remus), viewItem.primaryValue)
        assertEquals(ColoredValue("+XYZ:4", ColorName.Remus), viewItem.secondaryValue)
    }

    @Test
    fun convertToViewItemCached_tonWithdrawalWithFailedReceipt_showsOnlySuccessfulAmount() = runTest {
        val record = tonRecord(
            LiquidityOperation(Direction.Remove, failed = false),
            burn(),
            receive(jettonValue("USDT", "5")),
            actions = listOf(
                TonTransactionRecord.Action(receive(jettonValue("STON", "7")), TransactionStatus.Failed)
            ),
        )

        val viewItem = factory.convertToViewItemCached(item(record))

        assertEquals(ColoredValue("+USDT:5", ColorName.Remus), viewItem.primaryValue)
        assertEquals(null, viewItem.secondaryValue)
    }

    @Test
    fun convertToViewItemCached_tonFailedDeposit_stillShowsAttemptedAmounts() = runTest {
        val record = tonRecord(
            LiquidityOperation(Direction.Add, failed = true),
            actions = listOf(
                TonTransactionRecord.Action(
                    TonTransactionRecord.Action.Type.LiquidityDeposit(
                        deposits = listOf(
                            TonTransactionRecord.Action.Type.LiquidityDeposit.Deposit(tokenValue("TON", "-2"), "v1"),
                            TonTransactionRecord.Action.Type.LiquidityDeposit.Deposit(jettonValue("XYZ", "-5"), "v2"),
                        ),
                        protocol = "stonfi",
                    ),
                    TransactionStatus.Failed,
                )
            ),
        )

        val viewItem = factory.convertToViewItemCached(item(record))

        assertEquals(ColoredValue("-TON:2", ColorName.Lucian), viewItem.primaryValue)
        assertEquals(ColoredValue("-XYZ:5", ColorName.Lucian), viewItem.secondaryValue)
    }

    @Test
    fun convertToViewItemCached_pendingTonLiquidity_showsProgress() = runTest {
        val record = tonRecord(
            LiquidityOperation(Direction.Add, failed = false),
            burn(),
            inProgress = true,
        )

        val viewItem = factory.convertToViewItemCached(item(record))

        assertNotNull(viewItem.progress)
    }

    private fun item(record: TransactionRecord) = TransactionItem(record, currencyValue = null, lastBlockInfo = null)

    private fun tokenValue(code: String, value: String) =
        TransactionValue.TokenValue(code, code, 6, BigDecimal(value))

    private fun jettonValue(code: String, value: String) =
        TransactionValue.JettonValue(code, code, 6, BigDecimal(value), null)

    private fun burn() = TonTransactionRecord.Action.Type.Burn(jettonValue("LP", "-9"))

    private fun receive(value: TransactionValue) =
        TonTransactionRecord.Action.Type.Receive(value, from = "from", to = "to", comment = null)

    private fun evmRecord(
        operation: LiquidityOperation?,
        incoming: List<TransactionValue> = emptyList(),
        outgoing: List<TransactionValue> = emptyList(),
    ) = EvmTransactionRecord(
        transaction = mockk(relaxed = true) {
            every { hashString } returns "0xhash"
            every { timestamp } returns 1_000L
        },
        token = mockk<Token>(relaxed = true),
        source = mockk<TransactionSource>(relaxed = true) {
            every { blockchain } returns mockk(relaxed = true) { every { type } returns BlockchainType.Ethereum }
        },
        protected = false,
        transactionRecordType = TransactionRecordType.EVM_CONTRACT_CALL,
        contractAddress = "0xcontract",
        method = "Multicall",
        incomingEvents = incoming.map { TransferEvent("0xa", null, it) },
        outgoingEvents = outgoing.map { TransferEvent("0xb", null, it) },
        liquidityOperation = operation,
    )

    private fun tonRecord(
        operation: LiquidityOperation,
        vararg types: TonTransactionRecord.Action.Type,
        actions: List<TonTransactionRecord.Action> = emptyList(),
        inProgress: Boolean = false,
    ): TonTransactionRecord {
        val blockchain = Blockchain(BlockchainType.Ton, "TON", null)
        return TonTransactionRecord(
            source = TransactionSource(blockchain, mockk(relaxed = true), null),
            event = Event("event", 1L, 1_000L, false, inProgress, 0L, emptyList()),
            token = mockk<Token>(relaxed = true),
            actions = types.map { TonTransactionRecord.Action(it, TransactionStatus.Completed) } + actions,
            liquidityOperation = operation,
        )
    }

    private companion object {
        val TITLES: Map<StringResource, String> = mapOf(
            Res.string.transactions_liquidity_added to "Added",
            Res.string.transactions_liquidity_removed to "Withdrawn",
            Res.string.transactions_liquidity_add_failed to "Failed to add",
            Res.string.transactions_liquidity_remove_failed to "Failed to withdraw",
        )
    }
}
