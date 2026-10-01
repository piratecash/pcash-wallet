package cash.p.terminal.trezor.domain

import cash.p.terminal.trezor.domain.model.TrezorModel

/**
 * One stored predicate for the Zcash unified-address form: can the device the app last recorded
 * sign a unified address, without a live connection. Fails closed — an unresolvable model, an
 * unparseable or empty firmware version, or a Trezor One all answer `false`.
 */
object TrezorZcashAdmissionPolicy {
    /** Callers outside this module show it to the user, so the minimum is published as text. */
    const val MIN_UNIFIED_FIRMWARE_VERSION = "2.5.3"

    private val minimumUnifiedFirmware =
        checkNotNull(FirmwareVersion.parse(MIN_UNIFIED_FIRMWARE_VERSION))

    fun supportsUnifiedAddress(modelId: String, firmwareVersion: String): Boolean {
        val model = TrezorModel.fromInternalModel(modelId) ?: return false
        if (model == TrezorModel.One) return false
        val firmware = FirmwareVersion.parse(firmwareVersion) ?: return false
        return firmware >= minimumUnifiedFirmware
    }
}
