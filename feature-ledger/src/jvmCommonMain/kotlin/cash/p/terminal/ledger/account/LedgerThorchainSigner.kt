package cash.p.terminal.ledger.account

import cash.p.terminal.ledger.domain.LedgerSigningKey
import cash.p.terminal.ledgerkit.thorchain.ThorchainAppClient
import io.horizontalsystems.thorchainkit.transaction.ThorchainExternalSigner

/** Signs amino JSON sign docs on the THORChain app, which shows them; never touches the network. */
class LedgerThorchainSigner(private val key: LedgerSigningKey) : ThorchainExternalSigner {

    override val publicKey: ByteArray = key.hardwarePublicKey.publicKey

    override suspend fun signAmino(signDoc: ByteArray): ByteArray =
        key.sign { transport, path -> ThorchainAppClient(transport).signTransaction(path, signDoc) }
}
