package cash.p.terminal.core.adapters

import cash.p.terminal.core.NftBroadcastResult
import cash.p.terminal.core.TonFeeEstimate
import cash.p.terminal.core.managers.TonKitWrapper
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.core.TonWallet
import io.horizontalsystems.tonkit.models.Network
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import io.horizontalsystems.tonkit.models.TonFeeEstimate as KitTonFeeEstimate

// Robolectric: the request is built with org.json, which the plain Android stubs do not implement.
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TonDomainRenewalTest {

    private val tonKit: TonKit = mockk(relaxed = true)
    private val wrapper: TonKitWrapper = mockk(relaxed = true)
    private val signed = mutableListOf<SendRequestEntity>()
    private val signedSeqnos = mutableListOf<Int?>()
    private val sentAt = mutableListOf<Long>()
    private var seqnoReads = 0
    private val domains = (1..5).map { nftAddress(it) }

    private fun TestScope.createAdapter(tonWallet: TonWallet = fullAccess(messageLimit = 4)): TonAdapter {
        every { wrapper.tonKit } returns tonKit
        every { wrapper.tonWallet } returns tonWallet
        every { tonKit.account } returns null
        every { tonKit.network } returns Network.MainNet
        coEvery { tonKit.sign(any(), any(), any()) } answers {
            signed += firstArg<SendRequestEntity>()
            signedSeqnos += thirdArg<Int?>()
            "boc"
        }
        coEvery { tonKit.send(any<String>()) } answers { sentAt += testScheduler.currentTime }
        // Each read sees one more applied transaction.
        coEvery { tonKit.getAccountSeqno() } answers { seqnoReads++ }
        return TonAdapter(wrapper)
    }

    @Test
    fun renewDomains_oneDomain_sendsRenewalMessageToBounceableNft() = runTest {
        val result = createAdapter().renewDomains(domains.take(1))

        val message = signed.single().messages.single()
        val bits = message.payload.bits
        assertEquals(NftBroadcastResult(domains.take(1), error = null), result)
        assertEquals(domains.first(), message.addressValue)
        assertTrue(message.walletTransfer.bounceable)
        assertEquals(20_000_000L, message.amount)
        assertEquals(32 + 64 + 256, bits.size)
        assertEquals(0x4eb1f0f9u, message.payload.beginParse().loadUInt32())
        assertTrue((32 + 64 until bits.size).none { bits[it] })
    }

    @Test
    fun renewDomains_oneChunk_signsWithoutReadingSeqno() = runTest {
        createAdapter().renewDomains(domains.take(3))

        coVerify(exactly = 0) { tonKit.getAccountSeqno() }
        assertEquals(listOf<Int?>(null), signedSeqnos)
    }

    @Test
    fun renewDomains_fiveDomainsFourMessageWallet_sendsFourThenOne() = runTest {
        val result = createAdapter().renewDomains(domains)

        assertEquals(listOf(domains.take(4), domains.drop(4)), signed.map { it.addresses() })
        assertEquals(NftBroadcastResult(domains, error = null), result)
    }

    @Test
    fun renewDomains_fiveDomainsLargeMessageLimit_sendsOneTransaction() = runTest {
        createAdapter(fullAccess(messageLimit = 255)).renewDomains(domains)

        assertEquals(listOf(domains), signed.map { it.addresses() })
    }

    @Test
    fun renewDomains_fiveDomainsExternalSigner_sendsOneMessagePerTransaction() = runTest {
        createAdapter(mockk<TonWallet.ExternalSigner>()).renewDomains(domains)

        assertEquals(domains.map { listOf(it) }, signed.map { it.addresses() })
    }

    @Test
    fun renewDomains_secondChunk_waitsUntilSeqnoAdvances() = runTest {
        val adapter = createAdapter()
        coEvery { tonKit.getAccountSeqno() } returnsMany listOf(7, 7, 7, 8, 8)

        adapter.renewDomains(domains)

        assertEquals(listOf(0L, 4_000L), sentAt)
    }

    @Test
    fun renewDomains_threeChunks_signsEachWithSeqnoReadBeforeItAndWaitsPastIt() = runTest {
        val adapter = createAdapter()
        val calls = mutableListOf<String>()
        val seqnos = ArrayDeque(listOf(7, 7, 8, 8, 8, 9, 9))
        coEvery { tonKit.sign(any(), any(), any()) } answers { calls += "sign ${thirdArg<Int?>()}"; "boc" }
        coEvery { tonKit.send(any<String>()) } answers { calls += "send" }
        coEvery { tonKit.getAccountSeqno() } answers { seqnos.removeFirst().also { calls += "seqno $it" } }

        adapter.renewDomains((1..9).map { nftAddress(it) })

        assertEquals(
            listOf(
                "seqno 7", "sign 7", "send",
                "seqno 7", "seqno 8", "seqno 8", "sign 8", "send",
                "seqno 8", "seqno 9", "seqno 9", "sign 9", "send",
            ),
            calls,
        )
    }

    @Test
    fun renewDomains_earlierTxConfirmsAfterSeqnoRead_chunksCarryDistinctSeqnos() = runTest {
        val adapter = createAdapter()
        val carried = mutableListOf<Int>()
        // The chain is already at 8 when chunk 1 is signed; a signer left to read it itself would sign 8.
        coEvery { tonKit.sign(any(), any(), any()) } answers { carried += thirdArg<Int?>() ?: 8; "boc" }
        coEvery { tonKit.getAccountSeqno() } returnsMany listOf(7, 8, 8)

        adapter.renewDomains(domains)

        assertEquals(listOf(7, 8), carried)
    }

    @Test
    fun renewDomains_firstChunkConfirmsImmediately_signsSecondWithoutWaiting() = runTest {
        val adapter = createAdapter()
        coEvery { tonKit.getAccountSeqno() } returnsMany listOf(7, 8, 8)

        val result = adapter.renewDomains(domains)

        assertEquals(NftBroadcastResult(domains, error = null), result)
        assertEquals(listOf<Int?>(7, 8), signedSeqnos)
        assertEquals(listOf(0L, 0L), sentAt)
    }

    @Test
    fun renewDomains_seqnoNeverAdvances_returnsFirstChunkAndTimeout() = runTest {
        val adapter = createAdapter()
        coEvery { tonKit.getAccountSeqno() } returns 7

        val result = adapter.renewDomains(domains)

        assertEquals(domains.take(4), result.broadcast)
        assertIs<DomainRenewalTimeoutException>(result.error)
        assertEquals(1, signed.size)
    }

    @Test
    fun renewDomains_secondChunkSendFails_returnsOnlyFirstChunk() = runTest {
        val adapter = createAdapter()
        val failure = IllegalStateException("send failed")
        coEvery { tonKit.send(any<String>()) } answers { sentAt += 0L } andThenThrows failure

        val result = adapter.renewDomains(domains)

        assertEquals(domains.take(4), result.broadcast)
        assertSame(failure, result.error)
    }

    @Test
    fun renewDomains_signCancelled_rethrows() = runTest {
        val adapter = createAdapter()
        coEvery { tonKit.sign(any(), any(), any()) } throws CancellationException()

        assertFailsWith<CancellationException> { adapter.renewDomains(domains) }
    }

    @Test
    fun renewDomains_watchOnlyWallet_returnsErrorWithoutSigning() = runTest {
        val result = createAdapter(TonWallet.WatchOnly(domains.first())).renewDomains(domains)

        assertTrue(result.broadcast.isEmpty())
        assertIs<IllegalStateException>(result.error)
        assertTrue(signed.isEmpty())
    }

    @Test
    fun estimateDomainRenewalFee_twoChunks_sumsFeeAndDebit() = runTest {
        val adapter = createAdapter()
        coEvery { tonKit.estimateFee(any<SendRequestEntity>()) } returnsMany listOf(
            KitTonFeeEstimate(fee = BigInteger("5000000"), totalDebit = BigInteger("85000000")),
            KitTonFeeEstimate(fee = BigInteger("3000000"), totalDebit = BigInteger("23000000")),
        )

        val estimate = adapter.estimateDomainRenewalFee(domains)

        assertEquals(TonFeeEstimate(fee = BigDecimal("0.008"), requiredTon = BigDecimal("0.108")), estimate)
    }

    private fun SendRequestEntity.addresses() = messages.map { it.addressValue }

    private fun fullAccess(messageLimit: Int): TonWallet.FullAccess = mockk {
        every { maxMessages(Network.MainNet) } returns messageLimit
    }

    private fun nftAddress(index: Int): String =
        Address.parse("0:" + "%02x".format(index).repeat(32)).toUserFriendly(true)
}
