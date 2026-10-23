package cash.p.terminal.core.managers

import cash.p.terminal.wallet.entities.HardwarePublicKey
import io.horizontalsystems.tronkit.transaction.Signer

/** Tangem and Trezor Tron signers, which exist only on platforms with hardware wallet support. */
interface TronHardwareSigners {
    fun cardSigner(hardwarePublicKey: HardwarePublicKey, expectedPublicKeyBytes: ByteArray): Signer

    fun trezorSigner(expectedAddressBase58: String, derivationPath: String): Signer
}
