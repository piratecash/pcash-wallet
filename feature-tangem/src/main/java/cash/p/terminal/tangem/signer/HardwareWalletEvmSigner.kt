package cash.p.terminal.tangem.signer

import cash.p.terminal.tangem.domain.usecase.SignOneHashTransactionUseCase
import cash.p.terminal.wallet.crypto.EvmSignatureRecovery
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.hardware.DeviceEvmSigner
import com.tangem.Message
import com.tangem.common.CompletionResult
import com.tangem.crypto.hdWallet.DerivationPath
import io.horizontalsystems.ethereumkit.crypto.EIP712Encoder
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.RawTransaction
import io.horizontalsystems.ethereumkit.models.Signature
import org.koin.java.KoinJavaComponent.inject
import java.math.BigInteger
import java.security.SignatureException

class HardwareWalletEvmSigner(
    address: Address,
    private val publicKey: HardwarePublicKey,
    private val chain: Chain,
    private val expectedPublicKeyBytes: ByteArray
) : DeviceEvmSigner(address, chain.id) {

    private val signOneHashTransactionUseCase: SignOneHashTransactionUseCase by inject(
        SignOneHashTransactionUseCase::class.java
    )

    override suspend fun signature(rawTransaction: RawTransaction): Signature {
        val (r, s, recId) = signHashRaw(EvmSignatureRecovery.signingHash(rawTransaction, chain.id))
        val v = EvmSignatureRecovery.transactionV(recId, rawTransaction.gasPrice, chain.id)
        return Signature(v = v, r = r, s = s)
    }

    /** Signs [message] as an EIP-191 `personal_sign` payload; returns `r‖s‖recId` (recId in 0..1). */
    override suspend fun signPersonalMessage(message: ByteArray): ByteArray =
        signMessageBytes(EvmSignatureRecovery.personalSignHash(message))

    /** Signs an EIP-712 typed data payload; returns `r‖s‖recId` (recId in 0..1). */
    override suspend fun signTypedDataMessage(rawJson: String, cardPrompt: String?): ByteArray =
        signMessageBytes(EIP712Encoder().encodeTypedDataHash(rawJson), cardPrompt)


    private suspend fun signMessageBytes(hash: ByteArray, cardPrompt: String? = null): ByteArray {
        val (r, s, recId) = signHashRaw(hash, cardPrompt)
        return r + s + byteArrayOf(recId.toByte())
    }

    /** Signs a raw hash on the card; the common building block for both transaction and message signing. */
    private suspend fun signHashRaw(hash: ByteArray, cardPrompt: String? = null): Triple<ByteArray, ByteArray, Int> {
        // The SDK replaces the body with its own scan hint, so a custom text goes into the header.
        val signBytesResponse = signOneHashTransactionUseCase(
            hash = hash,
            walletPublicKey = publicKey.publicKey,
            derivationPath = DerivationPath(publicKey.derivationPath),
            message = cardPrompt?.let { Message(header = it) },
        )
        when (signBytesResponse) {
            is CompletionResult.Success -> {
                val byteSignature = signBytesResponse.data.signature
                require(byteSignature.size == 64) { "Wrong signature size: ${byteSignature.size}" }
                val r = byteSignature.sliceArray(0..31)
                val s = byteSignature.sliceArray(32..63)
                val recId = EvmSignatureRecovery.findRecoveryId(
                    messageHash = hash,
                    r = BigInteger(1, r),
                    s = BigInteger(1, s),
                    expectedPublicKeyBytes = expectedPublicKeyBytes
                )
                if (recId == -1) {
                    throw SignatureException("Could not find valid recoveryId for the signature")
                }

                return Triple(r, s, recId)
            }

            is CompletionResult.Failure -> {
                throw signBytesResponse.error
            }
        }
    }
}
