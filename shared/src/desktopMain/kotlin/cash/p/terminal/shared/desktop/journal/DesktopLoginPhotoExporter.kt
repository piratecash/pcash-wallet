package cash.p.terminal.shared.desktop.journal

import cash.p.terminal.core.tryOrNull
import cash.p.terminal.feature.logging.photo.LoginPhoto
import cash.p.terminal.feature.logging.photo.LoginPhotoExporter
import cash.p.terminal.feature.logging.photo.LoginPhotoSource
import cash.p.terminal.feature.logging.photo.PhotoSaveResult
import cash.p.terminal.shared.desktop.chooseFile
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ICurrentDateProvider
import kotlinx.coroutines.withContext
import java.awt.FileDialog

/** The photo leaves the journal only where the user points the save dialog; nothing is written before that. */
class DesktopLoginPhotoExporter(
    private val source: LoginPhotoSource,
    private val dateProvider: ICurrentDateProvider,
    private val dispatcherProvider: DispatcherProvider,
) : LoginPhotoExporter {

    override suspend fun save(photo: LoginPhoto): PhotoSaveResult {
        val bytes = source.read(photo) ?: return PhotoSaveResult.FAILED
        val file = withContext(dispatcherProvider.main) {
            chooseFile(FileDialog.SAVE, "login_${dateProvider.currentDate.time}.jpg")
        } ?: return PhotoSaveResult.CANCELLED
        val saved = withContext(dispatcherProvider.io) { tryOrNull { file.writeBytes(bytes) } }
        return if (saved != null) PhotoSaveResult.SAVED else PhotoSaveResult.FAILED
    }
}
