package cash.p.terminal.core.managers

import cash.p.terminal.wallet.hardware.ExternalEvmMessageSigner
import io.horizontalsystems.ethereumkit.core.signer.Signer

/**
 * Single dispatch point for EVM message signing. The base [Signer] exposes only non-suspend,
 * final `signByteArray*`/`signTypedData` methods signed with a mock key for external signers,
 * so [ExternalEvmMessageSigner]s must be routed to their own suspend counterparts here.
 */
object EvmMessageSigning {

    suspend fun signPersonalMessage(signer: Signer, message: ByteArray): ByteArray = when (signer) {
        is ExternalEvmMessageSigner -> signer.signPersonalMessage(message)
        else -> signer.signByteArray(message)
    }

    suspend fun signLegacyHash(signer: Signer, hash: ByteArray): ByteArray = when (signer) {
        is ExternalEvmMessageSigner -> signer.signLegacyHash(hash)
        else -> signer.signByteArrayLegacy(hash)
    }

    suspend fun signTypedData(signer: Signer, rawJson: String): ByteArray = when (signer) {
        is ExternalEvmMessageSigner -> signer.signTypedDataMessage(rawJson)
        else -> signer.signTypedData(rawJson)
    }
}
