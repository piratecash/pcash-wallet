package cash.p.terminal.ledger.bitcoin

import cash.p.terminal.ledger.domain.LedgerAppAccess
import cash.p.terminal.ledger.domain.LedgerSigningFailedException
import cash.p.terminal.ledger.domain.tokenQuery
import cash.p.terminal.ledgerkit.bitcoin.BitcoinAppClient
import cash.p.terminal.ledgerkit.bitcoin.BtchipLegacyClient
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.hardware.BtcPreviousTransactionProvider
import cash.p.terminal.wallet.hardware.rawTransactions
import io.horizontalsystems.bitcoincore.extensions.hexToByteArray
import io.horizontalsystems.bitcoincore.extensions.toHexString
import io.horizontalsystems.bitcoincore.models.Transaction
import io.horizontalsystems.bitcoincore.models.TransactionOutput
import io.horizontalsystems.bitcoincore.network.Network
import io.horizontalsystems.bitcoincore.serializers.BaseTransactionSerializer
import io.horizontalsystems.bitcoincore.storage.InputToSign
import io.horizontalsystems.bitcoincore.transactions.builder.IFullTransactionSigner
import io.horizontalsystems.bitcoincore.transactions.builder.IInputSigner
import io.horizontalsystems.bitcoincore.transactions.builder.ISchnorrInputSigner
import io.horizontalsystems.bitcoincore.transactions.builder.MutableTransaction
import io.horizontalsystems.bitcoincore.transactions.builder.SignedTransactionData
import kotlin.concurrent.Volatile

/** Signs a whole Bitcoin-family transaction on the Ledger, after the device proved it holds [hardwarePublicKey]. */
class LedgerBtcSigner(
    private val appAccess: LedgerAppAccess,
    private val account: Account,
    private val hardwarePublicKey: HardwarePublicKey,
    private val previousTransactions: BtcPreviousTransactionProvider,
) : IInputSigner, ISchnorrInputSigner, IFullTransactionSigner {

    private val query = hardwarePublicKey.tokenQuery
    private val specAndApp = checkNotNull(LedgerBtcApp.specFor(query)) { "Ledger cannot sign ${query.id}" }
    private val spec = specAndApp.first
    private val app = specAndApp.second

    @Volatile
    private var transactionSerializer: BaseTransactionSerializer? = null

    override fun setTransactionSerializer(serializer: BaseTransactionSerializer) {
        transactionSerializer = serializer
    }

    // The signing mode follows from the app; the network adds nothing.
    override fun setNetwork(network: Network) = Unit

    override suspend fun signFullTransaction(mutableTransaction: MutableTransaction): SignedTransactionData {
        val serializer = checkNotNull(transactionSerializer) { "Transaction serializer is not set" }
        mutableTransaction.requireSupportedInputs()
        val previous = previousTransactionsOf(mutableTransaction)
        val signatures =
            appAccess.signWithLedgerApp(account, query) { transport -> sign(transport, mutableTransaction, previous) }
        mutableTransaction.applySignatures(signatures)
        return SignedTransactionData(
            serializedTx = serializer.serialize(mutableTransaction.build(serializer)).toHexString(),
            signatures = signatures.map { it.toHexString() },
        )
    }

    private suspend fun sign(
        transport: LedgerTransport,
        transaction: MutableTransaction,
        previous: Map<String, ByteArray>,
    ): List<ByteArray> {
        if (!app.usesPsbt) {
            return BtchipLegacyClient(transport).signTransaction(transaction.toLegacyTransaction(app, spec, previous))
        }
        val client = BitcoinAppClient(transport)
        val account = LedgerBtcAccount(client.getMasterFingerprint(), spec)
        val psbt = transaction.toPsbt(account, previous)
        return client.signPsbt(psbt, account.policy(hardwarePublicKey.key.value)).inInputOrder(psbt)
    }

    private fun previousTransactionsOf(transaction: MutableTransaction): Map<String, ByteArray> =
        previousTransactions.rawTransactions(
            transaction.inputsToSign.filter { it.needsPreviousTransaction }.map { it.displayTxid }
        ) { txid -> LedgerSigningFailedException("Missing previous transaction $txid") }
            .mapValues { (_, rawHex) -> rawHex.hexToByteArray() }

    override suspend fun sigScriptEcdsaData(
        transaction: Transaction,
        inputsToSign: List<InputToSign>,
        outputs: List<TransactionOutput>,
        index: Int,
    ): List<ByteArray> = throw UnsupportedOperationException("Use signFullTransaction")

    override suspend fun sigScriptSchnorrData(
        transaction: Transaction,
        inputsToSign: List<InputToSign>,
        outputs: List<TransactionOutput>,
        index: Int,
    ): List<ByteArray> = throw UnsupportedOperationException("Use signFullTransaction")
}
