package cash.p.terminal.ledger.bitcoin

import cash.p.terminal.ledger.domain.LedgerSigningFailedException
import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.bitcoin.KeyDerivation
import cash.p.terminal.ledgerkit.bitcoin.LegacyInput
import cash.p.terminal.ledgerkit.bitcoin.LegacyTransaction
import cash.p.terminal.ledgerkit.bitcoin.PartialSignature
import cash.p.terminal.ledgerkit.bitcoin.Psbt
import cash.p.terminal.ledgerkit.bitcoin.PsbtInput
import cash.p.terminal.ledgerkit.bitcoin.PsbtOutput
import cash.p.terminal.ledgerkit.bitcoin.TxOutput
import cash.p.terminal.ledgerkit.bitcoin.WalletPolicy
import cash.p.terminal.wallet.hardware.HardwareAddressPosition
import cash.p.terminal.wallet.hardware.HardwareKeySpec
import io.horizontalsystems.bitcoincore.extensions.toReversedHex
import io.horizontalsystems.bitcoincore.models.PublicKey
import io.horizontalsystems.bitcoincore.models.TransactionOutput
import io.horizontalsystems.bitcoincore.storage.InputToSign
import io.horizontalsystems.bitcoincore.transactions.builder.MutableTransaction
import io.horizontalsystems.bitcoincore.transactions.scripts.OpCodes
import io.horizontalsystems.bitcoincore.transactions.scripts.ScriptType
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import io.horizontalsystems.hdwalletkit.HDExtendedKeyVersion
import io.horizontalsystems.hdwalletkit.HDWallet

/** The signing account as the device knows it: its master fingerprint and account key spec. */
internal class LedgerBtcAccount(val masterFingerprint: ByteArray, val spec: HardwareKeySpec) {
    private val isTaproot: Boolean get() = spec.expectedPurpose == HDWallet.Purpose.BIP86

    /** The Bitcoin app's default policy; [accountKey] is the stored account key in any version. */
    fun policy(accountKey: String): WalletPolicy = WalletPolicy.singleSig(
        type = checkNotNull(spec.expectedPurpose).addressType,
        masterFingerprint = masterFingerprint,
        accountPath = DerivationPath.parse(spec.derivationPath),
        accountXpub = HDExtendedKey(accountKey).key.serializePublic(HDExtendedKeyVersion.xpub.value),
    )

    fun bip32Derivation(key: PublicKey): KeyDerivation? = derivation(key).takeUnless { isTaproot }

    // The kit types a nested-segwit change output as plain P2SH, so the account decides.
    fun changeRedeemScript(key: PublicKey): ByteArray? =
        OpCodes.scriptWPKH(key.publicKeyHash).takeIf { spec.expectedPurpose == HDWallet.Purpose.BIP49 }

    fun tapBip32Derivation(key: PublicKey): KeyDerivation? = derivation(key).takeIf { isTaproot }

    private fun derivation(key: PublicKey): KeyDerivation {
        val publicKey = if (isTaproot) key.publicKey.copyOfRange(1, key.publicKey.size) else key.publicKey
        return KeyDerivation(publicKey, masterFingerprint, ledgerAddressPath(spec.derivationPath, key.position))
    }
}

internal val PublicKey.position: HardwareAddressPosition
    get() = HardwareAddressPosition(change = !external, index = index)

internal val InputToSign.displayTxid: String get() = input.previousOutputTxHash.toReversedHex()

/** Taproot inputs are verified through their witness UTXO alone; every other input needs its previous transaction. */
internal val InputToSign.needsPreviousTransaction: Boolean get() = previousOutput.scriptType != ScriptType.P2TR

/** [previousTransactions] maps display txids to raw previous transactions. */
internal fun MutableTransaction.toPsbt(
    account: LedgerBtcAccount,
    previousTransactions: Map<String, ByteArray>,
): Psbt = Psbt(
    txVersion = transaction.version,
    lockTime = transaction.lockTime,
    inputs = inputsToSign.map { inputToSign ->
        val previousOutput = inputToSign.previousOutput
        PsbtInput(
            previousTxid = inputToSign.input.previousOutputTxHash,
            outputIndex = inputToSign.input.previousOutputIndex,
            sequence = inputToSign.input.sequence,
            nonWitnessUtxo = previousTransactions[inputToSign.displayTxid],
            witnessUtxo = TxOutput(previousOutput.value, previousOutput.lockingScript)
                .takeIf { previousOutput.scriptType.isWitness },
            redeemScript = inputToSign.previousOutputPublicKey.nestedSegwitRedeemScript(previousOutput.scriptType),
            bip32Derivation = account.bip32Derivation(inputToSign.previousOutputPublicKey),
            tapBip32Derivation = account.tapBip32Derivation(inputToSign.previousOutputPublicKey),
        )
    },
    outputs = outputs.map { output ->
        val changeKey = changeKeyOf(output)
        PsbtOutput(
            amount = output.value,
            script = output.lockingScript,
            redeemScript = changeKey?.let(account::changeRedeemScript),
            bip32Derivation = changeKey?.let(account::bip32Derivation),
            tapBip32Derivation = changeKey?.let(account::tapBip32Derivation),
        )
    },
)

internal fun MutableTransaction.toLegacyTransaction(
    app: LedgerBtcApp,
    spec: HardwareKeySpec,
    previousTransactions: Map<String, ByteArray>,
): LegacyTransaction = LegacyTransaction(
    version = transaction.version,
    inputs = inputsToSign.map { inputToSign ->
        LegacyInput(
            previousTransaction = previousTransactions.getValue(inputToSign.displayTxid),
            outputIndex = inputToSign.input.previousOutputIndex.toInt(),
            sequence = inputToSign.input.sequence,
            path = ledgerAddressPath(spec.derivationPath, inputToSign.previousOutputPublicKey.position),
            redeemScript = inputToSign.previousOutputPublicKey
                .nestedSegwitRedeemScript(inputToSign.previousOutput.scriptType),
        )
    },
    outputs = outputs.map { TxOutput(it.value, it.lockingScript) },
    lockTime = transaction.lockTime,
    mode = app.signingMode(checkNotNull(spec.expectedPurpose)),
    changePath = outputs.firstNotNullOfOrNull(::changeKeyOf)
        ?.let { ledgerAddressPath(spec.derivationPath, it.position) },
)

/** One signature per PSBT input, in input order, each made by the key the input was derived for. */
internal fun List<PartialSignature>.inInputOrder(psbt: Psbt): List<ByteArray> = psbt.inputs.mapIndexed { index, input ->
    val signature = singleOrNull { it.inputIndex == index && it.publicKey.contentEquals(input.signingKey) }
        ?: throw LedgerSigningFailedException("No signature from the input key for input $index")
    signature.signature
}

// A taproot key-path signature comes with the tweaked output key, which is the witness program.
private val PsbtInput.signingKey: ByteArray?
    get() = bip32Derivation?.publicKey
        ?: witnessUtxo?.script?.copyOfRange(TAPROOT_PROGRAM_START, TAPROOT_SCRIPT_SIZE)

/** Fails before the device is asked to sign inputs the wallet cannot place a signature into. */
internal fun MutableTransaction.requireSupportedInputs() {
    inputsToSign.firstOrNull { it.previousOutput.scriptType !in SUPPORTED_INPUTS }?.let {
        throw LedgerSigningFailedException("Ledger cannot sign ${it.previousOutput.scriptType} inputs")
    }
}

/** Places the signatures the way the kit's own input signer does, so the kit sees a regular signed transaction. */
internal fun MutableTransaction.applySignatures(signatures: List<ByteArray>) {
    check(signatures.size == inputsToSign.size) { "Expected ${inputsToSign.size} signatures, got ${signatures.size}" }
    inputsToSign.zip(signatures).forEach { (inputToSign, signature) ->
        val publicKey = inputToSign.previousOutputPublicKey
        val input = inputToSign.input
        when (val scriptType = inputToSign.previousOutput.scriptType) {
            ScriptType.P2PKH -> input.sigScript = OpCodes.push(signature) + OpCodes.push(publicKey.publicKey)
            ScriptType.P2WPKH -> input.witness = listOf(signature, publicKey.publicKey)
            ScriptType.P2WPKHSH -> {
                input.sigScript = OpCodes.push(checkNotNull(publicKey.nestedSegwitRedeemScript(scriptType)))
                input.witness = listOf(signature, publicKey.publicKey)
            }
            ScriptType.P2TR -> input.witness = listOf(signature)
            else -> error("Unsupported input $scriptType")
        }
    }
    transaction.segwit = inputsToSign.any { it.previousOutput.scriptType.isWitness }
}

private fun MutableTransaction.changeKeyOf(output: TransactionOutput): PublicKey? {
    val changeAddress = changeAddress?.stringValue ?: return null
    return changePublicKey?.takeIf { output.address == changeAddress }
}

private fun PublicKey.nestedSegwitRedeemScript(scriptType: ScriptType): ByteArray? =
    OpCodes.scriptWPKH(publicKeyHash).takeIf { scriptType == ScriptType.P2WPKHSH }

private const val TAPROOT_PROGRAM_START = 2
private const val TAPROOT_SCRIPT_SIZE = 34

private val SUPPORTED_INPUTS = setOf(ScriptType.P2PKH, ScriptType.P2WPKH, ScriptType.P2WPKHSH, ScriptType.P2TR)
