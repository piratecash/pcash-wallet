package cash.p.terminal.trezor.domain

import cash.p.terminal.trezorkit.client.TrezorFeatures
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import timber.log.Timber

/**
 * Keeps the stored firmware version current so a stored-only reader such as
 * [TrezorZcashAdmissionPolicy] never sees a stale value. Overwrites unconditionally rather than
 * healing only an empty value, since firmware — unlike [AccountType.TrezorDevice.walletPublicKey] —
 * can genuinely change between sessions.
 */
class TrezorFirmwareVersionRecorder(
    private val accountManager: IAccountManager,
    private val identityValidator: TrezorAccountIdentityValidator,
) {
    suspend fun record(accountId: String, features: TrezorFeatures) {
        val account = accountManager.account(accountId)
        val accountType = account?.type as? AccountType.TrezorDevice
        if (accountType == null) {
            Timber.w("TrezorFirmwareVersionRecorder: account $accountId is not a Trezor device")
            return
        }
        if (!identityValidator.matchesDevice(accountType.deviceId, features.deviceId)) {
            Timber.w("TrezorFirmwareVersionRecorder: live device does not match account $accountId")
            return
        }
        if (accountType.firmwareVersion == features.firmwareVersion) return
        accountManager.update(account.copy(type = accountType.copy(firmwareVersion = features.firmwareVersion)))
    }
}
