package cash.p.terminal.core.managers

import cash.p.terminal.wallet.entities.HardwarePublicKey
import com.solana.core.Account
import com.solana.core.PublicKey

/** Tangem and Trezor Solana signers, which exist only on platforms with hardware wallet support. */
interface SolanaHardwareSigners {
    fun cardAccount(publicKey: PublicKey, hardwarePublicKey: HardwarePublicKey): Account

    fun trezorAccount(publicKey: PublicKey, hardwarePublicKey: HardwarePublicKey): Account
}
