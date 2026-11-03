package cash.p.terminal.modules.send.offline

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import cash.p.terminal.resources.Button_Share
import cash.p.terminal.resources.Res
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.IPinComponent
import io.horizontalsystems.core.launchExternalActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import java.io.File

class AndroidOfflineTransactionFiles(
    private val context: Context,
    private val fileTransfer: OfflineTransactionFileTransfer,
    private val pinComponent: IPinComponent,
    private val dispatcherProvider: DispatcherProvider,
) : OfflineTransactionFiles {

    override suspend fun read(location: String): OfflineTransactionFileTransfer.ReadResult {
        val uri = Uri.parse(location)
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return OfflineTransactionFileTransfer.ReadResult.Unavailable
        return fileTransfer.read { context.contentResolver.openInputStream(uri) }
    }

    @Composable
    override fun rememberOpenFile(onOpen: (location: String) -> Unit, onError: () -> Unit): () -> Unit {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { onOpen(it.toString()) }
        }
        return {
            try {
                pinComponent.launchExternalActivity {
                    launcher.launch(arrayOf("text/plain", "application/octet-stream"))
                }
            } catch (_: ActivityNotFoundException) {
                onError()
            }
        }
    }

    @Composable
    override fun rememberSaveFile(
        content: String,
        onResult: (OfflineFileSaveResult) -> Unit,
    ): (fileName: String) -> Unit {
        val scope = rememberCoroutineScope()
        val currentContent by rememberUpdatedState(content)
        val currentOnResult by rememberUpdatedState(onResult)
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri == null) {
                currentOnResult(OfflineFileSaveResult.Cancelled)
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                val saved = fileTransfer.save(currentContent) { context.contentResolver.openOutputStream(uri) }
                currentOnResult(if (saved) OfflineFileSaveResult.Saved else OfflineFileSaveResult.Failed)
            }
        }
        return { fileName ->
            try {
                pinComponent.launchExternalActivity { launcher.launch(fileName) }
            } catch (_: ActivityNotFoundException) {
                currentOnResult(OfflineFileSaveResult.Failed)
            }
        }
    }

    @Composable
    override fun rememberShareFile(content: String, onResult: (shared: Boolean) -> Unit): () -> Unit {
        val activityContext = LocalContext.current
        val scope = rememberCoroutineScope()
        val title = stringResource(Res.string.Button_Share)
        val currentContent by rememberUpdatedState(content)
        val currentOnResult by rememberUpdatedState(onResult)
        return {
            scope.launch {
                val uri = createShareUri(currentContent)
                currentOnResult(uri != null && shareTransactionFile(activityContext, uri, title))
            }
        }
    }

    private suspend fun createShareUri(content: String): Uri? =
        withContext(dispatcherProvider.io) {
            val directory = File(context.cacheDir, SHARE_DIRECTORY)
            try {
                directory.mkdirs()
                fileTransfer.cleanupShareDirectory(directory)
                val file = File.createTempFile(SHARE_FILE_PREFIX, FILE_EXTENSION, directory)
                try {
                    file.outputStream().use { output -> fileTransfer.writeContent(output, content) }
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                } catch (e: CancellationException) {
                    file.delete()
                    throw e
                } catch (_: Exception) {
                    file.delete()
                    null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }

    private fun shareTransactionFile(context: Context, uri: Uri, title: String): Boolean =
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(title, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, title))
            true
        } catch (_: RuntimeException) {
            false
        }

    private companion object {
        const val SHARE_DIRECTORY = "offline-transactions"
        const val SHARE_FILE_PREFIX = "pcash-offline-transaction-"
        const val FILE_EXTENSION = ".txt"
    }
}
