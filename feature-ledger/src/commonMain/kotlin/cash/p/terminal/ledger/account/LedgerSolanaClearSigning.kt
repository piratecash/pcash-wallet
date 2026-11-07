package cash.p.terminal.ledger.account

import cash.p.terminal.ledger.ethereum.LedgerCalClient
import cash.p.terminal.ledger.ethereum.LedgerTokenDescriptorStore
import cash.p.terminal.ledger.ethereum.SignedDescriptor
import cash.p.terminal.ledgerkit.PkiKeyUsage
import cash.p.terminal.ledgerkit.device.LedgerModel
import cash.p.terminal.ledgerkit.loadPkiCertificate
import cash.p.terminal.ledgerkit.solana.SolanaAppClient
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/** Gives the Solana app what shows an SPL transfer in clear; whatever fails leaves the transfer to blind signing. */
class LedgerSolanaClearSigning(
    private val calClient: LedgerCalClient,
    private val store: LedgerTokenDescriptorStore,
) {

    /** Caches what does not depend on the device session, so signing only waits for the challenge-bound name. */
    suspend fun prefetch(model: String, mint: String) {
        val device = calDevice(model) ?: return
        Certificate.entries.forEach { certificate(device, it) }
        tokenDescriptor(mint)
    }

    /** Runs right before the sign APDU, inside the same app session. */
    suspend fun prepare(transport: LedgerTransport, model: String, message: ByteArray) {
        val device = calDevice(model) ?: return
        try {
            val transfer = SolanaTokenTransfer.parse(message) ?: return
            provideTokenDescriptor(transport, device, transfer.mint)
            provideTrustedName(transport, device, transfer)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.w(error) { "SPL transfer left to blind signing" }
        }
    }

    private suspend fun provideTokenDescriptor(transport: LedgerTransport, device: String, mint: String) {
        val descriptor = tokenDescriptor(mint) ?: return
        val certificate = certificate(device, Certificate.TokenMetadata) ?: return
        transport.loadPkiCertificate(PkiKeyUsage.COIN_META, certificate.data, certificate.signature)
        SolanaAppClient(transport).provideTrustedDynamicDescriptor(descriptor.data, descriptor.signature)
    }

    private suspend fun provideTrustedName(transport: LedgerTransport, device: String, transfer: SolanaTokenTransfer) {
        val certificate = certificate(device, Certificate.TrustedName) ?: return
        val app = SolanaAppClient(transport)
        val challenge = app.getChallenge()
        val trustedName = withTimeoutOrNull(REQUEST_TIMEOUT) { calClient.solanaTrustedName(transfer, challenge) }
            ?: return
        transport.loadPkiCertificate(PkiKeyUsage.TRUSTED_NAME, certificate.data, certificate.signature)
        app.provideTrustedName(trustedName)
    }

    private suspend fun certificate(device: String, certificate: Certificate) =
        cachedOrFetched("pki:$device:${certificate.usage}") {
            calClient.certificate(device, certificate.usage, certificate.keyId)
        }

    private suspend fun tokenDescriptor(mint: String) =
        cachedOrFetched("solana:$mint") { calClient.solanaTokenDescriptor(mint) }

    private suspend fun cachedOrFetched(key: String, fetch: suspend () -> SignedDescriptor?): SignedDescriptor? =
        store.signed(key) ?: withTimeoutOrNull(REQUEST_TIMEOUT) { fetch() }?.also { store.save(key, it) }

    private enum class Certificate(val usage: String, val keyId: String) {
        TrustedName("trusted_name", "domain_metadata_key"),
        TokenMetadata("coin_meta", "token_metadata_key"),
    }

    internal companion object {
        private val REQUEST_TIMEOUT = 3.seconds
        private val logger = Logger.withTag("LedgerSolanaClearSigning")

        /** The CAL `target_device` of a stored model; the Nano S has no PKI, so the CAL issues it nothing. */
        fun calDevice(model: String): String? = when (LedgerModel.entries.find { it.name == model }) {
            LedgerModel.NanoSPlus -> "nanosp"
            LedgerModel.NanoX -> "nanox"
            LedgerModel.Stax -> "stax"
            LedgerModel.Flex -> "flex"
            LedgerModel.NanoGen5 -> "apexp"
            LedgerModel.NanoS, LedgerModel.Unknown, null -> null
        }
    }
}
