package cash.p.terminal.tangem.signer

import cash.p.terminal.tangem.domain.canonicalise
import cash.p.terminal.tangem.domain.usecase.SignHashesTransactionUseCase
import cash.p.terminal.tangem.domain.usecase.SignMultipleHashesUseCase
import cash.p.terminal.wallet.entities.HardwarePublicKey
import co.touchlab.kermit.Logger
import com.tangem.common.CompletionResult
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.operations.sign.SignData
import com.tangem.operations.sign.SignResponse
import io.horizontalsystems.bitcoincore.messages.IStandardMessageSigner
import io.horizontalsystems.bitcoincore.messages.StandardMessage
import io.horizontalsystems.bitcoincore.models.PublicKey
import io.horizontalsystems.bitcoincore.models.Transaction
import io.horizontalsystems.bitcoincore.models.TransactionOutput
import io.horizontalsystems.bitcoincore.network.Network
import io.horizontalsystems.bitcoincore.serializers.BaseTransactionSerializer
import io.horizontalsystems.bitcoincore.storage.InputToSign
import io.horizontalsystems.bitcoincore.transactions.builder.IEcdsaInputBatchSigner
import io.horizontalsystems.bitcoincore.transactions.builder.IInputSigner
import io.horizontalsystems.bitcoincore.transactions.builder.MutableTransaction
import io.horizontalsystems.bitcoincore.transactions.model.DataToSign
import io.horizontalsystems.bitcoincore.transactions.scripts.ScriptType
import io.horizontalsystems.bitcoincore.utils.Utils
import io.horizontalsystems.hdwalletkit.ECDSASignature
import io.horizontalsystems.hdwalletkit.ECKey
import org.koin.java.KoinJavaComponent.inject

class HardwareWalletEcdaSigner(
    private val hardwarePublicKey: HardwarePublicKey
) : IInputSigner, IEcdsaInputBatchSigner, IStandardMessageSigner {
    private val logger = Logger.withTag("HardwareWalletSigner")

    private val signHashesTransactionUseCase: SignHashesTransactionUseCase by inject(
        SignHashesTransactionUseCase::class.java
    )

    private val signMultipleHashesUseCase: SignMultipleHashesUseCase by inject(
        SignMultipleHashesUseCase::class.java
    )

    private var transactionSerializer: BaseTransactionSerializer? = null
    private var network: Network? = null

    override fun setTransactionSerializer(serializer: BaseTransactionSerializer) {
        this.transactionSerializer = serializer
    }

    override fun setNetwork(network: Network) {
        this.network = network
    }

    override suspend fun sigScriptEcdsaData(
        transaction: Transaction,
        inputsToSign: List<InputToSign>,
        outputs: List<TransactionOutput>,
        index: Int
    ): List<ByteArray> {
        val transactionSerializer =
            requireNotNull(transactionSerializer) { "Transaction serializer must be set before signing" }
        val network = requireNotNull(network) { "Network must be set before signing" }

        val input = inputsToSign[index]
        val prevOutput = input.previousOutput
        val publicKey = input.previousOutputPublicKey

        val txContent = transactionSerializer.serializeForSignature(
            transaction = transaction,
            inputsToSign = inputsToSign,
            outputs = outputs,
            inputIndex = index,
            isWitness = prevOutput.scriptType.isWitness || network.sigHashForked
        ) + byteArrayOf(network.sigHashValue, 0, 0, 0)

        val finalSignature = signHash(Utils.doubleDigest(txContent), publicKey).encodeToDER() + network.sigHashValue
        return when (prevOutput.scriptType) {
            ScriptType.P2PK -> listOf(finalSignature)
            else -> listOf(finalSignature, publicKey.publicKey)
        }
    }

    override suspend fun signStandardMessage(publicKey: PublicKey, scriptType: ScriptType, message: String): ByteArray {
        val hash = ECKey.messageHash(message)
        val compactSignature = signHash(hash, publicKey).toCompactSignature()
        return StandardMessage.signature(hash, compactSignature, publicKey.publicKey, scriptType)
    }

    private suspend fun signHash(hash: ByteArray, publicKey: PublicKey): ECDSASignature {
        val derivationPath = derivationPath(publicKey)
        logger.d { "signHash ${derivationPath.rawPath}" }
        val signResponse: CompletionResult<SignResponse> = signHashesTransactionUseCase(
            hashes = arrayOf(hash),
            walletPublicKey = hardwarePublicKey.publicKey,
            derivationPath = derivationPath
        )
        return when (signResponse) {
            is CompletionResult.Success -> {
                val rawSignatureFromTangem = signResponse.data.signatures.firstOrNull()
                    ?: throw Error("No signature returned from signing operation")
                rawSignatureFromTangem.toCanonicalSignature()
            }

            is CompletionResult.Failure -> throw signResponse.error
        }
    }

    private fun ByteArray.toCanonicalSignature(): ECDSASignature =
        ECDSASignature.fromCompact(copyOfRange(0, 64)).canonicalise()

    private fun derivationPath(publicKey: PublicKey): DerivationPath {
        val changeSegment = if (publicKey.external) "0" else "1"
        return DerivationPath("${hardwarePublicKey.derivationPath}/$changeSegment/${publicKey.index}")
    }

    override suspend fun prepareDataForEcdsaSigning(mutableTransaction: MutableTransaction): List<DataToSign> {
        logger.d { "prepareDataForEcdsaSigning ${mutableTransaction.inputsToSign.size}" }
        val transactionSerializer =
            requireNotNull(transactionSerializer) { "Transaction serializer must be set before signing" }
        val network = requireNotNull(network) { "Network must be set before signing" }

        return buildList {
            mutableTransaction.inputsToSign.forEachIndexed { index, input ->
                val prevOutput = input.previousOutput
                val publicKey = input.previousOutputPublicKey

                val txContent = transactionSerializer.serializeForSignature(
                    transaction = mutableTransaction.transaction,
                    inputsToSign = mutableTransaction.inputsToSign,
                    outputs = mutableTransaction.outputs,
                    inputIndex = index,
                    isWitness = prevOutput.scriptType.isWitness || network.sigHashForked
                ) + byteArrayOf(network.sigHashValue, 0, 0, 0)

                add(
                    DataToSign(
                        publicKey = publicKey,
                        scriptType = prevOutput.scriptType,
                        data = Utils.doubleDigest(txContent)
                    )
                )
            }
        }
    }

    override suspend fun sigScriptEcdsaData(data: List<DataToSign>): List<List<ByteArray>> {
        logger.d { "sigScriptEcdsaData with MultipleSignCommand: ${data.size}" }
        val network = requireNotNull(network) { "Network must be set before signing" }

        if (data.isEmpty()) {
            logger.w { "No data to sign" }
            return emptyList()
        }

        // Prepare SignData for each input with its derivation path
        val signDataList = data.map { dataToSign ->
            SignData(
                derivationPath = derivationPath(dataToSign.publicKey),
                hash = dataToSign.data,
                publicKey = dataToSign.publicKey.publicKey
            )
        }

        // Sign all hashes with different derivation paths in a single card tap
        val signResponse = signMultipleHashesUseCase(
            dataToSign = signDataList,
            walletPublicKey = hardwarePublicKey.publicKey
        )

        return when (signResponse) {
            is CompletionResult.Success -> {
                val responses = signResponse.data.asReversed()

                require(responses.size == data.size) {
                    "Unexpected signatures count: ${responses.size} != ${data.size}"
                }

                data.zip(responses).map { (dataToSign, response) ->
                    val finalSignature = response.signature.toCanonicalSignature().encodeToDER() + network.sigHashValue

                    when (dataToSign.scriptType) {
                        ScriptType.P2PK -> listOf(finalSignature)
                        else -> listOf(finalSignature, dataToSign.publicKey.publicKey)
                    }
                }
            }

            is CompletionResult.Failure -> throw signResponse.error
        }
    }
}
