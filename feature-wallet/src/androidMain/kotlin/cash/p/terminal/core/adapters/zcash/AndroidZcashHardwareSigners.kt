package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.UnsupportedException
import cash.p.terminal.trezor.domain.TrezorAccountIdentityValidator
import cash.p.terminal.trezor.domain.TrezorFirmwareVersionRecorder
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Wallet

class AndroidZcashHardwareSigners(
    private val trezorClient: ITrezorClient,
    private val identityValidator: TrezorAccountIdentityValidator,
    private val firmwareVersionRecorder: TrezorFirmwareVersionRecorder,
) : ZcashHardwareSigners {

    override fun trezorSigner(wallet: Wallet, device: AccountType.TrezorDevice): ZcashTransactionSigner {
        val hardwareKey = wallet.hardwarePublicKey
            ?: throw UnsupportedException("Trezor does not have a key for Zcash")
        return TrezorZcashSigner(
            accountId = wallet.account.id,
            deviceId = device.deviceId,
            derivationPath = hardwareKey.derivationPath,
            trezorClient = trezorClient,
            identityValidator = identityValidator,
            firmwareVersionRecorder = firmwareVersionRecorder,
        )
    }
}
