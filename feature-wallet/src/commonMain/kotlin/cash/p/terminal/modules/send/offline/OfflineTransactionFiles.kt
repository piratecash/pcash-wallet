package cash.p.terminal.modules.send.offline

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.offline_broadcast_file_read_failed
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import io.horizontalsystems.core.Translator
import org.koin.compose.koinInject

/** Platform side of offline transaction files; the bounded read and the write are [OfflineTransactionFileTransfer]. */
interface OfflineTransactionFiles {
    /** Reads the file at [location]: a content URI on Android, a file URI on desktop. */
    suspend fun read(location: String): OfflineTransactionFileTransfer.ReadResult

    /** Returns a launcher that lets the user pick a file; [onOpen] gets its location for [read]. */
    @Composable
    fun rememberOpenFile(onOpen: (location: String) -> Unit, onError: () -> Unit): () -> Unit

    /** Returns a launcher that asks where to save [content] under the suggested `fileName`. */
    @Composable
    fun rememberSaveFile(content: String, onResult: (OfflineFileSaveResult) -> Unit): (fileName: String) -> Unit

    /** Returns a launcher that hands [content] to another app as a file; `null` where the platform has no share sheet. */
    @Composable
    fun rememberShareFile(content: String, onResult: (shared: Boolean) -> Unit): (() -> Unit)?
}

/** Saves the QR code of an offline transaction as a PNG image. */
interface OfflineQrImageSaver {
    suspend fun save(image: ImageBitmap): OfflineFileSaveResult
}

enum class OfflineFileSaveResult { Saved, Failed, Cancelled }

/** The settings «Import transaction file» entry: pick a file, then check it on the broadcast page. */
@Composable
fun rememberImportTransactionFile(navigation: HSNavigation): () -> Unit {
    val hud = rememberHudMessenger()
    return koinInject<OfflineTransactionFiles>().rememberOpenFile(
        onOpen = { location ->
            navigation.slideFromRight(OfflineBroadcastPage(OfflineBroadcastPage.Input(fileUri = location)))
        },
        onError = { hud.showError(Translator.getString(Res.string.offline_broadcast_file_read_failed)) },
    )
}
