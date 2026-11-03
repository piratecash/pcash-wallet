package cash.p.terminal.core

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SwapSettings_Error_InvalidAddress
import cash.p.terminal.resources.platform_not_supported_yet
import cash.p.terminal.strings.helpers.TranslatableString
import io.horizontalsystems.core.Translator
import io.horizontalsystems.ethereumkit.api.jsonrpc.JsonRpc
import io.horizontalsystems.ethereumkit.core.AddressValidator
import org.jetbrains.compose.resources.StringResource

class UnsupportedException(override val message: String?) : Exception()
class UnsupportedAccountException : Exception()
class LocalizedException(val errorTextRes: StringResource, vararg val formatArgs: Any) : Exception()
class MoneroRescanException(override val message: String?) : Exception()
class ZcashRescanException(override val message: String?) : Exception()

/** The wallet's chain has no adapter on this platform yet. */
class NotSupportedOnPlatformException : Exception(Translator.getString(Res.string.platform_not_supported_yet))

fun LocalizedException.toResString(): TranslatableString =
    TranslatableString.ResString(errorTextRes, *formatArgs)

fun LocalizedException.toLocalizedString(): String = toResString().toString()

sealed class EvmError(message: String? = null) : Throwable(message) {
    object InsufficientBalanceWithFee : EvmError()
    object CannotEstimateSwap : EvmError()
    object InsufficientLiquidity : EvmError()
    object LowerThanBaseGasLimit : EvmError()
    object BlockedByProvider : EvmError()
    class ExecutionReverted(message: String?) : EvmError(message)
    class RpcError(message: String?) : EvmError(message)
}

sealed class EvmAddressError : Throwable() {
    object InvalidAddress : EvmAddressError() {
        override fun getLocalizedMessage(): String {
            return Translator.getString(Res.string.SwapSettings_Error_InvalidAddress)
        }
    }
}

val Throwable.convertedError: Throwable
    get() = when (this) {
        is JsonRpc.ResponseError.RpcError -> {
            if (error.message.contains("insufficient funds for transfer") || error.message.contains(
                    "gas required exceeds allowance"
                )
            ) {
                EvmError.InsufficientBalanceWithFee
            } else if (error.message.contains("max fee per gas less than block base fee") ||
                error.message.contains("fee cap less than block base fee")
            ) {
                EvmError.LowerThanBaseGasLimit
            } else if (error.message.contains("execution reverted")) {
                EvmError.ExecutionReverted(error.message)
            } else {
                EvmError.RpcError(error.message)
            }
        }

        is AddressValidator.AddressValidationException -> {
            EvmAddressError.InvalidAddress
        }

        is retrofit2.HttpException -> {
            if (code() == 403) {
                EvmError.BlockedByProvider
            } else {
                val errorBody = response()?.errorBody()?.string()
                if (errorBody?.contains("Try to leave the buffer of ETH for gas") == true ||
                    errorBody?.contains("you may not have enough ETH balance for gas fee") == true ||
                    errorBody?.contains("Not enough ETH balance") == true ||
                    errorBody?.contains("insufficient funds for transfer") == true
                ) {
                    EvmError.InsufficientBalanceWithFee
                } else if (errorBody?.contains("cannot estimate") == true) {
                    EvmError.CannotEstimateSwap
                } else if (errorBody?.contains("insufficient liquidity") == true) {
                    EvmError.InsufficientLiquidity
                } else {
                    this
                }
            }
        }

        else -> this
    }
