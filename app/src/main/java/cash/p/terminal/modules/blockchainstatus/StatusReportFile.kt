package cash.p.terminal.modules.blockchainstatus

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import cash.p.terminal.BuildConfig
import cash.p.terminal.core.tryOrNull
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.logger.AppLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class StatusReportFile(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val mutex = Mutex()

    /** Returns null when another export is already running instead of overlapping it. */
    suspend fun share(fileName: String, header: String, logTag: String?, footer: String): Uri? = exclusively(null) {
        val file = write(fileName, header, logTag, footer)
        FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
    }

    /** Returns false when [uri] cannot be written or another export is already running. */
    suspend fun save(uri: Uri, header: String, logTag: String?, footer: String): Boolean = exclusively(false) {
        withContext(dispatcherProvider.io) {
            tryOrNull {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writeReport(writer, header, logTag, footer)
                }
            } != null
        }
    }

    internal suspend fun write(fileName: String, header: String, logTag: String?, footer: String): File =
        withContext(dispatcherProvider.io) {
            val target = File(context.cacheDir, fileName)
            val tmp = File(context.cacheDir, "$fileName.tmp")

            tmp.bufferedWriter().use { writer -> writeReport(writer, header, logTag, footer) }

            if (!tmp.renameTo(target)) {
                throw IOException("Failed to rename $tmp to $target")
            }

            target
        }

    private suspend fun writeReport(writer: Appendable, header: String, logTag: String?, footer: String) {
        writer.append(header)
        AppLog.writeLog(writer, logTag)
        writer.append(footer)
    }

    private inline fun <T> exclusively(onBusy: T, block: () -> T): T {
        if (!mutex.tryLock()) return onBusy
        try {
            return block()
        } finally {
            mutex.unlock()
        }
    }
}
