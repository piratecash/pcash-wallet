package cash.p.terminal.modules.blockchainstatus

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ISystemInfoManager
import io.horizontalsystems.core.logger.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val REPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC)

fun formatReportTime(epochMillis: Long): String =
    REPORT_TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis))

class BlockchainStatusViewModel(
    private val provider: BlockchainStatusProvider,
    dispatcherProvider: DispatcherProvider,
    private val systemInfoManager: ISystemInfoManager,
    private val buildInfo: StatusReportBuildInfo,
) : ViewModel() {

    val reportFileName = "${provider.logFilterTag.lowercase()}_blockchain_status_report.txt"

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

    fun report(): StatusReport {
        val state = _uiState.value
        return StatusReport(
            fileName = reportFileName,
            header = buildShareHeader(state.statusSections, state.sharedSection),
            logTag = provider.logFilterTag,
            footer = "",
        )
    }

    /** Ignored while a previous share is still running. */
    suspend fun share(export: suspend (StatusReport) -> Unit) {
        if (_uiState.getAndUpdate { it.copy(shareInProgress = true) }.shareInProgress) return
        try {
            export(report())
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
        buildInfo.entries().forEach { (title, value) ->
            appendLine("$title: $value")
        }
        appendLine("Device Model: ${systemInfoManager.deviceModel}")
        appendLine("OS Version: ${systemInfoManager.osVersion}")
        appendLine("Device ABIs: ${buildInfo.deviceAbis}")
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
