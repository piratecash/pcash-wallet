package cash.p.terminal.core.adapters

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.managers.EvmLabelManager
import cash.p.terminal.core.managers.TronKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.tronkit.TronKit
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.models.Contract
import io.horizontalsystems.tronkit.models.TriggerSmartContract
import io.horizontalsystems.tronkit.network.Network
import io.horizontalsystems.tronkit.transaction.Fee
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.math.BigDecimal
import java.math.BigInteger

// The fee is estimated on Dispatchers.Default, so these tests wait in real time.
class Trc20AdapterTest {

    private val ownAddress = Address.fromBase58(OWN_ADDRESS)
    private val balance = BigInteger.valueOf(5_000_000)
    private val balanceFlow = MutableStateFlow(balance)
    private val recipients = mutableListOf<Address>()
    private val tronKit = mockk<TronKit>(relaxed = true) {
        every { address } returns ownAddress
        every { network } returns Network.Mainnet
        every { getTrc20Balance(USDT_CONTRACT) } returns balance
        every { getTrc20BalanceFlow(USDT_CONTRACT) } returns balanceFlow
        every { transferTrc20TriggerSmartContract(any(), capture(recipients), any()) } returns mockk<TriggerSmartContract>()
        coEvery { estimateFee(any<Contract>()) } returns listOf(
            Fee.Energy(required = 130_285, price = 100),
            Fee.Bandwidth(points = 345, price = 1_000),
        )
    }
    private lateinit var adapter: Trc20Adapter

    @Before
    fun setUp() {
        startKoin {
            modules(module {
                single { mockk<ICoinManager>(relaxed = true) }
                single { mockk<EvmLabelManager>(relaxed = true) }
            })
        }
        val wallet = mockk<Wallet>(relaxed = true) { every { decimal } returns 6 }
        adapter = Trc20Adapter(TronKitWrapper(tronKit, signer = null), USDT_CONTRACT, wallet, mockk<Token>(relaxed = true))
    }

    @After
    fun tearDown() {
        if (::adapter.isInitialized) adapter.stop()
        stopKoin()
    }

    @Test
    fun fee_estimate_simulatesTransferToFreshAddressEachTime() = runBlocking {
        adapter.resumeNetwork()
        withTimeout(TIMEOUT_MS) { adapter.fee.first { it.compareTo(BigDecimal("13.3735")) == 0 } }
        balanceFlow.value = balance + BigInteger.ONE
        verify(timeout = TIMEOUT_MS, atLeast = 2) { tronKit.transferTrc20TriggerSmartContract(any(), any(), any()) }

        val (first, second) = recipients
        assertNotEquals(ownAddress, first)
        assertNotEquals(first, second)
        assertEquals(Network.Mainnet.addressPrefixByte, first.raw.first())
    }

    private companion object {
        const val OWN_ADDRESS = "TLa2f6VPqDgRE67v1736s7bJ8Ray5wYjU7"
        const val USDT_CONTRACT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
        const val TIMEOUT_MS = 10_000L
    }
}
