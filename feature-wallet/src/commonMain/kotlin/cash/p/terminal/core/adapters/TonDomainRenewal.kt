package cash.p.terminal.core.adapters

import com.tonapps.blockchain.ton.extensions.toByteArray
import io.horizontalsystems.tonkit.core.TonKit
import io.ktor.util.encodeBase64
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.ton.cell.CellBuilder
import java.math.BigInteger
import kotlin.time.Duration.Companion.seconds

/** 0.02 TON in nanotons. */
internal val DOMAIN_RENEW_AMOUNT: BigInteger = BigInteger.valueOf(20_000_000)

private const val CHANGE_DNS_RECORD_OP = 0x4eb1f0f9
private val SEQNO_POLL_INTERVAL = 2.seconds
private val SEQNO_WAIT_TIMEOUT = 90.seconds

class DomainRenewalTimeoutException : Exception("Wallet seqno did not advance in time")

/** `change_dns_record` with an empty key and no value: the DNS item contract treats it as a renewal. */
internal fun domainRenewalPayload(queryId: Long): String = CellBuilder.createCell {
    storeUInt(CHANGE_DNS_RECORD_OP, 32)
    storeUInt(queryId, 64)
    storeUInt(0, 256)
}.toByteArray().encodeBase64()

/** Waits until the previous chunk is applied: the wallet seqno exceeds the one it was signed with. */
internal suspend fun TonKit.awaitSeqnoAbove(seqno: Int) {
    withTimeoutOrNull(SEQNO_WAIT_TIMEOUT) {
        while (getAccountSeqno() <= seqno) delay(SEQNO_POLL_INTERVAL)
    } ?: throw DomainRenewalTimeoutException()
}
