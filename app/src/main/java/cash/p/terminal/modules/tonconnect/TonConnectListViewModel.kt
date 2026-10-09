package cash.p.terminal.modules.tonconnect

import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import cash.p.terminal.core.managers.TonConnectManager
import cash.p.terminal.wallet.IAccountManager
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.tonkit.tonconnect.TonConnectKit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TonConnectListViewModel(
    accountManager: IAccountManager,
    private val tonConnectManager: TonConnectManager,
) : ViewModelUiState<TonConnectListUiState>() {

    private var dapps: Map<String, List<DAppEntity>> = emptyMap()
    private var dAppRequestUri: String? = null
    private var error: Throwable? = null

    private val logger = Logger.withTag("TonConnectListViewModel")

    private val accountNamesById = accountManager.accounts.associate { it.id to it.name }

    override fun createState() = TonConnectListUiState(
        dapps = dapps,
        dAppRequestUri = dAppRequestUri,
        error = error
    )

    init {
        viewModelScope.launch {
            launch { requestKit() }
            tonConnectManager.getDApps().collect {
                dapps = it.groupBy { entity ->
                    accountNamesById.getOrDefault(
                        entity.walletId,
                        entity.walletId
                    )
                }
                emitState()
            }
        }
    }

    // The manager builds the kit lazily; without a request here a failed startup attempt is never retried.
    private suspend fun requestKit() {
        try {
            tonConnectManager.kit()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.w(e) { "TON Connect kit initialization failed" }
        }
    }

    fun setConnectionUri(v: String) {
        error = null

        try {
            TonConnectKit.readData(v)
            dAppRequestUri = v
        } catch (e: Throwable) {
            error = e
        }
        emitState()
    }

    fun onDappRequestHandled() {
        dAppRequestUri = null
        emitState()
    }

    fun onErrorHandled() {
        error = null
        emitState()
    }

    fun disconnect(dapp: DAppEntity) {
        viewModelScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            this.error = error
            emitState()
        }) {
            tonConnectManager.kit().disconnect(dapp)
        }
    }

}


data class TonConnectListUiState(
    val dapps: Map<String, List<DAppEntity>>,
    val dAppRequestUri: String?,
    val error: Throwable?
)
