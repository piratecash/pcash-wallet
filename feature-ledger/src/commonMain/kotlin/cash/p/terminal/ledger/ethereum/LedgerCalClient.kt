package cash.p.terminal.ledger.ethereum

import cash.p.terminal.ledger.account.SolanaTokenTransfer
import io.horizontalsystems.ethereumkit.core.hexStringToByteArray
import io.horizontalsystems.ethereumkit.core.toRawHexString
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

/** Ledger's crypto-asset list and trust service: the signed data device apps show transactions in clear with. */
class LedgerCalClient(private val httpClient: HttpClient) {

    /** The descriptor as the app takes it, or null when the list does not know the token. */
    suspend fun erc20Descriptor(chainId: Int, contract: String): ByteArray? =
        httpClient.get(TOKENS_URL) {
            parameter("chain_id", chainId)
            parameter("contract_address", contract.lowercase())
            parameter("output", "ticker,descriptor")
        }.body<List<CalToken>>().firstOrNull()?.appDescriptor()

    /** The certificate [device] needs to accept data signed by [keyId], or null when the device has none. */
    suspend fun certificate(device: String, usage: String, keyId: String): SignedDescriptor? =
        httpClient.get(CERTIFICATES_URL) {
            parameter("output", "id,descriptor")
            parameter("target_device", device)
            parameter("public_key_usage", usage)
            parameter("public_key_id", keyId)
            parameter("latest", true)
        }.body<List<CalEntry>>().singleOrNull()?.descriptor?.signed()

    /** The ticker and decimals of a Solana [mint], or null when the list does not know the token. */
    suspend fun solanaTokenDescriptor(mint: String): SignedDescriptor? =
        httpClient.get(TOKENS_URL) {
            parameter("blockchain_name", "solana")
            parameter("contract_address", mint)
            parameter("output", "descriptor")
        }.body<List<CalEntry>>().firstOrNull()?.descriptor?.signed()

    /** The owner of [transfer]'s destination, signed for this device session's [challenge]. */
    suspend fun solanaTrustedName(transfer: SolanaTokenTransfer, challenge: ByteArray): ByteArray {
        val path = transfer.newAccountOwner
            ?.let { "computed-token-account/$it/${transfer.mint}" }
            ?: "owner/${transfer.destination}"
        return httpClient.get("$SOLANA_TRUST_URL/$path") {
            parameter("challenge", "0x${challenge.toRawHexString()}")
        }.body<TrustedName>().signedDescriptor.hexStringToByteArray()
    }

    private companion object {
        const val TOKENS_URL = "https://crypto-assets-service.api.ledger.com/v1/tokens"
        const val CERTIFICATES_URL = "https://crypto-assets-service.api.ledger.com/v1/certificates"
        const val SOLANA_TRUST_URL = "https://nft.api.live.ledger.com/v2/solana"
    }
}

class SignedDescriptor(val data: ByteArray, val signature: ByteArray)

@Serializable
internal class CalToken(val ticker: String, val descriptor: CalDescriptor)

@Serializable
internal class CalEntry(val descriptor: CalDescriptor)

@Serializable
internal class CalDescriptor(val data: String, val signatures: CalSignatures) {
    fun signed() = SignedDescriptor(data.hexStringToByteArray(), signatures.prod.hexStringToByteArray())
}

@Serializable
internal class CalSignatures(val prod: String)

@Serializable
internal class TrustedName(val signedDescriptor: String)

// The list omits the ticker length the app expects in front: `length, ticker, address, decimals, chain id, signature`.
internal fun CalToken.appDescriptor(): ByteArray? {
    val ticker = ticker.encodeToByteArray()
    val data = descriptor.data.hexStringToByteArray()
    if (!data.copyOfRange(0, minOf(ticker.size, data.size)).contentEquals(ticker)) return null
    return byteArrayOf(ticker.size.toByte()) + data + descriptor.signatures.prod.hexStringToByteArray()
}
