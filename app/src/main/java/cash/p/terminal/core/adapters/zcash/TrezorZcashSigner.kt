package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.hexToByteArray
import cash.p.terminal.trezor.client.TrezorDerivationPath
import cash.p.terminal.trezor.domain.TrezorAccountIdentityValidator
import cash.p.terminal.trezor.domain.TrezorFirmwareVersionRecorder
import cash.p.terminal.trezor.domain.TrezorSigningException
import cash.p.terminal.trezor.domain.TrezorZcashAdmissionPolicy
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.trezorkit.client.TrezorBtcInput
import cash.p.terminal.trezorkit.client.TrezorBtcOutput
import cash.p.terminal.trezorkit.client.TrezorBtcSignTx
import cash.p.terminal.trezorkit.client.TrezorFeatures
import cash.p.terminal.trezorkit.client.TrezorInputScriptType
import cash.p.terminal.trezorkit.client.TrezorOutputScriptType
import cash.p.zcash.PreparedTransaction
import cash.p.zcash.TransparentSigningInput
import cash.p.zcash.TransparentSigningOutput
import cash.p.zcash.TransparentSigningRequest
import cash.p.zcash.ZcashAddressKind
import cash.p.zcash.ZcashWallet

/** Refuses a firmware too old for the recipient's unified address, named so callers can tell it apart. */
class TrezorZcashUnsupportedAddressException : TrezorSigningException(
    "Connected Trezor firmware does not support unified addresses"
)

/**
 * Signs a Zcash transaction's transparent bundle on a Trezor device. The account has no
 * spending key of its own - the PCZT is built with `hardwareSigning = true`, reviewed here as
 * a [TransparentSigningRequest], and the device's ECDSA signatures are applied back onto it.
 */
class TrezorZcashSigner(
    private val accountId: String,
    private val deviceId: String,
    private val derivationPath: String,
    private val trezorClient: ITrezorClient,
    private val identityValidator: TrezorAccountIdentityValidator,
    private val firmwareVersionRecorder: TrezorFirmwareVersionRecorder,
) : ZcashTransactionSigner {

    override suspend fun sign(
        wallet: ZcashWallet,
        account: Int,
        transaction: PreparedTransaction,
    ): PreparedTransaction {
        val request = wallet.transparentSigningRequest(transaction)
        requireSignableBundle(request)
        val signTx = request.toTrezorSignTx()
        val hasUnifiedRecipient = request.outputs.any {
            !it.isChange && zcashAddressKind(it.address) == ZcashAddressKind.UNIFIED
        }
        val result = trezorClient.connect {
            val features = getFeatures()
            requireExpectedDevice(features)
            firmwareVersionRecorder.record(accountId, features)
            if (hasUnifiedRecipient) requireUnifiedAddressSupported(features)
            signBitcoin(ZCASH_COIN_NAME, signTx, emptyMap())
        }
        val indices = request.inputs.map { it.index }.toIntArray()
        val sigs = result.signatures.toTypedArray()
        return wallet.applyTransparentSignatures(transaction, indices, sigs)
    }

    private fun requireSignableBundle(request: TransparentSigningRequest) {
        if (request.txVersion != ZIP244_TX_VERSION) {
            throw TrezorSigningException("Trezor can only sign a v5 (ZIP-244) Zcash transaction")
        }
        val shielded = request.shielded
        if (shielded.saplingSpends != 0 || shielded.saplingOutputs != 0 || shielded.orchardActions != 0) {
            throw TrezorSigningException("Trezor cannot sign a transaction with shielded components")
        }
    }

    private fun requireExpectedDevice(features: TrezorFeatures) {
        if (!identityValidator.matchesDevice(deviceId, features.deviceId)) {
            throw TrezorSigningException("Connected Trezor does not match the account")
        }
    }

    private fun requireUnifiedAddressSupported(features: TrezorFeatures) {
        val supported = TrezorZcashAdmissionPolicy.supportsUnifiedAddress(
            features.internalModel.orEmpty(),
            features.firmwareVersion,
        )
        if (!supported) throw TrezorZcashUnsupportedAddressException()
    }

    private fun TransparentSigningRequest.toTrezorSignTx() = TrezorBtcSignTx(
        version = txVersion,
        lockTime = lockTime,
        inputs = inputs.map { it.toTrezorInput() },
        outputs = outputs.map { it.toTrezorOutput() },
        expiry = expiryHeight.toLong(),
        versionGroupId = versionGroupId,
        branchId = consensusBranchId,
    )

    private fun TransparentSigningInput.toTrezorInput() = TrezorBtcInput(
        addressN = addressPath(scope, dindex),
        // Already hex in display order (the SDK reverses the PCZT's internal bytes) - decode, don't reverse.
        prevHash = prevTxid.hexToByteArray(),
        prevIndex = prevIndex,
        amount = value,
        scriptType = TrezorInputScriptType.SPENDADDRESS,
        sequence = sequence,
    )

    private fun TransparentSigningOutput.toTrezorOutput(): TrezorBtcOutput = if (isChange) {
        TrezorBtcOutput.Change(
            addressN = addressPath(
                checkNotNull(scope) { "Change output is missing its derivation scope" },
                checkNotNull(dindex) { "Change output is missing its derivation index" },
            ),
            amount = value,
            scriptType = TrezorOutputScriptType.PAYTOADDRESS,
        )
    } else {
        TrezorBtcOutput.Address(
            address = address,
            amount = value,
            scriptType = TrezorOutputScriptType.PAYTOADDRESS,
        )
    }

    private fun addressPath(scope: Int, dindex: Int): List<Int> =
        TrezorDerivationPath.parse(derivationPath) + scope + dindex

    private companion object {
        private const val ZIP244_TX_VERSION = 5
        private const val ZCASH_COIN_NAME = "Zcash"
    }
}
