package cash.p.terminal.shared.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import cash.p.terminal.modules.send.offline.OfflineFileSaveResult
import cash.p.terminal.modules.send.offline.OfflineQrImageSaver
import cash.p.terminal.modules.send.offline.OfflineTransactionFileTransfer
import cash.p.terminal.modules.send.offline.OfflineTransactionFiles
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.awt.FileDialog
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URI

/** AWT file dialogs instead of the Android document picker; desktop has no share sheet. */
class DesktopOfflineTransactionFiles(
    private val fileTransfer: OfflineTransactionFileTransfer,
    private val dispatcherProvider: DispatcherProvider,
) : OfflineTransactionFiles, OfflineQrImageSaver {

    override suspend fun read(location: String): OfflineTransactionFileTransfer.ReadResult =
        fileTransfer.read { FileInputStream(File(URI(location))) }

    @Composable
    override fun rememberOpenFile(onOpen: (location: String) -> Unit, onError: () -> Unit): () -> Unit = {
        chooseFile(FileDialog.LOAD, null)?.let { file -> onOpen(file.toURI().toString()) }
    }

    @Composable
    override fun rememberSaveFile(
        content: String,
        onResult: (OfflineFileSaveResult) -> Unit,
    ): (fileName: String) -> Unit {
        val scope = rememberCoroutineScope()
        val currentContent by rememberUpdatedState(content)
        val currentOnResult by rememberUpdatedState(onResult)
        return { fileName ->
            val file = chooseFile(FileDialog.SAVE, fileName)
            if (file == null) {
                currentOnResult(OfflineFileSaveResult.Cancelled)
            } else {
                scope.launch { currentOnResult(saveText(file, currentContent)) }
            }
        }
    }

    internal suspend fun saveText(file: File, content: String): OfflineFileSaveResult =
        saveAtomically(file) { fileTransfer.encode(content) }

    @Composable
    override fun rememberShareFile(content: String, onResult: (shared: Boolean) -> Unit): (() -> Unit)? = null

    override suspend fun save(image: ImageBitmap): OfflineFileSaveResult {
        val file = chooseFile(FileDialog.SAVE, "pcash-offline-tx-${System.currentTimeMillis()}.png")
            ?: return OfflineFileSaveResult.Cancelled
        return saveAtomically(file) {
            Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
        }
    }

    /** Never truncates [file] in place: a failed overwrite keeps the previous file. */
    private suspend fun saveAtomically(file: File, bytes: () -> ByteArray?): OfflineFileSaveResult =
        withContext(dispatcherProvider.io) {
            val data = bytes() ?: return@withContext OfflineFileSaveResult.Failed
            try {
                writeFileAtomically(file.toPath(), data)
                OfflineFileSaveResult.Saved
            } catch (_: IOException) {
                OfflineFileSaveResult.Failed
            }
        }
}
