package cash.p.terminal.wallet.hardware

/**
 * Supplies the raw hex of a previous transaction so a hardware wallet can verify the UTXO an input
 * spends. Backed by the wallet's own kit storage, so signing works offline.
 *
 * [hash] is the display-order (reversed) txid.
 */
interface BtcPreviousTransactionProvider {
    fun getRawTransaction(hash: String): String?
}

/** Raw hex by display txid, each looked up once; [missing] is the error for one the kit does not hold. */
fun BtcPreviousTransactionProvider.rawTransactions(
    displayTxids: List<String>,
    missing: (String) -> Exception,
): Map<String, String> = displayTxids.distinct().associateWith { txid ->
    getRawTransaction(txid) ?: throw missing(txid)
}
