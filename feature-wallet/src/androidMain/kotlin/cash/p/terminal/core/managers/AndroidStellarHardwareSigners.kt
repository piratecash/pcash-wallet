package cash.p.terminal.core.managers

import cash.p.terminal.tangem.signer.HardwareWalletStellarSigner
import cash.p.terminal.trezor.signer.TrezorStellarSigner
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.wallet.entities.HardwarePublicKey
import io.horizontalsystems.stellarkit.Signer

class AndroidStellarHardwareSigners(private val trezorClient: ITrezorClient) : StellarHardwareSigners {

    override fun cardSigner(hardwarePublicKey: HardwarePublicKey): Signer =
        HardwareWalletStellarSigner(hardwarePublicKey = hardwarePublicKey)

    override fun trezorSigner(publicKey: ByteArray, derivationPath: String, networkPassphrase: String): Signer =
        TrezorStellarSigner(
            publicKey = publicKey,
            derivationPath = derivationPath,
            networkPassphrase = networkPassphrase,
            trezorClient = trezorClient
        )
}
