package cash.p.terminal.modules.transactionInfo

import cash.p.terminal.core.adapters.TronTransactionsAdapter
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.tron.TronTransactionRecord
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.tron_fee_energy_contract
import cash.p.terminal.resources.tron_fee_energy_own
import cash.p.terminal.resources.tron_resource_bandwidth
import cash.p.terminal.resources.tron_resource_energy
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.Translator
import io.horizontalsystems.tronkit.TronKit
import io.horizontalsystems.tronkit.models.Transaction
import io.horizontalsystems.tronkit.models.TransferContract
import io.horizontalsystems.tronkit.models.TriggerSmartContract
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.math.BigDecimal

class TronTransactionDetailsTest {

    private val formatter = mockk<IAppNumberFormatter> {
        every { format(ofType<BigDecimal>(), 0, 0) } answers { "#" + firstArg<BigDecimal>() }
        every { formatCoinFull(any(), any(), any()) } answers { firstArg<BigDecimal>().toPlainString() + " TRX" }
    }
    private val tronKit = mockk<TronKit>(relaxed = true)
    private val adapter = TronTransactionsAdapter(
        tronKitWrapper = mockk { every { tronKit } returns this@TronTransactionDetailsTest.tronKit },
        transactionConverter = mockk(relaxed = true),
        dispatcherProvider = mockk { every { io } returns Dispatchers.Unconfined },
    )

    @Before
    fun setUp() {
        startKoin { modules(module { single { formatter } }) }
        mockkObject(Translator)
        mapOf(
            Res.string.tron_resource_energy to "Energy",
            Res.string.tron_resource_bandwidth to "Bandwidth",
        ).forEach { (res, text) -> every { Translator.getString(res) } returns text }
        every { Translator.getString(Res.string.tron_fee_energy_own, any()) } answers { "${secondArg<Array<Any>>()[0]} own" }
        every { Translator.getString(Res.string.tron_fee_energy_contract, any()) } answers { "${secondArg<Array<Any>>()[0]} contract" }
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkAll()
    }

    @Test
    fun getItems_contractCallWithReceipt_showsEnergyAndBurnedBandwidth() {
        val record = record(
            netUsage = 0, netFee = 345_000, energyUsage = 20_000, energyFee = 4_489_500,
            originEnergyUsage = 10_000, energyUsageTotal = 64_895,
        )

        val items = TronResourceItemsFactory.getItems(record, hideSensitiveInfo = false)

        assertEquals(
            listOf(
                "Energy" to "#64895 · #20000 own · #10000 contract · 4.4895 TRX",
                "Bandwidth" to "0.345 TRX",
            ),
            items.map { (it as TransactionInfoViewItem.Value).let { v -> v.title to v.value } }
        )
    }

    @Test
    fun getItems_trxTransferFromQuota_showsOnlyBandwidthPoints() {
        val record = record(
            type = TransactionRecordType.TRON_OUTGOING,
            smartContract = false,
            netUsage = 345, netFee = 0, energyUsage = 0, energyFee = 0, energyUsageTotal = 0,
        )

        val items = TronResourceItemsFactory.getItems(record, hideSensitiveInfo = false)

        assertEquals(
            listOf("Bandwidth" to "#345"),
            items.map { (it as TransactionInfoViewItem.Value).let { v -> v.title to v.value } }
        )
    }

    @Test
    fun getItems_trc20TransferWithReceipt_showsEnergyRow() {
        val record = record(
            type = TransactionRecordType.TRON_OUTGOING,
            netUsage = 345, netFee = 0, energyUsage = 0, energyFee = 13_844_850,
            originEnergyUsage = 0, energyUsageTotal = 64_895,
        )

        val items = TronResourceItemsFactory.getItems(record, hideSensitiveInfo = false)

        assertEquals(
            listOf("Energy" to "#64895 · 13.84485 TRX", "Bandwidth" to "#345"),
            items.map { (it as TransactionInfoViewItem.Value).let { v -> v.title to v.value } }
        )
    }

    @Test
    fun getItems_sensitiveInfoHidden_masksResourceValues() {
        val record = record(
            type = TransactionRecordType.TRON_OUTGOING,
            netUsage = 345, netFee = 0, energyUsage = 0, energyFee = 13_844_850,
            originEnergyUsage = 0, energyUsageTotal = 64_895,
        )

        val items = TronResourceItemsFactory.getItems(record, hideSensitiveInfo = true)

        assertEquals(
            listOf("Energy" to "*****", "Bandwidth" to "*****"),
            items.map { (it as TransactionInfoViewItem.Value).let { v -> v.title to v.value } }
        )
    }

    @Test
    fun getItems_receiptUnknown_showsDashes() {
        val items = TronResourceItemsFactory.getItems(record(), hideSensitiveInfo = false)

        assertEquals(
            listOf("Energy" to "—", "Bandwidth" to "—"),
            items.map { (it as TransactionInfoViewItem.Value).let { v -> v.title to v.value } }
        )
    }

    @Test
    fun getItems_incoming_noRows() {
        val items = TronResourceItemsFactory.getItems(
            record(type = TransactionRecordType.TRON_INCOMING),
            hideSensitiveInfo = false,
        )

        assertTrue(items.isEmpty())
    }

    @Test
    fun fetchMissingDetails_ownTxInBlockWithoutReceipt_fetchesReceipt() = runTest {
        adapter.fetchMissingDetails(record())

        coVerify(exactly = 1) { tronKit.fetchTransactionReceipt("hash") }
    }

    @Test
    fun fetchMissingDetails_pendingTx_doesNotFetch() = runTest {
        adapter.fetchMissingDetails(record(blockNumber = null))

        coVerify(exactly = 0) { tronKit.fetchTransactionReceipt(any()) }
    }

    @Test
    fun fetchMissingDetails_completeReceipt_doesNotFetch() = runTest {
        adapter.fetchMissingDetails(
            record(
                netUsage = 345, netFee = 0, energyUsage = 1, energyFee = 0,
                originEnergyUsage = 0, energyUsageTotal = 1,
            )
        )

        coVerify(exactly = 0) { tronKit.fetchTransactionReceipt(any()) }
    }

    @Test
    fun fetchMissingDetails_zeroFilledPlaceholder_fetchesReceipt() = runTest {
        adapter.fetchMissingDetails(
            record(
                netUsage = 0, netFee = 0, energyUsage = 0, energyFee = 0,
                originEnergyUsage = 0, energyUsageTotal = 0,
            )
        )

        coVerify(exactly = 1) { tronKit.fetchTransactionReceipt("hash") }
    }

    @Test
    fun fetchMissingDetails_incomingTx_doesNotFetch() = runTest {
        adapter.fetchMissingDetails(record(type = TransactionRecordType.TRON_INCOMING))

        coVerify(exactly = 0) { tronKit.fetchTransactionReceipt(any()) }
    }

    private fun record(
        type: TransactionRecordType = TransactionRecordType.TRON_CONTRACT_CALL,
        smartContract: Boolean = true,
        blockNumber: Long? = 100,
        netUsage: Long? = null,
        netFee: Long? = null,
        energyUsage: Long? = null,
        energyFee: Long? = null,
        originEnergyUsage: Long? = null,
        energyUsageTotal: Long? = null,
    ): TronTransactionRecord {
        val transaction = mockk<Transaction>(relaxed = true) {
            every { hashString } returns "hash"
            every { contract } returns if (smartContract) mockk<TriggerSmartContract>() else mockk<TransferContract>()
            every { this@mockk.blockNumber } returns blockNumber
            every { this@mockk.netUsage } returns netUsage
            every { this@mockk.netFee } returns netFee
            every { this@mockk.energyUsage } returns energyUsage
            every { this@mockk.energyFee } returns energyFee
            every { this@mockk.originEnergyUsage } returns originEnergyUsage
            every { this@mockk.energyUsageTotal } returns energyUsageTotal
        }
        val token = mockk<Token>(relaxed = true) {
            every { decimals } returns 6
            every { coin.code } returns "TRX"
        }
        return TronTransactionRecord(
            token = token,
            source = mockk<TransactionSource>(relaxed = true),
            transactionRecordType = type,
            incomingEvents = emptyList(),
            outgoingEvents = emptyList(),
            transaction = transaction,
        )
    }
}
