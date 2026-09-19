package cash.p.terminal.core.managers

import android.util.Base64
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.entities.DecodedOfflineTransaction
import cash.p.terminal.entities.OfflineBeamMetadata
import cash.p.terminal.entities.OfflineFeeMetadata
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.entities.OfflineSolanaRetryMetadata
import cash.p.terminal.entities.OfflineStellarRetryMetadata
import cash.p.terminal.entities.OfflineTonRetryMetadata
import cash.p.terminal.entities.OfflineTokenMetadata
import cash.p.terminal.entities.OfflineTronRetryMetadata
import cash.p.terminal.entities.OfflineTransactionOutpoint
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenQuery
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.Base58
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.zip.Deflater
import java.util.zip.Inflater

class OfflineTransactionPayloadEncoder {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun encode(draft: OfflineSignedTransactionDraft): String {
        require(draft.rawHex.length <= MAX_INPUT_CHARACTERS) { "Offline transaction is too large" }
        val blockchainUid = draft.wallet.token.blockchainType.uid
        val feeToken = draft.feeToken ?: draft.wallet.token
        val payload = Payload(
            blockchainUid = blockchainUid,
            rawHex = draft.rawHex.lowercase(),
            txHash = draft.txHash,
            token = draft.wallet.token.toPayloadToken(),
            amountAtomic = draft.amount.toAtomic(draft.wallet.token),
            fee = draft.fee?.toPayloadFee(feeToken),
            toAddress = draft.toAddress,
            createdAt = draft.createdAt,
            inputOutpoints = draft.inputOutpoints,
            solanaRetry = draft.solanaRetryMetadata?.toPayload(),
            tonRetry = draft.tonRetryMetadata?.toPayload(),
            tronRetry = draft.tronRetryMetadata?.toPayload(),
            stellarRetry = draft.stellarRetryMetadata?.toPayload(),
            beam = draft.beamMetadata,
            checksum = checksum(draft.rawHex),
        )
        require(isStructurallyValidBeam(payload)) { "Invalid BEAM transport metadata" }
        return listOf(SCHEME, TYPE, VERSION, blockchainUid, compressedBase64(payload))
            .joinToString(separator = ":")
    }

    /** Compatibility API; import callers must use decodeResult to forbid malformed-envelope fallback. */
    fun decode(payload: String): DecodedOfflineTransaction? =
        (decodeResult(payload) as? DecodeResult.Decoded)?.transaction

    sealed interface DecodeResult {
        data object NotEnvelope : DecodeResult
        data object Invalid : DecodeResult
        /** Structural checks only; this does not authorize BEAM relay. */
        data class Decoded(val transaction: DecodedOfflineTransaction) : DecodeResult
    }

    fun decodeResult(payload: String): DecodeResult {
        if (payload.length > MAX_INPUT_CHARACTERS) return DecodeResult.Invalid
        if (!isOfflineTransactionPayload(payload)) return DecodeResult.NotEnvelope
        return tryOrNull { decodeEnvelope(payload)?.let { DecodeResult.Decoded(it) } } ?: DecodeResult.Invalid
    }

    private fun decodeEnvelope(payload: String): DecodedOfflineTransaction? {
        val parts = payload.trim().split(":", limit = 5)
        if (parts.size != 5) return null
        if (parts[0] != SCHEME || parts[1] != TYPE || parts[2] != VERSION) return null
        val blockchainUid = parts[3]
        val body = parts[4]
        if (!body.all { (it.isLetterOrDigit() && it.code < 128) || it in "-_=\r\n\t " }) return null

        val compressed = Base64.decode(body, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val bodyJson = decompress(compressed).decodeToString(throwOnInvalidSequence = true)
        val decoded = json.decodeFromString<Payload>(bodyJson)

        if (!isValidPayload(decoded, blockchainUid)) return null

        return decoded.toDecodedTransaction()
    }

    private fun Payload.toDecodedTransaction() = DecodedOfflineTransaction(
        blockchainUid = blockchainUid,
        rawHex = rawHex,
        txHash = txHash,
        token = token.toMetadata(),
        amountAtomic = amountAtomic,
        fee = fee?.toMetadata(),
        toAddress = toAddress,
        createdAt = createdAt,
        inputOutpoints = inputOutpoints,
        solanaRetryMetadata = solanaRetry?.toMetadata(),
        tonRetryMetadata = tonRetry?.toMetadata(),
        tronRetryMetadata = tronRetry?.toMetadata(),
        stellarRetryMetadata = stellarRetry?.toMetadata(),
        beamMetadata = beam,
    )

    private fun compressedBase64(payload: Payload): String {
        val bytes = json.encodeToString(payload).encodeToByteArray()
        require(bytes.size <= MAX_DECOMPRESSED_SIZE) { "Offline payload is too large" }
        val compressed = compress(bytes)
        return Base64.encodeToString(compressed, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun compress(bytes: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        return try {
            deflater.setInput(bytes)
            deflater.finish()
            val output = ByteArrayOutputStream(bytes.size)
            val buffer = ByteArray(COMPRESSION_BUFFER_SIZE)
            while (!deflater.finished()) {
                output.write(buffer, 0, deflater.deflate(buffer))
            }
            output.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun decompress(bytes: ByteArray): ByteArray {
        val inflater = Inflater()
        return try {
            inflater.setInput(bytes)
            val initialCapacity = (bytes.size * INFLATE_SIZE_HINT).coerceAtMost(MAX_DECOMPRESSED_SIZE)
            val output = ByteArrayOutputStream(initialCapacity)
            val buffer = ByteArray(COMPRESSION_BUFFER_SIZE)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                // The whole input is fed at once, so a complete stream only stops by finishing. Any zero
                // read means truncation or a required preset dictionary: stop instead of looping forever.
                require(count > 0 || inflater.finished()) { "Incomplete compressed offline payload" }
                require(output.size() + count <= MAX_DECOMPRESSED_SIZE) {
                    "Decompressed offline payload exceeds the allowed size"
                }
                output.write(buffer, 0, count)
            }
            require(inflater.remaining == 0) { "Trailing compressed offline payload data" }
            output.toByteArray()
        } finally {
            inflater.end()
        }
    }

    // The checksum detects rawHex corruption only; it authenticates neither bytes nor display metadata.
    private fun isValidPayload(payload: Payload, blockchainUid: String): Boolean =
        payload.version == VERSION_INT &&
                payload.encoding == RAW_HEX_ENCODING &&
                payload.blockchainUid == blockchainUid &&
                isStructurallyValidBeam(payload) &&
                isHex(payload.rawHex) &&
                payload.checksum == checksum(payload.rawHex) &&
                isTxHash(payload.txHash, blockchainUid) &&
                isValidToken(payload.token, blockchainUid) &&
                isValidFee(payload.fee, blockchainUid) &&
                isValidRetries(payload, blockchainUid) &&
                hasReceiver(payload, blockchainUid) &&
                isNonNegativeAtomic(payload.amountAtomic)

    // A BEAM receiver is confidential and absent from the signed bytes, so its export carries none.
    private fun hasReceiver(payload: Payload, blockchainUid: String): Boolean =
        blockchainUid == BlockchainType.Beam.uid || payload.toAddress.isNotBlank()

    private fun isValidRetries(payload: Payload, blockchainUid: String): Boolean =
        isValidSolanaRetry(payload.solanaRetry, blockchainUid) &&
                isValidTonRetry(payload.tonRetry, blockchainUid) &&
                isValidTronRetry(payload.tronRetry, blockchainUid) &&
                isValidStellarRetry(payload.stellarRetry, blockchainUid)

    private fun isStructurallyValidBeam(payload: Payload): Boolean {
        if (payload.blockchainUid != BlockchainType.Beam.uid) return payload.beam == null
        val beam = payload.beam ?: return false
        return beam.version == 1 &&
                beam.network in setOf("mainnet", "testnet") &&
                isValidBeamRules(beam.rulesSignature) &&
                isCanonicalHex(beam.mainKernelId, TX_HASH_HEX_LENGTH) &&
                beam.mainKernelId == payload.txHash &&
                (beam.coreTxId == null || isCanonicalHex(beam.coreTxId, CORE_TX_ID_HEX_LENGTH)) &&
                isCanonicalHex(payload.rawHex, payload.rawHex.length) &&
                isValidBeamAmounts(payload)
    }

    private fun isValidBeamRules(rules: String): Boolean =
        rules.length in 1..MAX_BEAM_RULES_CHARACTERS &&
                rules.isNotBlank() &&
                rules.all { it.code in 32..126 || it == '\n' || it == '\t' }

    private fun isValidBeamAmounts(payload: Payload): Boolean {
        val fee = payload.fee
        return isBeamNativeToken(payload.token.tokenQueryId, payload.token.decimals) &&
                isBeamAtomic(payload.amountAtomic) &&
                (fee == null || isBeamNativeToken(fee.tokenQueryId, fee.decimals) && isBeamAtomic(fee.atomic))
    }

    private fun isBeamNativeToken(queryId: String, decimals: Int): Boolean =
        queryId == "beam|native" && decimals == BEAM_DECIMALS

    private fun isBeamAtomic(value: String): Boolean =
        value.isNotEmpty() && value.length <= MAX_BEAM_ATOMIC_CHARACTERS &&
                value.all { it in '0'..'9' } && value.toLongOrNull() != null

    private fun isCanonicalHex(value: String, length: Int): Boolean =
        value.length == length && isHex(value) && value.none { it in 'A'..'F' }

    private fun BigDecimal.toAtomic(token: Token): String =
        if (token.blockchainType == BlockchainType.Beam) {
            require(isBeamNativeToken(token.tokenQuery.id, token.decimals)) { "Invalid native BEAM token" }
            movePointRight(BEAM_DECIMALS).longValueExact().also { require(it >= 0) }.toString()
        } else {
            movePointRight(token.decimals).toBigInteger().toString()
        }

    private fun BigDecimal.toPayloadFee(token: Token) = PayloadFee(
        tokenQueryId = token.tokenQuery.id,
        atomic = toAtomic(token),
        decimals = token.decimals,
    )

    private fun isValidToken(token: PayloadToken, blockchainUid: String): Boolean {
        val query = TokenQuery.fromId(token.tokenQueryId) ?: return false
        return query.blockchainType.uid == blockchainUid &&
                token.coinCode.isNotBlank() &&
                token.decimals >= 0
    }

    private fun isValidFee(fee: PayloadFee?, blockchainUid: String): Boolean {
        fee ?: return true
        val query = TokenQuery.fromId(fee.tokenQueryId) ?: return false
        return query.blockchainType.uid == blockchainUid &&
                fee.decimals >= 0 &&
                isNonNegativeAtomic(fee.atomic)
    }

    private fun isTxHash(value: String, blockchainUid: String): Boolean =
        if (blockchainUid == BlockchainType.Solana.uid) {
            isSolanaSignature(value)
        } else {
            // A Bitcoin/EVM txid is a 32-byte hash, i.e. exactly 64 hex characters; anything else means
            // the signer wrote a value the wallet would never produce, so reject it instead of keying a
            // record by it.
            value.length == TX_HASH_HEX_LENGTH && isHex(value)
        }

    private fun isSolanaSignature(value: String): Boolean =
        tryOrNull { Base58.decode(value).size == SOLANA_SIGNATURE_BYTES } == true

    private fun isValidSolanaRetry(
        solanaRetry: PayloadSolanaRetry?,
        blockchainUid: String,
    ): Boolean =
        if (blockchainUid == BlockchainType.Solana.uid) {
            solanaRetry != null &&
                    solanaRetry.blockHash.isNotBlank() &&
                    solanaRetry.lastValidBlockHeight > 0
        } else {
            solanaRetry == null
        }

    private fun isValidTonRetry(
        tonRetry: PayloadTonRetry?,
        blockchainUid: String,
    ): Boolean =
        if (blockchainUid == BlockchainType.Ton.uid) {
            tonRetry != null &&
                    tonRetry.validUntil > 0 &&
                    tonRetry.senderAddress.isNotBlank() &&
                    tonRetry.seqno >= 0
        } else {
            tonRetry == null
        }

    private fun isValidTronRetry(
        tronRetry: PayloadTronRetry?,
        blockchainUid: String,
    ): Boolean =
        if (blockchainUid == BlockchainType.Tron.uid) {
            tronRetry != null && tronRetry.expiration > 0
        } else {
            tronRetry == null
        }

    private fun isValidStellarRetry(
        stellarRetry: PayloadStellarRetry?,
        blockchainUid: String,
    ): Boolean =
        if (blockchainUid == BlockchainType.Stellar.uid) {
            stellarRetry != null &&
                    stellarRetry.sourceAccountId.isNotBlank() &&
                    stellarRetry.sequenceNumber > 0 &&
                    stellarRetry.validUntil > 0
        } else {
            stellarRetry == null
        }

    private fun isNonNegativeAtomic(value: String): Boolean =
        (value.toBigIntegerOrNull()?.signum() ?: -1) >= 0

    private fun checksum(rawHex: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawHex.lowercase().encodeToByteArray())
        return Base64.encodeToString(
            digest.copyOf(CHECKSUM_BYTES), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }

    private fun Token.toPayloadToken() = PayloadToken(
        tokenQueryId = tokenQuery.id,
        coinUid = coin.uid,
        coinCode = coin.code,
        coinName = coin.name,
        decimals = decimals,
    )

    private fun PayloadToken.toMetadata() = OfflineTokenMetadata(
        tokenQueryId = tokenQueryId,
        coinUid = coinUid,
        coinCode = coinCode,
        coinName = coinName,
        decimals = decimals,
    )

    private fun PayloadFee.toMetadata() = OfflineFeeMetadata(
        tokenQueryId = tokenQueryId,
        atomic = atomic,
        decimals = decimals,
    )

    private fun OfflineSolanaRetryMetadata.toPayload() = PayloadSolanaRetry(
        blockHash = blockHash,
        lastValidBlockHeight = lastValidBlockHeight,
    )

    private fun PayloadSolanaRetry.toMetadata() = OfflineSolanaRetryMetadata(
        blockHash = blockHash,
        lastValidBlockHeight = lastValidBlockHeight,
    )

    private fun OfflineTonRetryMetadata.toPayload() = PayloadTonRetry(
        validUntil = validUntil,
        senderAddress = senderAddress,
        seqno = seqno,
    )

    private fun PayloadTonRetry.toMetadata() = OfflineTonRetryMetadata(
        validUntil = validUntil,
        senderAddress = senderAddress,
        seqno = seqno,
    )

    private fun OfflineTronRetryMetadata.toPayload() = PayloadTronRetry(
        expiration = expiration,
    )

    private fun PayloadTronRetry.toMetadata() = OfflineTronRetryMetadata(
        expiration = expiration,
    )

    private fun OfflineStellarRetryMetadata.toPayload() = PayloadStellarRetry(
        sourceAccountId = sourceAccountId,
        sequenceNumber = sequenceNumber,
        validUntil = validUntil,
    )

    private fun PayloadStellarRetry.toMetadata() = OfflineStellarRetryMetadata(
        sourceAccountId = sourceAccountId,
        sequenceNumber = sequenceNumber,
        validUntil = validUntil,
    )

    @Serializable
    private data class Payload(
        val version: Int = 1,
        val blockchainUid: String,
        val encoding: String = RAW_HEX_ENCODING,
        val rawHex: String,
        val txHash: String,
        val token: PayloadToken,
        val amountAtomic: String,
        val fee: PayloadFee?,
        val toAddress: String,
        val createdAt: Long,
        val inputOutpoints: List<OfflineTransactionOutpoint>,
        val checksum: String,
        val solanaRetry: PayloadSolanaRetry? = null,
        val tonRetry: PayloadTonRetry? = null,
        val tronRetry: PayloadTronRetry? = null,
        val stellarRetry: PayloadStellarRetry? = null,
        val beam: OfflineBeamMetadata? = null,
    )

    @Serializable
    private data class PayloadToken(
        val tokenQueryId: String,
        val coinUid: String?,
        val coinCode: String,
        val coinName: String?,
        val decimals: Int,
    )

    @Serializable
    private data class PayloadFee(
        val tokenQueryId: String,
        val atomic: String,
        val decimals: Int,
    )

    @Serializable
    private data class PayloadSolanaRetry(
        val blockHash: String,
        val lastValidBlockHeight: Long,
    )

    @Serializable
    private data class PayloadTonRetry(
        val validUntil: Long,
        val senderAddress: String,
        val seqno: Int,
    )

    @Serializable
    private data class PayloadTronRetry(
        val expiration: Long,
    )

    @Serializable
    private data class PayloadStellarRetry(
        val sourceAccountId: String,
        val sequenceNumber: Long,
        val validUntil: Long,
    )

    companion object {
        private const val SCHEME = "pcash"
        private const val TYPE = "tx"
        private const val VERSION = "v1"

        // Cheap prefix check so the global scanner can route a P.CASH payload to broadcast
        // without injecting the encoder or running a full decode.
        const val PAYLOAD_PREFIX = "$SCHEME:$TYPE:"

        fun isOfflineTransactionPayload(text: String): Boolean =
            text.indexOfFirst { !it.isWhitespace() }.let { start ->
                start >= 0 && text.startsWith(PAYLOAD_PREFIX, start)
            }

        fun isRawTransactionHex(text: String): Boolean =
            text.length <= MAX_INPUT_CHARACTERS && text.trim().let { value ->
                value.length >= MIN_RAW_HEX_LENGTH && isHex(value)
            }

        private fun isHex(value: String): Boolean =
            value.isNotEmpty() && value.length % 2 == 0 &&
                    value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

        private const val VERSION_INT = 1
        private const val RAW_HEX_ENCODING = "rawhex"
        private const val CHECKSUM_BYTES = 8
        private const val TX_HASH_HEX_LENGTH = 64
        private const val SOLANA_SIGNATURE_BYTES = 64
        private const val MIN_RAW_HEX_LENGTH = 20
        private const val COMPRESSION_BUFFER_SIZE = 512
        private const val INFLATE_SIZE_HINT = 4
        private const val BEAM_DECIMALS = 8
        private const val MAX_BEAM_ATOMIC_CHARACTERS = 19
        private const val MAX_BEAM_RULES_CHARACTERS = 2048
        private const val CORE_TX_ID_HEX_LENGTH = 32

        // Covers Base64 of a 1 MiB zlib stream (including overhead), and raw hex of 1 MiB bytes.
        const val MAX_INPUT_CHARACTERS = 2 * 1024 * 1024

        // Keep the existing admission limit until legitimate BEAM envelope sizes have been measured.
        private const val MAX_DECOMPRESSED_SIZE = 1 * 1024 * 1024
    }
}
