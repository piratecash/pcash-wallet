package cash.p.terminal.shared.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import cash.p.terminal.modules.blockchainstatus.StatusReport
import cash.p.terminal.modules.blockchainstatus.StatusReportBuildInfo
import cash.p.terminal.modules.blockchainstatus.StatusReportExporter
import cash.p.terminal.modules.blockchainstatus.formatReportTime
import cash.p.terminal.modules.blockchainstatus.writeStatusReport
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ISystemInfoManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.io.File
import java.io.IOException

internal class DesktopStatusReportBuildInfo(private val systemInfoManager: ISystemInfoManager) : StatusReportBuildInfo {
    override fun entries(): Map<String, String> = linkedMapOf(
        "Report Time" to formatReportTime(System.currentTimeMillis()),
        "App Version" to systemInfoManager.appVersion,
    )

    override val deviceAbis: String = System.getProperty("os.arch").orEmpty()
}

/** No share sheet on desktop: the report is only saved through a file dialog. */
internal class DesktopStatusReportExporter(private val dispatcherProvider: DispatcherProvider) : StatusReportExporter {

    @Composable
    override fun rememberShareReport(): (suspend (StatusReport) -> Unit)? = null

    @Composable
    override fun rememberSaveReport(
        fileName: String?,
        buildReport: () -> StatusReport,
        onResult: (saved: Boolean) -> Unit,
    ): () -> Unit {
        val coroutineScope = rememberCoroutineScope()
        val currentBuildReport by rememberUpdatedState(buildReport)
        val currentOnResult by rememberUpdatedState(onResult)
        return save@{
            val name = fileName ?: return@save
            val file = chooseFile(FileDialog.SAVE, name) ?: return@save
            val report = currentBuildReport()
            coroutineScope.launch { currentOnResult(write(report, file)) }
        }
    }

    private suspend fun write(report: StatusReport, file: File): Boolean = withContext(dispatcherProvider.io) {
        val text = buildString { writeStatusReport(this, report.header, report.logTag, report.footer) }
        try {
            writeFileAtomically(file.toPath(), text.toByteArray())
            true
        } catch (_: IOException) {
            false
        }
    }
}
