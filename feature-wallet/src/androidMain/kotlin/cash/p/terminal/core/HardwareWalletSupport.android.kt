package cash.p.terminal.core

import cash.p.terminal.core.managers.EvmMessageSigning
import cash.p.terminal.tangem.domain.isHardwareWalletUserCancelled
import cash.p.terminal.tangem.domain.isTangemSdkError
import cash.p.terminal.tangem.signer.HardwareWalletEvmSigner
import cash.p.terminal.trezor.client.TrezorPublicKeySpecs
import cash.p.terminal.trezor.domain.TrezorCancelledException
import cash.p.terminal.trezor.domain.TrezorModelSupport
import cash.p.terminal.trezor.domain.TrezorMoneroAdmissionPolicy
import cash.p.terminal.trezor.domain.TrezorSigningException
import cash.p.terminal.trezor.domain.TrezorZcashAdmissionPolicy
import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.trezor.signer.TrezorEvmSigner
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.signer.Signer

internal actual object HardwareWalletSupport {
    actual fun supports(device: AccountType.TrezorDevice, blockchainType: BlockchainType): Boolean =
        TrezorModelSupport.isSupported(TrezorModel.fromInternalModel(device.model), blockchainType)

    actual fun supports(
        device: AccountType.TrezorDevice,
        blockchainType: BlockchainType,
        tokenType: TokenType,
    ): Boolean =
        TrezorMoneroAdmissionPolicy.supportsStoredToken(device.model, blockchainType, tokenType) ||
            TrezorPublicKeySpecs.supports(TrezorModel.fromInternalModel(device.model), blockchainType, tokenType)

    actual fun supportsZcashUnifiedAddress(device: AccountType.TrezorDevice): Boolean =
        TrezorZcashAdmissionPolicy.supportsUnifiedAddress(device.model, device.firmwareVersion)

    actual val zcashUnifiedAddressFirmware: String = TrezorZcashAdmissionPolicy.MIN_UNIFIED_FIRMWARE_VERSION

    actual fun trezorDisplayName(model: String?): String? = TrezorModel.fromInternalModel(model)?.displayName

    actual fun isUserCancellation(error: Throwable): Boolean =
        error is TrezorCancelledException || error.isHardwareWalletUserCancelled()

    actual fun isReportedBySdk(error: Throwable): Boolean = error.isTangemSdkError()

    actual fun isTrezorCancellation(error: Throwable): Boolean = error is TrezorCancelledException

    actual fun isSigningFailure(error: Throwable): Boolean = error is TrezorSigningException

    actual fun supportsTypedData(device: AccountType.TrezorDevice): Boolean =
        TrezorModelSupport.supportsTypedData(TrezorModel.fromInternalModel(device.model), device.firmwareVersion)

    actual suspend fun signTypedDataOnDevice(signer: Signer, rawJson: String): ByteArray? =
        if (signer is HardwareWalletEvmSigner || signer is TrezorEvmSigner) {
            EvmMessageSigning.signTypedData(signer, rawJson)
        } else {
            null
        }
}
