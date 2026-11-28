package cash.p.terminal.ledger.account

import cash.p.terminal.ledger.domain.LedgerSigningKey
import cash.p.terminal.ledgerkit.solana.SolanaAppClient
import com.solana.core.Account
import com.solana.core.PublicKey
import kotlinx.coroutines.runBlocking

/** Signs on the Solana app; without [clearSigning] (offline signing) it never touches the network. */
class LedgerSolanaSigner(
    private val key: LedgerSigningKey,
    private val clearSigning: LedgerSolanaClearSigning? = null,
) : Account {

    override val publicKey: PublicKey = PublicKey(key.hardwarePublicKey.key.value.hexToByteArray())

    // solana-kit signs synchronously on its background send path, never on the main thread.
    override fun sign(serializedMessage: ByteArray): ByteArray = runBlocking {
        key.sign { transport, path ->
            clearSigning?.prepare(transport, key.deviceModel, serializedMessage)
            SolanaAppClient(transport).signTransaction(path, serializedMessage)
        }
    }
}
