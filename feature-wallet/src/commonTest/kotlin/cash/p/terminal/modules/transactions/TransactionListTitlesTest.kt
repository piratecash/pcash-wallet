package cash.p.terminal.modules.transactions

import cash.p.beam.BeamTransactionDirection
import cash.p.terminal.core.TransactionSettingsStorage
import cash.p.terminal.core.managers.BalanceHiddenManager
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.utils.SwapTransactionMatcher
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.LiquidityOperation
import cash.p.terminal.entities.transactionrecords.PendingTransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.entities.transactionrecords.evm.TransferEvent
import cash.p.terminal.entities.transactionrecords.monero.MoneroTransactionRecord
import cash.p.terminal.entities.transactionrecords.solana.SolanaTransactionRecord
import cash.p.terminal.entities.transactionrecords.stellar.StellarTransactionRecord
import cash.p.terminal.entities.transactionrecords.thorchain.ThorchainTransactionRecord
import cash.p.terminal.entities.transactionrecords.ton.TonTransactionRecord
import cash.p.terminal.entities.transactionrecords.tron.TronTransactionRecord
import cash.p.terminal.modules.balance.token.addresspoisoning.AddressPoisoningViewMode
import cash.p.terminal.modules.multiswap.providers.AndroidUnstoppableProviderTitles
import cash.p.terminal.modules.transactions.poison_status.PoisonStatus
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.helpers.DateHelper
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.models.AccountDelegation
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.stellarkit.room.Operation
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tronkit.models.Contract
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.StringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import io.horizontalsystems.ethereumkit.models.Transaction as EvmTransaction
import io.horizontalsystems.solanakit.models.Transaction as SolanaTransaction
import io.horizontalsystems.thorchainkit.models.Transaction as ThorchainTransaction
import io.horizontalsystems.tronkit.models.Transaction as TronTransaction

class TransactionListTitlesTest {

    private val blockchain = Blockchain(BlockchainType.BinanceSmartChain, "BNB Smart Chain", null)
    private val token = Token(Coin("bsc", "BNB Smart Chain", "BNB"), blockchain, TokenType.Native, 18)
    private val otherToken = Token(Coin("cake", "Cake", "CAKE"), blockchain, TokenType.Eip20("0xcake"), 18)
    private val source = TransactionSource(blockchain, mockk(relaxed = true), null)
    private val swapStorage = mockk<SwapProviderTransactionsStorage>(relaxed = true) {
        every { getByOutgoingRecordUid(any()) } returns null
        every { getByTokenIn(any(), any(), any()) } returns null
    }
    private val swapTransactionMatcher = mockk<SwapTransactionMatcher> {
        every { findMatchingSwap(any()) } returns null
    }

    private lateinit var factory: TransactionViewItemFactory

    @Before
    fun setUp() {
        mockkObject(DateHelper)
        mockkObject(Translator)
        every { DateHelper.getOnlyTime(any()) } returns "12:00"
        every { DateHelper.shortDate(any(), any(), any()) } returns "Apr 6"
        // Distinct text per resource, so two different titles can never compare equal by accident.
        every { Translator.getString(any(), *anyVararg()) } answers { firstArg<StringResource>().key }

        val localStorage = mockk<TransactionSettingsStorage> {
            every { addressPoisoningViewMode } returns AddressPoisoningViewMode.COMPACT
        }
        factory = TransactionViewItemFactory(
            addressMetadataManager = mockk(relaxed = true),
            balanceHiddenManager = mockk<BalanceHiddenManager>(relaxed = true) {
                every { balanceHidden } returns false
            },
            swapProviderTransactionsStorage = swapStorage,
            swapTransactionMatcher = swapTransactionMatcher,
            numberFormatter = mockk<IAppNumberFormatter>(relaxed = true),
            marketKit = mockk(relaxed = true),
            localStorage = localStorage,
            poisonAddressManager = mockk<PoisonAddressManager> {
                coEvery { getPoisonStatus(any<TransactionRecord>()) } returns PoisonStatus.BLOCKCHAIN
            },
            unstoppableProviderTitles = AndroidUnstoppableProviderTitles(),
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun listTitle_everyRecordBranch_equalsFactoryTitle() = runTest {
        val records = evmRecords() + tronRecords() + bitcoinRecords() + solanaRecords() + tonRecords() +
            stellarRecords() + thorchainRecords() + beamRecords() + moneroRecords() + pendingRecords()

        records.forEach { (case, record) ->
            val viewItem = factory.convertToViewItemCached(TransactionItem(record, null, null))
            assertEquals(case, viewItem.title, record.listTitle())
        }
    }

    private fun coin(amount: String, valueToken: Token = token) =
        TransactionValue.CoinValue(valueToken, BigDecimal(amount))

    private fun event(amount: String, valueToken: Token = token) =
        TransferEvent("0xpeer", null, coin(amount, valueToken))

    private fun evmTransaction(failed: Boolean = false, delegate: String? = null) =
        mockk<EvmTransaction>(relaxed = true) {
            every { hashString } returns "0xhash"
            every { isFailed } returns failed
            every { accountDelegation } returns delegate?.let { AccountDelegation(Address(it), false) }
        }

    private fun evm(
        type: TransactionRecordType,
        failed: Boolean = false,
        method: String? = null,
        incoming: List<TransferEvent>? = null,
        outgoing: List<TransferEvent>? = null,
        liquidity: LiquidityOperation? = null,
        delegate: String? = null,
    ) = EvmTransactionRecord(
        from = "0xfrom",
        to = "0xto",
        transaction = evmTransaction(failed, delegate),
        token = token,
        source = source,
        protected = false,
        transactionRecordType = type,
        spender = "0xspender",
        value = coin("-1"),
        contractAddress = "0xcontract",
        method = method,
        incomingEvents = incoming,
        outgoingEvents = outgoing,
        exchangeAddress = "0xrouter",
        amountIn = EvmTransactionRecord.Amount.Exact(coin("-1")),
        amountOut = EvmTransactionRecord.Amount.Exact(coin("2", otherToken)),
        liquidityOperation = liquidity,
    )

    private fun evmRecords() = listOf(
        "evm approve" to evm(TransactionRecordType.EVM_APPROVE),
        "evm contract call, known method" to evm(
            TransactionRecordType.EVM_CONTRACT_CALL, method = "deposit", incoming = emptyList(), outgoing = emptyList()
        ),
        "evm contract call, no method" to evm(
            TransactionRecordType.EVM_CONTRACT_CALL, incoming = emptyList(), outgoing = emptyList()
        ),
        "evm contract call, liquidity" to evm(
            TransactionRecordType.EVM_CONTRACT_CALL,
            method = "addLiquidity",
            incoming = emptyList(),
            outgoing = listOf(event("-1"), event("-2", otherToken)),
            liquidity = LiquidityOperation(LiquidityOperation.Direction.Add, failed = false),
        ),
        "evm external call, incoming" to evm(
            TransactionRecordType.EVM_EXTERNAL_CONTRACT_CALL, incoming = listOf(event("1")), outgoing = emptyList()
        ),
        "evm external call, outgoing" to evm(
            TransactionRecordType.EVM_EXTERNAL_CONTRACT_CALL, incoming = emptyList(), outgoing = listOf(event("-1"))
        ),
        "evm external call, both" to evm(
            TransactionRecordType.EVM_EXTERNAL_CONTRACT_CALL,
            incoming = listOf(event("1", otherToken)),
            outgoing = listOf(event("-1")),
        ),
        "evm contract creation" to evm(TransactionRecordType.EVM_CONTRACT_CREATION),
        "evm incoming" to evm(TransactionRecordType.EVM_INCOMING),
        "evm outgoing" to evm(TransactionRecordType.EVM_OUTGOING),
        "evm swap" to evm(TransactionRecordType.EVM_SWAP),
        "evm failed swap" to evm(TransactionRecordType.EVM_SWAP, failed = true),
        "evm unknown swap" to evm(TransactionRecordType.EVM_UNKNOWN_SWAP),
        "evm failed unknown swap" to evm(TransactionRecordType.EVM_UNKNOWN_SWAP, failed = true),
        "evm plain" to evm(TransactionRecordType.EVM),
        "evm delegation" to evm(TransactionRecordType.EVM_ACCOUNT_DELEGATION, delegate = DELEGATE),
        "evm delegation revoke" to evm(TransactionRecordType.EVM_ACCOUNT_DELEGATION, delegate = ZERO_ADDRESS),
    )

    private fun tron(
        type: TransactionRecordType,
        method: String? = null,
        incoming: List<TransferEvent>? = null,
        outgoing: List<TransferEvent>? = null,
        contract: Contract? = null,
    ) = TronTransactionRecord(
        from = "Tfrom",
        to = "Tto",
        token = token,
        source = source,
        transactionRecordType = type,
        spender = "Tspender",
        value = coin("-1"),
        contractAddress = "Tcontract",
        method = method,
        incomingEvents = incoming,
        outgoingEvents = outgoing,
        transaction = mockk<TronTransaction>(relaxed = true) {
            every { hashString } returns "tronhash"
            every { isFailed } returns false
            every { fee } returns null
            every { this@mockk.contract } returns contract
        },
    )

    private fun tronRecords() = listOf(
        "tron approve" to tron(TransactionRecordType.TRON_APPROVE),
        "tron contract call" to tron(
            TransactionRecordType.TRON_CONTRACT_CALL,
            method = "transfer",
            incoming = emptyList(),
            outgoing = emptyList()
        ),
        "tron contract call, no method" to tron(
            TransactionRecordType.TRON_CONTRACT_CALL, incoming = emptyList(), outgoing = emptyList()
        ),
        "tron external call, incoming" to tron(
            TransactionRecordType.TRON_EXTERNAL_CONTRACT_CALL, incoming = listOf(event("1")), outgoing = emptyList()
        ),
        "tron external call, outgoing" to tron(
            TransactionRecordType.TRON_EXTERNAL_CONTRACT_CALL, incoming = emptyList(), outgoing = listOf(event("-1"))
        ),
        "tron external call, both" to tron(
            TransactionRecordType.TRON_EXTERNAL_CONTRACT_CALL,
            incoming = listOf(event("1", otherToken)),
            outgoing = listOf(event("-1")),
        ),
        "tron incoming" to tron(TransactionRecordType.TRON_INCOMING),
        "tron outgoing" to tron(TransactionRecordType.TRON_OUTGOING),
        "tron simple, labelled contract" to tron(
            TransactionRecordType.TRON, contract = mockk { every { label } returns "TRX Stake 2.0" }
        ),
        "tron simple, no contract" to tron(TransactionRecordType.TRON),
    )

    private fun bitcoin(type: TransactionRecordType, migration: Boolean = false) = BitcoinTransactionRecord(
        token = token,
        amount = BigDecimal("-0.01"),
        to = listOf("bc1-to"),
        from = "bc1-from",
        changeAddresses = emptyList(),
        uid = "btc-$type-$migration",
        transactionHash = "btchash",
        transactionIndex = 0,
        blockHeight = null,
        confirmationsThreshold = 1,
        timestamp = TIMESTAMP,
        failed = false,
        memo = null,
        source = source,
        transactionRecordType = type,
        fee = null,
        lockInfo = null,
        conflictingHash = null,
        showRawTransaction = false,
        isIronwoodMigration = migration,
    )

    private fun bitcoinRecords() = listOf(
        "bitcoin incoming" to bitcoin(TransactionRecordType.BITCOIN_INCOMING),
        "bitcoin outgoing" to bitcoin(TransactionRecordType.BITCOIN_OUTGOING),
        "bitcoin ironwood migration" to bitcoin(TransactionRecordType.BITCOIN_OUTGOING, migration = true),
    )

    private fun solana(type: TransactionRecordType) = SolanaTransactionRecord(
        to = "sol-to",
        from = "sol-from",
        token = token,
        source = source,
        transactionRecordType = type,
        transaction = SolanaTransaction(hash = "solhash", timestamp = TIMESTAMP, pending = false),
        mainValue = coin("1"),
    )

    private fun solanaRecords() = listOf(
        "solana incoming" to solana(TransactionRecordType.SOLANA_INCOMING),
        "solana outgoing" to solana(TransactionRecordType.SOLANA_OUTGOING),
        "solana unknown" to solana(TransactionRecordType.SOLANA_UNKNOWN),
    )

    private fun ton(vararg types: TonTransactionRecord.Action.Type, liquidity: LiquidityOperation? = null) =
        TonTransactionRecord(
            source = source,
            event = Event(
                id = "ton-event",
                lt = 1L,
                timestamp = TIMESTAMP,
                scam = false,
                inProgress = false,
                extra = 0L,
                actions = emptyList(),
            ),
            token = token,
            actions = types.map { TonTransactionRecord.Action(it, TransactionStatus.Completed) },
            liquidityOperation = liquidity,
        )

    private fun tonRecords(): List<Pair<String, TonTransactionRecord>> {
        val send = TonTransactionRecord.Action.Type.Send(coin("-1"), "ton-to", sentToSelf = false, comment = null)
        return listOf(
            "ton liquidity" to ton(send, liquidity = LiquidityOperation(LiquidityOperation.Direction.Remove, true)),
            "ton send" to ton(send),
            "ton receive" to ton(TonTransactionRecord.Action.Type.Receive(coin("1"), "ton-from", null, null)),
            "ton unsupported" to ton(TonTransactionRecord.Action.Type.Unsupported("custom")),
            "ton liquidity deposit" to ton(TonTransactionRecord.Action.Type.LiquidityDeposit(emptyList(), "dex")),
            "ton burn" to ton(TonTransactionRecord.Action.Type.Burn(coin("-1"))),
            "ton contract call" to ton(TonTransactionRecord.Action.Type.ContractCall("ton-contract", coin("-1"), "op")),
            "ton contract deploy" to ton(TonTransactionRecord.Action.Type.ContractDeploy(listOf("wallet"))),
            "ton mint" to ton(TonTransactionRecord.Action.Type.Mint(coin("1"), null)),
            "ton swap" to ton(TonTransactionRecord.Action.Type.Swap("STON.fi", "ton-router", coin("-1"), coin("2"))),
            "ton multiple" to ton(send, send),
        )
    }

    private fun stellar(type: StellarTransactionRecord.Type) = StellarTransactionRecord(
        baseToken = token,
        source = source,
        operation = Operation(
            id = 1L,
            timestamp = TIMESTAMP,
            pagingToken = "paging",
            sourceAccount = "G-source",
            transactionHash = "xlmhash",
            transactionSuccessful = true,
            fee = null,
            memo = null,
            type = "payment",
            payment = null,
            accountCreated = null,
            changeTrust = null,
        ),
        type = type,
        spam = false,
    )

    private fun stellarRecords() = listOf(
        "stellar send" to stellar(StellarTransactionRecord.Type.Send(coin("-1"), "G-to", false, null, false)),
        "stellar receive" to stellar(StellarTransactionRecord.Type.Receive(coin("1"), "G-from", "G-to", null, false)),
        "stellar change trust" to stellar(StellarTransactionRecord.Type.ChangeTrust("G-trustee", coin("0"))),
        "stellar swap" to stellar(StellarTransactionRecord.Type.Swap(coin("-1"), coin("2"))),
        "stellar unsupported" to stellar(StellarTransactionRecord.Type.Unsupported("custom")),
    )

    private fun thorchain(type: ThorchainTransactionRecord.Type) = ThorchainTransactionRecord(
        uid = "thor-${type::class.simpleName}",
        transaction = mockk<ThorchainTransaction>(relaxed = true) { every { hash } returns "thorhash" },
        spam = false,
        source = source,
        token = token,
        type = type,
        fee = null,
    )

    private fun thorchainRecords() = listOf(
        "thorchain incoming" to thorchain(ThorchainTransactionRecord.Type.Incoming(coin("1"), from = "thor-from")),
        "thorchain outgoing" to thorchain(
            ThorchainTransactionRecord.Type.Outgoing(coin("-1"), to = "thor-to", sentToSelf = false)
        ),
    )

    private fun beamRecords() = BeamTransactionDirection.entries.map { "beam $it" to beamHistoryRecord(it) }

    private fun monero(type: TransactionRecordType) = MoneroTransactionRecord(
        uid = "xmr-$type",
        transactionHash = "xmrhash",
        blockHeight = 100,
        confirmationsThreshold = 10,
        timestamp = TIMESTAMP,
        source = source,
        transactionRecordType = type,
        token = token,
        to = "xmr-to",
        from = "xmr-from",
        amount = BigDecimal.ONE,
        fee = coin("0.0001"),
        subaddressLabel = null,
        isPending = false,
        confirmations = 10,
    )

    private fun moneroRecords() = listOf(
        "monero incoming" to monero(TransactionRecordType.MONERO_INCOMING),
        "monero outgoing" to monero(TransactionRecordType.MONERO_OUTGOING),
    )

    private fun pending(migration: Boolean) = PendingTransactionRecord(
        uid = "pending-$migration",
        transactionHash = "",
        timestamp = TIMESTAMP,
        source = source,
        token = token,
        amount = BigDecimal.ONE,
        toAddress = "pending-to",
        fromAddress = "pending-from",
        expiresAt = Long.MAX_VALUE,
        memo = null,
        isIronwoodMigration = migration,
    )

    private fun pendingRecords() = listOf(
        "pending" to pending(migration = false),
        "pending ironwood migration" to pending(migration = true),
    )

    private companion object {
        init {
            // Delegation records compute Address.eip55 through BouncyCastle's keccak, registered by EthereumKit.
            EthereumKit.init()
        }

        const val TIMESTAMP = 1_700_000_000L
        const val DELEGATE = "0x1111111111111111111111111111111111111111"
        const val ZERO_ADDRESS = "0x0000000000000000000000000000000000000000"
    }
}
