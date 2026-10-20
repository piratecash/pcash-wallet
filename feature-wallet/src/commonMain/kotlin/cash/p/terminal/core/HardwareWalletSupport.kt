package cash.p.terminal.core

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType

/** Hardware-wallet rules owned by the platform; a platform without hardware supports nothing. */
internal expect object HardwareWalletSupport {
    fun supports(device: AccountType.TrezorDevice, blockchainType: BlockchainType): Boolean

    fun supports(device: AccountType.TrezorDevice, blockchainType: BlockchainType, tokenType: TokenType): Boolean
}
