package cash.p.terminal.core.ethereum

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.unknown_send_error
import io.horizontalsystems.core.Translator
import io.horizontalsystems.ethereumkit.api.jsonrpc.JsonRpc

fun Throwable.evmSendErrorMessage(): String =
    if (this is JsonRpc.ResponseError.RpcError) {
        error.message
    } else {
        message ?: Translator.getString(Res.string.unknown_send_error)
    }
