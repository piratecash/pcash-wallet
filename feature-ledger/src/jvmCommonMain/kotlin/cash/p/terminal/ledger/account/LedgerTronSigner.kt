package cash.p.terminal.ledger.account

import cash.p.terminal.ledger.domain.LedgerSigningFailedException
import cash.p.terminal.ledger.domain.LedgerSigningKey
import cash.p.terminal.ledgerkit.tron.TronAppClient
import cash.p.terminal.wallet.crypto.EvmSignatureRecovery
import io.horizontalsystems.hdwalletkit.Utils
import io.horizontalsystems.tronkit.network.CreatedTransaction
import io.horizontalsystems.tronkit.transaction.Signer
import kotlinx.coroutines.runBlocking
import java.math.BigInteger

/** Signs on the Tron app; never touches the network. Returns `r‖s‖recId`, as mnemonic signing does. */
class LedgerTronSigner(private val key: LedgerSigningKey) : Signer(BigInteger.ZERO) {

    // tron-kit signs synchronously on its background send path, never on the main thread.
    override fun sign(createdTransaction: CreatedTransaction): ByteArray {
        val rawData = createdTransaction.raw_data_hex.hexToByteArray()
        val signature = runBlocking {
            key.sign { transport, path -> TronAppClient(transport).signTransaction(path, rawData) }
        }
        val r = signature.copyOfRange(0, SCALAR_SIZE)
        val s = signature.copyOfRange(SCALAR_SIZE, 2 * SCALAR_SIZE)
        // The recovery id is derived, not trusted: no id recovers the stored key from another account's signature.
        val recId = EvmSignatureRecovery.findRecoveryId(
            Utils.sha256(rawData),
            BigInteger(1, r),
            BigInteger(1, s),
            key.hardwarePublicKey.publicKey,
        )
        if (recId < 0) throw LedgerSigningFailedException("Device signed with an unexpected account")
        return r + s + byteArrayOf(recId.toByte())
    }

    private companion object {
        const val SCALAR_SIZE = 32
    }
}
