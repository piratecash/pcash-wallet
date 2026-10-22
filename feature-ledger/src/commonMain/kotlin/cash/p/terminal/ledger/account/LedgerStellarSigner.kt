package cash.p.terminal.ledger.account

import cash.p.terminal.ledger.domain.LedgerSigningFailedException
import cash.p.terminal.ledger.domain.LedgerSigningKey
import cash.p.terminal.ledgerkit.stellar.StellarAppClient
import io.horizontalsystems.stellarkit.Signer
import org.stellar.sdk.KeyPair
import org.stellar.sdk.Transaction
import org.stellar.sdk.xdr.DecoratedSignature
import org.stellar.sdk.xdr.Signature

/** Signs whole transactions on the Stellar app, which shows their operations; never touches the network. */
class LedgerStellarSigner(private val key: LedgerSigningKey) : Signer {

    private val keyPair = KeyPair.fromAccountId(key.hardwarePublicKey.key.value)

    override val publicKey: ByteArray = keyPair.publicKey

    override fun canSign(): Boolean = true

    // A bare hash would leave the app nothing to show.
    override suspend fun sign(hash: ByteArray): DecoratedSignature =
        throw LedgerSigningFailedException("Ledger signs whole Stellar transactions only")

    override suspend fun signTransaction(transaction: Transaction): DecoratedSignature {
        val signature = key.sign { transport, path ->
            StellarAppClient(transport).signTransaction(path, transaction.signatureBase())
        }
        return DecoratedSignature(keyPair.signatureHint, Signature(signature))
    }
}
