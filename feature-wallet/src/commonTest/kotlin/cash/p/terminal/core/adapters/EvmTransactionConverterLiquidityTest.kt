package cash.p.terminal.core.adapters

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.managers.EvmLabelManager
import cash.p.terminal.data.repository.EvmTransactionRepository
import cash.p.terminal.entities.transactionrecords.LiquidityOperation
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.erc20kit.events.TokenInfo
import io.horizontalsystems.erc20kit.events.TransferEventInstance
import io.horizontalsystems.ethereumkit.contracts.ContractEventInstance
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.decorations.UnknownTransactionDecoration
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.FullTransaction
import io.horizontalsystems.ethereumkit.models.InternalTransaction
import io.horizontalsystems.ethereumkit.models.Transaction
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigInteger

internal class EvmConverterFixture(chain: Chain = Chain.Ethereum) {

    init {
        // TransferEventInstance needs the keccak provider EthereumKit registers at runtime.
        EthereumKit.init()
    }

    val user = Address("0x1111111111111111111111111111111111111111")
    val router = Address("0x2222222222222222222222222222222222222222")
    val pool = Address("0x3333333333333333333333333333333333333333")
    val tokenA = Address("0x4444444444444444444444444444444444444444")
    val tokenB = Address("0x5555555555555555555555555555555555555555")
    val tokenC = Address("0x6666666666666666666666666666666666666666")

    val labelManager: EvmLabelManager = mockk(relaxed = true) {
        every { methodLabel(any()) } returns null
    }

    private val blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null)
    private val baseToken = Token(Coin("ethereum", "Ethereum", "ETH"), blockchain, TokenType.Native, 18)
    private val coinManager: ICoinManager = mockk(relaxed = true) {
        every { getToken(any()) } returns null
    }
    private val repository: EvmTransactionRepository = mockk(relaxed = true) {
        every { receiveAddress } returns user
        every { getBlockchainType() } returns BlockchainType.Ethereum
        every { this@mockk.chain } returns chain
    }
    private val converter = EvmTransactionConverter(
        coinManager = coinManager,
        evmTransactionRepository = repository,
        source = TransactionSource(blockchain, mockk<Account>(relaxed = true), null),
        baseToken = baseToken,
        evmLabelManager = labelManager,
    )

    fun transfer(contract: Address, from: Address, to: Address, tokens: Long) = TransferEventInstance(
        contractAddress = contract,
        from = from,
        to = to,
        value = BigInteger.TEN.pow(18) * BigInteger.valueOf(tokens),
        tokenInfo = TokenInfo("Token ${contract.hex.takeLast(4)}", "T${contract.hex.takeLast(2)}", 18),
    )

    fun record(
        input: ByteArray,
        events: List<ContractEventInstance> = emptyList(),
        failed: Boolean = false,
        value: BigInteger = BigInteger.ZERO,
        from: Address = user,
        internalTransactions: List<InternalTransaction> = emptyList(),
    ): EvmTransactionRecord {
        val transaction = Transaction(
            hash = ByteArray(32) { 9 },
            timestamp = 1_700_000_000L,
            isFailed = failed,
            from = from,
            to = router,
            value = value,
            input = input,
        )
        val decoration = UnknownTransactionDecoration(from, router, user, value, internalTransactions, events)
        return converter.transactionRecord(FullTransaction(transaction, decoration, emptyMap()))
    }
}

class EvmTransactionConverterLiquidityTest {

    private val fixture = EvmConverterFixture()

    @Test
    fun transactionRecord_mintMulticall_setsOperationAndKeepsTypeAndMethod() {
        every { fixture.labelManager.methodLabel(any()) } returns "Multicall"

        val record = fixture.record(multicall(listOf(mintCall)))

        assertEquals(LiquidityOperation(LiquidityOperation.Direction.Add, failed = false), record.liquidityOperation)
        assertEquals(TransactionRecordType.EVM_CONTRACT_CALL, record.transactionRecordType)
        assertEquals("Multicall", record.method)
    }

    @Test
    fun transactionRecord_failedCall_marksOperationFailed() {
        val record = fixture.record(multicall(listOf(mintCall)), failed = true)

        assertEquals(LiquidityOperation(LiquidityOperation.Direction.Add, failed = true), record.liquidityOperation)
    }

    @Test
    fun transactionRecord_labelOnlyRecognition_setsOperation() {
        every { fixture.labelManager.methodLabel(any()) } returns "Remove Liquidity"

        val record = fixture.record(abiCall(0xdeadbeef, word(1)))

        assertEquals(LiquidityOperation.Direction.Remove, record.liquidityOperation?.direction)
    }

    @Test
    fun transactionRecord_recognisedCallWithDustEvents_isNotSpam() {
        val dust = fixture.transfer(fixture.tokenA, fixture.user, fixture.pool, 0)

        val record = fixture.record(multicall(listOf(mintCall)), events = listOf(dust))

        assertFalse(record.spam)
    }

    @Test
    fun transactionRecord_foreignCall_isNotClassified() {
        val incoming = fixture.transfer(fixture.tokenA, fixture.pool, fixture.user, 1)

        val record = fixture.record(multicall(listOf(mintCall)), events = listOf(incoming), from = fixture.pool)

        assertEquals(TransactionRecordType.EVM_EXTERNAL_CONTRACT_CALL, record.transactionRecordType)
        assertNull(record.liquidityOperation)
    }

    @Test
    fun transactionRecord_malformedCalldata_staysPlainContractCall() {
        val record = fixture.record(selector(0xac9650d8) + word(0x10000))

        assertEquals(TransactionRecordType.EVM_CONTRACT_CALL, record.transactionRecordType)
        assertNull(record.liquidityOperation)
    }
}
