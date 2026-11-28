package cash.p.terminal.ledger.ethereum

import cash.p.terminal.ledgerkit.apdu.StatusWord
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.wallet.crypto.EvmSignatureRecovery
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.ethereumkit.crypto.CryptoUtils
import io.horizontalsystems.ethereumkit.crypto.InternalBouncyCastleProvider
import io.horizontalsystems.ethereumkit.models.Address
import java.math.BigInteger
import java.security.Security

/** The Ethereum app over single-chunk APDUs, signing for [chainId] with [privateKey] the way the device does. */
class FakeEthereumApp(
    private val privateKey: BigInteger,
    private val chainId: Int,
    private val typedDataHash: ByteArray? = null,
) : LedgerTransport {
    init {
        // Keccak comes from the kit's provider, which only the running app installs.
        Security.addProvider(InternalBouncyCastleProvider.getInstance())
    }

    val address: Address = Signer.address(privateKey)
    val instructions = mutableListOf<Int>()

    override suspend fun exchange(apdu: ByteArray): ByteArray {
        val ins = apdu[1].toInt() and 0xFF
        instructions += ins
        val data = apdu.copyOfRange(APDU_HEADER, apdu.size)
        return when (ins) {
            SIGN_TRANSACTION -> signTransaction(data.copyOfRange(pathSize(data), data.size))
            SIGN_PERSONAL_MESSAGE -> {
                val message = data.copyOfRange(pathSize(data) + MESSAGE_LENGTH_SIZE, data.size)
                messageSignature(EvmSignatureRecovery.personalSignHash(message))
            }
            SIGN_EIP712 -> messageSignature(checkNotNull(typedDataHash))
            PROVIDE_ERC20_TOKEN_INFORMATION -> byteArrayOf(0) + OK
            else -> OK
        }
    }

    override fun close() = Unit

    // A legacy `v` keeps only its low byte, as the app does; a typed transaction answers the parity.
    private fun signTransaction(unsigned: ByteArray): ByteArray {
        val (r, s, recId) = sign(CryptoUtils.sha3(unsigned))
        val isLegacy = (unsigned[0].toInt() and 0xFF) >= RLP_LIST
        val v = if (isLegacy) (chainId * 2 + EIP155_V_OFFSET + recId) and 0xFF else recId
        return byteArrayOf(v.toByte()) + r + s + OK
    }

    private fun messageSignature(hash: ByteArray): ByteArray {
        val (r, s, recId) = sign(hash)
        return byteArrayOf((MESSAGE_V_BASE + recId).toByte()) + r + s + OK
    }

    private fun sign(hash: ByteArray): Triple<ByteArray, ByteArray, Int> {
        val signature = CryptoUtils.ellipticSign(hash, privateKey)
        val r = signature.copyOfRange(0, SCALAR_SIZE)
        val s = signature.copyOfRange(SCALAR_SIZE, 2 * SCALAR_SIZE)
        val recId = checkNotNull(EvmSignatureRecovery.messageSignature(hash, r, s, address)).last().toInt()
        return Triple(r, s, recId)
    }

    private fun pathSize(data: ByteArray) = 1 + PATH_INDEX_SIZE * data[0]

    private companion object {
        const val APDU_HEADER = 5
        const val SIGN_TRANSACTION = 0x04
        const val SIGN_PERSONAL_MESSAGE = 0x08
        const val PROVIDE_ERC20_TOKEN_INFORMATION = 0x0A
        const val SIGN_EIP712 = 0x0C
        const val PATH_INDEX_SIZE = 4
        const val MESSAGE_LENGTH_SIZE = 4
        const val SCALAR_SIZE = 32
        const val EIP155_V_OFFSET = 35
        const val MESSAGE_V_BASE = 27
        const val RLP_LIST = 0xC0
        val OK = byteArrayOf((StatusWord.OK shr 8).toByte(), StatusWord.OK.toByte())
    }
}
