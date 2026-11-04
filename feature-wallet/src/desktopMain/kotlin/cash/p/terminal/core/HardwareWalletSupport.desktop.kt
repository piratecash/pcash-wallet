package cash.p.terminal.core

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.signer.Signer

internal actual object HardwareWalletSupport {
    actual fun supports(device: AccountType.TrezorDevice, blockchainType: BlockchainType): Boolean = false

    actual fun supports(
        device: AccountType.TrezorDevice,
        blockchainType: BlockchainType,
        tokenType: TokenType,
    ): Boolean = false

    actual fun supportsZcashUnifiedAddress(device: AccountType.TrezorDevice): Boolean = false

    actual val zcashUnifiedAddressFirmware: String = ""

    actual fun trezorDisplayName(model: String?): String? = null

    actual fun isUserCancellation(error: Throwable): Boolean = false

    actual fun isReportedBySdk(error: Throwable): Boolean = false

    actual fun isTrezorCancellation(error: Throwable): Boolean = false

    actual fun isSigningFailure(error: Throwable): Boolean = false

    actual fun supportsTypedData(device: AccountType.TrezorDevice): Boolean = false

    actual suspend fun signTypedDataOnDevice(signer: Signer, rawJson: String): ByteArray? = null
}
