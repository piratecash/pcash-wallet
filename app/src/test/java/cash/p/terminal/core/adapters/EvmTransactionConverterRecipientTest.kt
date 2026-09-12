package cash.p.terminal.core.adapters

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.installEthereumCryptoProviderForTest
import cash.p.terminal.core.managers.EvmLabelManager
import cash.p.terminal.data.repository.EvmTransactionRepository
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.storage.TransactionSyncSourceStorage
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.FullTransaction
import io.horizontalsystems.ethereumkit.models.Transaction
import io.horizontalsystems.uniswapkit.decorations.SwapDecoration
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigInteger

/**
 * A swap output delivered to our own address must not be reported as a third-party
 * recipient. Rows synced before the kit fix still carry a polluted sender, so the
 * converter has to neutralize the recipient itself.
 */
class EvmTransactionConverterRecipientTest {

    companion object {
        init {
            installEthereumCryptoProviderForTest()
        }
    }

    private val bscBlockchain =
        Blockchain(BlockchainType.BinanceSmartChain, "Binance Smart Chain", null)
    private val userAddress = Address("0x1111111111111111111111111111111111111111")
    private val thirdParty = Address("0x2222222222222222222222222222222222222222")
    private val routerAddress = Address("0x13f4ea83d0bd40e75c8222255bc855a974568dd4")

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
    private val syncSourceStorage: TransactionSyncSourceStorage = mockk(relaxed = true)

    private fun createConverter(): EvmTransactionConverter {
        every { repository.receiveAddress } returns userAddress
        every { repository.getBlockchainType() } returns BlockchainType.BinanceSmartChain
        every { syncSourceStorage.getSource(any()) } returns null

        return EvmTransactionConverter(
            coinManager = coinManager,
            evmTransactionRepository = repository,
            source = source,
            baseToken = baseToken,
            evmLabelManager = evmLabelManager,
            syncSourceStorage = syncSourceStorage
        )
    }

    private fun swapTransaction(): Transaction = Transaction(
        hash = ByteArray(32) { 7 },
        timestamp = 1_700_000_000L,
        isFailed = false,
        from = userAddress,
        to = routerAddress,
        value = BigInteger.ONE
    )

    private fun swapDecoration(recipient: Address?) = SwapDecoration(
        contractAddress = routerAddress,
        amountIn = SwapDecoration.Amount.Exact(BigInteger.ONE),
        amountOut = SwapDecoration.Amount.Exact(BigInteger.TEN),
        tokenIn = SwapDecoration.Token.EvmCoin,
        tokenOut = SwapDecoration.Token.EvmCoin,
        recipient = recipient,
        deadline = null
    )

    @Test
    fun transactionRecord_swapRecipientIsOwnAddress_hasNoRecipient() {
        val record = createConverter().transactionRecord(
            FullTransaction(swapTransaction(), swapDecoration(userAddress), emptyMap())
        )

        assertNull(record.recipient)
    }

    @Test
    fun transactionRecord_swapRecipientIsThirdParty_keepsRecipient() {
        val record = createConverter().transactionRecord(
            FullTransaction(swapTransaction(), swapDecoration(thirdParty), emptyMap())
        )

        assertEquals(thirdParty.eip55, record.recipient)
    }

    @Test
    fun transactionRecord_swapWithoutRecipient_hasNoRecipient() {
        val record = createConverter().transactionRecord(
            FullTransaction(swapTransaction(), swapDecoration(null), emptyMap())
        )

        assertNull(record.recipient)
    }
}
