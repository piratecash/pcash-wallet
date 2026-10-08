package cash.p.terminal.core.utils

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.AppDatabase
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SwapTransactionMatcherTest {

    private val storage = mockk<SwapProviderTransactionsStorage>(relaxed = true)
    private val matcher = SwapTransactionMatcher(storage)

    private val onChainAmount = BigDecimal("0.01965841")

    private val litecoinToken = Token(
        coin = Coin(uid = "litecoin", name = "Litecoin", code = "LTC"),
        blockchain = Blockchain(BlockchainType.Litecoin, "Litecoin", null),
        type = TokenType.Derived(TokenType.Derivation.Bip84),
        decimals = 8,
    )

    private fun swap() = SwapProviderTransaction(
        date = 1_000L,
        outgoingRecordUid = null,
        transactionId = "tx-1",
        status = TransactionStatusEnum.SENDING.name.lowercase(),
        provider = SwapProvider.CHANGENOW,
        coinUidIn = "binancecoin",
        blockchainTypeIn = "binance-smart-chain",
        amountIn = BigDecimal.ONE,
        addressIn = "addr-in",
        coinUidOut = "litecoin",
        blockchainTypeOut = "litecoin",
        amountOut = BigDecimal("0.0196651"),
        addressOut = "ltc1qzvd",
        accountId = "acc-1",
    )

    private fun incoming(
        addresses: List<String>? = listOf("ltc1qzvd"),
        amount: BigDecimal? = onChainAmount,
    ) = IncomingTransaction(
        uid = "record-uid",
        amount = amount,
        timestamp = 1_000L,
        token = litecoinToken,
        addresses = addresses,
        accountId = "acc-1",
    )

    @Test
    fun findMatchingSwap_alreadyMatched_returnsCachedWithoutFurtherLookup() {
        val swap = swap()
        every { storage.getByIncomingRecordUid("record-uid") } returns swap

        val result = matcher.findMatchingSwap(incoming())

        assertEquals(swap, result)
        verify(exactly = 0) { storage.getByAddressAndAmount(any(), any(), any(), any(), any()) }
    }

    @Test
    fun findMatchingSwap_addressMatch_returnsSwapAndCachesIncomingRecordUid() {
        val swap = swap()
        every { storage.getByIncomingRecordUid(any()) } returns null
        every {
            storage.getByAddressAndAmount("ltc1qzvd", litecoinToken, "acc-1", any(), any())
        } returns swap

        val result = matcher.findMatchingSwap(incoming())

        assertEquals(swap, result)
        verify {
            storage.setIncomingRecordUid(
                date = swap.date,
                incomingRecordUid = "record-uid",
                amountOutReal = onChainAmount,
            )
        }
    }

    @Test
    fun findMatchingSwap_noAddresses_usesAmountTimestampPathAndCaches() {
        val swap = swap()
        every { storage.getByIncomingRecordUid(any()) } returns null
        every {
            storage.getUnmatchedSwapsByTokenOut(any(), any(), any(), any(), any(), "acc-1", any())
        } returns listOf(swap)

        val result = matcher.findMatchingSwap(incoming(addresses = null))

        assertEquals(swap, result)
        verify { storage.setIncomingRecordUid(swap.date, "record-uid", onChainAmount) }
    }

    @Test
    fun findMatchingSwap_addressMissAmountMiss_fallsBackToTimestampOnly() {
        val swap = swap()
        every { storage.getByIncomingRecordUid(any()) } returns null
        every { storage.getByAddressAndAmount(any(), any(), any(), any(), any()) } returns null
        every { storage.getByTokenOut(litecoinToken, 1_000L, "acc-1") } returns swap

        val result = matcher.findMatchingSwap(incoming())

        assertEquals(swap, result)
    }

    @Test
    fun findMatchingSwap_nothingFound_returnsNull() {
        every { storage.getByIncomingRecordUid(any()) } returns null
        every { storage.getByAddressAndAmount(any(), any(), any(), any(), any()) } returns null
        every { storage.getByTokenOut(any(), any(), any()) } returns null

        val result = matcher.findMatchingSwap(incoming())

        assertNull(result)
    }

    @Test
    fun findMatchingSwap_recordCoinUidDiffersFromWalletCoin_matchesBySameToken() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dispatcher = UnconfinedTestDispatcher()
            val realStorage = SwapProviderTransactionsStorage(
                dao = database.swapProviderTransactionsDao(),
                dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
            )
            realStorage.save(
                swap().copy(coinUidOut = "renamed-litecoin", tokenQueryIdOut = litecoinToken.tokenQuery.id)
            )

            val result = SwapTransactionMatcher(realStorage).findMatchingSwap(incoming())

            assertEquals("tx-1", result?.transactionId)
        } finally {
            database.close()
        }
    }
}
