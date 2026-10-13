package cash.p.terminal.modules.blockchainstatus

import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.BuildConfig
import cash.p.terminal.R
import cash.p.terminal.core.App
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ISystemInfoManager
import io.horizontalsystems.core.logger.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun reportBuildInfo(appVersion: String, gitBranch: String): Map<String, String> =
    linkedMapOf(
        "App Version" to appVersion,
        "Version Code" to BuildConfig.VERSION_CODE.toString(),
        "Build Type" to BuildConfig.BUILD_TYPE,
        "Git Branch" to gitBranch,
        "Git Hash" to BuildConfig.GIT_HASH,
        "Mapping ID" to (
            App.instance.getString(R.string.com_google_firebase_crashlytics_mapping_file_id)
                .takeUnless { id -> id.all { it == '0' } } ?: "None"
        ),
        "Native ABI" to nativeAbiLabel(App.instance.applicationInfo.nativeLibraryDir, Build.SUPPORTED_ABIS.toList()),
    )

class BlockchainStatusViewModel(
    private val provider: BlockchainStatusProvider,
    dispatcherProvider: DispatcherProvider,
    private val systemInfoManager: ISystemInfoManager,
    private val statusReportFile: StatusReportFile,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BlockchainStatusUiState(
            blockchainName = provider.blockchainName,
            kitVersion = provider.kitVersion,
            kitStarted = provider.kitStarted,
            statusSections = emptyList(),
            sharedSection = null,
            logGroups = emptyList(),
            statusAsText = null,
            statusLoading = true,
            logsLoading = true,
            shareInProgress = false,
        )
    )
    val uiState: StateFlow<BlockchainStatusUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(dispatcherProvider.io) {
            val logGroups = buildLogGroups(AppLog.getRecentLog(provider.logFilterTag))
            _uiState.update { it.copy(logGroups = logGroups, logsLoading = false) }
            recomputeStatusAsTextIfReady()
        }

        viewModelScope.launch(dispatcherProvider.io) {
            val status = provider.getStatus()
            _uiState.update {
                it.copy(
                    statusSections = status.sections,
                    sharedSection = status.sharedSection,
                    statusLoading = false,
                )
            }
            recomputeStatusAsTextIfReady()
        }
    }

    suspend fun prepareShareUri(): Uri? {
        if (_uiState.getAndUpdate { it.copy(shareInProgress = true) }.shareInProgress) return null
        try {
            val state = _uiState.value
            return statusReportFile.share(
                fileName = "${provider.logFilterTag.lowercase()}_blockchain_status_report.txt",
                header = buildShareHeader(state.statusSections, state.sharedSection),
                logTag = provider.logFilterTag,
                footer = "",
            )
        } finally {
            _uiState.update { it.copy(shareInProgress = false) }
        }
    }

    private fun recomputeStatusAsTextIfReady() {
        _uiState.update { state ->
            if (state.statusLoading || state.logsLoading) return@update state
            state.copy(
                statusAsText = buildCopyText(
                    buildStatusText(state.statusSections, state.sharedSection),
                    state.logGroups,
                )
            )
        }
    }

    private fun buildShareHeader(
        sections: List<StatusSection>,
        sharedSection: StatusSection?,
    ): String = buildString {
        append(buildStatusText(sections, sharedSection))
        appendLine("App Log")
    }

    private fun buildStatusText(
        sections: List<StatusSection>,
        sharedSection: StatusSection?,
    ): String = buildString {
        appendLine("${provider.blockchainName} Status")
        reportBuildInfo(BuildConfig.VERSION_NAME, BuildConfig.GIT_BRANCH).forEach { (title, value) ->
            appendLine("$title: $value")
        }
        appendLine("Device Model: ${systemInfoManager.deviceModel}")
        appendLine("OS Version: ${systemInfoManager.osVersion}")
        appendLine("Device ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Kit Version: ${provider.kitVersion}")
        appendLine("Kit Started: ${if (provider.kitStarted) "Yes" else "No"}")
        appendLine()

        sections.forEach { appendStatusSection(it) }
        sharedSection?.let { appendStatusSection(it) }
    }
}

@Immutable
data class BlockchainStatusUiState(
    val blockchainName: String,
    val kitVersion: String,
    val kitStarted: Boolean,
    val statusSections: List<StatusSection>,
    val sharedSection: StatusSection?,
    val logGroups: List<LogGroup>,
    val statusAsText: String?,
    val statusLoading: Boolean,
    val logsLoading: Boolean,
    val shareInProgress: Boolean,
) {
    val loading: Boolean get() = statusLoading || logsLoading
}
