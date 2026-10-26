package cash.p.terminal.core.managers

import cash.p.terminal.tangem.signer.HardwareWalletTronSigner
import cash.p.terminal.trezor.signer.TrezorTronSigner
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.wallet.entities.HardwarePublicKey
import io.horizontalsystems.tronkit.transaction.Signer

class AndroidTronHardwareSigners(private val trezorClient: ITrezorClient) : TronHardwareSigners {

    override fun cardSigner(hardwarePublicKey: HardwarePublicKey, expectedPublicKeyBytes: ByteArray): Signer =
        HardwareWalletTronSigner(
            hardwarePublicKey = hardwarePublicKey,
            expectedPublicKeyBytes = expectedPublicKeyBytes
        )

    override fun trezorSigner(expectedAddressBase58: String, derivationPath: String): Signer =
        TrezorTronSigner(
            expectedAddressBase58 = expectedAddressBase58,
            derivationPath = derivationPath,
            trezorClient = trezorClient
        )
}
