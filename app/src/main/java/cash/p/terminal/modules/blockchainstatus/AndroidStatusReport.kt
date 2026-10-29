package cash.p.terminal.modules.blockchainstatus

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import cash.p.terminal.BuildConfig
import cash.p.terminal.R
import cash.p.terminal.core.App
import io.horizontalsystems.core.IPinComponent
import io.horizontalsystems.core.launchExternalActivity
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

internal fun reportBuildInfo(appVersion: String, gitBranch: String): Map<String, String> =
    linkedMapOf(
        "Report Time" to formatReportTime(System.currentTimeMillis()),
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

class AndroidStatusReportBuildInfo : StatusReportBuildInfo {
    override fun entries(): Map<String, String> = reportBuildInfo(BuildConfig.VERSION_NAME, BuildConfig.GIT_BRANCH)
    override val deviceAbis: String get() = Build.SUPPORTED_ABIS.joinToString()
}

class AndroidStatusReportExporter(
    private val statusReportFile: StatusReportFile,
    private val pinComponent: IPinComponent,
) : StatusReportExporter {

    @Composable
    override fun rememberShareReport(): suspend (StatusReport) -> Unit {
        val context = LocalContext.current
        return { report ->
            statusReportFile.share(report.fileName, report.header, report.logTag, report.footer)?.let { uri ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, getString(report.shareText))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, null))
            }
        }
    }

    @Composable
    override fun rememberSaveReport(
        fileName: String?,
        buildReport: () -> StatusReport,
        onResult: (saved: Boolean) -> Unit,
    ): () -> Unit {
        val coroutineScope = rememberCoroutineScope()
        // Read when the picker returns: the activity may have been recreated meanwhile.
        val currentBuildReport by rememberUpdatedState(buildReport)
        val currentOnResult by rememberUpdatedState(onResult)
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val report = currentBuildReport()
            coroutineScope.launch {
                currentOnResult(statusReportFile.save(uri, report.header, report.logTag, report.footer))
            }
        }
        return {
            fileName?.let { name ->
                try {
                    pinComponent.launchExternalActivity { launcher.launch(name) }
                } catch (_: ActivityNotFoundException) {
                    currentOnResult(false)
                }
            }
        }
    }
}
