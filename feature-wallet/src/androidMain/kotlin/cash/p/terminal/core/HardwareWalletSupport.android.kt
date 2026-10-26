package cash.p.terminal.core

import cash.p.terminal.tangem.domain.isHardwareWalletUserCancelled
import cash.p.terminal.trezor.client.TrezorPublicKeySpecs
import cash.p.terminal.trezor.domain.TrezorCancelledException
import cash.p.terminal.trezor.domain.TrezorModelSupport
import cash.p.terminal.trezor.domain.TrezorMoneroAdmissionPolicy
import cash.p.terminal.trezor.domain.TrezorZcashAdmissionPolicy
import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType

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
}
