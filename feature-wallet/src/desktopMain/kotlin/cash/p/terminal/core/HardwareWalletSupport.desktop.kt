package cash.p.terminal.core

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType

internal actual object HardwareWalletSupport {
    actual fun supports(device: AccountType.TrezorDevice, blockchainType: BlockchainType): Boolean = false

    actual fun supports(
        device: AccountType.TrezorDevice,
        blockchainType: BlockchainType,
        tokenType: TokenType,
    ): Boolean = false
}
