package cash.p.terminal.core.adapters

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.managers.EvmLabelManager
import cash.p.terminal.data.repository.EvmTransactionRepository
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import cash.p.terminal.entities.TransactionValue.CoinValue
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.erc20kit.events.TokenInfo
import io.horizontalsystems.erc20kit.events.TransferEventInstance
import io.horizontalsystems.erc20kit.decorations.ApproveEip20Decoration
import io.horizontalsystems.erc20kit.decorations.OutgoingEip20Decoration
import io.horizontalsystems.ethereumkit.contracts.ContractEventInstance
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.decorations.IncomingDecoration
import io.horizontalsystems.ethereumkit.decorations.OutgoingDecoration
import io.horizontalsystems.ethereumkit.decorations.TransactionDecoration
import io.horizontalsystems.ethereumkit.decorations.UnknownTransactionDecoration
import io.horizontalsystems.ethereumkit.models.AccountDelegation
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.FullTransaction
import io.horizontalsystems.ethereumkit.models.InternalTransaction
import io.horizontalsystems.ethereumkit.models.Transaction
import io.mockk.every
import io.mockk.mockk
import io.horizontalsystems.uniswapkit.decorations.SwapDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class EvmTransactionConverterExternalCallTest {

    companion object {
        init {
            EthereumKit.init()
        }
    }

    private val bscBlockchain =
        Blockchain(BlockchainType.BinanceSmartChain, "Binance Smart Chain", null)
    private val user = Address("0x1111111111111111111111111111111111111111")
    private val relayer = Address("0x2222222222222222222222222222222222222222")
    private val recipientA = Address("0x3333333333333333333333333333333333333333")
    private val recipientB = Address("0x4444444444444444444444444444444444444444")
    private val contract = Address("0x5555555555555555555555555555555555555555")
    private val usdt = Address("0x55d398326f99059ff775485246999027b3197955")

    private val baseToken = Token(
        coin = Coin(uid = "binancecoin", name = "BNB", code = "BNB"),
        blockchain = bscBlockchain,
        type = TokenType.Native,
        decimals = 18
    )

    private val source = TransactionSource(
        blockchain = bscBlockchain,
        account = mockk<Account>(relaxed = true),
        meta = null
    )

    private val repository: EvmTransactionRepository = mockk(relaxed = true)
    private val coinManager: ICoinManager = mockk(relaxed = true)
    private val evmLabelManager: EvmLabelManager = mockk(relaxed = true)

    private fun createConverter(): EvmTransactionConverter {
        every { repository.receiveAddress } returns user
        every { repository.getBlockchainType() } returns BlockchainType.BinanceSmartChain
        every { coinManager.getToken(any<TokenQuery>()) } returns null

        return EvmTransactionConverter(
            coinManager = coinManager,
            evmTransactionRepository = repository,
            source = source,
            baseToken = baseToken,
            evmLabelManager = evmLabelManager
        )
    }

    private fun transaction(from: Address?, to: Address?, value: BigInteger = BigInteger.ZERO) = Transaction(
        hash = ByteArray(32) { 7 },
        timestamp = 1_700_000_000L,
        isFailed = false,
        from = from,
        to = to,
        value = value
    )

    private fun tokenTransfer(from: Address, to: Address, amount: Long) = TransferEventInstance(
        contractAddress = usdt,
        from = from,
        to = to,
        value = BigInteger.valueOf(amount).multiply(BigInteger.TEN.pow(18)),
        tokenInfo = TokenInfo("Tether", "USDT", 18)
    )

    private fun internalTransaction(
        from: Address,
        to: Address,
        traceId: String,
        value: BigInteger = BigInteger.TEN.pow(18)
    ) = InternalTransaction(
        hash = ByteArray(32) { 7 },
        traceId = traceId,
        blockNumber = 1L,
        from = from,
        to = to,
        value = value
    )

    private fun convert(
        tx: Transaction,
        events: List<ContractEventInstance> = emptyList(),
        internals: List<InternalTransaction> = emptyList()
    ): EvmTransactionRecord = convert(
        tx,
        UnknownTransactionDecoration(
            fromAddress = tx.from,
            toAddress = tx.to,
            userAddress = user,
            value = tx.value,
            internalTransactions = internals,
            eventInstances = events
        )
    )

    private fun convert(tx: Transaction, decoration: TransactionDecoration): EvmTransactionRecord =
        createConverter().transactionRecord(
            FullTransaction(tx, decoration, emptyMap())
        ) as EvmTransactionRecord

    private fun Transaction.delegatingTo(
        delegate: Address = contract,
        isActive: Boolean = true
    ) = apply { accountDelegation = AccountDelegation(delegate, isActive) }

    private fun sponsoredTwoTransfers() = convert(
        transaction(relayer, user),
        events = listOf(
            tokenTransfer(user, recipientA, 100),
            tokenTransfer(user, recipientB, 50)
        )
    )

    @Test
    fun transactionRecord_relayerCallsOwnAccountWithOutgoingTransfers_isExternalContractCall() {
        val record = sponsoredTwoTransfers()

        assertEquals(TransactionRecordType.EVM_EXTERNAL_CONTRACT_CALL, record.transactionRecordType)
        assertEquals(
            listOf(recipientA.eip55, recipientB.eip55),
            record.outgoingEvents?.map { it.address }
        )
        assertTrue(record.outgoingEvents.orEmpty().all { (it.value.decimalValue ?: BigDecimal.ZERO) < BigDecimal.ZERO })
        assertTrue(record.incomingEvents.orEmpty().isEmpty())
    }

    @Test
    fun transactionRecord_relayerCallsOwnAccountWithOutgoingInternalNative_isExternalContractCall() {
        val record = convert(
            transaction(relayer, user),
            events = listOf(tokenTransfer(user, recipientA, 100)),
            internals = listOf(
                internalTransaction(user, recipientB, "0"),
                internalTransaction(contract, user, "1")
            )
        )

        assertEquals(TransactionRecordType.EVM_EXTERNAL_CONTRACT_CALL, record.transactionRecordType)
        val outgoing = record.outgoingEvents.orEmpty()
        assertEquals(setOf(recipientA.eip55, recipientB.eip55), outgoing.map { it.address }.toSet())
        val native = outgoing.first { it.address == recipientB.eip55 }
        assertNull(native.addressForIncomingAddress)
        assertEquals(BigDecimal.ONE.negate(), native.value.decimalValue)
        val incoming = record.incomingEvents.orEmpty()
        assertEquals(1, incoming.size)
        assertEquals(BigDecimal.ONE, incoming.first().value.decimalValue)
    }

    @Test
    fun transactionRecord_relayerFundsOwnAccountThatForwardsNative_showsBothLegs() {
        val record = convert(
            transaction(relayer, user, value = BigInteger.TEN.pow(18)),
            internals = listOf(internalTransaction(user, recipientB, "0"))
        )

        assertEquals(TransactionRecordType.EVM_EXTERNAL_CONTRACT_CALL, record.transactionRecordType)
        val incoming = record.incomingEvents.orEmpty().single()
        assertEquals(relayer.eip55, incoming.address)
        assertEquals(BigDecimal.ONE, incoming.value.decimalValue)
        assertEquals(BigDecimal.ONE.negate(), record.outgoingEvents.orEmpty().single().value.decimalValue)
        assertFalse(record.spam)
    }

    @Test
    fun transactionRecord_zeroValueOutgoingInternalCall_isNotAnOutgoingEvent() {
        val record = convert(
            transaction(relayer, user),
            internals = listOf(
                internalTransaction(user, contract, "0", value = BigInteger.ZERO),
                internalTransaction(contract, user, "1")
            )
        )

        assertTrue(record.outgoingEvents.orEmpty().isEmpty())
        assertFalse(record.spam)
    }

    @Test
    fun transactionRecord_sponsoredNativeSendWithEqualRefund_isNotSpam() {
        val record = convert(
            transaction(relayer, user),
            internals = listOf(
                internalTransaction(user, recipientB, "0"),
                internalTransaction(contract, user, "1")
            )
        )

        assertEquals(TransactionRecordType.EVM_EXTERNAL_CONTRACT_CALL, record.transactionRecordType)
        assertFalse(record.spam)
    }

    @Test
    fun transactionRecord_relayerCallsOwnAccount_isForeignWithInitiator() {
        val record = sponsoredTwoTransfers()

        assertTrue(record.foreignTransaction)
        assertEquals(relayer.eip55, record.initiator)
    }

    @Test
    fun transactionRecord_foreignCallToOwnAddressWithoutUserTransfers_staysUnknown() {
        val record = convert(transaction(relayer, user))

        assertEquals(TransactionRecordType.EVM, record.transactionRecordType)
        assertTrue(record.foreignTransaction)
        assertEquals(relayer.eip55, record.initiator)
    }

    @Test
    fun transactionRecord_foreignExternalCall_isForeignWithInitiator() {
        val record = convert(
            transaction(relayer, contract),
            events = listOf(tokenTransfer(relayer, user, 5))
        )

        assertEquals(TransactionRecordType.EVM_EXTERNAL_CONTRACT_CALL, record.transactionRecordType)
        assertTrue(record.foreignTransaction)
        assertEquals(relayer.eip55, record.initiator)
    }

    @Test
    fun transactionRecord_userContractCall_isNotForeignWithoutInitiator() {
        val record = convert(transaction(user, contract))

        assertEquals(TransactionRecordType.EVM_CONTRACT_CALL, record.transactionRecordType)
        assertFalse(record.foreignTransaction)
        assertNull(record.initiator)
    }

    @Test
    fun transactionRecord_tokenSyncerOnlyTransaction_isExternalContractCall() {
        val record = convert(transaction(user, null))

        assertEquals(TransactionRecordType.EVM_EXTERNAL_CONTRACT_CALL, record.transactionRecordType)
        assertFalse(record.foreignTransaction)
        assertNull(record.initiator)
    }

    @Test
    fun transactionRecord_unknownWithoutTransfersAndDelegation_isAccountDelegation() {
        val record = convert(transaction(relayer, user).delegatingTo())

        assertEquals(TransactionRecordType.EVM_ACCOUNT_DELEGATION, record.transactionRecordType)
        assertFalse(record.spam)
        assertTrue(record.foreignTransaction)
        assertEquals(relayer.eip55, record.initiator)
        assertEquals(contract.eip55, record.delegatedTo)
    }

    @Test
    fun transactionRecord_zeroValueIncomingWithDelegation_isAccountDelegation() {
        val tx = transaction(relayer, user).delegatingTo()
        val record = convert(tx, IncomingDecoration(relayer, BigInteger.ZERO))

        assertEquals(TransactionRecordType.EVM_ACCOUNT_DELEGATION, record.transactionRecordType)
        assertFalse(record.spam)
        assertTrue(record.foreignTransaction)
        assertEquals(relayer.eip55, record.initiator)
    }

    @Test
    fun transactionRecord_zeroValueOutgoingToSelfWithDelegation_isAccountDelegation() {
        val tx = transaction(user, user).delegatingTo()
        val record = convert(tx, OutgoingDecoration(user, BigInteger.ZERO, sentToSelf = true))

        assertEquals(TransactionRecordType.EVM_ACCOUNT_DELEGATION, record.transactionRecordType)
        assertFalse(record.foreignTransaction)
        assertNull(record.initiator)
    }

    @Test
    fun transactionRecord_zeroTokenTransferWithDelegation_keepsOutgoing() {
        val usdtToken = Token(
            coin = Coin(uid = "tether", name = "Tether", code = "USDT"),
            blockchain = bscBlockchain,
            type = TokenType.Eip20(usdt.hex),
            decimals = 18
        )
        val tx = transaction(user, usdt).delegatingTo()
        val decoration = OutgoingEip20Decoration(usdt, recipientA, BigInteger.ZERO, false, null)
        val converter = createConverter()
        every { coinManager.getToken(any<TokenQuery>()) } returns usdtToken

        val record = converter.transactionRecord(FullTransaction(tx, decoration, emptyMap())) as EvmTransactionRecord

        assertEquals(TransactionRecordType.EVM_OUTGOING, record.transactionRecordType)
        assertEquals(listOf(recipientA.eip55), record.to)
        assertEquals(usdtToken, (record.value as? CoinValue)?.token)
    }

    @Test
    fun transactionRecord_nonZeroOutgoingWithDelegation_keepsOutgoing() {
        val tx = transaction(user, recipientA, BigInteger.TEN.pow(18)).delegatingTo()
        val record = convert(tx, OutgoingDecoration(recipientA, BigInteger.TEN.pow(18), sentToSelf = false))

        assertEquals(TransactionRecordType.EVM_OUTGOING, record.transactionRecordType)
        assertEquals(contract.eip55, record.delegatedTo)
    }

    @Test
    fun transactionRecord_sponsoredCallWithTransfersAndDelegation_keepsExternalCallAndExposesDelegate() {
        val record = convert(
            transaction(relayer, user).delegatingTo(),
            events = listOf(tokenTransfer(user, recipientA, 100))
        )

        assertEquals(TransactionRecordType.EVM_EXTERNAL_CONTRACT_CALL, record.transactionRecordType)
        assertEquals(contract.eip55, record.delegatedTo)
    }

    @Test
    fun transactionRecord_swapWithDelegation_keepsSwapWithAmounts() {
        val tx = transaction(user, contract).delegatingTo()
        val record = convert(
            tx,
            SwapDecoration(
                contractAddress = contract,
                amountIn = SwapDecoration.Amount.Exact(BigInteger.TEN.pow(18)),
                amountOut = SwapDecoration.Amount.Extremum(BigInteger.TEN.pow(18)),
                tokenIn = SwapDecoration.Token.EvmCoin,
                tokenOut = SwapDecoration.Token.Eip20Coin(usdt, TokenInfo("Tether", "USDT", 18)),
                recipient = null,
                deadline = null
            )
        )

        assertEquals(TransactionRecordType.EVM_SWAP, record.transactionRecordType)
        assertEquals(BigDecimal.ONE.negate(), record.valueIn?.decimalValue)
        assertEquals(BigDecimal.ONE, record.valueOut?.decimalValue)
    }

    @Test
    fun transactionRecord_approveWithDelegation_keepsApprove() {
        val tx = transaction(user, usdt).delegatingTo()
        val record = convert(tx, ApproveEip20Decoration(usdt, contract, BigInteger.TEN.pow(18)))

        assertEquals(TransactionRecordType.EVM_APPROVE, record.transactionRecordType)
    }

    @Test
    fun transactionRecord_transferlessSelfCallWithDelegation_isAccountDelegation() {
        val record = convert(transaction(user, contract).delegatingTo())

        assertEquals(TransactionRecordType.EVM_ACCOUNT_DELEGATION, record.transactionRecordType)
        assertFalse(record.foreignTransaction)
    }

    @Test
    fun transactionRecord_contractCallWithTokenTransferAndDelegation_keepsContractCall() {
        val record = convert(
            transaction(user, contract).delegatingTo(),
            events = listOf(tokenTransfer(user, recipientA, 100))
        )

        assertEquals(TransactionRecordType.EVM_CONTRACT_CALL, record.transactionRecordType)
    }

    @Test
    fun transactionRecord_zeroAddressDelegate_isRevoked() {
        val zero = Address("0x0000000000000000000000000000000000000000")
        val record = convert(transaction(relayer, user).delegatingTo(zero, isActive = false))

        assertTrue(record.delegationRevoked)
        assertFalse(record.delegationActive)
    }

    @Test
    fun transactionRecord_activeDelegate_isActiveAndNotRevoked() {
        val record = convert(transaction(relayer, user).delegatingTo(isActive = true))

        assertTrue(record.delegationActive)
        assertFalse(record.delegationRevoked)
    }

    @Test
    fun transactionRecord_withoutDelegation_hasNoDelegationData() {
        val record = convert(transaction(relayer, user))

        assertEquals(TransactionRecordType.EVM, record.transactionRecordType)
        assertNull(record.delegatedTo)
        assertFalse(record.delegationRevoked)
        assertFalse(record.delegationActive)
    }
}
