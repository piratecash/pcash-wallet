package cash.p.terminal.modules.transactions

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.adapters.SolanaTransactionConverterTestFixture
import cash.p.terminal.core.managers.localizedEvmMethodLabel
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.LiquidityOperation
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.entities.transactionrecords.thorchain.ThorchainTransactionRecord
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.transactions_liquidity_add_failed
import cash.p.terminal.resources.transactions_liquidity_removed
import cash.p.terminal.resources.transactions_liquidity_search_key
import io.horizontalsystems.core.Translator
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.thorchainkit.models.Transaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import io.horizontalsystems.ethereumkit.models.Transaction as EvmTransaction

class TransactionRecordSearchMatcherTest {

    private val matcher = TransactionRecordSearchMatcher()
    private val thorchain = Blockchain(BlockchainType.Thorchain, "THORChain", null)
    private val rune = Token(Coin("thorchain", "THORChain", "RUNE"), thorchain, TokenType.Native, 8)

    private fun thorchainRecord(type: ThorchainTransactionRecord.Type) = ThorchainTransactionRecord(
        uid = "uid",
        transaction = mockk<Transaction>(relaxed = true) {
            every { hash } returns "HASH"
            every { memo } returns MEMO
        },
        spam = false,
        source = TransactionSource(thorchain, mockk(relaxed = true), null),
        token = rune,
        type = type,
        fee = null,
    )

    @Test
    fun matches_thorchainOutgoing_findsMemoAndRecipient() {
        val record = thorchainRecord(
            ThorchainTransactionRecord.Type.Outgoing(
                TransactionValue.CoinValue(rune, BigDecimal.ONE.negate()),
                to = RECIPIENT,
                sentToSelf = false,
            )
        )

        assertTrue(matcher.matches(record, "=:eth.eth"))
        assertTrue(matcher.matches(record, RECIPIENT.uppercase()))
        assertFalse(matcher.matches(record, "unrelated"))
    }

    @Test
    fun matches_thorchainIncoming_findsSender() {
        val record = thorchainRecord(
            ThorchainTransactionRecord.Type.Incoming(TransactionValue.CoinValue(rune, BigDecimal.ONE), from = SENDER)
        )

        assertTrue(matcher.matches(record, SENDER))
    }

    @Test
    fun matches_evmContractCall_findsRawAndLocalizedMethod() {
        val bsc = Blockchain(BlockchainType.BinanceSmartChain, "BNB Smart Chain", null)
        val bnb = Token(Coin("bsc", "BNB Smart Chain", "BNB"), bsc, TokenType.Native, 18)
        val record = EvmTransactionRecord(
            transaction = mockk<EvmTransaction>(relaxed = true) {
                every { hashString } returns "HASH"
            },
            token = bnb,
            source = TransactionSource(bsc, mockk(relaxed = true), null),
            protected = false,
            transactionRecordType = TransactionRecordType.EVM,
            method = "Multicall",
        )

        assertTrue(matcher.matches(record, "multicall"))
        assertTrue(matcher.matches(record, localizedEvmMethodLabel("Multicall")))
        assertFalse(matcher.matches(record, "unrelated"))
    }

    @Test
    fun matches_solanaNftTransfer_findsMintAddress() {
        val coinManager = mockk<ICoinManager>(relaxed = true) {
            every { getToken(any()) } returns null
        }
        val record = SolanaTransactionConverterTestFixture.createConverter(coinManager).transactionRecord(
            SolanaTransactionConverterTestFixture.tokenTransferTransaction(
                hash = "HASH",
                from = SENDER,
                to = SolanaTransactionConverterTestFixture.USER_ADDRESS,
                mint = NFT_MINT,
                incoming = true,
                amount = BigDecimal.ONE,
                decimals = 0,
                isNft = true,
            )
        )

        assertTrue(matcher.matches(record, NFT_MINT))
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun matches_removeLiquidity_findsSearchKeyAndTitleCaseInsensitively() {
        val record = liquidityRecord(LiquidityOperation(LiquidityOperation.Direction.Remove, failed = false))

        assertTrue(matcher.matches(record, "ЛИКВИДНОСТЬ"))
        assertTrue(matcher.matches(record, "выведено из ликвидности"))
        assertFalse(matcher.matches(record, "добавлено"))
    }

    @Test
    fun matches_failedAddLiquidity_findsFailedTitle() {
        val record = liquidityRecord(LiquidityOperation(LiquidityOperation.Direction.Add, failed = true))

        assertTrue(matcher.matches(record, "не удалось добавить"))
    }

    @Test
    fun matches_recordWithoutLiquidityOperation_ignoresLiquidityKey() {
        val record = liquidityRecord(null)

        assertFalse(matcher.matches(record, "ликвидность"))
    }

    private fun liquidityRecord(operation: LiquidityOperation?): EvmTransactionRecord {
        mockkObject(Translator)
        every { Translator.getString(Res.string.transactions_liquidity_search_key) } returns "ликвидность"
        every { Translator.getString(Res.string.transactions_liquidity_removed) } returns "Выведено из ликвидности"
        every { Translator.getString(Res.string.transactions_liquidity_add_failed) } returns
            "Не удалось добавить в ликвидность"
        return EvmTransactionRecord(
            transaction = mockk(relaxed = true) { every { hashString } returns "0xhash" },
            token = rune,
            source = TransactionSource(thorchain, mockk(relaxed = true), null),
            protected = false,
            transactionRecordType = TransactionRecordType.EVM_CONTRACT_CALL,
            incomingEvents = emptyList(),
            outgoingEvents = emptyList(),
            liquidityOperation = operation,
        )
    }

    private companion object {
        const val MEMO = "=:ETH.ETH:0xabc"
        const val RECIPIENT = "thor1recipientaddress"
        const val SENDER = "thor1senderaddress"
        const val NFT_MINT = "NftMint11111111111111111111111111111111111"
    }
}
