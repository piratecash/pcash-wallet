package cash.p.terminal.trezor.domain

import cash.p.terminal.trezor.domain.model.TrezorModel
import cash.p.terminal.trezorkit.client.TrezorFeatures
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType

enum class TrezorMoneroAdmissionFailure {
    UnsupportedModel,
    DeviceNotInitialized,
    CapabilityMissing,
    FirmwareUnsupported,
}

object TrezorMoneroAdmissionPolicy {
    private val minimumFirmware = FirmwareVersion(2, 4, 3)

    fun supportsStoredToken(
        modelId: String,
        blockchainType: BlockchainType,
        tokenType: TokenType,
    ): Boolean =
        blockchainType == BlockchainType.Monero &&
            tokenType == TokenType.Native &&
            TrezorModelSupport.isSupported(
                TrezorModel.fromInternalModel(modelId),
                BlockchainType.Monero,
            )

    fun liveFailure(features: TrezorFeatures): TrezorMoneroAdmissionFailure? {
        val firmware = FirmwareVersion.parse(features.firmwareVersion)
        return when {
            TrezorModel.fromInternalModel(features.internalModel) != TrezorModel.Safe5 ->
                TrezorMoneroAdmissionFailure.UnsupportedModel
            !features.initialized ->
                TrezorMoneroAdmissionFailure.DeviceNotInitialized
            !features.supportsMonero ->
                TrezorMoneroAdmissionFailure.CapabilityMissing
            firmware == null || firmware < minimumFirmware ->
                TrezorMoneroAdmissionFailure.FirmwareUnsupported
            else -> null
        }
    }
}
