package cash.p.terminal.ledger.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.ledger.domain.ICreateLedgerWalletUseCase
import cash.p.terminal.ledger.domain.LedgerAppProgress
import cash.p.terminal.ledger.domain.LedgerConnector
import cash.p.terminal.ledger.domain.LedgerNoSupportedAppsException
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LedgerWalletViewModel(
    private val accountName: String,
    private val createLedgerWallet: ICreateLedgerWalletUseCase,
    connector: LedgerConnector,
) : ViewModel() {

    var uiState by mutableStateOf(LedgerSetupUiState())
        private set

    private var connectJob: Job? = null
    private var accountCreated = false

    init {
        viewModelScope.launch {
            connector.awaitingPermission.collect { uiState = uiState.copy(awaitingPermission = it) }
        }
    }

    fun connect() {
        if (connectJob?.isActive == true) return

        connectJob = viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            try {
                val creation = createLedgerWallet(
                    accountName = accountName,
                    onOpeningApp = { uiState = uiState.copy(progress = it) },
                    onExportingViewKey = { uiState = uiState.copy(progress = null, exportingViewKey = true) },
                )
                accountCreated = creation.created
                uiState = when {
                    creation.skippedApps.isNotEmpty() -> uiState.copy(skippedApps = creation.skippedApps)
                    creation.created -> uiState.copy(success = true)
                    else -> uiState.copy(error = LedgerNoSupportedAppsException())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(e) { "Failed to connect Ledger" }
                uiState = uiState.copy(error = e)
            } finally {
                uiState = uiState.copy(loading = false, progress = null, exportingViewKey = false)
            }
        }
    }

    fun dismissSkippedApps() {
        uiState = uiState.copy(skippedApps = emptyList(), success = accountCreated)
    }

    private companion object {
        val logger = Logger.withTag("LedgerWalletViewModel")
    }
}

data class LedgerSetupUiState(
    val loading: Boolean = false,
    val awaitingPermission: Boolean = false,
    val progress: LedgerAppProgress? = null,
    val exportingViewKey: Boolean = false,
    val skippedApps: List<String> = emptyList(),
    val error: Throwable? = null,
    val success: Boolean = false,
)
