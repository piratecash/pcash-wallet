package cash.p.terminal.core.adapters

import cash.p.terminal.core.BroadcastRawTransactionResult
import cash.p.terminal.core.BroadcastRawTransactionStatus
import cash.p.terminal.core.DomainRenewalResult
import cash.p.terminal.core.OfflineBroadcastMetadata
import cash.p.terminal.core.OfflineTransactionAdapter
import cash.p.terminal.core.OfflineTransactionStatusAdapter
import cash.p.terminal.core.SignedOfflineTonTransaction
import cash.p.terminal.core.TonFeeEstimate
import cash.p.terminal.core.TonOfflineAnchor
import cash.p.terminal.core.hexToByteArray
import cash.p.terminal.core.managers.TonKitWrapper
import cash.p.terminal.core.managers.statusInfo
import cash.p.terminal.core.managers.RawTransactionHex
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IBalanceAdapter
import cash.p.terminal.wallet.IReceiveAdapter
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.FriendlyAddress
import io.horizontalsystems.tonkit.core.TonWallet
import io.horizontalsystems.tonkit.models.Network
import io.horizontalsystems.tonkit.models.RawMessageBroadcastMetadata
import io.horizontalsystems.tonkit.models.RawMessageBroadcastStatus
import io.horizontalsystems.tonkit.models.TonFeeEstimate as KitTonFeeEstimate
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.BigInteger

abstract class BaseTonAdapter(
    protected val tonKitWrapper: TonKitWrapper,
    val decimals: Int
) : IAdapter,
    IBalanceAdapter,
    IReceiveAdapter,
    OfflineTransactionAdapter<SignedOfflineTonTransaction>,
    OfflineTransactionStatusAdapter {

    val tonKit = tonKitWrapper.tonKit

    override val debugInfo: String
        get() = ""

    override val statusInfo: Map<String, Any>
        get() = tonKit.statusInfo()

    // IReceiveAdapter

    suspend fun sendWithPayloadBoc(amount: BigInteger, address: String, payload: String) {
        signAndSend(singlePayloadRequest(amount, address, payload))
    }

    suspend fun estimateFeeWithPayload(amount: BigInteger, address: String, payload: String): TonFeeEstimate =
        tonKit.estimateFee(singlePayloadRequest(amount, address, payload)).toAppEstimate()

    suspend fun estimateDomainRenewalFee(nftAddresses: List<String>): TonFeeEstimate =
        renewalChunks(nftAddresses)
            .map { tonKit.estimateFee(domainRenewalRequest(it)) }
            .fold(KitTonFeeEstimate(fee = BigInteger.ZERO, totalDebit = BigInteger.ZERO)) { total, chunk ->
                KitTonFeeEstimate(fee = total.fee + chunk.fee, totalDebit = total.totalDebit + chunk.totalDebit)
            }
            .toAppEstimate()

    suspend fun renewDomains(nftAddresses: List<String>): DomainRenewalResult {
        val broadcast = mutableListOf<String>()
        return try {
            val chunks = renewalChunks(nftAddresses)
            var previousSeqno: Int? = null
            chunks.forEach { chunk ->
                previousSeqno?.let { tonKit.awaitSeqnoAbove(it) }
                // Signing with the seqno read here tells the next chunk exactly which value to wait past.
                val seqno = if (chunks.size > 1) tonKit.getAccountSeqno() else null
                signAndSend(domainRenewalRequest(chunk), seqno)
                broadcast += chunk
                previousSeqno = seqno
            }
            DomainRenewalResult(broadcast, error = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DomainRenewalResult(broadcast, error = e)
        }
    }

    suspend fun sendNftTransfer(nftAddress: String, address: FriendlyAddress, comment: String?) {
        tonKit.sendNftTransfer(Address.parse(nftAddress), address, comment)
    }

    suspend fun estimateNftTransferFee(nftAddress: String, address: FriendlyAddress, comment: String?): TonFeeEstimate =
        tonKit.estimateNftTransferFee(Address.parse(nftAddress), address, comment).toAppEstimate()

    fun resolveDomain(domain: String): FriendlyAddress? = tonKit.resolveDomain(domain)

    private suspend fun signAndSend(request: SendRequestEntity, seqno: Int? = null) {
        tonKit.send(tonKit.sign(request, tonKitWrapper.tonWallet, seqno))
    }

    private fun renewalChunks(nftAddresses: List<String>): List<List<String>> =
        nftAddresses.chunked(renewalBatchSize())

    private fun renewalBatchSize(): Int = when (val wallet = tonKitWrapper.tonWallet) {
        // The device signs exactly one message per transaction.
        is TonWallet.ExternalSigner -> 1
        is TonWallet.FullAccess -> wallet.maxMessages(tonKit.network)
        is TonWallet.WatchOnly -> error("Watch-only wallet cannot renew domains")
    }

    private fun domainRenewalRequest(nftAddresses: List<String>): SendRequestEntity {
        val payload = domainRenewalPayload(queryId = System.currentTimeMillis())
        return payloadRequest(nftAddresses.map { PayloadMessage(it, DOMAIN_RENEW_AMOUNT, payload) })
    }

    private fun singlePayloadRequest(amount: BigInteger, address: String, payload: String) =
        payloadRequest(listOf(PayloadMessage(address, amount, payload)))

    private fun payloadRequest(messages: List<PayloadMessage>) = SendRequestEntity(
        data = JSONObject().apply {
            put("valid_until", System.currentTimeMillis() / 1000 + 300)
            put("messages", JSONArray().apply {
                messages.forEach { message ->
                    put(JSONObject().apply {
                        put("address", message.address)
                        put("amount", message.amount.toString())
                        put("payload", message.payload)
                    })
                }
            })
        },
        tonConnectRequestId = "id",
        dAppId = "p.cash"
    )

    protected fun KitTonFeeEstimate.toAppEstimate() =
        TonFeeEstimate(fee = fee.toTonAmount(), requiredTon = totalDebit.toTonAmount())

    protected fun BigInteger.toTonAmount(): BigDecimal = toBigDecimal(TON_DECIMALS).stripTrailingZeros()

    override val receiveAddress: String
        get() = tonKit.receiveAddress.toUserFriendly(false)

    override val isMainNet: Boolean
        get() = tonKit.network == Network.MainNet

    override suspend fun broadcastRawTransaction(
        rawTransactionHex: String,
        metadata: OfflineBroadcastMetadata?,
    ): BroadcastRawTransactionResult {
        val normalizedRawHex = rawTransactionHex.trim()
        require(RawTransactionHex.isValid(normalizedRawHex)) {
            "Valid raw transaction hex is required"
        }
        val result = tonKit.broadcastRawTransaction(
            rawMessage = normalizedRawHex.hexToByteArray(),
            metadata = (metadata as? OfflineBroadcastMetadata.Ton)?.toTonMetadata(),
        )
        return BroadcastRawTransactionResult(
            txHash = result.messageHash,
            status = result.status.toAppStatus(),
        )
    }

    override suspend fun transactionExists(txHash: String): Boolean =
        tonKit.transactionExistsByMessageHash(txHash)

    // Network time first, seqno last: the later the seqno is read, the smaller
    // the window in which a concurrent transaction can consume it unnoticed.
    suspend fun fetchOfflineAnchor(): TonOfflineAnchor {
        val unixTimeSeconds = tonKit.getRawTime().toLong()
        return TonOfflineAnchor(
            seqno = tonKit.getAccountSeqno(),
            unixTimeSeconds = unixTimeSeconds,
        )
    }
}

private const val TON_DECIMALS = 9

private class PayloadMessage(val address: String, val amount: BigInteger, val payload: String)

private fun OfflineBroadcastMetadata.Ton.toTonMetadata() = RawMessageBroadcastMetadata(
    validUntil = validUntil,
    senderAddress = senderAddress,
    seqno = seqno,
)

private fun RawMessageBroadcastStatus.toAppStatus(): BroadcastRawTransactionStatus =
    when (this) {
        RawMessageBroadcastStatus.Submitted -> BroadcastRawTransactionStatus.Submitted
        RawMessageBroadcastStatus.Queued -> BroadcastRawTransactionStatus.Queued
        RawMessageBroadcastStatus.AlreadyKnown -> BroadcastRawTransactionStatus.AlreadyKnown
        RawMessageBroadcastStatus.SeqnoConsumed -> BroadcastRawTransactionStatus.SeqnoConsumed
    }
