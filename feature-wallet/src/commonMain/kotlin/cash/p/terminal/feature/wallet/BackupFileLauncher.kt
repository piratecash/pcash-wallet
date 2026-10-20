package cash.p.terminal.feature.wallet

import androidx.compose.runtime.Composable

/** The platform file picker for backup files. */
interface BackupFileLauncher {
    /** Returns a launcher that asks where to save [bytes] under the suggested `fileName`. */
    @Composable
    fun rememberSaveBackupFile(
        bytes: ByteArray?,
        onSave: () -> Unit,
        onCancel: () -> Unit,
        onError: (Throwable) -> Unit,
    ): (fileName: String) -> Unit

    /** Returns a launcher that lets the user pick a file; the caller reads it in a scope it owns. */
    @Composable
    fun rememberOpenBackupFile(onOpen: (source: BackupFileSource, fileName: String?) -> Unit): () -> Unit
}

/** A picked backup file; blocking, so read it off the main thread. */
fun interface BackupFileSource {
    /** `null` when the platform gives no stream for the file. */
    fun read(): ByteArray?
}
