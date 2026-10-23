package cash.p.terminal.wallet.hardware

import io.horizontalsystems.ethereumkit.core.TransactionBuilder
import io.horizontalsystems.ethereumkit.core.TransactionSigner
import io.horizontalsystems.ethereumkit.core.signer.EthSigner
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.ethereumkit.crypto.CryptoUtils
import io.horizontalsystems.ethereumkit.crypto.EIP712Encoder
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.RawTransaction
import io.horizontalsystems.ethereumkit.models.Signature
import java.math.BigInteger

/** The base Signer's message methods sign with a mock key for external signers, so callers must route here. */
interface ExternalEvmMessageSigner {
    suspend fun signPersonalMessage(message: ByteArray): ByteArray
    suspend fun signLegacyHash(hash: ByteArray): ByteArray
    suspend fun signTypedDataMessage(rawJson: String): ByteArray
}

interface ExternalEvmTransactionSigner {
    /** Returns the transaction the device actually signed, which can differ from [rawTransaction]. */
    suspend fun signTransaction(rawTransaction: RawTransaction): SignedEvmTransaction
}

data class SignedEvmTransaction(
    val signature: Signature,
    val rawTransaction: RawTransaction
)

/** A [Signer] whose key stays on a device; the base class holds a mock key that must never sign. */
abstract class DeviceEvmSigner(address: Address, chainId: Int) : Signer(
    transactionBuilder = TransactionBuilder(address, chainId),
    transactionSigner = TransactionSigner(MOCK_PRIVATE_KEY, chainId),
    ethSigner = EthSigner(MOCK_PRIVATE_KEY, CryptoUtils, EIP712Encoder()),
), ExternalEvmMessageSigner {
    private companion object {
        val MOCK_PRIVATE_KEY: BigInteger = BigInteger.ONE
    }
}
