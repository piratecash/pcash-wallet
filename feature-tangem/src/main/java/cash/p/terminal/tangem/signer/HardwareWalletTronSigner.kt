package cash.p.terminal.tangem.signer

import cash.p.terminal.tangem.domain.canonicalise
import cash.p.terminal.tangem.domain.usecase.SignOneHashTransactionUseCase
import cash.p.terminal.wallet.crypto.EvmSignatureRecovery
import cash.p.terminal.wallet.entities.HardwarePublicKey
import com.tangem.common.CompletionResult
import com.tangem.crypto.hdWallet.DerivationPath
import io.horizontalsystems.hdwalletkit.ECDSASignature
import io.horizontalsystems.tronkit.hexStringToByteArray
import io.horizontalsystems.tronkit.network.CreatedTransaction
import io.horizontalsystems.tronkit.transaction.Signer
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent.inject
import java.math.BigInteger
import java.security.SignatureException


class HardwareWalletTronSigner(
    private val hardwarePublicKey: HardwarePublicKey,
    private val expectedPublicKeyBytes: ByteArray
) : Signer(BigInteger.ZERO) {

    private val signOneHashTransactionUseCase: SignOneHashTransactionUseCase by inject(
        SignOneHashTransactionUseCase::class.java
    )

    override fun sign(createdTransaction: CreatedTransaction): ByteArray {
        val rawTransactionHash =
            io.horizontalsystems.hdwalletkit.Utils.sha256(createdTransaction.raw_data_hex.hexStringToByteArray())
        return runBlocking {
            val signBytesResponse =
                signOneHashTransactionUseCase(
                    hash = rawTransactionHash,
                    walletPublicKey = hardwarePublicKey.publicKey,
                    derivationPath = DerivationPath(hardwarePublicKey.derivationPath)
                )
            when (signBytesResponse) {
                is CompletionResult.Success -> {
                    val byteSignature = signBytesResponse.data.signature
                    val r = byteSignature.sliceArray(0..31)
                    val s = byteSignature.sliceArray(32..63)
                    val canonicalSignature = ECDSASignature.fromCompact(r + s).canonicalise()

                    val recoveryId = EvmSignatureRecovery.findRecoveryId(
                        messageHash = rawTransactionHash,
                        r = BigInteger(1, canonicalSignature.r),
                        s = BigInteger(1, canonicalSignature.s),
                        expectedPublicKeyBytes = expectedPublicKeyBytes
                    )

                    if (recoveryId == -1) {
                        throw SignatureException("Could not find valid recoveryId for the signature")
                    }

                    val v = recoveryId + 27

                    r + s + byteArrayOf(v.toByte())
                }

                is CompletionResult.Failure -> {
                    throw signBytesResponse.error
                }
            }
        }
    }

}
