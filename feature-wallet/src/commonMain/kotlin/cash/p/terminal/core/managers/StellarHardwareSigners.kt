package cash.p.terminal.core.managers

import cash.p.terminal.wallet.entities.HardwarePublicKey
import io.horizontalsystems.stellarkit.Signer

/** Tangem and Trezor Stellar signers, which exist only on platforms with hardware wallet support. */
interface StellarHardwareSigners {
    fun cardSigner(hardwarePublicKey: HardwarePublicKey): Signer

    fun trezorSigner(publicKey: ByteArray, derivationPath: String, networkPassphrase: String): Signer
}
