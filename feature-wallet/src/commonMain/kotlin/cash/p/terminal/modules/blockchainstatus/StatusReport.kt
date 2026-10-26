package cash.p.terminal.modules.blockchainstatus

import androidx.compose.runtime.Composable
import io.horizontalsystems.core.logger.AppLog

/** [header], then the app log for [logTag] (the whole log when `null`), then [footer]. */
class StatusReport(val fileName: String, val header: String, val logTag: String?, val footer: String)

suspend fun writeStatusReport(writer: Appendable, header: String, logTag: String?, footer: String) {
    writer.append(header)
    AppLog.writeLog(writer, logTag)
    writer.append(footer)
}

/** Hands a status report to the platform's share sheet or file picker. */
interface StatusReportExporter {
    /** `null` where the platform has no share sheet; the action throws when the report cannot be written. */
    @Composable
    fun rememberShareReport(): (suspend (StatusReport) -> Unit)?

    /**
     * Returns a launcher that asks where to save the report under [fileName] (does nothing while `null`),
     * builds it with [buildReport] once the place is picked and tells [onResult] whether it was written.
     */
    @Composable
    fun rememberSaveReport(
        fileName: String?,
        buildReport: () -> StatusReport,
        onResult: (saved: Boolean) -> Unit,
    ): () -> Unit
}

/** Build facts of the running app, printed at the top of a status report. */
interface StatusReportBuildInfo {
    fun entries(): Map<String, String>
    val deviceAbis: String
}
