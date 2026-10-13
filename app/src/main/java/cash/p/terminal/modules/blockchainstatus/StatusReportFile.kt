package cash.p.terminal.modules.blockchainstatus

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import cash.p.terminal.BuildConfig
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
    suspend fun share(fileName: String, header: String, logTag: String?, footer: String): Uri? {
        if (!mutex.tryLock()) return null
        try {
            val file = write(fileName, header, logTag, footer)
            return FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        } finally {
            mutex.unlock()
        }
    }

    internal suspend fun write(fileName: String, header: String, logTag: String?, footer: String): File =
        withContext(dispatcherProvider.io) {
            val target = File(context.cacheDir, fileName)
            val tmp = File(context.cacheDir, "$fileName.tmp")

            tmp.bufferedWriter().use { writer ->
                writer.append(header)
                AppLog.writeLog(writer, logTag)
                writer.append(footer)
            }

            if (!tmp.renameTo(target)) {
                throw IOException("Failed to rename $tmp to $target")
            }

            target
        }
}
