package cash.p.terminal.modules.syncerror

import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.NetworkErrorTracker
import cash.p.terminal.core.tor.TorSyncState
import cash.p.terminal.wallet.Wallet
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ISystemInfoManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SyncErrorService(
    private val wallet: Wallet,
    private val adapterManager: IAdapterManager,
    val reportEmail: String,
    private val btcBlockchainManager: BtcBlockchainManager,
    private val evmBlockchainManager: EvmBlockchainManager,
    private val networkErrorTracker: NetworkErrorTracker,
    private val systemInfoManager: ISystemInfoManager,
    private val torSyncState: TorSyncState,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val logger = Logger.withTag("SyncErrorService")

    val blockchainWrapper by lazy {
        btcBlockchainManager.blockchain(wallet.token.blockchainType)?.let {
            SyncErrorModule.BlockchainWrapper(it, SyncErrorModule.BlockchainWrapper.Type.Bitcoin)
        } ?: run {
            evmBlockchainManager.getBlockchain(wallet.token)?.let {
                SyncErrorModule.BlockchainWrapper(it, SyncErrorModule.BlockchainWrapper.Type.Evm)
            }
        }
    }

    val coinName: String = wallet.coin.name

    val sourceChangeable = blockchainWrapper != null

    // The sheet closes right after Retry, so the work must not depend on its lifetime
    fun retry() {
        dispatcherProvider.applicationScope.launch {
            try {
                torSyncState.requestNewCircuits()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(e) { "New Tor circuits request failed" }
            }
            adapterManager.refreshByWallet(wallet)
        }
    }

    fun buildReportBody(error: String): String = buildString {
        appendLine(error)
        appendLine()
        appendLine("--- Diagnostic Info ---")
        appendLine("App Version: ${systemInfoManager.appVersionFull}")
        appendLine("Device: ${systemInfoManager.deviceModel}")
        appendLine("OS: ${systemInfoManager.osVersion}")
        appendLine("Coin: ${wallet.coin.code} (${wallet.coin.name})")
        appendLine("Token Type: ${wallet.token.type}")
        appendLine("Blockchain: ${wallet.token.blockchainType.uid}")
        appendLine("Account Type: ${wallet.account.type.description}")
        appendLine("Account Origin: ${wallet.account.origin.value}")
        networkErrorTracker.errorInfo(wallet.token.blockchainType, wallet.account.id)
            ?.takeIf { it.isNotEmpty() }?.let { info ->
                appendLine()
                appendLine("--- ${wallet.token.blockchain.name} Network Error ---")
                info.forEach { (key, value) ->
                    appendLine("$key: $value")
                }
            }
    }
}
