package cash.p.terminal.modules.multiswap.sendtransaction

import cash.p.terminal.modules.send.SendResult
import io.horizontalsystems.ethereumkit.models.FullTransaction
import org.stellar.sdk.responses.TransactionResponse

sealed class SendTransactionResult {
    data class Evm(val fullTransaction: FullTransaction) : SendTransactionResult()
    data class Btc(
        val uid: String,
        val canonicalHashReversedHex: String,
        val isQueued: Boolean = false
    ) : SendTransactionResult()
    data class Ton(val result: SendResult) : SendTransactionResult()
    data class Tron(val result: SendResult) : SendTransactionResult()
    data class Stellar(val transactionResponse: TransactionResponse) : SendTransactionResult()
    data class Solana(val result: SendResult) : SendTransactionResult()
    data class ZCash(val result: SendResult) : SendTransactionResult()
    data class Monero(val result: SendResult) : SendTransactionResult()
    data class Beam(val result: SendResult, val transactionId: String) : SendTransactionResult()

    fun getRecordUid(): String? = when (this) {
        is Evm -> fullTransaction.transaction.hashString
        is Btc -> uid
        is Stellar -> transactionResponse.hash
        is Tron -> result.recordUid()
        is Ton -> result.recordUid()
        is ZCash -> result.recordUid()
        is Solana -> result.recordUid()
        is Monero -> result.recordUid()
        is Beam -> result.recordUid()
    }

    // For Stellar the record uid already IS the canonical hash; UTXO carries its own field.
    // The EVM record uid is the 0x-prefixed hash, but Thornode/Mayanode tx ids are the bare hex hash.
    // A BEAM record uid is scoped to the account; the chain knows only the Core TxID.
    fun getCanonicalTxHash(): String? = when (this) {
        is Btc -> canonicalHashReversedHex
        is Evm -> fullTransaction.transaction.hashString.removePrefix("0x")
        is Beam -> transactionId
        else -> getRecordUid()
    }

    private fun SendResult.recordUid(): String? = when (this) {
        is SendResult.Sent -> recordUid
        is SendResult.SentButQueued -> recordUid
        is SendResult.Failed,
        SendResult.Sending -> null
    }
}
