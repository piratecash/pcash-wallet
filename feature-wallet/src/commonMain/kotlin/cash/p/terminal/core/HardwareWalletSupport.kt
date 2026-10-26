package cash.p.terminal.core

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType

/** Hardware-wallet rules owned by the platform; a platform without hardware supports nothing. */
internal expect object HardwareWalletSupport {
    fun supports(device: AccountType.TrezorDevice, blockchainType: BlockchainType): Boolean

    fun supports(device: AccountType.TrezorDevice, blockchainType: BlockchainType, tokenType: TokenType): Boolean

    fun supportsZcashUnifiedAddress(device: AccountType.TrezorDevice): Boolean

    /** The firmware a Trezor needs for unified Zcash addresses, as shown to the user. */
    val zcashUnifiedAddressFirmware: String

    /** Null for a model this platform does not know. */
    fun trezorDisplayName(model: String?): String?

    /** A cancellation the user made on a Trezor or Tangem prompt. */
    fun isUserCancellation(error: Throwable): Boolean
}
