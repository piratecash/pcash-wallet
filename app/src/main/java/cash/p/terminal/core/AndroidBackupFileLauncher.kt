package cash.p.terminal.core

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import cash.p.terminal.feature.wallet.BackupFileLauncher
import cash.p.terminal.feature.wallet.BackupFileSource
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.error_no_file_manager
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import io.horizontalsystems.core.IPinComponent
import io.horizontalsystems.core.launchExternalActivity
import org.jetbrains.compose.resources.stringResource
import java.io.File
import java.io.IOException

class AndroidBackupFileLauncher(
    private val pinComponent: IPinComponent,
) : BackupFileLauncher {

    @Composable
    override fun rememberSaveBackupFile(
        bytes: ByteArray?,
        onSave: () -> Unit,
        onCancel: () -> Unit,
        onError: (Throwable) -> Unit,
    ): (fileName: String) -> Unit {
        val context = LocalContext.current
        val noFileManager = rememberNoFileManagerHandler(onCancel)
        // Read when the picker returns: the activity may have been recreated meanwhile.
        val currentBytes by rememberUpdatedState(bytes)
        val contract = ActivityResultContracts.CreateDocument("application/octet-stream")
        val launcher = rememberLauncherForActivityResult(contract) { uri ->
            val backupData = currentBytes
            if (uri == null || backupData == null) {
                onCancel()
                return@rememberLauncherForActivityResult
            }
            context.contentResolver.saveBackup(uri, backupData, onSave, onError)
        }
        return { fileName -> launchPicker(noFileManager) { launcher.launch(fileName) } }
    }

    @Composable
    override fun rememberOpenBackupFile(onOpen: (source: BackupFileSource, fileName: String?) -> Unit): () -> Unit {
        val context = LocalContext.current
        val noFileManager = rememberNoFileManagerHandler {}
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            // The application resolver: the read may outlive this activity.
            val contentResolver = context.applicationContext.contentResolver
            val source = BackupFileSource { contentResolver.openInputStreamSafe(uri)?.use { it.readBytes() } }
            onOpen(source, context.getFileName(uri))
        }
        return {
            launchPicker(noFileManager) {
                launcher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
            }
        }
    }

    @Composable
    private fun rememberNoFileManagerHandler(onCancel: () -> Unit): () -> Unit {
        val hudMessenger = rememberHudMessenger()
        val message = stringResource(Res.string.error_no_file_manager)
        return {
            hudMessenger.showError(message)
            onCancel()
        }
    }

    private fun launchPicker(onNoFileManager: () -> Unit, launch: () -> Unit) {
        try {
            pinComponent.launchExternalActivity(launch)
        } catch (_: ActivityNotFoundException) {
            onNoFileManager()
        }
    }
}

/** Reports success only after close(): a provider may still fail the write there. */
internal fun ContentResolver.saveBackup(
    uri: Uri,
    bytes: ByteArray,
    onSave: () -> Unit,
    onError: (Throwable) -> Unit,
) {
    try {
        // "w" may keep the tail of a longer existing file.
        val stream = openOutputStream(uri, "wt") ?: throw IOException("Unable to open file")
        stream.use { it.write(bytes) }
    } catch (e: Throwable) {
        onError(e)
        return
    }
    onSave()
}

private fun Context.getFileName(uri: Uri): String? = when (uri.scheme) {
    ContentResolver.SCHEME_CONTENT -> getContentFileName(uri)
    else -> uri.path?.let(::File)?.name
}

private fun Context.getContentFileName(uri: Uri): String? = tryOrNull {
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        cursor.moveToFirst()
        return@use cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME).let(cursor::getString)
    }
}
