package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.core.App
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.core.adapters.Eip20Adapter
import cash.p.terminal.core.installEthereumCryptoProviderForTest
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.modules.multiswap.action.ActionApprove
import cash.p.terminal.modules.multiswap.action.ActionRevoke
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Transaction
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class EvmSwapHelperTest {

    companion object {
        init {
            installEthereumCryptoProviderForTest()
        }
    }

    private val router = Address("0x1111111111111111111111111111111111111111")
    private val otherSpender = "0x2222222222222222222222222222222222222222"
    private val usdtAddress = "0xdac17f958d2ee523a2206206994597c13d831ec7"
    private val ethereum = Blockchain(BlockchainType.Ethereum, "Ethereum", null)

    private val adapterManager: IAdapterManager = mockk()
    private val eip20Adapter: Eip20Adapter = mockk()

    @Before
    fun setUp() {
        mockkObject(App)
        every { App.adapterManager } returns adapterManager
        every { adapterManager.getAdapterForToken<Eip20Adapter>(any()) } returns eip20Adapter
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun token(address: String) = Token(
        coin = Coin(uid = "token", name = "Token", code = "TKN"),
        blockchain = ethereum,
        type = TokenType.Eip20(address),
        decimals = 6
    )

    private fun pendingApprove(spender: String, zeroValue: Boolean, token: Token) = EvmTransactionRecord(
        transaction = Transaction(
            hash = ByteArray(32) { 1 },
            timestamp = 1_700_000_000L,
            isFailed = false,
            from = Address("0x3333333333333333333333333333333333333333"),
            to = Address(spender),
            value = BigInteger.ZERO
        ),
        token = token,
        source = TransactionSource(ethereum, mockk(relaxed = true), null),
        protected = false,
        transactionRecordType = TransactionRecordType.EVM_APPROVE,
        spender = spender,
        value = TransactionValue.CoinValue(token, if (zeroValue) BigDecimal.ZERO else BigDecimal.ONE)
    )

    @Test
    fun actionApprove_pendingApproveToRouter_reportsInProgress() = runTest {
        val token = token("0x4444444444444444444444444444444444444444")
        coEvery { eip20Adapter.pendingTransactions() } returns
            listOf(pendingApprove(router.eip55, zeroValue = false, token))

        val action = EvmSwapHelper.actionApprove(BigDecimal.ZERO, BigDecimal.TEN, router.eip55, token)

        assertTrue(action is ActionApprove)
        assertTrue(requireNotNull(action).inProgress)
    }

    @Test
    fun actionApprove_pendingApproveToOtherSpender_notInProgress() = runTest {
        val token = token("0x4444444444444444444444444444444444444444")
        coEvery { eip20Adapter.pendingTransactions() } returns
            listOf(pendingApprove(otherSpender, zeroValue = false, token))

        val action = EvmSwapHelper.actionApprove(BigDecimal.ZERO, BigDecimal.TEN, router.eip55, token)

        assertFalse(requireNotNull(action).inProgress)
    }

    @Test
    fun actionApprove_usdtWithExistingAllowanceAndPendingZeroApprove_reportsRevokeInProgress() = runTest {
        val token = token(usdtAddress)
        coEvery { eip20Adapter.pendingTransactions() } returns
            listOf(pendingApprove(router.eip55, zeroValue = true, token))

        val action = EvmSwapHelper.actionApprove(BigDecimal.ONE, BigDecimal.TEN, router.eip55, token)

        assertTrue(action is ActionRevoke)
        assertTrue(requireNotNull(action).inProgress)
    }

    @Test
    fun actionApprove_allowanceCoversAmount_returnsNull() = runTest {
        val action = EvmSwapHelper.actionApprove(BigDecimal.TEN, BigDecimal.TEN, router.eip55, token(usdtAddress))

        assertNull(action)
    }
}
