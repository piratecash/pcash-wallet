package cash.p.terminal.modules.walletconnect

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.reown.walletkit.client.Wallet
import com.reown.walletkit.client.WalletKit
import cash.p.terminal.core.managers.EvmBlockchainManager
import io.horizontalsystems.ethereumkit.core.hexStringToIntOrNull

class WCWalletRequestHandler(
    private val evmBlockchainManager: EvmBlockchainManager,
    private val wcManager: WCManager,
) {
    private val gson by lazy { Gson() }

    fun rejectIfUnsupported(sessionRequest: Wallet.Model.SessionRequest): Boolean {
        if (!wcManager.isUnsupportedMethod(sessionRequest)) return false

        respond(
            sessionRequest,
            Wallet.Model.JsonRpcResponse.JsonRpcError(
                id = sessionRequest.request.id,
                code = 4200,
                message = "Method not supported"
            )
        )
        return true
    }

    fun handle(sessionRequest: Wallet.Model.SessionRequest): Boolean {
        try {
            if (rejectIfUnsupported(sessionRequest)) return true

            val request = sessionRequest.request
            val params = JsonParser.parseString(sessionRequest.request.params).asJsonArray
            val chain = gson.fromJson(params.first(), WalletConnectChain::class.java)

            return when (request.method) {
                "wallet_addEthereumChain",
                "wallet_switchEthereumChain" -> {
                    val blockchain =
                        chain.chainId.hexStringToIntOrNull()?.let { evmBlockchainManager.getBlockchain(it) }
                    val response = if (blockchain != null) {
                        Wallet.Model.JsonRpcResponse.JsonRpcResult(id = request.id, result = "null")
                    } else {
                        Wallet.Model.JsonRpcResponse.JsonRpcError(
                            id = request.id,
                            code = 4902,
                            message = "Unrecognized chain ID"
                        )
                    }
                    respond(sessionRequest, response)

                    true
                }

                else -> false
            }
        } catch (error: Throwable) {
            return false
        }
    }

    private fun respond(
        sessionRequest: Wallet.Model.SessionRequest,
        jsonRpcResponse: Wallet.Model.JsonRpcResponse,
    ) {
        WalletKit.respondSessionRequest(
            params = Wallet.Params.SessionRequestResponse(
                sessionTopic = sessionRequest.topic,
                jsonRpcResponse = jsonRpcResponse
            ),
            onSuccess = { WCDelegate.notifyPendingRequestsChanged() },
            onError = { error ->
                Log.e("WCWalletHandler", "${sessionRequest.request.method} response error: $error")
            })
    }

    data class WalletConnectChain(
        val chainId: String,
        val chainName: String?,
        val rpcUrls: List<String>?,
        val iconUrls: List<String>?,
        val nativeCurrency: WalletConnectNativeCurrency?,
        val blockExplorerUrls: List<String>?,
    )

    data class WalletConnectNativeCurrency(
        val name: String,
        val symbol: String,
        val decimals: Int,
    )

}
