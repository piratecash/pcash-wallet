package cash.p.terminal.modules.transactions

import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.utils.IncomingTransaction
import cash.p.terminal.core.utils.SwapTransactionMatcher
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.stellar.StellarTransactionRecord
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.stellarkit.room.Operation
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class SwapProviderRecordResolverStellarTest {

    private val stellar = Blockchain(BlockchainType.Stellar, "Stellar", null)
    private val xlm = Token(Coin("stellar", "Stellar", "XLM"), stellar, TokenType.Native, 7)
    private val usdc = Token(Coin("usd-coin", "USD Coin", "USDC"), stellar, TokenType.Asset("USDC", "GISSUER"), 7)

    private val storage = mockk<SwapProviderTransactionsStorage> {
        every { getByOutgoingRecordUid(any()) } returns null
        every { getByTokenIn(any(), any(), any()) } returns null
    }
    private val incoming = slot<IncomingTransaction>()
    private val swapTransactionMatcher = mockk<SwapTransactionMatcher> {
        every { findMatch(capture(incoming)) } returns null
    }
    private val resolver = SwapProviderRecordResolver(storage, swapTransactionMatcher)

    @Test
    fun find_dexSwap_looksUpNoProviderSwap() {
        resolver.find(record(StellarTransactionRecord.Type.Swap(coin(xlm, "-5"), coin(usdc, "0.86"))))

        verify(exactly = 0) { storage.getByOutgoingRecordUid(any()) }
        verify(exactly = 0) { swapTransactionMatcher.findMatch(any()) }
    }

    @Test
    fun find_receiveOfAsset_matchesPayoutByReceivedAsset() {
        resolver.find(record(StellarTransactionRecord.Type.Receive(coin(usdc, "0.86"), "GFROM", "GSELF", null, false)))

        assertEquals(usdc, incoming.captured.token)
        assertEquals(BigDecimal("0.86"), incoming.captured.amount)
    }

    @Test
    fun find_sendOfUnknownAsset_isNotMatchedAsXlmByAmount() {
        val unknown = TransactionValue.TokenValue("AQUA", "AQUA", 7, BigDecimal("-5"), null)

        resolver.find(record(StellarTransactionRecord.Type.Send(unknown, "GTO", false, null, false)))

        verify(exactly = 0) { storage.getByTokenIn(any(), any(), any()) }
    }

    private fun coin(token: Token, amount: String) = TransactionValue.CoinValue(token, BigDecimal(amount))

    private fun record(type: StellarTransactionRecord.Type) = StellarTransactionRecord(
        baseToken = xlm,
        source = TransactionSource(stellar, mockk(relaxed = true), null),
        operation = Operation(
            id = 1L,
            timestamp = 1_700_000_000L,
            pagingToken = "1",
            sourceAccount = "GSELF",
            transactionHash = "hash",
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
}
